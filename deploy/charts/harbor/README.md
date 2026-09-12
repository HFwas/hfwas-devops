# Harbor Helm 部署包

> 日期：2026-09-13
> 版本：v0.1
> Harbor：v2.14.4
> Helm Chart：1.18.4（官方 `goharbor/harbor-helm`）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：官方 chart 1.18.4 离线包 + Desktop / k3s values + 一键脚本 |

---

单机 **Harbor v2.14.4**。用官方微服务 Chart，不自写模板。目标是本机 Docker Desktop / 单节点 k3s 能尽快起来：登录、建项目、HTTP 推拉镜像、Trivy 扫描。

对照文档：[`docs/container-platform/harbor-deployment.md`](../../../docs/container-platform/harbor-deployment.md)。

## 目录

```
deploy/charts/harbor/
├── values.yaml              # 通用默认（NodePort、关 TLS、钉死 v2.14.4）
├── values-desktop.yaml      # Docker Desktop（默认 StorageClass + 宿主机代理）
├── values-k3s.yaml          # k3s local-path
├── images.txt
├── deploy.sh
├── charts/harbor-1.18.4.tgz # 官方 chart vendored
└── README.md
```

## 资源与端口

| 项 | 值 |
|----|-----|
| HTTP | NodePort `30002` |
| HTTPS 端口预留 | NodePort `30003`（本包关闭 TLS，浏览器走 HTTP） |
| 镜像盘 | registry 10Gi |
| 数据库盘 | 5Gi |
| Redis / JobLog / Trivy | 2Gi / 2Gi / 5Gi |
| 管理员 | `admin` / `values.yaml` 里的 `harborAdminPassword`（默认 `Harbor12345`） |

机器内存建议 ≥ 8Gi 可用。本包与 GitLab Omnibus 同机时，Desktop 节点约 19Gi 一般够用。

## 前置

- 可用的 Kubernetes（Docker Desktop K8s 或 Docker 里的 k3s）
- `kubectl`、`helm` 3.x
- 能拉 Docker Hub（可用 Clash `127.0.0.1:7890`）

当前 Windows Docker Desktop 用**宿主机** `helm` / `kubectl`（context `docker-desktop`）。  
k3s 在容器里时：先进 k3s 容器再 `helm` / `kubectl`，不要用宿主机 kubeconfig 打集群。

## 一键部署（Docker Desktop）

```bash
cd deploy/charts/harbor
chmod +x deploy.sh

./deploy.sh all
```

或分步：

```bash
./deploy.sh pull
./deploy.sh deploy
./deploy.sh status
```

手动 Helm：

```bash
kubectl create namespace harbor
helm upgrade --install harbor charts/harbor-1.18.4.tgz -n harbor \
  -f values.yaml -f values-desktop.yaml \
  --timeout 15m --wait=false
```

## k3s（无网节点）

集群进不了外网时，宿主机拉完镜像再导入 containerd 的 `k8s.io` 命名空间：

```bash
./deploy.sh pull
./deploy.sh import-k3s
VALUES_FILE=./values-k3s.yaml ./deploy.sh deploy
```

`import-k3s` 会 `docker ps | grep k3s` 定位容器，不要写死名字。必须 `ctr -n k8s.io images import`，不要用 `k3s kubectl`。

## 访问

- URL: http://localhost:30002
- 用户: `admin`
- 密码: `values.yaml` 里的 `harborAdminPassword`（默认 `Harbor12345`）

```bash
kubectl -n harbor get pods -w
```

首次启动时 `harbor-jobservice` 可能在 core 未就绪时 CrashLoop 几次，core Ready 后会自己起来。全部 `1/1 Running` 后再打开页面。

## 改密码 / 地址

```bash
helm upgrade --install harbor charts/harbor-1.18.4.tgz -n harbor \
  -f values.yaml -f values-desktop.yaml \
  --set harborAdminPassword='YourStrongPass' \
  --set externalURL='http://localhost:30002'
```

`harborAdminPassword` **只在首次初始化**写入；已有数据盘时请用 Harbor UI 改密码。

## 卸载

```bash
./deploy.sh uninstall
# PVC 默认保留；彻底清理：
kubectl -n harbor delete pvc --all
kubectl delete ns harbor
```

## 刻意关掉的东西

- Ingress / HTTPS（本机 NodePort HTTP）
- metrics exporter
- 官方默认 Ingress `core.harbor.domain`

Trivy 默认开启；漏洞库走 `host.docker.internal:7890`（见 `values-desktop.yaml` / `values-k3s.yaml`）。
