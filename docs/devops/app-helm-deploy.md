# 应用镜像构建与 Helm 升级

> 日期：2026-09-13
> 版本：v0.1

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：把原先手工 docker build + helm upgrade 收成 `deploy-app` 脚本 |

---

## 1. 背景

此前升级本仓库应用（`devops-backend` / `devops-frontend`）没有脚本，是在仓库根目录手工：

```bash
docker build -t hfwas/devops-backend:$TAG -f backend/Dockerfile .
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

## 4. 验证

```bash
kubectl -n devops get deploy devops-backend -o jsonpath='{.spec.template.spec.containers[0].image}{"\n"}'
curl -sS http://localhost:30889/health/check
```

期望镜像带本次 `TAG`，健康检查返回 `UP`。

前端经 Kong / 集群内 Service 访问，脚本不单独打前端 HTTP。旧流水线运行记录里的耗时等库内数据不会随镜像升级自动纠正，需要新跑一次业务验证。
