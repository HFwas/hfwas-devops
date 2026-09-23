# cn-app-operator 与 delivery-platform 部署到 k3s

> 日期：2026-09-23
> 版本：v0.1

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-23 | 初版：记录两个组件在本地 k3s（devops 命名空间）的构建与部署方式、本机环境约束与遗留问题 |

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
- 本目录的 `10-rbac.yaml` 建了一个 SA + `kubernetes.io/service-account-token` 长期 token Secret，
  可以据此拼一份 `server: https://kubernetes.default.svc:443` 的 kubeconfig，
  启动后在集群内 POST 给平台即可把「本集群」注册进去。

```bash
# 取出 token / CA，拼 kubeconfig 后 POST（在容器内做，浏览器侧无关）
docker exec devops-k3s kubectl -n devops get secret delivery-platform-token -o jsonpath='{.data.token}'
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

1. **k3s 控制面不稳定**。VM 只有 4 vCPU / 8GiB，装了 Harbor + Tekton + Prometheus + GitLab，
   内存长期只剩 ~90MB 可用，节点反复 Ready↔NotReady，`kubectl` 频繁
   `TLS handshake timeout` / `http2: client connection lost`。
   本次部署过程中 Pod 曾全部 `1/1 Running`，但集群抖动时 API 与 NodePort 会短暂不可用。
   **这是部署之前就存在的状态**（`hfwas-devops/devops-backend` 已 Pending 8 天、Harbor 多个 Pod Pending 8 天）。
2. **NodePort 出不了宿主机**，见 §2.3。
3. **`devops` 命名空间下的「本集群」注册未完成**：注册接口会同步探活 `kubernetes.default.svc`，
   在控制面抖动时该请求会长时间挂住。集群稳定后重试即可。