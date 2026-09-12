#!/usr/bin/env bash
# ============================================================
# Harbor v2.14.4（官方 Helm chart 1.18.4）部署
# ============================================================
# 用法:
#   ./deploy.sh pull        # 拉取镜像
#   ./deploy.sh deploy      # Helm 安装（默认 Docker Desktop values）
#   ./deploy.sh import-k3s  # 把已拉取镜像导入 k3s containerd（k8s.io）
#   ./deploy.sh status      # 查看状态
#   ./deploy.sh uninstall   # 卸载（保留 PVC）
#   ./deploy.sh all         # pull + deploy
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="${NAMESPACE:-harbor}"
RELEASE_NAME="${RELEASE_NAME:-harbor}"
VALUES_FILE="${VALUES_FILE:-$ROOT_DIR/values-desktop.yaml}"
CHART_TGZ="${CHART_TGZ:-$ROOT_DIR/charts/harbor-1.18.4.tgz}"
CHART_VERSION="${CHART_VERSION:-1.18.4}"
IMAGES_FILE="$ROOT_DIR/images.txt"
TIMEOUT="${TIMEOUT:-15m}"
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

ensure_chart() {
  if [ -f "$CHART_TGZ" ]; then
    return
  fi
  require_cmd helm
  mkdir -p "$(dirname "$CHART_TGZ")"
  log "未找到 $CHART_TGZ，从官方仓库拉取 chart ${CHART_VERSION}"
  export HTTP_PROXY HTTPS_PROXY http_proxy="$HTTP_PROXY" https_proxy="$HTTPS_PROXY"
  helm repo add harbor https://helm.goharbor.io >/dev/null 2>&1 || helm repo add harbor https://helm.goharbor.io
  helm repo update harbor >/dev/null
  helm pull harbor/harbor --version "$CHART_VERSION" --destination "$(dirname "$CHART_TGZ")"
  [ -f "$CHART_TGZ" ] || die "helm pull 后仍没有 $CHART_TGZ"
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

deploy() {
  log "=== 部署 Harbor v2.14.4 ==="
  require_cmd helm
  require_cmd kubectl
  [ -f "$VALUES_FILE" ] || die "Values 不存在: $VALUES_FILE"
  ensure_chart

  kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

  helm upgrade --install "$RELEASE_NAME" "$CHART_TGZ" \
    --namespace "$NAMESPACE" \
    --values "$ROOT_DIR/values.yaml" \
    --values "$VALUES_FILE" \
    --timeout "$TIMEOUT" \
    --wait=false

  log "已提交安装。首次拉镜像 + 启动通常需要几分钟。"
  log "观察: kubectl -n $NAMESPACE get pods -w"
  log "就绪后访问: http://localhost:30002  (admin / values.yaml 中 harborAdminPassword)"
}

status() {
  require_cmd kubectl
  kubectl -n "$NAMESPACE" get pods,svc,pvc 2>/dev/null || true
  echo
  kubectl -n "$NAMESPACE" get secret "$RELEASE_NAME" \
    -o jsonpath='{.data.HARBOR_ADMIN_PASSWORD}' 2>/dev/null | base64 -d 2>/dev/null || true
  echo
}

uninstall() {
  require_cmd helm
  log "卸载 release（PVC 默认保留）"
  helm uninstall "$RELEASE_NAME" -n "$NAMESPACE" || true
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
