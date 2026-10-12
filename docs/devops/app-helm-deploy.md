# 应用镜像构建与 Helm 升级

> 日期：2026-10-12
> 版本：v0.4

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：把原先手工 docker build + helm upgrade 收成 `deploy-app` 脚本 |
| v0.2 | 2026-10-01 | 构建命令改为 `deploy/docker/backend/Dockerfile` |
| v0.3 | 2026-10-12 | 增加推送到阿里云杭州仓库 `dkyy` 的 `scripts/push-aliyun.sh` |
| v0.4 | 2026-10-12 | 无 buildx 时回退 legacy builder；Dockerfile 去掉 BuildKit `--mount` |

---

## 1. 背景

此前升级本仓库应用（`devops-backend` / `devops-frontend`）没有脚本，是在仓库根目录手工：

```bash
docker build -t hfwas/devops-backend:$TAG -f deploy/docker/backend/Dockerfile .
helm upgrade --install devops-backend deploy/charts/backend -n devops \
  -f deploy/charts/backend/values.yaml \
  -f deploy/charts/backend/values-desktop.yaml \
  --set config.jwtSecret=desktop-dev-jwt-secret \
  --set image.tag=$TAG
kubectl -n devops rollout status deploy/devops-backend
```

Harbor / GitLab 各自已有 `deploy.sh`。应用侧现在用同一套约定：

| 环境 | 脚本 |
|------|------|
| macOS / Linux / Git Bash | `deploy/charts/deploy-app.sh` |
| Windows PowerShell | `deploy/charts/deploy-app.ps1` |

Keycloak / Kong / Harbor / GitLab **不在**本脚本范围内。

## 2. 集群约定

| 项 | 值 |
|----|----|
| 命名空间 | `devops` |
| 后端 release / Deployment | `devops-backend` |
| 前端 release / Deployment | `devops-frontend` |
| 后端镜像 | `hfwas/devops-backend:$TAG`（同时打 `latest`） |
| 前端镜像 | `hfwas/devops-frontend:$TAG`（同时打 `latest`） |
| `$TAG` 默认 | 本地时间 `YYYYMMDD-HHMM` |
| Values | `values.yaml` + `values-desktop.yaml` 或 `values-k3s.yaml` |
| 后端健康检查 | `http://localhost:30889/health/check` |

`PROFILE` 自动判断：

- `kubectl` context 为 `docker-desktop` → `desktop`（与 Docker 共用镜像，**不必** `ctr import`）
- 否则若存在 k3s 容器 → `k3s`（构建后 `docker save | ctr -n k8s.io images import`）
- 可强制：`PROFILE=desktop` 或 `PROFILE=k3s`

k3s 路径按 AGENTS：先 `docker ps | grep k3s` 定位容器，再在该容器里 `ctr import`。Helm / kubectl 仍走当前 kube-context（本机 Docker Desktop 即 host `helm`）。

## 3. 用法

在仓库根目录，或 `cd deploy/charts` 后执行。

```bash
# 改完后端：构建并升级（最近一次发版就是这条）
./deploy/charts/deploy-app.sh backend

# 改完前端
./deploy/charts/deploy-app.sh frontend

# 两个都升
./deploy/charts/deploy-app.sh all

# 只构建 / 只升级
./deploy/charts/deploy-app.sh build backend
TAG=20260913-0248 ./deploy/charts/deploy-app.sh upgrade backend

./deploy/charts/deploy-app.sh status
```

Windows：

```powershell
.\deploy\charts\deploy-app.ps1 backend
.\deploy\charts\deploy-app.ps1 frontend
.\deploy\charts\deploy-app.ps1 status
```

常用环境变量：

| 变量 | 默认 | 说明 |
|------|------|------|
| `TAG` | `YYYYMMDD-HHMM` | 镜像与 `image.tag` |
| `PROFILE` | 自动 | `desktop` / `k3s` |
| `NAMESPACE` | `devops` | Helm 命名空间 |
| `JWT_SECRET` | `desktop-dev-jwt-secret` | 后端 chart，升级时显式写入以免被默认值覆盖 |
| `SKIP_IMPORT` | 空 | `1` 时 k3s 也不 import |
| `K3S_CONTAINER` | 自动探测 | k3s 容器名 |
| `HEALTH_URL` | `http://localhost:30889/health/check` | 后端就绪探测 |

`docker build` 使用 `--pull=false`，复用本机已有基础镜像，不强制拉外网。

Homebrew `docker` + Colima 常常没有 `buildx`。此时若 `DOCKER_BUILDKIT=1`，会报
`buildx component is missing`。`deploy-app.sh` / `push-aliyun.sh` 会探测 `docker buildx`：
有则用 BuildKit，无则自动 `DOCKER_BUILDKIT=0`（legacy builder）。也可显式设置。

可选安装 buildx（之后脚本会优先用 BuildKit）：

```bash
brew install docker-buildx
mkdir -p ~/.docker/cli-plugins
ln -sfn "$(brew --prefix)/opt/docker-buildx/bin/docker-buildx" ~/.docker/cli-plugins/docker-buildx
docker buildx version
```

## 4. 验证

```bash
kubectl -n devops get deploy devops-backend -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
curl -sS http://localhost:30889/health/check
```

期望镜像带本次 `TAG`，健康检查返回 `UP`。

前端经 Kong / 集群内 Service 访问，脚本不单独打前端 HTTP。旧流水线运行记录里的耗时等库内数据不会随镜像升级自动纠正，需要新跑一次业务验证。

## 5. 推到阿里云

`scripts/push-aliyun.sh` 用同一套 Dockerfile 构建，并推到 `registry.cn-hangzhou.aliyuncs.com/dkyy/<仓库名>:<版本>`。密码放在环境变量里，脚本不保存密码。

```bash
export ALIYUN_REGISTRY_PASSWORD='你的仓库密码'
./scripts/push-aliyun.sh backend          # 默认仓库名 devops-backend，版本为当前时间
./scripts/push-aliyun.sh frontend 1.2.0
./scripts/push-aliyun.sh all 1.2.0
```

远端仓库名可用 `ALIYUN_IMAGE_BACKEND`、`ALIYUN_IMAGE_FRONTEND` 改掉。例如把后端推成 `code-api`：

```bash
ALIYUN_IMAGE_BACKEND=code-api ./scripts/push-aliyun.sh backend 1.2.0
# docker pull registry.cn-hangzhou.aliyuncs.com/dkyy/code-api:1.2.0
```

未设置密码时，脚本会在终端询问。设 `SKIP_LATEST=1` 则不额外推 `:latest`。

若构建报 `BuildKit is enabled but the buildx component is missing`：脚本已自动回退；
也可 `DOCKER_BUILDKIT=0 ./scripts/push-aliyun.sh all`，或按上面安装 buildx。
