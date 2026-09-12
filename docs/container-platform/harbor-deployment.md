# Harbor 部署文档

> 日期：2026-09-13
> 版本：v0.2
> Harbor：v2.14.4（从 v2.15.2 降级）
> Helm Chart：1.18.4

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-10 | 初版：k3s 手工 helm + 宿主机拉镜像导入 containerd |
| v0.2 | 2026-09-13 | 推荐改用 `deploy/charts/harbor` 部署包；补充 Docker Desktop 路径 |

---

## 目录

1. [环境信息](#1-环境信息)
2. [安装步骤](#2-安装步骤)
3. [镜像清单](#3-镜像清单)
4. [升级/降级操作](#4-升级降级操作)
5. [遇到的问题及解决方法](#5-遇到的问题及解决方法)
6. [验证](#6-验证)

---

## 1. 环境信息

| 项目 | 值 |
|------|-----|
| Harbor 版本 | v2.14.4 |
| Harbor Helm Chart | 1.18.4（官方，vendored 在 `deploy/charts/harbor/charts/`） |
| 部署包 | `deploy/charts/harbor/` |
| 暴露方式 | NodePort（HTTP: 30002，HTTPS: 30003 预留，本包关闭 TLS） |
| 外部访问地址 | http://localhost:30002 |
| 默认管理员 | `admin` / `Harbor12345`（见 `values.yaml` 的 `harborAdminPassword`） |

| 集群 | 存储 | kubectl / helm |
|------|------|----------------|
| Docker Desktop Kubernetes | 默认 `hostpath` | 宿主机 |
| k3s（Docker 容器，如 `devops-k3s`） | `local-path` | **进容器再操作**，不要用宿主机 kubeconfig |

---

## 2. 安装步骤

推荐用本仓库部署包，不要每次手写一长串 `--set`。包内 README：[`deploy/charts/harbor/README.md`](../../deploy/charts/harbor/README.md)。

### 2.1 Docker Desktop（当前本机）

```bash
cd deploy/charts/harbor
./deploy.sh pull
./deploy.sh deploy
./deploy.sh status
```

Desktop 与集群共用 Docker 镜像，**不需要** `ctr import`。Values 用 `values-desktop.yaml`。

手动等价：

```bash
kubectl create namespace harbor
helm upgrade --install harbor deploy/charts/harbor/charts/harbor-1.18.4.tgz -n harbor \
  -f deploy/charts/harbor/values.yaml \
  -f deploy/charts/harbor/values-desktop.yaml \
  --timeout 15m --wait=false
```

### 2.2 k3s（节点无外网）

先定位 k3s 容器，不要写死名字：

```bash
docker ps | grep k3s
```

```bash
cd deploy/charts/harbor
./deploy.sh pull
./deploy.sh import-k3s
VALUES_FILE=./values-k3s.yaml ./deploy.sh deploy
```

`import-k3s` 把镜像导入 containerd 的 **`k8s.io`** 命名空间。rancher/k3s 里用 `/bin/kubectl`，不要写 `k3s kubectl`。

手工导入示例（容器名以 `docker ps` 为准）：

```bash
docker save goharbor/harbor-core:v2.14.4 | docker exec -i <k3s容器> ctr -n k8s.io images import -
```

### 2.3 验证部署

```bash
# Docker Desktop
kubectl -n harbor get pods -w

# k3s
docker exec <k3s容器> kubectl -n harbor get pods -w

curl http://localhost:30002
```

---

## 3. 镜像清单

### 3.1 Harbor 组件镜像（9 个）

| # | 镜像 | 组件 | 大小 |
|---|------|------|------|
| 1 | `goharbor/harbor-core:v2.14.4` | Harbor 核心服务 | ~200MB |
| 2 | `goharbor/harbor-db:v2.14.4` | PostgreSQL 数据库 | ~200MB |
| 3 | `goharbor/harbor-jobservice:v2.14.4` | 异步任务服务 | ~100MB |
| 4 | `goharbor/harbor-portal:v2.14.4` | 前端页面 | ~50MB |
| 5 | `goharbor/nginx-photon:v2.14.4` | 反向代理 | ~50MB |
| 6 | `goharbor/registry-photon:v2.14.4` | 镜像仓库（Docker Registry） | ~60MB |
| 7 | `goharbor/harbor-registryctl:v2.14.4` | 仓库管理控制器 | ~60MB |
| 8 | `goharbor/trivy-adapter-photon:v2.14.4` | Trivy 漏洞扫描适配器 | ~100MB |
| 9 | `goharbor/redis-photon:v2.14.4` | Redis 缓存 | ~30MB |

> **注意**: Harbor v2.15.x 使用 `valkey-photon` 替代 `redis-photon`，v2.14.x 仍使用 `redis-photon`。

### 3.2 k3s 系统组件镜像（已预装）

| # | 镜像 | 组件 |
|---|------|------|
| 1 | `rancher/local-path-provisioner:v0.0.30` | 本地存储 Provisioner |
| 2 | `rancher/mirrored-coredns-coredns:1.12.0` | CoreDNS |
| 3 | `rancher/mirrored-metrics-server:v0.7.2` | Metrics Server |
| 4 | `rancher/mirrored-pause:3.6` | Pod 网络占位容器 |
| 5 | `rancher/mirrored-library-busybox:1.36.1` | Busybox 工具 |

### 3.3 Tekton 镜像（已预装）

| # | 镜像 | 组件 |
|---|------|------|
| 1 | `ghcr.io/tektoncd/pipeline/controller:v1.15.1` | Tekton Pipeline 控制器 |
| 2 | `ghcr.io/tektoncd/pipeline/webhook:v1.15.1` | Tekton Webhook |
| 3 | `ghcr.io/tektoncd/pipeline/events:v1.15.1` | Tekton 事件控制器 |
| 4 | `ghcr.io/tektoncd/pipeline/resolvers:v1.15.1` | Tekton 远程解析器 |
| 5 | `ghcr.io/tektoncd/pipeline/entrypoint:v1.15.1` | Tekton 入口点 |
| 6 | `ghcr.io/tektoncd/pipeline/nop:v1.15.1` | Tekton NOP |
| 7 | `ghcr.io/tektoncd/pipeline/sidecarlogresults:v1.15.1` | Tekton Sidecar 日志 |
| 8 | `ghcr.io/tektoncd/pipeline/workingdirinit:v1.15.1` | Tekton 工作目录初始化 |

### 3.4 流水线任务镜像（可选，按需预拉）

| # | 镜像 | 用途 | 类型 |
|---|------|------|------|
| 1 | `docker.io/alpine/git:2.45.2` | 代码克隆 | 固定 |
| 2 | `docker.io/aquasec/trivy:0.66.0` | 安全扫描 | 固定 |
| 3 | `docker.io/rclone/rclone:1.68.2` | 对象存储上传 | 固定 |
| 4 | `docker.io/bitnami/kubectl:1.31.4` | 部署 | 固定 |
| 5 | `docker.io/curlimages/curl:8.11.1` | 通知 | 固定 |
| 6 | `quay.io/containers/buildah:v1.37.0` | 镜像构建 | 固定 |
| 7 | `ghcr.io/sigstore/cosign:v2.4.3` | 镜像签名 | 固定 |
| 8 | `docker.io/semgrep/semgrep:1.97.0` | Semgrep 检查 | 固定 |
| 9 | `docker.io/sonarsource/sonar-scanner-cli:11.2` | Sonar 检查 | 固定 |
| 10 | `docker.io/maven:3.8.8-eclipse-temurin-17` | Java 17 + Maven 3.8 | 动态 |
| 11 | `docker.io/maven:3.9.9-eclipse-temurin-17` | Java 17 + Maven 3.9 | 动态 |
| 12 | `docker.io/maven:3.9.9-eclipse-temurin-21` | Java 21 + Maven 3.9 | 动态 |
| 13 | `docker.io/node:20-bookworm` | Node 20 | 动态 |
| 14 | `docker.io/node:22-bookworm` | Node 22 | 动态 |
| 15 | `docker.io/golang:1.22` | Go 1.22 | 动态 |
| 16 | `docker.io/golang:1.23` | Go 1.23 | 动态 |
| 17 | `docker.io/python:3.11-bookworm` | Python 3.11 | 动态 |
| 18 | `docker.io/python:3.12-bookworm` | Python 3.12 | 动态 |

---

## 4. 升级/降级操作

### 4.1 从 v2.15.2 降级到 v2.14.4

#### 步骤 1：拉取新版本镜像

```bash
# 宿主机拉取镜像
for img in harbor-core harbor-db harbor-jobservice harbor-portal \
           nginx-photon registry-photon harbor-registryctl \
           trivy-adapter-photon redis-photon; do
  docker pull "goharbor/${img}:v2.14.4"
done
```

#### 步骤 2：导入 k3s containerd

```bash
for img in harbor-core harbor-db harbor-jobservice harbor-portal \
           nginx-photon registry-photon harbor-registryctl \
           trivy-adapter-photon redis-photon; do
  docker save "goharbor/${img}:v2.14.4" | \
    docker exec -i devops-k3s ctr -n k8s.io images import -
done
```

#### 步骤 3：Helm 升级

```bash
docker exec devops-k3s sh -c 'KUBECONFIG=/etc/rancher/k3s/k3s.yaml \
  helm upgrade harbor harbor/harbor --version 1.18.4 -n harbor \
  --reuse-values \
  --set core.image.tag=v2.14.4 \
  --set jobservice.image.tag=v2.14.4 \
  --set portal.image.tag=v2.14.4 \
  --set registry.registry.image.tag=v2.14.4 \
  --set registry.controller.image.tag=v2.14.4 \
  --set trivy.image.tag=v2.14.4 \
  --set nginx.image.tag=v2.14.4 \
  --set database.internal.image.tag=v2.14.4 \
  --set redis.internal.image.tag=v2.14.4 \
  --set redis.internal.image.repository=goharbor/redis-photon'
```

#### 步骤 4：处理 Redis 数据兼容性

```bash
# 如果 Redis Pod 因 RDB 格式不兼容崩溃：
# Valkey → Redis 的 RDB 格式不兼容，需要删除旧 PVC
docker exec devops-k3s kubectl -n harbor delete pod harbor-redis-0 --force --grace-period=0
docker exec devops-k3s kubectl -n harbor delete pvc data-harbor-redis-0 --force --grace-period=0
# StatefulSet 会自动重建 Pod + PVC
```

---

## 5. 遇到的问题及解决方法

### 问题 1：Core / JobService 持续 CrashLoopBackOff

**现象**：Core 和 JobService Pod 反复重启，无法进入 Running。

**根因**：镜像拉取失败。k3s 集群无互联网访问，Docker Hub 连接超时。

**解决**：
1. 在宿主机 `docker pull` 拉取镜像
2. 通过 `docker save | docker exec ctr -n k8s.io images import -` 导入到 k3s

### 问题 2：镜像导入后 Pod 仍拉取失败

**现象**：镜像已导入 `ctr images import`，但 Pod 仍然 `ImagePullBackOff`。

**根因**：`ctr images import` 默认导入到 containerd 的 `default` 命名空间，但 **kubelet 只读取 `k8s.io` 命名空间**。

**解决**：使用 `ctr -n k8s.io images import -` 导入。

```bash
# ❌ 错误（kubelet 找不到）
ctr images import -

# ✅ 正确
ctr -n k8s.io images import -
```

### 问题 3：Harbor v2.15.2 Core 运行时 panic

**现象**：Core Pod 启动后在初始化 OpenAPI 路由时崩溃，日志显示 Go nil pointer dereference。

```
net/url.(*URL).String()
    → go-openapi/spec/expander.go → nil pointer dereference
```

**根因**：Harbor v2.15.2 依赖的 `go-openapi/spec` 库在 ARM64 宿主（Rosetta 2 模拟 x86_64）下存在 nil pointer 问题。

**解决**：降级到 v2.14.4（使用 `go-openapi/spec` 较旧版本，无此 bug）。

### 问题 4：Harbor v2.14.4 Core 运行时 panic（降级后）

**现象**：降级到 v2.14.4 后 Core 仍然 panic，但错误位置不同。

```
k8s.io/api/apps/v1beta2 → map.init() panic
```

**根因**：仍是 Rosetta 2 模拟下的 Go 运行时问题，**非确定性**（intermittent）。多次重启后 Pod 可正常启动。

**解决**：等待 Pod 自动重启几次后稳定运行。最终 Core 在第 4 次重启后正常。

### 问题 5：Valkey → Redis 数据格式不兼容

**现象**：Redis Pod 启动后立即退出：
```
# Wrong signature trying to load DB from file
# Fatal error loading the DB
```

**根因**：Harbor v2.15.2 使用的 Valkey 与 v2.14.4 的 Redis RDB 格式不兼容。旧 PVC 中的 `dump.rdb` 由 Valkey 写入，Redis 无法读取。

**解决**：删除 Redis PVC，让 StatefulSet 重建：

```bash
kubectl -n harbor delete pod harbor-redis-0 --force
kubectl -n harbor delete pvc data-harbor-redis-0 --force
```

> **注意**: Redis 仅缓存会话数据，删除不影响持久数据（数据在 PostgreSQL 中）。

### 问题 6：Helm `--reuse-values` 保留旧版镜像标签

**现象**：升级 chart 版本后 Pod 仍使用旧版镜像（2.15.2）。

**根因**：`--reuse-values` 保留了所有旧 values，包括镜像 tag。

**解决**：显式通过 `--set image.tag=v2.14.4` 覆盖每个组件的镜像标签。

---

## 6. 验证

### 6.1 Pod 状态

```bash
# Docker Desktop
kubectl -n harbor get pods

# k3s
docker exec <k3s容器> kubectl -n harbor get pods
```

期望输出：
```
NAME                    READY   STATUS    RESTARTS   AGE
harbor-core             1/1     Running   4          6m
harbor-database         1/1     Running   0          6m
harbor-jobservice       1/1     Running   6          6m
harbor-nginx            1/1     Running   0          7m
harbor-portal           1/1     Running   0          7m
harbor-redis            1/1     Running   0          1m
harbor-registry         2/2     Running   0          6m
harbor-trivy            1/1     Running   0          7m
```

### 6.2 页面访问

```bash
# NodePort 方式访问
curl http://localhost:30002
# 应返回 Harbor 登录页面 HTML
```

### 6.3 查看 Helm Release

```bash
# Docker Desktop
helm list -n harbor

# k3s
docker exec <k3s容器> helm list -n harbor
```

---

## 附录：常用命令

```bash
# 查看日志
kubectl -n harbor logs -f deployment/harbor-core

# 查看所有 PVC
kubectl -n harbor get pvc

# 查看 Helm Values
helm get values harbor -n harbor

# 查看镜像缓存
ctr -n k8s.io images ls | grep goharbor

# 强制删除卡住 Pod
kubectl -n harbor delete pod <pod-name> --force --grace-period=0
```