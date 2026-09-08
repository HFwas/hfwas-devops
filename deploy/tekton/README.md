# Tekton 离线部署包

版本：`v1.15.1`（与 `scripts/install-tekton.sh` 一致）。

离线集群**不能**在 apply 时访问 `infra.tekton.dev` / `ghcr.io`。本目录自带官方 `release.yaml`；镜像需在有网机器上打成 tar，再拷到目标节点导入。

## 包内容

| 路径 | 作用 |
|------|------|
| `pipeline/v1.15.1/release.yaml` | 官方 Pipelines 安装清单（CRD + controller/webhook） |
| `pipeline/v1.15.1/images.txt` | 引擎镜像（含 TaskRun sidecar：entrypoint / nop / workingdirinit） |
| `jobs/images.txt` | 平台固定任务镜像（clone / lint / scan / image / upload / deploy / notify） |
| `jobs/toolchain-images.txt` | 技术栈镜像（Maven / Node / Go / Python） |

Triggers / Dashboard / Operator **不包含**。本期控制面只需要 Pipelines。

## 有网机器：打包

```bash
# 只打引擎（装集群用）
scripts/pack-tekton-offline.sh

# 引擎 + 平台任务镜像
scripts/pack-tekton-offline.sh --with-jobs

# 再加上工具链镜像（体积大）
scripts/pack-tekton-offline.sh --with-jobs --with-toolchain
```

产物在 `data/tekton-offline/`（已 gitignore）：

- `pipeline-images.tar`
- `job-images.tar`（可选）
- `toolchain-images.tar`（可选）

把 `deploy/tekton/` 与 `data/tekton-offline/*.tar` 拷到离线环境。

## 离线：导入镜像 + 安装

**本仓库 compose / k3s：**

```bash
# tar 放到 data/tekton-offline/ 后
scripts/start-pipeline-cluster.sh
```

脚本会先 `k3s ctr images import`，再 `kubectl apply` 本地 yaml，不再下载。

**已有 Kubernetes 节点（containerd）：**

```bash
# 每个节点
sudo ctr -n k8s.io images import pipeline-images.tar
# 或
sudo nerdctl --namespace k8s.io load -i pipeline-images.tar

kubectl apply -f deploy/tekton/pipeline/v1.15.1/release.yaml
kubectl wait --for=condition=Available -n tekton-pipelines --timeout=180s \
  deploy/tekton-pipelines-controller deploy/tekton-pipelines-webhook
kubectl create namespace hfwas-pipeline --dry-run=client -o yaml | kubectl apply -f -
```

有私有仓库时：在有网机 `docker pull` 后 `docker tag` + `docker push` 到 Harbor，再改 `release.yaml` 里的 `ghcr.io/tektoncd/pipeline/...` 前缀。不要跟 `latest` 浮动 tag。

## 两层离线

1. **引擎离线**：没有 `pipeline-images.tar`，controller/webhook 会 ImagePullBackOff，集群装不上。
2. **作业离线**：引擎起来了，跑 CLONE/LINT/IMAGE 等仍会拉任务镜像。需要 `--with-jobs` / `--with-toolchain`，或集群能访问对应 Registry。
