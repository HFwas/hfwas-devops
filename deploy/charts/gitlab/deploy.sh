#!/usr/bin/env bash
# ============================================================
# 单机最小 GitLab CE（Omnibus）部署
# ============================================================
# 用法:
#   ./deploy.sh pull      # 拉取镜像
#   ./deploy.sh deploy    # Helm 安装（默认 Docker Desktop values）
#   ./deploy.sh status    # 查看状态
#   ./deploy.sh uninstall # 卸载（保留 PVC）
#   ./deploy.sh all       # pull + deploy
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
NAMESPACE="${NAMESPACE:-gitlab}"
RELEASE_NAME="${RELEASE_NAME:-gitlab}"
VALUES_FILE="${VALUES_FILE:-$ROOT_DIR/values-desktop.yaml}"
IMAGES_FILE="$ROOT_DIR/images.txt"
TIMEOUT="${TIMEOUT:-20m}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

pull_images() {
  log "=== 拉取镜像 ==="
  require_cmd docker
  while IFS= read -r line || [[ -n "$line" ]]; do
    [[ "$line" =~ ^#.*$ || -z "${line// /}" ]] && continue
    log "拉取: $line"
    docker pull "$line" || die "拉取失败: $line（请确认代理/Clash 已开启）"
  done < "$IMAGES_FILE"
  log "镜像拉取完成"
}

deploy() {
  log "=== 部署 GitLab（最小 Omnibus）==="
  require_cmd helm
  require_cmd kubectl
  [ -f "$VALUES_FILE" ] || die "Values 不存在: $VALUES_FILE"

  kubectl get ns "$NAMESPACE" >/dev/null 2>&1 || kubectl create namespace "$NAMESPACE"

  helm upgrade --install "$RELEASE_NAME" "$ROOT_DIR" \
    --namespace "$NAMESPACE" \
    --values "$ROOT_DIR/values.yaml" \
    --values "$VALUES_FILE" \
    --timeout "$TIMEOUT" \
    --wait=false

  log "已提交安装。GitLab 首次 reconfigure 通常需要 5~15 分钟。"
  log "观察: kubectl -n $NAMESPACE get pods -w"
  log "就绪后访问: http://localhost:30880  (root / values 中 rootPassword)"
}

status() {
  require_cmd kubectl
  kubectl -n "$NAMESPACE" get sts,pods,svc,pvc 2>/dev/null || true
  echo
  kubectl -n "$NAMESPACE" get secret "${RELEASE_NAME}-root" \
    -o jsonpath='{.data.password}' 2>/dev/null | base64 -d 2>/dev/null || true
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
  status) status ;;
  uninstall) uninstall ;;
  all) pull_images; deploy ;;
  *)
    echo "用法: $0 {pull|deploy|status|uninstall|all}"
    exit 1
    ;;
esac
