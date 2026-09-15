# 监控无数据排查记录

> 日期：2026-09-15
> 版本：v0.1
> 关联：[prometheus-deployment.md](./prometheus-deployment.md)、[monitor-integration-design.md](./monitor-integration-design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-15 | 初版：监控无数据排查过程、根因分析与修复步骤 |

---

## 目录

1. [问题现象](#1-问题现象)
2. [排查步骤](#2-排查步骤)
3. [根因分析](#3-根因分析)
4. [修复操作](#4-修复操作)
5. [验证结果](#5-验证结果)
6. [解决思路总结](#6-解决思路总结)
7. [反思与改进](#7-反思与改进)

---

## 1. 问题现象

容器管理平台 Node 详情页监控数据全部为空。以 CPU 监控为例：

```bash
curl 'http://localhost:8000/api/container/clusters/1/monitor/nodes/c3c3cfecae67/cpu?range=6h&step=1m'
```

返回空数据，前端 ECharts 图表空白。

---

## 2. 排查步骤

### Step 1：确认 Prometheus 部署状态

进入 k3s 容器查看 `monitoring` 命名空间：

```bash
docker exec devops-k3s kubectl -n monitoring get pods
```

所有 Pod 均处于 `Running` 状态：
- `prometheus-prometheus-0` — 2/2 Running
- `prometheus-node-exporter-xxxxx` — 1/1 Running
- `prometheus-operator-xxxxx` — 1/1 Running
- `prometheus-kube-state-metrics-xxxxx` — 1/1 Running

### Step 2：检查 Prometheus 服务暴露方式

```bash
docker exec devops-k3s kubectl -n monitoring get svc
```

`prometheus-kube-prometheus-prometheus` 服务类型为 **NodePort**，端口 `9090:30090/TCP`，NodePort 为 30090。

### Step 3：检查宿主机能否直接访问 Prometheus

```bash
curl -s http://localhost:30090/api/v1/query?query=up
```

返回 **HTTP 000**（连接超时），原因：docker-compose 中 k3s 服务未暴露端口 30090（只暴露了 6443/30222/30880）。

### Step 4：检查后端日志发现错误

```bash
tail -100 logs/backend/devops.log | grep -i prometheus
```

大量错误日志：

```
ERROR c.h.d.c.s.p.PrometheusClient - Prometheus range query failed:
promql=...,
error=I/O error on GET request for "http://172.19.0.6:30090/api/v1/query_range": Connection refused
```

关键信息：后端尝试连接 `http://172.19.0.6:30090`，但连接被拒绝。

### Step 5：确认各容器 IP

```bash
docker inspect devops-k3s | jq '.[0].NetworkSettings.Networks."hfwas-devops_default".IPAddress'
# → 172.19.0.2

docker container inspect -f '{{.Name}}: {{range .NetworkSettings.Networks}}{{.IPAddress}}{{end}}' $(docker ps -aq)
```

| 容器 | IP |
|------|-----|
| devops-k3s | **172.19.0.2** ✅ 正确 |
| devops-backend | 172.19.0.3 |
| devops-keycloak | 172.19.0.4 |
| devops-frontend | 172.19.0.5 |
| devops-kong | **172.19.0.6** ← 被误写为 Prometheus 地址 |

结论：`172.19.0.6` 是 Kong 容器，不是 k3s。Prometheus 地址配错了。

### Step 6：确认 Prometheus 地址来源

```bash
docker exec devops-backend cat /app/data/hfwas-devops.db > /tmp/hfwas-db-copy.db
sqlite3 /tmp/hfwas-db-copy.db "SELECT id, name, labels FROM cluster_info;"
```

输出：
```
1|k3s|{"prometheusUrl":"http://172.19.0.6:30090"}
```

确认：`cluster_info` 表的 `labels` 字段中 `prometheusUrl` 指向了错误的 IP。

### Step 7：验证后端容器能否正确访问 Prometheus

```bash
docker exec devops-backend wget -q -O - "http://devops-k3s:30090/api/v1/query?query=up"
```

成功返回，所有 11 个 target 全部 `up=1`。后端容器可以通过 Docker DNS 名 `devops-k3s:30090` 正确访问 Prometheus。

---

## 3. 根因分析

### 直接原因

`cluster_info` 表的 `labels` 字段中 `prometheusUrl: "http://172.19.0.6:30090"`，**指向了 Kong 容器**的 IP，而非 k3s 容器。

### 根本原因

Docker bridge 网络使用 **DHCP 分配 IP**，容器重启后 IP 可能变化。当 k3s 容器重启时，新的 IP 与建表时记录的 IP 不同。记录为 `172.19.0.2`（重启前的 k3s IP）实际上被重新分配给了 **Kong**，导致 Prometheus 地址指向了错误的容器。

### 影响链路

```
前端 → Kong (8000) → 后端 (8089)
                      ↓
               MonitorService.getPrometheusUrl()
                      ↓
               cluster_info.labels.prometheusUrl
                      ↓
               "http://172.19.0.6:30090"  ← ❌ 这是 Kong
                      ↓
               Connection refused → 空数据
```

---

## 4. 修复操作

### 修复 1：修正数据库中的 prometheusUrl

将 `prometheusUrl` 从硬编码 IP 改为 **Docker DNS 名称**，确保容器重启后地址不失效。

```sql
UPDATE cluster_info
SET labels = '{"prometheusUrl":"http://devops-k3s:30090"}'
WHERE id = 1;
```

**为什么用 `devops-k3s` 而不是 IP：**
- Docker Compose 为每个服务注册 DNS 名，同一网络内的容器可使用服务名访问
- DNS 名在容器重启后保持不变
- 即使 Docker 网络重建，只要服务名不变就能解析到正确 IP

### 修复 2：修复数据库文件权限

`docker cp` 覆写 DB 文件后，文件属主变成宿主机的 uid 501，而容器内进程以 `devops`(uid 1000) 运行，导致 SQLite 只读：

```
# 修复前
-rw-r--r-- 1 501   root  [...] hfwas-devops.db   ← devops 用户无写权限
# 修复后
-rw-r--r-- 1 devops devops [...] hfwas-devops.db  ← 正确属主
```

```bash
docker exec -u 0 devops-backend chown devops:devops /app/data/hfwas-devops.db
```

### 修复 3：重启后端

```bash
docker restart devops-backend
```

清空 MyBatis 缓存，重新读取 cluster_info 配置。

---

## 5. 验证结果

### 5.1 日志验证：无 Prometheus 连接错误

修复前（14:15:20）：
```
ERROR - Prometheus range query failed: ... Connection refused
```

修复后（14:21:31）：
```
INFO  - Request start: GET /container/clusters/1/monitor/nodes/c3c3cfecae67/cpu
INFO  - Request end: GET ...  ← 耗时 98ms，无错误
```

### 5.2 数据库验证

```bash
sqlite3 /tmp/hfwas-db-check.db "SELECT id, name, labels FROM cluster_info;"
# → 1|k3s|{"prometheusUrl":"http://devops-k3s:30090"}  ✅
```

### 5.3 Prometheus 数据验证

从后端容器查询 Prometheus 成功，CPU 监控数据正常：

```
172.19.0.2:9100: 12.2%  ← CPU 使用率有数据
```

所有 11 个采集目标全部 `up=1`。

---

## 6. 解决思路总结

| 步骤 | 方法 | 工具 |
|------|------|------|
| 1. 确认 Prometheus 是否部署 | 查 k3s 内 Pod 状态 | `kubectl -n monitoring get pods` |
| 2. 确认 Prometheus 服务暴露方式 | 查 Service 类型和 NodePort | `kubectl -n monitoring get svc` |
| 3. 直接测试 Prometheus API | curl 到 Prometheus 地址 | `curl /api/v1/query?query=up` |
| 4. 检查后端日志 | grep Prometheus 相关错误 | `tail logs/backend/devops.log | grep -i prometheus` |
| 5. 定位错误地址 | 从错误地址反查是哪个容器 | `docker inspect -f '...IPAddress...'` API |
| 6. 查数据库确认配置 | 查询 cluster_info 表的 labels | `sqlite3` 查询 |
| 7. 修复并验证 | 改配置 + 重启后端 | SQL UPDATE + docker restart |

---

## 7. 反思与改进

### 架构问题

1. **prometheusUrl 存在数据库，但依赖容器 IP**：Docker bridge 网络的 IP 是动态的，重启后可能变化。应该使用 **Docker DNS 名** 或 **静态 IP**。
2. **无健康检查**：后端没有定期检查 Prometheus 连接健康度，用户只能等数据不显示才发现问题。
3. **数据库文件权限风险**：`docker cp` 会改变文件属主，导致容器内进程无法写入。

### 改进建议

1. **所有跨容器地址优先使用 Docker DNS 名**，避免硬编码 IP（已通过本次修复解决）
2. 在 `MonitorService` 启动时或定时任务中增加 Prometheus 连通性检查，提前告警
3. 更新 `prometheus-deployment.md` 和 `monitor-integration-design.md`，明确标注应使用 DNS 名而非 IP
4. 如需修改容器内 SQLite 数据库，优先用 `docker exec` 在容器内执行 SQL，或用 API 更新，避免 `docker cp` 改变文件属主