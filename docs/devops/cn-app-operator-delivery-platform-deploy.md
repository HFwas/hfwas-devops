# cn-app-operator 与 delivery-platform 部署到 k3s

> 日期：2026-09-23
> 版本：v0.3

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-23 | 初版：记录两个组件在本地 k3s（devops 命名空间）的构建与部署方式、本机环境约束与遗留问题 |
| v0.2 | 2026-09-23 | 补完「本集群」注册：改用 `kubectl create token` 签发（legacy SA token Secret 被 k3s 判 401）；新增 §8 停掉 Harbor/Tekton/Prometheus 的操作与回滚，并修正 §7 遗留问题 |
| v0.3 | 2026-09-23 | 新增 §9：停掉 compose 的 backend/keycloak/nacos，并把 Colima VM 由 4C/8GiB 扩到 6C/16GiB；据实测结果重写 §7（内存已不再是瓶颈） |

---

## 1. 范围

把仓库里两个子项目部署到本地 k3s 集群的 `devops` 命名空间：

| 组件 | 类型 | 入口 |
|------|------|------|
| `cn-app-operator` | Go Operator（CRD + 控制器骨架） | `kubectl get app/cloudservice/cloudcomponent/producttask` |
| `delivery-platform` | Go 后端 + Vue 3 前端 | 前端 NodePort `30881` → nginx 反代 `/api` 到后端 |

`devops` 命名空间此前不存在（集群里只有 `hfwas-devops`），本次一并创建。
`cn-app-operator/config/manager/manager.yaml` 里写死了 `namespace: devops`，与文档口径一致。

## 2. 本机环境约束（重要）

这台机器上有三个会直接影响构建/部署的既有状况，**不是本次改动引入的**：

### 2.1 拉不动任何 Docker 镜像

- Docker daemon 跑在 **Colima**（`docker context` 为 `colima`），其代理配置为 `http://192.168.5.2:7890` —— 该地址已失效（本机当前网段是 192.168.1.0/24）。
- 结果：`registry-1.docker.io` 直连被墙，走 127.0.0.1:7890 也不通；`docker.m.daocloud.io` / `docker.1ms.run` 等镜像站能取到 manifest，但 **blob 下载一律 EOF**（blob CDN 走了那个失效代理）。
- 因此**基础镜像全部使用本机已有 tag**，不新增 pull：

| 用途 | 使用镜像 |
|------|----------|
| Go 构建阶段 | `golang:1.22`（Debian，本机已有，含 gcc） |
| Node 构建阶段 | `node:20-alpine` |
| 运行阶段（Go） | `alpine:latest`（3.24.1） |
| 运行阶段（前端） | `nginx:1.27-alpine` |

- 容器内网络正常：`goproxy.cn`、`mirrors.aliyun.com`、`dl-cdn.alpinelinux.org` 直连可用，所以 Dockerfile 里依赖走国内源。

### 2.2 没有 buildx / BuildKit

`DOCKER_BUILDKIT=1 docker build` 会报 `buildx component is missing`。只能用 legacy builder，
它**不会**注入 `TARGETARCH`。所以 Go 构建不再写死 `GOARCH`，改为按构建镜像自身架构编译
（BuildKit 会按 `--platform` 拉 builder，两种方式都得到与节点一致的二进制）。

本机与 k3s 节点都是 **aarch64**；原先 `cn-app-operator/Dockerfile` 写死 `GOARCH=amd64`，即使构建成功也跑不起来。

### 2.3 宿主机访问不到 NodePort

`docker-compose.yml` 里 k3s 服务只 publish 了 `6443 / 30880 / 30222`，其它 NodePort 出不了容器。
Harbor 的 `30002`、既有后端的 `30889` 同样从宿主机不通，属同一原因。

验证 NodePort 是否正常，要在容器内测：

```bash
docker exec devops-k3s wget -qO- --timeout=6 http://127.0.0.1:30881/
```

要让浏览器直连，需在 compose 的 k3s `ports:` 里补 `"30881:30881"` 并重建容器
（k3s 数据在 `k3s-server` 卷里，重建不丢集群，但会重启整个集群）。

## 3. 构建镜像

```bash
# operator
cd cn-app-operator && docker build -t hfwas/cn-app-operator:latest .

# 后端（CGO sqlite：musl-gcc 静态链接，运行阶段才落得了 alpine）
cd delivery-platform/backend && docker build -t hfwas/delivery-platform-backend:latest .

# 前端（vue-tsc 类型检查 + vite build，nginx 托管）
cd delivery-platform/frontend && docker build -t hfwas/delivery-platform-frontend:latest .
```

导入 k3s（AGENTS 约定：容器内 `ctr`，不要用宿主机 kubeconfig）：

```bash
for img in hfwas/cn-app-operator hfwas/delivery-platform-backend hfwas/delivery-platform-frontend; do
  docker save "$img:latest" | docker exec -i devops-k3s ctr -n k8s.io images import -
done
```

`mattn/go-sqlite3` 需要 CGO。构建阶段 `apt-get install musl-tools` 后用
`CC=musl-gcc CGO_ENABLED=1 -ldflags '-linkmode external -extldflags "-static"'`，
得到不依赖 glibc 的静态二进制。该步编译 `sqlite3-binding.c` 较慢（本机约 30+ 分钟）。

## 4. 部署

```bash
# 1) CRD
docker exec -i devops-k3s kubectl apply -f - < <(cat cn-app-operator/config/crd/*.yaml)

# 2) operator RBAC + Deployment
docker exec -i devops-k3s kubectl apply -f cn-app-operator/config/rbac/rbac.yaml
docker exec -i devops-k3s kubectl apply -f cn-app-operator/config/manager/manager.yaml

# 3) delivery-platform
cd delivery-platform/deploy/k8s
for f in 00-namespace.yaml 10-rbac.yaml 20-backend.yaml 30-frontend.yaml; do
  docker exec -i devops-k3s kubectl apply -f "$f"
done
```

部署产物：

| 资源 | 名称 | 说明 |
|------|------|------|
| CRD | `apps` / `cloudservices` / `cloudcomponents` / `producttasks`.delivery.hfwas.io | ProductTask 是 Cluster 作用域，其余 Namespaced |
| Deployment | `cn-app-operator` | 4 个控制器（App / CloudService / CloudComponent / ProductTask） |
| Deployment | `delivery-backend` | ClusterIP `8180`，`strategy: Recreate`（SQLite 单写） |
| Deployment | `delivery-frontend` | NodePort `30881` → 80 |
| PVC | `delivery-platform-data` | 2Gi，local-path，挂到后端 `/data`（SQLite + 导入的包） |
| RBAC | `delivery-platform` SA + ClusterRole(Binding) + 长期 token Secret | 见 §5 |

## 5. 集群注册（delivery-platform 怎么拿到集群）

后端**不是**用 in-cluster ServiceAccount 直接调 API 的：它按「集群」维度存用户导入的
kubeconfig（`internal/service/kube.go`）。所以：

- 直接导入 `data/pipeline/kubeconfig.yaml` 没用 —— 它的 `server` 是 `https://127.0.0.1:6443`，Pod 内不可达。
- 要拼一份 `server: https://kubernetes.default.svc:443` 的 kubeconfig，在集群内 POST 给
  `/api/delivery/clusters`，把「本集群」注册进去。

**token 必须用 `kubectl create token` 现签**：

```bash
docker exec devops-k3s kubectl -n devops create token delivery-platform --duration=8760h
```

`kubernetes.io/service-account-token` 类型的 Secret（legacy 长期 token）在 k3s 上**用不了**：
Secret 里的 token 身份与 SA uid 都对得上、JWT 也没有 `exp`，但直接打 API server 一律
`Unauthorized`（干净 kubeconfig 复现）。换成 `kubectl create token` 的 bound token 立刻可用。

CA 取 `data/pipeline/kubeconfig.yaml` 里的 `certificate-authority-data`（与集群内
`rancher/k3s` 的 CA 一致，已比对过）。

实测注册结果：

```json
{"code":0,"data":{"id":1,"name":"local-k3s","serverHost":"https://kubernetes.default.svc:443",
                  "isCurrent":true,"status":"UP","version":"1.31"}}
```

权限自检（用签发出来的 kubeconfig）：

```bash
docker exec devops-k3s kubectl --kubeconfig=/tmp/kc.yaml auth can-i list cloudcomponents.delivery.hfwas.io
```

## 6. 验证

```bash
docker exec devops-k3s kubectl -n devops get deploy,pods,svc,pvc

# 前端页面
docker exec devops-k3s wget -qO- --timeout=20 http://127.0.0.1:30881/

# 前端 nginx → 后端 Service（同时验证集群 DNS）
docker exec devops-k3s kubectl -n devops exec deploy/delivery-frontend -- \
  wget -qO- --timeout=8 http://delivery-backend:8180/health
```

## 7. 已知遗留问题

1. **NodePort 出不了宿主机**，见 §2.3。集群内 `127.0.0.1:<nodePort>` 正常。
2. **VM 内存曾经严重超卖**，按 §8 + §9 处理后已解决（详见 §9.3 的前后对比）。
   8GiB 时期的实证：`prometheus-prometheus-node-exporter` 8 天内被 OOMKilled **92 次**、
   `tekton-pipelines-webhook` 11 次，连 `delivery-backend` 也被 OOMKilled 过；停掉这些负载后
   coredns 与 metrics-server 自己从 CrashLoopBackOff 恢复成 Running。
3. **`devops-frontend` 会随 `devops-backend` 一起废掉**：它的 nginx 配置里
   `proxy_pass http://backend:8089` 是启动期静态解析，后端容器一停就
   `nginx: [emerg] host not found in upstream "backend"`，随即循环重启（见 §9.2）。
   要改用变量 + `resolver` 才能在 upstream 缺失时存活，当前没改。
4. **`hfwas-devops/devops-backend`（k8s 里那份）仍 Pending 8 天**、`hfwas-pipeline/busbox` 仍
   ImagePullBackOff。这两个是历史遗留，与本次部署无关，未处理。

## 8. 停掉 Harbor / Tekton / Prometheus

用 **scale 到 0**（不是 uninstall）—— 三者里 Harbor 与 Prometheus 是 Helm 装的、Tekton 是
manifest 装的，而**本机拉不动镜像**（§2.1），uninstall 之后装不回来。scale 是纯可逆操作。

```bash
# Prometheus：先停 operator，否则它会把 Pod 拉回来
docker exec devops-k3s kubectl -n monitoring scale deploy \
  prometheus-kube-prometheus-operator prometheus-kube-state-metrics --replicas=0
docker exec devops-k3s kubectl -n monitoring scale sts \
  prometheus-prometheus-kube-prometheus-prometheus --replicas=0
# DaemonSet 不支持 scale，改用 nodeSelector 让它调度不到节点
docker exec devops-k3s kubectl -n monitoring patch ds prometheus-prometheus-node-exporter \
  --type merge -p '{"spec":{"template":{"spec":{"nodeSelector":{"delivery.hfwas.io/disabled":"true"}}}}}'

# Tekton
docker exec devops-k3s kubectl -n tekton-pipelines scale deploy \
  tekton-pipelines-webhook tekton-pipelines-controller tekton-events-controller --replicas=0
docker exec devops-k3s kubectl -n tekton-pipelines-resolvers scale deploy \
  tekton-pipelines-remote-resolvers --replicas=0

# Harbor
docker exec devops-k3s kubectl -n harbor scale deploy \
  harbor-core harbor-jobservice harbor-nginx harbor-portal harbor-registry --replicas=0
docker exec devops-k3s kubectl -n harbor scale sts \
  harbor-database harbor-redis harbor-trivy --replicas=0
```

三者的原副本数均为 **1**。恢复即把上面的 `--replicas=1`，并去掉 node-exporter 的 nodeSelector。

**数据未受影响**：所有 PVC 仍 `Bound`，三个 StatefulSet 的
`persistentVolumeClaimRetentionPolicy` 都是 `whenDeleted/whenScaled: Retain`，scale 到 0 不会删卷。

**webhook 已实测不影响**：Tekton 的 `config.webhook.pipeline.tekton.dev`（validating）虽是
`failurePolicy: Fail` 且 `namespaceSelector` 为空，但规则写的是 `resources: ["configmaps/*"]` —— 
`/*` 被当作子资源解析，实际匹配不到任何对象。停掉 webhook 后实测创建 ConfigMap 与 Pod 均正常。

Prometheus 的 `prometheus-kube-prometheus-admission` 两条规则 `failurePolicy` 都是 `Ignore`，
停掉不影响。

## 9. 停 compose 常驻栈 + Colima 扩容

§8 只解决了 k8s 侧。VM 8GiB 的大头其实在 k8s 之外：compose 的 `devops-backend`（Spring，~1.3GB）、
`nacos-standalone-derby`（~1.1GB）、`devops-keycloak`（~0.6GB），加起来约 3GB。

### 9.1 停掉三个容器

`backend` / `keycloak` 是本仓库 compose（project `hfwas-devops`）的服务；
`nacos-standalone-derby` **不归 compose 管**（无 `com.docker.compose.project` 标签，`restart=no`），
要单独 `docker stop`。

```bash
docker compose -p hfwas-devops -f docker-compose.yml stop backend keycloak
docker stop nacos-standalone-derby
```

恢复：`docker compose -p hfwas-devops start backend keycloak` + `docker start nacos-standalone-derby`。

### 9.2 副作用：devops-frontend 会循环重启

前端容器启动时 nginx 静态解析 upstream，后端一停就：

```
nginx: [emerg] host not found in upstream "backend" in /etc/nginx/conf.d/default.conf:23
```

进程随即退出并被反复拉起。Docker 的重启退避让它几乎不占资源（实测 0% CPU / 0B），
但要彻底安静就一并 `docker stop devops-frontend`，否则把 nginx 配置改成
变量 + `resolver` 的形式。

### 9.3 Colima 扩容

宿主机 8 核 / 32GB，VM 原为 4 核 / 8GiB。改规格必须 stop → start（会重启整个 VM）：

```bash
colima stop
colima start --memory 16 --cpu 6
```

`--memory`/`--cpu` 只覆盖这两项，其余（`runtime: docker`、`kubernetes.enabled: false`、
`vmType: vz`、disk 50GiB）读 `~/.colima/default/colima.yaml` 保持不变。

VM 重启后容器按 restart policy 自动回来（`devops-k3s` / `devops-kong` 都是
`unless-stopped`），而 9.1 里**手动 stop 过的容器不会回来**，符合预期；
k8s 侧工作负载也从 k3s 卷里原地恢复（`local-k3s` 的注册记录、PVC 数据都在）。

前后对比（同为「§8 三个组件已停」前提）：

| 指标 | 8GiB / 4C | 16GiB / 6C |
|------|-----------|------------|
| VM 可用内存 | ~37 MB | ~11.7 GB |
| VM load average | 148 | 1.16 |
| 节点状态 | 反复 Ready↔NotReady | 稳定 Ready |
| `gitlab-0` | 0/1，卡 20 小时 | 1/1 Running |
| `kube-system/helper-pod-delete-pvc-*` | 反复出现 | 消失 |

`delivery-platform` 读到的节点容量也随之更新（`GET /api/delivery/clusters/1/nodes`）：
`allocatableCpu: "6"`、`allocatableMemory: "16341772Ki"`。
