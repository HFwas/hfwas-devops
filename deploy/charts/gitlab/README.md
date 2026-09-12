# 单机最小 GitLab CE（Helm）

Omnibus **单 Pod**，内置 PostgreSQL / Redis / Gitaly / Nginx。不做官方微服务 Chart，目标是本机 Docker Desktop / 单节点 k8s 能尽快起来。

## 目录

```
deploy/charts/gitlab/
├── Chart.yaml
├── values.yaml            # 通用默认
├── values-desktop.yaml    # Docker Desktop NodePort
├── images.txt
├── deploy.sh
└── templates/
```

## 资源（默认）

| 项 | 值 |
|----|----|
| CPU | request 1 / limit 2 |
| 内存 | request 4Gi / limit 8Gi |
| 数据盘 | 20Gi + config 2Gi + logs 5Gi |
| HTTP | NodePort `30880` |
| SSH | NodePort `30222` |

机器内存建议 ≥ 8Gi 可用；当前 Desktop 节点约 19Gi，够用。

## 前置

- 可用的 Kubernetes（如 Docker Desktop K8s）
- `kubectl`、`helm` 3.x（本机未装 helm 时可先安装）
- 能拉 Docker Hub（可用 Clash 系统代理）

## 一键部署

```bash
cd deploy/charts/gitlab
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
kubectl create namespace gitlab
helm upgrade --install gitlab . -n gitlab \
  -f values.yaml -f values-desktop.yaml \
  --timeout 20m --wait=false
```

## 访问

- URL: http://localhost:30880  
- 用户: `root`  
- 密码: `values.yaml` 里的 `rootPassword`（默认 `ChangeMe123!`）

首次启动会跑 `gitlab-ctl reconfigure`，**5~15 分钟**属正常。看日志：

```bash
kubectl -n gitlab logs -f sts/gitlab
kubectl -n gitlab get pods -w
```

`startupProbe` 最长约 20 分钟（120s + 40×30s），超时再查资源/镜像。

## 改密码 / 地址

编辑 `values.yaml` 或部署时覆盖：

```bash
helm upgrade --install gitlab . -n gitlab \
  -f values.yaml -f values-desktop.yaml \
  --set rootPassword='YourStrongPass' \
  --set externalUrl='http://localhost:30880'
```

> `initial_root_password` **只在首次初始化**生效；已有数据盘时改密码请用 GitLab UI 或 `gitlab-rake`.

## 卸载

```bash
./deploy.sh uninstall
# PVC 默认保留；彻底清理：
kubectl -n gitlab delete pvc --all
kubectl delete ns gitlab
```

## 刻意关掉的东西

- HTTPS / Let’s Encrypt  
- Prometheus / Grafana / Alertmanager  
- GitLab Runner（需要再单独装）  
- 官方 `gitlab/gitlab` 微服务 Chart  

够登录、建库、HTTP/SSH clone 即可。
