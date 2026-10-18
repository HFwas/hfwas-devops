# `http://10.10.101.81:30483` 流量如何到达 Pod

> 日期：2026-09-20
> 版本：v0.1
> 集群：`ssh root@10.10.101.81`（kubeadm，Kubernetes v1.27.10）
> 示例入口：`http://10.10.101.81:30483`（Kong NodePort）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-20 | 初版：以 30483 为示例，现场排查 NodePort → IPVS → Calico IPIP → Pod 的完整数据路径 |

---

## 目录

1. [一句话结论](#1-一句话结论)
2. [现场环境](#2-现场环境)
3. [数据面总图](#3-数据面总图)
4. [排查步骤（命令 + 输出 + 原理）](#4-排查步骤命令--输出--原理)
5. [两条真实路径](#5-两条真实路径)
6. [用这个例子把 K8s 网络讲清楚](#6-用这个例子把-k8s-网络讲清楚)
7. [对照：如果不是现在这套组合](#7-对照如果不是现在这套组合)
8. [复盘清单](#8-复盘清单)

---

## 1. 一句话结论

浏览器访问 `http://10.10.101.81:30483` 时：

1. 包打到 **任意节点** 的 NodePort `30483`（本例打到 master `10.10.101.81`）。
2. 节点上 **没有用户态进程监听 30483**。`kube-proxy`（IPVS 模式）事先把这条 VIP 写进内核 IPVS。
3. 内核把 `10.10.101.81:30483` **DNAT** 成 Pod `10.244.240.35:8000`，同时 **SNAT** 成 master 的 Calico 隧道地址 `10.244.233.0`。
4. Pod 不在 master 上，而在 worker `10.10.101.82`。Calico（`ipipMode: Always`）把内层包封进 **IPIP**（外层 `81 → 82`，协议 4），经 `ens192` 送到 82。
5. 82 解封装后，按主机路由把包送到 veth `calid2845512a24`，对端就是 Kong Pod 网卡。Kong 在 `0.0.0.0:8000` 收下 HTTP，再按自己的路由反代到上游（本例是 `model-studio-console`，响应头有 `Via: kong/3.9.3`）。

对应的 Kubernetes 对象：

| 层级 | 对象 | 本例取值 |
|------|------|----------|
| 入口 | NodePort | 任意节点 IP 的 **30483/TCP** |
| Service | `bailian/bailian-kong` | ClusterIP `10.106.51.83:8000`，`type=NodePort`，`externalTrafficPolicy=Cluster` |
| Endpoint | `10.244.240.35:8000` | 由 selector 选出 |
| Pod | `bailian-kong-7bb657697c-7wzfj` | 节点 `cluster-node0001.10.10.101.82`，容器端口 `proxy=8000` |
| 转发平面 | kube-proxy **IPVS** | VIP → RS，调度器 `rr`，转发 `Masq` |
| 跨节点 | Calico **IPIP Always** | Pod CIDR `10.244.0.0/16`，块大小 `/26` |

---

## 2. 现场环境

登录节点：

```bash
ssh root@10.10.101.81
```

节点与运行时：

```bash
hostname
kubectl get nodes -o wide
```

| 节点 | 角色 | INTERNAL-IP | 版本 | 运行时 |
|------|------|-------------|------|--------|
| `cluster-master0001.10.10.101.81` | control-plane | 10.10.101.81 | v1.27.10 | containerd 1.6.28 |
| `cluster-node0001.10.10.101.82` | worker（Kong 所在） | 10.10.101.82 | v1.27.10 | containerd 1.6.28 |
| `cluster-node0002.10.10.101.83` | worker | 10.10.101.83 | v1.27.10 | containerd 1.6.28 |
| `cluster-node0003.10.10.101.84` | worker | 10.10.101.84 | v1.27.10 | containerd 1.6.28 |
| `cluster-node0004.10.10.101.89` | worker | 10.10.101.89 | v1.27.10 | containerd 1.6.28 |

网络组件（现场确认）：

| 组件 | 模式 / 取值 |
|------|-------------|
| 网络插件 | Calico（Multus 作为 meta-plugin 委托给 Calico） |
| Pod CIDR | `10.244.0.0/16`，每节点一块 `/26` |
| 封装 | `ipipMode: Always`，`vxlanMode: Never`，`tunl0` MTU 1480 |
| Service CIDR | `10.96.0.0/12`（kubeadm `serviceSubnet`） |
| kube-proxy | `mode: ipvs`，`scheduler` 空 → 默认 `rr`，`iptables.masqueradeAll: true`，`clusterCIDR: ""` |
| 本机网卡 | `ens192` = `10.10.101.81/24` |

本机 docs 未覆盖这台 `10.10.101.81` 集群；下文全部来自 2026-09-20 现场命令。

---

## 3. 数据面总图

```mermaid
flowchart LR
  C["客户端<br/>10.10.200.7"] -->|TCP 30483| N81["节点 ens192<br/>10.10.101.81"]
  N81 --> IPVS["内核 IPVS<br/>VIP 10.10.101.81:30483"]
  IPVS -->|DNAT + SNAT| T["tunl0 / IPIP<br/>src 10.244.233.0<br/>dst 10.244.240.35:8000"]
  T -->|外层 81→82 proto 4| N82["节点 10.10.101.82<br/>解 IPIP"]
  N82 --> VETH["veth calid2845512a24"]
  VETH --> POD["Kong Pod eth0<br/>10.244.240.35:8000"]
  POD --> UP["Kong 反代上游<br/>nginx / console"]
```

控制面（不在每个数据包路径上，只负责「把规则写进内核」）：

```text
API Server
  ├─ Service / Endpoints / EndpointSlice   ← kube-proxy watch
  │     kube-proxy 把 VIP/RS 写入 IPVS、ipset、少量 iptables（只做 MASQ）
  └─ Pod IP / NetworkPolicy                 ← Calico Felix + BIRD
        calico-node 写路由、建 veth、IPIP、策略
```

**关键误区**：`ss -lntp | grep 30483` 是空的，并不代表端口没开。IPVS 的 VIP 不是 socket，是内核虚拟服务器。

---

## 4. 排查步骤（命令 + 输出 + 原理）

下面按「从外到内」走。每一步都是这次实际跑过的命令。

### 4.1 确认 30483 是谁的 NodePort

**命令**

```bash
# 所有 Service 里找 nodePort == 30483
kubectl get svc -A -o json | python -c "
import json,sys
d=json.load(sys.stdin)
for i in d['items']:
    spec=i.get('spec',{})
    for p in spec.get('ports') or []:
        if p.get('nodePort')==30483:
            print(i['metadata']['namespace'], i['metadata']['name'], spec.get('type'), spec.get('clusterIP'), p)
"
```

**结果**

```text
bailian  bailian-kong  NodePort  10.106.51.83
  name=proxy  port=8000  nodePort=30483  targetPort=proxy  protocol=TCP
```

完整对象：

```bash
kubectl -n bailian get svc bailian-kong -o yaml
kubectl -n bailian describe svc bailian-kong
```

要点：

- `type: NodePort`
- `port: 8000`：集群内访问 ClusterIP 用的端口
- `nodePort: 30483`：每台节点宿主机上映射的端口（默认范围 30000–32767）
- `targetPort: proxy`：转到 Pod 容器端口名 `proxy`（实际是 8000）
- `externalTrafficPolicy: Cluster`：包可以打到**没有 Pod 的节点**，再转发到有 Pod 的节点；客户端源 IP **不会**保留
- `sessionAffinity: None`：IPVS 用 rr，无会话粘滞

**原理**：Service 是虚拟 IP + 端口的稳定入口，自己不跑业务进程。真正收包的是后面的 Pod。NodePort 只是「在每台机器的宿主机 IP 上再挂一个入口」。

---

### 4.2 找到 Endpoint / Pod

**命令**

```bash
kubectl -n bailian get endpoints bailian-kong -o yaml
kubectl -n bailian get pods -l app.kubernetes.io/instance=bailian,app.kubernetes.io/name=kong -o wide
kubectl -n bailian get pod bailian-kong-7bb657697c-7wzfj -o jsonpath='node={.spec.nodeName} hostIP={.status.hostIP} podIP={.status.podIP}{"\n"}'
```

**结果**

```text
Endpoints: 10.244.240.35:8000
Pod:       bailian-kong-7bb657697c-7wzfj
IP:        10.244.240.35
Node:      cluster-node0001.10.10.101.82
hostIP:    10.10.101.82
READY:     1/1
```

容器端口：

```text
kong
  proxy        containerPort=8000
  proxy-ssl    containerPort=8443
  admin        containerPort=8001
  admin-gui    containerPort=8002
```

**原理**：

- kube-controller-manager 的 endpoint(slice) 控制器用 Service 的 `selector` 找 Ready Pod，把 `PodIP:containerPort` 写成 Endpoints。
- kube-proxy **不看 Pod 对象本身**，看 Endpoints/EndpointSlice。
- 本例只有 1 个 Ready Pod，所以 IPVS 后面只有 1 台 Real Server。历史日志里能看到 Pod IP 换过（`10.244.49.44`、`10.244.209.100` 等），每次滚动，kube-proxy 都会改 RS。

---

### 4.3 先用 curl 证明四条入口都能通

**命令**

```bash
# 1) 题目里的入口（master NodePort）
curl -sS -D - -o /dev/null http://10.10.101.81:30483/ | head

# 2) 任意其它节点的同一个 NodePort 也应该通
for ip in 10.10.101.82 10.10.101.83 10.10.101.84 10.10.101.89; do
  curl -sS -m 5 -o /dev/null -w "$ip code=%{http_code}\n" http://$ip:30483/
done

# 3) 集群内 ClusterIP
curl -sS -o /dev/null -w "clusterIP %{http_code}\n" http://10.106.51.83:8000/

# 4) 直接打 Pod IP（绕过 Service）
curl -sS -o /dev/null -w "podIP %{http_code}\n" http://10.244.240.35:8000/
```

**结果（2026-09-20）**

| 目标 | HTTP |
|------|------|
| `10.10.101.81:30483` | 200 |
| `10.10.101.82:30483` | 200 |
| `10.10.101.83:30483` | 200 |
| `10.10.101.84:30483` | 200 |
| `10.10.101.89:30483` | 200 |
| `10.106.51.83:8000` | 200 |
| `10.244.240.35:8000` | 200 |

从工作站 `10.10.200.7` 访问 `http://10.10.101.81:30483/` 同样 200，响应头：

```http
HTTP/1.1 200 OK
Server: nginx/1.31.3
Via: 1.1 kong/3.9.3
X-Kong-Proxy-Latency: 1
X-Kong-Upstream-Latency: 5
```

**原理**：

- NodePort 是「每节点一份」，不是只开在 master。
- ClusterIP 只在集群网络里有意义（节点和 Pod 能路由到 `10.96.0.0/12`）；外部电脑一般到不了 ClusterIP，除非做了额外路由。
- 直接打 PodIP 能通，说明 **CNI 跨节点连通已经独立于 Service**。Service 只是在前面加了一层 VIP 负载均衡。

---

### 4.4 确认没有进程在听 30483，真正干活的是 IPVS

**命令**

```bash
ss -lntp | grep 30483          # 空
ip link show kube-ipvs0
ip addr show kube-ipvs0 | grep 10.106.51.83
```

**结果**

```text
4: kube-ipvs0: <BROADCAST,NOARP> mtu 1500 qdisc noop state DOWN
    inet 10.106.51.83/32 scope global kube-ipvs0
```

`kube-ipvs0` 是 kube-proxy 建的 dummy 网卡，**DOWN 是正常的**。它的唯一作用是把每个 ClusterIP 配成本机地址，这样内核会认为「访问 ClusterIP 是访问本机」，从而把包送进 IPVS。

看 kube-proxy 模式：

```bash
kubectl -n kube-system exec kube-proxy-wzsnc -- cat /var/lib/kube-proxy/config.conf
```

关键字段：

```yaml
mode: ipvs
clusterCIDR: ""
iptables:
  masqueradeAll: true
  masqueradeBit: 14
ipvs:
  scheduler: ""      # 空 = rr
  strictARP: false
detectLocalMode: ClusterCIDR
```

本机没有 `ipvsadm`，直接读内核表（十六进制）：

```bash
cat /proc/net/ip_vs
```

解码（`0A0A6551` = 10.10.101.81，`7713` hex = 30483，`0AF4F023` = 10.244.240.35，`1F40` hex = 8000）：

```text
# master 上
TCP  VIP 10.10.101.81:30483   scheduler=rr
     -> 10.244.240.35:8000    Masq  weight=1
TCP  VIP 10.244.233.0:30483   scheduler=rr     # 连 Calico 隧道地址上也挂了 NodePort
     -> 10.244.240.35:8000    Masq
TCP  VIP 10.106.51.83:8000    scheduler=rr     # ClusterIP
     -> 10.244.240.35:8000    Masq

# worker 82 上（从 calico-node 读 /proc/net/ip_vs）
TCP  VIP 10.10.101.82:30483   rr
     -> 10.244.240.35:8000    Masq
TCP  VIP 10.244.240.0:30483   rr
     -> 10.244.240.35:8000    Masq
TCP  VIP 10.106.51.83:8000    rr
     -> 10.244.240.35:8000    Masq
```

**原理（IPVS 模式 kube-proxy）**：

1. 每个 Service 端口变成一台 **Virtual Server（VIP）**。
2. 每个 Ready Endpoint 变成一台 **Real Server（RS）**。
3. 调度器 `rr`：多副本时轮询；现在只有 1 个 RS，每次都打到同一 Pod。
4. 转发方式 `Masq` = LVS NAT：改目的 IP/端口（DNAT），回包必须再改回来。这要求节点 `ip_forward=1`，并且通常伴随 SNAT。
5. kube-proxy **自己不转发数据包**。它 watch API，把规则写入内核；包的快路径全在 netfilter / IPVS。

所以 `ss` 看不到 30483：没有 `accept()` 的 socket。内核看到「目的是本机 30483」就按 IPVS 表改写。

---

### 4.5 iptables 在 IPVS 模式下还干什么

IPVS 模式并不是「完全不用 iptables」。MASQUERADE（SNAT）和少量过滤仍走 iptables。

**命令**

```bash
iptables -t nat -S KUBE-SERVICES
iptables -t nat -S KUBE-NODE-PORT
iptables -t nat -S KUBE-POSTROUTING
ipset list KUBE-NODE-PORT-TCP
ipset list KUBE-CLUSTER-IP | grep 10.106.51.83
```

**结果（简化）**

```text
KUBE-SERVICES:
  -s 127.0.0.0/8  RETURN                         # 本机回环不 MASQ
  match-set KUBE-CLUSTER-IP dst,dst  KUBE-MARK-MASQ
  dst-type LOCAL                    KUBE-NODE-PORT
  match-set KUBE-CLUSTER-IP         ACCEPT

KUBE-NODE-PORT:
  tcp  match-set KUBE-NODE-PORT-TCP dst  KUBE-MARK-MASQ

KUBE-POSTROUTING:
  带 mark 0x4000 的包  MASQUERADE

KUBE-NODE-PORT-TCP members:
  30483, 30676, 30890, 31860
```

现场 `sysctl`：

```text
net.ipv4.ip_forward = 1
net.ipv4.conf.all.rp_filter = 0
net.ipv4.vs.conntrack = 1
```

`net.ipv4.vs.conntrack=1` 让 IPVS 改写的连接进入 conntrack，后面才能看到完整 NAT 元组。

**原理**：

- `KUBE-MARK-MASQ` 给包打 `0x4000`。
- `KUBE-POSTROUTING` 对带这个 mark 的包做 `MASQUERADE`（源地址改成出接口的地址）。
- 本集群 `masqueradeAll: true` 且 `clusterCIDR` 为空：kube-proxy **无法区分**「集群内访问」和「集群外访问」，于是 NodePort / ClusterIP 流量一律 SNAT。
- `externalTrafficPolicy: Cluster` 即使不配 masqueradeAll，NodePort 外部流量也通常会 SNAT，否则回包可能从另一台节点出去，客户端对不上。

---

### 4.6 用 conntrack 看一次真实 NAT（最关键的证据）

从工作站 `10.10.200.7` curl `http://10.10.101.81:30483/` 之后，在 **master** 上：

```bash
conntrack -L -p tcp --dport 30483
```

**结果**

```text
tcp TIME_WAIT
  src=10.10.200.7 dst=10.10.101.81 sport=64879 dport=30483
  src=10.244.240.35 dst=10.244.233.0 sport=8000 dport=62998
```

conntrack 一行里有 **原始元组** 和 **应答元组**：

| | 源 | 目的 | 端口 |
|--|----|------|------|
| 客户端发出的原始包 | 10.10.200.7 | 10.10.101.81 | 30483 |
| 改写后 / 回包看到的 | 10.244.240.35 | 10.244.233.0 | 8000 |

也就是：

- **DNAT**：`dst 10.10.101.81:30483` → `10.244.240.35:8000`
- **SNAT**：`src 10.10.200.7` → `10.244.233.0`（master 的 `tunl0` 地址）

Kong 容器里的 TCP 表印证了「Pod 看见的对端是 Calico IP，不是客户端」：

```bash
kubectl -n bailian exec bailian-kong-7bb657697c-7wzfj -- cat /proc/net/tcp
# 监听（state 0A = LISTEN）
# 0.0.0.0:8000 / 8001 / 8002 / 8443
# 已建立（state 01 = ESTABLISHED）
# 10.244.240.35:8000  <->  10.244.233.0:<ephemeral>
```

**原理**：NAT 之后必须记住映射，回包才能改回去。这就是 conntrack。客户端仍然以为自己在跟 `10.10.101.81:30483` 说话；Pod 以为自己在跟 `10.244.233.0` 说话。中间两层地址被节点内核换过。

副作用：应用日志、Kong 的 `$binary_remote_addr`、NetworkPolicy 按「客户端 IP」写规则，在这条路径上都 **看不到** `10.10.200.7`。若要保留源 IP，需要 `externalTrafficPolicy: Local`（并保证客户端打到「本地有 Pod」的节点），或 ingress / 代理插入 `X-Forwarded-For`。

---

### 4.7 跨节点：Calico IPIP + BIRD 路由

入口节点是 81，Pod 在 82，中间必须有 Pod 网。

**命令**

```bash
ip route get 10.244.240.35
ip route | grep -E "10.244|tunl0"
ip -d link show tunl0
kubectl get ippool default-ipv4-ippool -o yaml
kubectl get blockaffinities.crd.projectcalico.org \
  -o custom-columns=NAME:.metadata.name,NODE:.spec.node,CIDR:.spec.cidr
```

**master 路由**

```text
10.244.240.35 via 10.10.101.82 dev tunl0 src 10.244.233.0  mtu 1480

10.244.49.0/26     via 10.10.101.89  dev tunl0  proto bird onlink
10.244.93.0/26     via 10.10.101.83  dev tunl0  proto bird onlink
10.244.209.64/26   via 10.10.101.84  dev tunl0  proto bird onlink
blackhole 10.244.233.0/26  proto bird
10.244.240.0/26    via 10.10.101.82  dev tunl0  proto bird onlink
```

**IPPool**

```yaml
cidr: 10.244.0.0/16
blockSize: 26
ipipMode: Always
vxlanMode: Never
natOutgoing: true
```

**每节点一块 IPAM**

| 节点 | 块 | tunl0 地址 |
|------|----|------------|
| 81 master | 10.244.233.0/26 | 10.244.233.0 |
| 82 worker（Kong） | 10.244.240.0/26 | 10.244.240.0 |
| 83 | 10.244.93.0/26 | — |
| 84 | 10.244.209.64/26 | — |
| 89 | 10.244.49.0/26 | — |

`blackhole 10.244.233.0/26`：本节点这块里、还没分给某个 Pod 的地址丢弃，避免错误转发。

**原理**：

1. Calico IPAM 给每个节点切一块 `/26`（64 个地址），再从块里给 Pod 分配 `/32`。
2. `ipipMode: Always`：跨节点访问 Pod IP 时，内层仍是 `PodIP ↔ PodIP`（这里是 SNAT 后的 `10.244.233.0 → 10.244.240.35`），外层封装成 **普通节点 IP 之间的 IPIP**（`10.10.101.81 → 10.10.101.82`，IP 协议号 4）。中间交换机只需要认识 `10.10.101.0/24`，不必认识 `10.244.0.0/16`。
3. 路由由 calico-node 里的 **BIRD** 注入（`proto bird`），不是 kube-proxy 写的。
4. `tunl0` 是 IPIP 隧道网卡，MTU 1480 = 1500 − 20（IPIP 头）。veth 同样 1480，避免分片。
5. `natOutgoing: true`：Pod 访问集群外网时，在节点上 SNAT 成节点 IP。这和 NodePort 的 MASQ 是两件不同的事。

本机没有 `tcpdump`，外层 IPIP 没抓到包；但 `ip route get` 的 `dev tunl0` + conntrack 的 SNAT 到 `10.244.233.0` + worker 上出现 `10.244.233.0 → 10.244.240.35:8000` 的 conntrack，三者已经把封装路径钉死。

worker 82 上对应的「已经解封装」记录：

```text
tcp TIME_WAIT
  src=10.244.233.0 dst=10.244.240.35 sport=62998 dport=8000
  src=10.244.240.35 dst=10.244.233.0 sport=8000 dport=62998
```

这正是 master SNAT 之后、IPIP 解出来的内层包。

---

### 4.8 到达 82 之后：veth 对送进 Pod

在 worker 上没有直接 SSH（master 跳过去公钥未配），改用 **hostNetwork 的 calico-node** 看主机网络：

```bash
kubectl -n kube-system exec -c calico-node calico-node-9rdkx -- ip route get 10.244.240.35
kubectl -n kube-system exec -c calico-node calico-node-9rdkx -- ip route | grep 10.244.240
kubectl -n kube-system exec -c calico-node calico-node-9rdkx -- ip neigh | grep 10.244.240.35
```

**结果**

```text
10.244.240.35 dev calid2845512a24 src 10.10.101.82

blackhole 10.244.240.0/26 proto bird          # 本节点的 IPAM 块
10.244.240.35 dev calid2845512a24 scope link  # 每个本机 Pod 一条 /32

10.244.240.35 dev calid2845512a24 lladdr 86:9b:bd:a6:d5:1e REACHABLE

125: calid2845512a24@if4 ... mtu 1480 ... link-netnsid 11
     link/ether ee:ee:ee:ee:ee:ee
```

**原理（Calico 的 Linux 数据面，不是 overlay 网桥）**：

1. 每个 Pod 一个 **network namespace**。
2. 一对 veth：主机端名叫 `caliXXXXXXXX`（MAC 固定 `ee:ee:ee:ee:ee:ee`），容器端一般是 `eth0`。
3. 主机路由：`PodIP/32 dev cali...`，二层邻居就是容器 `eth0` 的 MAC。
4. 包进 ns 后，容器协议栈按目的端口交给监听 `0.0.0.0:8000` 的 Kong。
5. 本集群 CNI 链是 **Multus → Calico**。Kong 的 annotation `k8s.v1.cni.cncf.io/network-status` 显示默认网是 `calico-heim`，IP `10.244.240.35`。对这条 NodePort 流量来说，Multus 只是委托，真正转发的是 Calico。

kubelet 创建 Pod 时调用 `/etc/cni/net.d/` 里的插件；calico-node 再通过 Felix 保证路由和策略与这个 veth 一致。**kube-proxy 不创建 veth。**

---

### 4.9 打到「Pod 所在节点」的 NodePort（对照）

从 master curl `http://10.10.101.82:30483/`，看 **82** 的 conntrack：

```text
src=10.10.101.81 dst=10.10.101.82 sport=50884 dport=30483
src=10.244.240.35 dst=10.10.101.82 sport=8000 dport=11023
```

这里 **没有 IPIP**：Pod 就在本机。

- DNAT：`82:30483` → `10.244.240.35:8000`
- SNAT：源变成 `10.10.101.82`（出接口是本地 cali veth，MASQUERADE 选用节点 IP）
- 路由：`10.244.240.35 dev calid2845512a24`，直接出 veth

同一 NodePort，打到哪台机器，路径长度不一样，但业务结果一样。这就是 `externalTrafficPolicy: Cluster` 的「任意节点都能进」。

---

### 4.10 进了 Pod 之后（L7，已经不属于 K8s 网络）

Kong 在 8000 收下 HTTP 后，按自己的路由反代上游。证据是响应头：

- `Via: 1.1 kong/3.9.3`
- `X-Kong-Proxy-Latency` / `X-Kong-Upstream-Latency`
- `Server: nginx/1.31.3`
- body 是 `model-studio-console`

worker 上还能看到 Kong 访问 Postgres ClusterIP `10.103.69.37:5432` 等，那是 **Pod 作为客户端再走一遍 Service**，和入口 NodePort 是对称的另一方向。

---

## 5. 两条真实路径

### 路径 A：题目原文（客户端 → master:30483 → 跨节点 Pod）

```text
10.10.200.7:64879
    │  TCP SYN  dst=10.10.101.81:30483
    ▼
ens192 @ 81
    │  PREROUTING / KUBE-SERVICES
    │  dst-type LOCAL → KUBE-NODE-PORT → mark 0x4000（要 SNAT）
    │  内核 IPVS: VIP 10.10.101.81:30483 → RS 10.244.240.35:8000  (Masq, rr)
    │  DNAT + SNAT 后：
    │     src=10.244.233.0   dst=10.244.240.35:8000
    ▼
ip route: 10.244.240.0/26 via 10.10.101.82 dev tunl0
    │  IPIP 封装
    │     外层 src=10.10.101.81  dst=10.10.101.82  proto=4
    │     内层 src=10.244.233.0  dst=10.244.240.35:8000
    ▼
ens192 @ 82  解 IPIP
    │  ip route: 10.244.240.35 dev calid2845512a24
    ▼
veth 主机端 calid2845512a24  →  容器 eth0 10.244.240.35
    │
    ▼
Kong LISTEN 0.0.0.0:8000
    │  L7 反代
    ▼
上游 nginx / console  → 200 沿原路 NAT 改回 → 客户端
```

### 路径 B：客户端（或 81）→ worker82:30483 → 本机 Pod

```text
src=10.10.101.81  dst=10.10.101.82:30483
    │  82 上 IPVS VIP 10.10.101.82:30483 → 10.244.240.35:8000
    │  DNAT + SNAT（源变成 10.10.101.82）
    ▼
dev calid2845512a24  →  Pod eth0:8000
    （不经过 tunl0 / IPIP）
```

---

## 6. 用这个例子把 K8s 网络讲清楚

K8s 网络可以拆成 **四张彼此独立、再拼在一起的平面**。本例每张都能对上号。

### 6.1 Pod 网络（CNI）

目标：每个 Pod 一张可路由的 IP，跨节点也能直接访问。

本例：

- 实现：Calico + IPIP Always
- Kong Pod IP `10.244.240.35` 属于 82 的块 `10.244.240.0/26`
- 同节点：主机 `/32` 路由 + veth
- 跨节点：BIRD 下发「那块在哪台节点」+ IPIP 封装

**不经过 Service 也能 `curl 10.244.240.35:8000`**，说明这一层已经自洽。

### 6.2 Service 网络（kube-proxy）

目标：给一组 Pod 一个稳定的虚拟 IP/端口，Pod 挂了换 IP 也不改客户端。

三种常见入口，本例全开了：

| 入口 | 地址 | 谁能用 |
|------|------|--------|
| ClusterIP | `10.106.51.83:8000` | 集群内 |
| NodePort | `任意节点IP:30483` | 集群外（三层能到节点即可） |
| 直接 PodIP | `10.244.240.35:8000` | 知道 IP 的人；IP 会变 |

kube-proxy 的三种数据面：

| 模式 | 包怎么走 | 本集群 |
|------|----------|--------|
| iptables | 每个 Service 一串 DNAT 规则 | 否 |
| IPVS | 内核 LVS 表，适合大量 Service | **是** |
| nftables | 较新版本 | 否 |

IPVS 模式下，**iptables 只做 MASQ / 过滤，DNAT 在 IPVS**。

### 6.3 节点网络（underlay）

目标：机器之间本来就能通。

本例：`10.10.101.0/24`，网卡 `ens192`。IPIP 的外层就走这张网。交换机 / 物理路由器 **不需要** 认识 `10.244.0.0/16`。

这也是 NodePort 能从机房里另一台电脑 `10.10.200.7` 访问的原因：对方只需要路由到 `10.10.101.81`，端口 30483。

### 6.4 控制面 vs 数据面

| | 控制面 | 数据面 |
|--|--------|--------|
| Service | API 里的 Service / EndpointSlice | IPVS 表 + ipset + 少量 iptables |
| Pod IP | kubelet 调 CNI、Calico IPAM | veth、主机路由、tunl0 |
| 谁改包 | 没人改 | 节点内核（IPVS + conntrack + IPIP） |

kube-proxy、calico-node 挂了，**已经建好的规则通常还能转发一段时间**；但 Endpoints 变化不会再更新，新 Pod 也没有网。

### 6.5 为什么 NodePort 要 SNAT

`externalTrafficPolicy: Cluster` 允许「包从 A 节点进、Pod 在 B 节点」。回包必须从 **A** 出去，因为客户端的 TCP 对端是 A:30483。

如果只 DNAT 不 SNAT：

- Pod 回包目的是客户端 `10.10.200.7`
- 82 可能走默认网关直接回客户端
- 客户端收到源 IP 是 `10.244.240.35` 或 82，和它发出去的 `81:30483` 对不上 → 丢弃

SNAT 成 `10.244.233.0`（A 的隧道地址）后，回包一定先回到 A，A 再按 conntrack 改回 `81:30483`。这是 LVS NAT / NodePort Cluster 模式的标准代价：**丢失真实客户端 IP**。

### 6.6 端口三个名字不要混

以本例 `proxy` 端口：

```text
客户端看到的     NodePort      30483
集群内看到的     Service port  8000   （ClusterIP:8000）
容器真正监听的   targetPort    8000   （名字 proxy）
```

三个数字可以都不同。本例 Service port 和 targetPort 碰巧都是 8000，NodePort 是 30483。

---

## 7. 对照：如果不是现在这套组合

| 变化 | 数据路径会怎样 |
|------|----------------|
| kube-proxy `mode: iptables` | 没有 `kube-ipvs0` / `/proc/net/ip_vs`。`iptables -t nat -L KUBE-SERVICES` 里会有一条 `KUBE-SVC-xxxx` 再进 `KUBE-SEP-xxxx` DNAT 到 Pod。`ss` 依然没有 30483 监听。 |
| `externalTrafficPolicy: Local` | 只有 **本地有 Ready Pod** 的节点（82）NodePort 能通；打 81 会拒绝或空。可能不做 SNAT，Pod 能看见 `10.10.200.7`。 |
| Calico VXLAN 而不是 IPIP | 外层是 UDP 4789，网卡是 `vxlan.calico`，不是 `tunl0`。 |
| Calico BGP 无封装（ipipMode Never） | 交换机必须路由 `10.244.0.0/16`，或节点之间做 BGP。`ip route get` 会是 `via 10.10.101.82 dev ens192`，没有 tunl0。 |
| hostNetwork: true 的 Pod | 没有独立 Pod IP / veth；容器直接听节点 8000。NodePort 仍可能再做一层转发，但本例 Kong 不是 hostNetwork。 |
| LoadBalancer | 云厂商或 MetalLB 再挂一个外部 IP，后面仍然落到 NodePort 或直接 IPVS。本例没有 ExternalIP。 |
| Ingress / Gateway | 客户端先打到 Ingress Controller 的 NodePort/LB，再由控制器按 Host/Path 转到 ClusterIP。本例客户端 **直接打 Kong 的 NodePort**，Kong 自己就是网关。 |

---

## 8. 复盘清单

现场把这条链路钉死，按这个顺序即可：

```bash
ssh root@10.10.101.81

# 1. 谁占用了 30483
kubectl get svc -A -o json | python -c "..."   # 见 4.1

# 2. Endpoint / Pod / 所在节点
kubectl -n bailian get ep,pod -o wide
kubectl -n bailian get svc bailian-kong -o yaml

# 3. 四条入口是否通
curl -D - http://10.10.101.81:30483/
curl http://<任意节点>:30483/
curl http://<ClusterIP>:8000/
curl http://<PodIP>:8000/

# 4. kube-proxy 模式 + IPVS 表
kubectl -n kube-system exec ds/kube-proxy -- cat /var/lib/kube-proxy/config.conf
python 解码 /proc/net/ip_vs                 # 见 4.4

# 5. NAT 证据
conntrack -L -p tcp --dport 30483
ipset list KUBE-NODE-PORT-TCP
iptables -t nat -S KUBE-SERVICES KUBE-NODE-PORT KUBE-POSTROUTING

# 6. 跨节点
ip route get <PodIP>
kubectl get ippool default-ipv4-ippool -o yaml

# 7. 本机 veth（到 Pod 所在节点）
kubectl -n kube-system exec -c calico-node <calico-node-on-82> -- ip route get <PodIP>
```

记住三句话就够把这个例子讲完：

1. **Service 不收包，内核收包。** NodePort 是 IPVS VIP，不是进程 listen。
2. **kube-proxy 改规则，Calico 改路由。** 前者把 `节点:30483` 映射到 `PodIP:8000`，后者让这个 PodIP 在 82 的 veth 上可达。
3. **跨节点要封装，回包要 SNAT。** IPIP 让 underlay 不用认识 Pod 网段；MASQ 让 TCP 会话仍粘在入口节点上。
