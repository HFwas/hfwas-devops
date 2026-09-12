# Colima + Clash 代理下 Docker 拉取镜像失败排查与修复

> 日期：2026-09-12  
> 版本：v0.1  
> 关联： [AGENTS.md](../../AGENTS.md)（代理/网络规则）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-12 | 初版：Docker pull EOF 故障根因分析与修复方案 |

---

## 1. 故障现象

```bash
docker pull python:3.12-alpine
Error response from daemon: Get "https://registry-1.docker.io/v2/": EOF
```

- 环境：macOS (Apple Silicon) + Colima + Docker runtime
- 网络：Clash Verge (verge-mihomo) 本地代理 `127.0.0.1:7890`
- Colima 配置：`network.address: true`，VM IP `192.168.5.1`，宿主机 gateway `192.168.5.2`

---

## 2. 排查过程

### 2.1 第一层：代理是否运行

```bash
lsof -i :7890 -P
# verge-mihomo 在监听，Clash Verge 正常运行

curl -fsSL -x http://127.0.0.1:7890 https://www.google.com
# ❌ SSL_ERROR_SYSCALL — 代理通但路由有问题
```

代理进程在运行但出站 HTTPS 失败。

### 2.2 第二层：Clash 节点与路由链

Clash 外部控管 API 绑在 Unix socket 上：

```bash
curl -s --unix-socket /tmp/verge/verge-mihomo.sock http://localhost/proxies
```

检查发现：

```
GLOBAL:     Selector now=DIRECT          ← 全局流量走直连
🕹 规则之外: Selector now=🌀 负载均衡
🌀 负载均衡: Selector now=🇭🇰 香港
🇭🇰 香港:    Selector now=香港|01         ← alive=false 死节点
```

**根因一**：规则模式下未匹配流量走 `🕹 规则之外 → 🌀 负载均衡 → 🇭🇰 香港 → 香港|01`，而 `香港|01` 节点已失效。

修复：

```bash
# 切换 GLOBAL 到香港|07（存活节点）
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/GLOBAL \
  -H "Content-Type: application/json" -d '{"name":"香港|07"}'

# 修复代理链
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/🕹%20规则之外 \
  -H "Content-Type: application/json" -d '{"name":"✈️ 手动选择"}'
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/✈️%20手动选择 \
  -H "Content-Type: application/json" -d '{"name":"香港|07"}'
```

验证：本机 curl 过代理到 Google 成功。

### 2.3 第三层：Colima VM 到宿主机代理不通

Docker daemon 配置了代理 `http://192.168.5.2:7890`，但仍拉取失败。排查：

```bash
# 从 Colima VM 内测代理连通性
colima ssh -- curl --proxy http://192.168.5.2:7890 https://www.google.com
# ❌ 502 Bad Gateway — 宿主机 7890 端口接受连接但拒绝转发
```

检查 Clash 监听：

```bash
lsof -iTCP -sTCP:LISTEN -P | grep verge-mih
# verge-mih  5808  TCP 127.0.0.1:7890 (LISTEN)
```

**根因二**：Clash 的 `mixed-port: 7890` 只绑在 `127.0.0.1`（`allow-lan: false`），Colima VM 从 `192.168.5.2:7890` 访问被拒。

修复：

```bash
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PATCH \
  http://localhost/configs \
  -H "Content-Type: application/json" -d '{"allow-lan": true}'

# 验证：Clash 改为 *:7890 (LISTEN)
```

### 2.4 第四层：代理节点对 Docker Hub 大流量不稳定

即使代理链修复，Docker pull 在下载 layers 时频繁断连：

```
ERROR: download failed after attempts=6: EOF
```

节点存活监测（health check）通过，但大数据传输断流。遍历所有节点测试：

```bash
for node in "美国|02" "美国|05" "日本|02" "日本|05" "韩国|01" "新加坡|04"; do
  # 切换节点...
  curl -fsSL -x http://127.0.0.1:7890 https://registry-1.docker.io/v2/
done
# 全部返回 401 — 节点可通 Docker Hub，但 layers 下载不稳定
```

不同时间段同一节点表现也不一致（时而通 Google 时而不通），属订阅节点质量问题。

**根因三**：代理订阅节点对 Docker Hub 等大流量站点不稳定，不适合镜像拉取。

### 2.5 第五层：国内镜像源也被代理绕路

配置 Docker registry mirrors 后仍失败，因为 Colima 的 `/etc/environment` 设置了全局代理：

```
HTTPS_PROXY=http://192.168.5.2:7890
HTTP_PROXY=http://192.168.5.2:7890
```

国内镜像源（DaoCloud、阿里云）的流量也走了海外代理节点，绕路且不可靠。

**根因四**：国内镜像源未加入 `NO_PROXY`。

---

## 3. 最终修复方案

### 3.1 修复 Clash 代理配置

```bash
# 1. 开启 allow-lan（允许其他设备访问本机代理）
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PATCH \
  http://localhost/configs \
  -H "Content-Type: application/json" -d '{"allow-lan": true}'

# 2. 修复代理链：规则之外 → 手动选择 → 稳定节点（如新加坡|04）
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/🕹%20规则之外 \
  -H "Content-Type: application/json" -d '{"name":"✈️ 手动选择"}'
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/✈️%20手动选择 \
  -H "Content-Type: application/json" -d '{"name":"新加坡|04"}'
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/GLOBAL \
  -H "Content-Type: application/json" -d '{"name":"新加坡|04"}'
```

> **注意**：Clash 外部控制器地址：`/tmp/verge/verge-mihomo.sock`（Unix socket），不对外暴露 TCP 端口。配置文件在 `~/Library/Application Support/io.github.clash-verge-rev.clash-verge-rev/`。

### 3.2 配置 Docker Registry Mirror

在 Colima VM 的 `/etc/docker/daemon.json` 添加国内镜像源：

```json
{
    "registry-mirrors": [
        "https://docker.m.daocloud.io",
        "https://registry.cn-hangzhou.aliyuncs.com"
    ]
}
```

### 3.3 为国内镜像源放行 NO_PROXY

在 Colima VM 的 `/etc/environment` 追加：

```
NO_PROXY=docker.m.daocloud.io,registry.cn-hangzhou.aliyuncs.com,localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
no_proxy=docker.m.daocloud.io,registry.cn-hangzhou.aliyuncs.com,localhost,127.0.0.1,10.0.0.0/8,172.16.0.0/12,192.168.0.0/16
```

### 3.4 重启 Docker

```bash
colima ssh -- sudo systemctl restart docker
```

### 3.5 验证

```bash
docker pull python:3.12-alpine
# ✅ 成功！layers 从 DaoCloud 国内镜像源直连拉取
```

---

## 4. 网络拓扑示意图

```
┌─────────────────────────────────────────────────────┐
│                     宿主机 (macOS)                    │
│                                                     │
│  Clash Verge (verge-mihomo)                         │
│  mixed-port: 7890  ──  allow-lan: true ─→ *:7890   │
│       │                                              │
│       ├─ GLOBAL → 新加坡|04                          │
│       ├─ ✈️ 手动选择 → 新加坡|04                     │
│       └─ 🕹 规则之外 → ✈️ 手动选择 → 新加坡|04       │
│                                                     │
│  Lima NAT 网关: 192.168.5.2                         │
│  Colima 共享网络: 192.168.64.1                      │
└─────────────────────┬───────────────────────────────┘
                      │
                      ▼
┌─────────────────────────────────────────────────────┐
│              Colima VM (192.168.5.1)                 │
│                                                     │
│  ┌──────────┐   代理(http://192.168.5.2:7890)       │
│  │ dockerd  ├──→ 海外节点（Google/GitHub 等）        │
│  └────┬─────┘                                       │
│       │ NO_PROXY（直连）                              │
│       ├──→ docker.m.daocloud.io ✅                   │
│       └──→ registry.cn-hangzhou.aliyuncs.com ✅      │
│                                                     │
│  /etc/environment:                                   │
│    HTTP_PROXY=http://192.168.5.2:7890                │
│    NO_PROXY=docker.m.daocloud.io,...                 │
└─────────────────────────────────────────────────────┘
```

---

## 5. 常见问题

### 5.1 代理节点切换

通过 Clash API 可直接切换节点，无需打开 GUI：

```bash
# 列出可用节点
curl -s --unix-socket /tmp/verge/verge-mihomo.sock http://localhost/proxies \
  | python3 -c "import sys,json;d=json.load(sys.stdin)['proxies']; \
  [print(f'{k}: alive={d[k][\"alive\"]} delay={d[k].get(\"history\",[{}])[-1].get(\"delay\",\"?\")}ms') \
  for k in d['GLOBAL']['all'] if k in d and d[k].get('type') in ('Shadowsocks','Vmess','Trojan','Hysteria2')]"

# 切换 GLOBAL 节点
curl -s --unix-socket /tmp/verge/verge-mihomo.sock -X PUT \
  http://localhost/proxies/GLOBAL \
  -H "Content-Type: application/json" -d '{"name":"新加坡|05"}'
```

### 5.2 Docker 代理配置持久化

Colima 的 Docker 代理配置在 `~/.colima/default/colima.yaml`：

```yaml
env:
  http_proxy: http://192.168.5.2:7890
  https_proxy: http://192.168.5.2:7890
docker:
  registry-mirrors:
    - https://docker.m.daocloud.io
    - https://registry.cn-hangzhou.aliyuncs.com
```

`NO_PROXY` 需手动写入 VM 内的 `/etc/environment`。

### 5.3 Clash allow-lan 持久化

Clash allow-lan 修改是运行时的，重启后失效。如需持久化，修改配置文件：

```yaml
# ~/Library/Application Support/io.github.clash-verge-rev.clash-verge-rev/config.yaml
allow-lan: true
```

或在 Clash Verge GUI → 设置 → 允许局域网连接 中开启。

---

## 6. 排查命令速查

| 目的 | 命令 |
|------|------|
| 检查代理端口 | `lsof -i :7890 -P` |
| 本机走代理测试 | `curl -x http://127.0.0.1:7890 https://www.google.com` |
| 检查 Clash 配置 | `curl -s --unix-socket /tmp/verge/verge-mihomo.sock http://localhost/configs` |
| 检查代理链 | `curl -s --unix-socket /tmp/verge/verge-mihomo.sock http://localhost/proxies` |
| 检查节点延迟 | 见 5.1 节脚本 |
| 从 Colima 测代理 | `colima ssh -- curl --proxy http://192.168.5.2:7890 https://www.google.com` |
| 检查 Docker 代理 | `docker info \| grep -i proxy` |
| VM 内环境变量 | `colima ssh -- cat /etc/environment` |
| Colima SSH | `colima ssh` |
| 重启 Docker | `colima ssh -- sudo systemctl restart docker` |