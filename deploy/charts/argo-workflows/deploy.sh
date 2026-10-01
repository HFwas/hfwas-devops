#!/usr/bin/env bash
# ============================================================
# Argo Workflows v4.1.4（官方 Helm chart 2.0.8）部署
# ============================================================
# 用法:
#   ./deploy.sh pull        # 拉取镜像
#   ./deploy.sh deploy      # Helm 安装（默认 Docker Desktop values）
#   ./deploy.sh import-k3s  # 把已拉取镜像导入 k3s containerd（k8s.io）
#   ./deploy.sh status      # 查看状态
#   ./deploy.sh uninstall   # 卸载
#   ./deploy.sh all         # pull + deploy
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="${NAMESPACE:-argo}"
RELEASE_NAME="${RELEASE_NAME:-argo-workflows}"
VALUES_FILE="${VALUES_FILE:-$ROOT_DIR/values-desktop.yaml}"
IMAGES_FILE="$ROOT_DIR/images.txt"
TIMEOUT="${TIMEOUT:-10m}"
CHART_VERSION="${CHART_VERSION:-2.0.8}"
HTTP_PROXY="${HTTP_PROXY:-http://127.0.0.1:7890}"
HTTPS_PROXY="${HTTPS_PROXY:-http://127.0.0.1:7890}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

find_k3s() {
  if [ -n "${K3S_CONTAINER:-}" ]; then
    printf '%s' "$K3S_CONTAINER"
    return
  fi
  docker ps --format '{{.Names}}' | grep -i k3s | head -n 1
}

pull_images() {
  log "=== 拉取镜像 ==="
  require_cmd docker
  export HTTP_PROXY HTTPS_PROXY http_proxy="$HTTP_PROXY" https_proxy="$HTTPS_PROXY"
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^#.*$ || -z "${line// /}" ]] && continue
    log "拉取: $line"
    docker pull "$line" || die "拉取失败: $line（请确认代理/Clash 已开启）"
  done < "$IMAGES_FILE"
  log "镜像拉取完成"
}

import_k3s() {
  log "=== 导入镜像到 k3s containerd (k8s.io) ==="
  require_cmd docker
  local ctn
  ctn="$(find_k3s)"
  [ -n "$ctn" ] || die "未找到 k3s 容器（docker ps | grep k3s）。可设置 K3S_CONTAINER="
  log "k3s 容器: $ctn"
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^#.*$ || -z "${line// /}" ]] && continue
    log "导入: $line"
    docker save "$line" | docker exec -i "$ctn" ctr -n k8s.io images import - \
      || die "导入失败: $line"
  done < "$IMAGES_FILE"
  log "导入完成"
}

ensure_chart() {
  require_cmd helm
  log "=== 添加/更新 Argo Helm 仓库 ==="
  export HTTP_PROXY HTTPS_PROXY http_proxy="$HTTP_PROXY" https_proxy="$HTTPS_PROXY"
  helm repo add argo https://argoproj.github.io/argo-helm >/dev/null 2>&1 || true
  helm repo update argo >/dev/null
}

deploy() {
  log "=== 部署 Argo Workflows v4.1.4（chart $CHART_VERSION）==="
  require_cmd helm
  require_cmd kubectl
  [ -f "$VALUES_FILE" ] || die "Values 不存在: $VALUES_FILE"

  ensure_chart

  kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

  # 安装/升级
  helm upgrade --install "$RELEASE_NAME" argo/argo-workflows \
    --version "$CHART_VERSION" \
    --namespace "$NAMESPACE" \
    --values "$VALUES_FILE" \
    --timeout "$TIMEOUT" \
    --wait=false

  log "已提交安装。Argo Workflows 组件启动需要等待镜像拉取。"
  log "观察: kubectl -n $NAMESPACE get pods -w"
  log ""
  log "就绪后访问 Web UI: http://localhost:32746  "
  log ""
  log "CLI 安装:"
  log "  # macOS"
  log "  curl -sLO https://github.com/argoproj/argo-workflows/releases/download/v4.1.4/argo-darwin-amd64.gz"
  log "  gunzip argo-darwin-amd64.gz && chmod +x argo-darwin-amd64 && sudo mv argo-darwin-amd64 /usr/local/bin/argo"
  log "  # Linux"
  log "  curl -sLO https://github.com/argoproj/argo-workflows/releases/download/v4.1.4/argo-linux-amd64.gz"
  log ""
  log "首次工作流示例:"
  log "  # 获取 auth token"
  log "  argo auth token"
  log ""
  log "  # 提交 hello-world"
  log "  kubectl -n $NAMESPACE apply -f - <<'EOF'"
  log "  apiVersion: argoproj.io/v1alpha1"
  log "  kind: Workflow"
  log "  metadata:"
  log "    generateName: hello-world-"
  log "  spec:"
  log "    entrypoint: whalesay"
  log "    templates:"
  log "      - name: whalesay"
  log "        container:"
  log "          image: docker/whalesay:latest"
  log "          command: [cowsay]"
  log "          args: [\"hello world\"]"
  log "  EOF"
  log "  argo -n $NAMESPACE list"
}

status() {
  require_cmd kubectl
  kubectl -n "$NAMESPACE" get pods,svc 2>/dev/null || true
  echo
  kubectl -n "$NAMESPACE" get crd 2>/dev/null | grep -i argoproj || true
  echo
  kubectl -n "$NAMESPACE" rollout status deploy/workflow-controller --timeout=10s 2>/dev/null || true
}

uninstall() {
  require_cmd helm
  log "卸载 release"
  helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" || true
  log ""
  log "Release 已卸载。CRD 手动清理:"
  log "  kubectl get crd | grep argoproj.io | awk '{print \\$1}' | xargs kubectl delete crd"
  log "如需删除 namespace:"
  log "  kubectl delete ns $NAMESPACE"
}

case "${1:-}" in
  pull) pull_images ;;
  deploy) deploy ;;
  import-k3s) import_k3s ;;
  status) status ;;
  uninstall) uninstall ;;
  all) pull_images; deploy ;;
  *)
    echo "用法: $0 {pull|deploy|import-k3s|status|uninstall|all}"
    exit 1
    ;;
esac