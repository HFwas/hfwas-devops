#!/usr/bin/env bash
# ============================================================
# 应用镜像构建 + Helm 升级（backend / frontend）
# ============================================================
# 用法:
#   ./deploy-app.sh backend              # 构建后端并 helm upgrade
#   ./deploy-app.sh frontend             # 构建前端并 helm upgrade
#   ./deploy-app.sh all                  # 后端 + 前端
#   ./deploy-app.sh build [target]       # 只构建（target: backend|frontend|all）
#   ./deploy-app.sh upgrade [target]     # 只 helm upgrade
#   ./deploy-app.sh status               # 查看 devops 命名空间应用状态
#
# 环境变量:
#   TAG            镜像 tag，默认本地时间 YYYYMMDD-HHMM
#   PROFILE        desktop | k3s，默认按 kubectl context / k3s 容器推断
#   NAMESPACE      默认 devops
#   JWT_SECRET     后端 chart，默认 desktop-dev-jwt-secret
#   SKIP_IMPORT    设为 1 时即使 PROFILE=k3s 也不 ctr import
#   K3S_CONTAINER  k3s 容器名；空则 docker ps | grep k3s
#   HEALTH_URL     默认 http://localhost:30889/health/check
# ============================================================
set -euo pipefail

CHARTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$CHARTS_DIR/../.." && pwd)"
NAMESPACE="${NAMESPACE:-devops}"
JWT_SECRET="${JWT_SECRET:-desktop-dev-jwt-secret}"
IMAGE_BACKEND="${IMAGE_BACKEND:-hfwas/devops-backend}"
IMAGE_FRONTEND="${IMAGE_FRONTEND:-hfwas/devops-frontend}"
HEALTH_URL="${HEALTH_URL:-http://localhost:30889/health/check}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-180s}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

usage() {
  sed -n '2,22p' "$0" | sed 's/^# \{0,1\}//'
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

# 访问集群 / 本机 NodePort 不要走代理
clear_cluster_proxy() {
  unset HTTP_PROXY HTTPS_PROXY ALL_PROXY http_proxy https_proxy all_proxy || true
  export NO_PROXY="${NO_PROXY:-*}"
  export no_proxy="$NO_PROXY"
}

find_k3s() {
  if [ -n "${K3S_CONTAINER:-}" ]; then
    printf '%s' "$K3S_CONTAINER"
    return
  fi
  docker ps --format '{{.Names}}' | grep -i k3s | head -n 1
}

detect_profile() {
  if [ -n "${PROFILE:-}" ]; then
    printf '%s' "$PROFILE"
    return
  fi
  local ctx=""
  ctx="$(kubectl config current-context 2>/dev/null || true)"
  case "$ctx" in
    docker-desktop|docker-desktop*)
      printf 'desktop'
      return
      ;;
  esac
  if [ -n "$(find_k3s)" ]; then
    printf 'k3s'
  else
    printf 'desktop'
  fi
}

overlay_file() {
  local chart=$1
  local profile=$2
  local specific="$CHARTS_DIR/$chart/values-${profile}.yaml"
  if [ -f "$specific" ]; then
    printf '%s' "$specific"
    return
  fi
  if [ -f "$CHARTS_DIR/$chart/values-desktop.yaml" ]; then
    printf '%s' "$CHARTS_DIR/$chart/values-desktop.yaml"
    return
  fi
  die "未找到 $chart 的 values overlay（profile=$profile）"
}

default_tag() {
  date '+%Y%m%d-%H%M'
}

build_backend() {
  local tag=$1
  log "=== docker build ${IMAGE_BACKEND}:$tag ==="
  docker build --pull=false \
    -t "${IMAGE_BACKEND}:latest" \
    -t "${IMAGE_BACKEND}:$tag" \
    -f "$REPO_ROOT/backend/Dockerfile" \
    "$REPO_ROOT"
}

build_frontend() {
  local tag=$1
  log "=== docker build ${IMAGE_FRONTEND}:$tag ==="
  docker build --pull=false \
    -t "${IMAGE_FRONTEND}:latest" \
    -t "${IMAGE_FRONTEND}:$tag" \
    "$REPO_ROOT/frontend"
}

import_k3s_images() {
  local tag=$1
  shift
  local ctn
  ctn="$(find_k3s)"
  [ -n "$ctn" ] || die "未找到 k3s 容器（docker ps | grep k3s）。可设置 K3S_CONTAINER="
  log "=== 导入镜像到 k3s containerd ($ctn) ==="
  local refs=()
  local name
  for name in "$@"; do
    refs+=("${name}:$tag" "${name}:latest")
  done
  docker save "${refs[@]}" | docker exec -i "$ctn" ctr -n k8s.io images import -
}

upgrade_backend() {
  local tag=$1
  local profile=$2
  local overlay
  overlay="$(overlay_file backend "$profile")"
  log "=== helm upgrade devops-backend (tag=$tag profile=$profile) ==="
  helm upgrade --install devops-backend "$CHARTS_DIR/backend" \
    --namespace "$NAMESPACE" \
    --create-namespace \
    --values "$CHARTS_DIR/backend/values.yaml" \
    --values "$overlay" \
    --set "config.jwtSecret=${JWT_SECRET}" \
    --set "image.tag=${tag}"
  kubectl -n "$NAMESPACE" rollout status deploy/devops-backend --timeout="$ROLLOUT_TIMEOUT"
}

upgrade_frontend() {
  local tag=$1
  local profile=$2
  local overlay
  overlay="$(overlay_file frontend "$profile")"
  log "=== helm upgrade devops-frontend (tag=$tag profile=$profile) ==="
  helm upgrade --install devops-frontend "$CHARTS_DIR/frontend" \
    --namespace "$NAMESPACE" \
    --create-namespace \
    --values "$CHARTS_DIR/frontend/values.yaml" \
    --values "$overlay" \
    --set "image.tag=${tag}"
  kubectl -n "$NAMESPACE" rollout status deploy/devops-frontend --timeout="$ROLLOUT_TIMEOUT"
}

check_backend_health() {
  local i=0
  local max=30
  local code=""
  while [ "$i" -lt "$max" ]; do
    code="$(curl -s -o /dev/null -w '%{http_code}' "$HEALTH_URL" 2>/dev/null || true)"
    if [ "$code" = "200" ]; then
      log "health: HTTP $code $HEALTH_URL"
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  log "WARN: health 未返回 200（最后 HTTP ${code:-none}）: $HEALTH_URL"
}

status_cmd() {
  require_cmd kubectl
  require_cmd helm
  clear_cluster_proxy
  helm -n "$NAMESPACE" list
  echo
  kubectl -n "$NAMESPACE" get deploy,pods,svc \
    -l 'app.kubernetes.io/instance in (devops-backend,devops-frontend)' \
    -o wide 2>/dev/null || kubectl -n "$NAMESPACE" get deploy,pods,svc
}

normalize_target() {
  case "${1:-}" in
    backend|frontend|all) printf '%s' "$1" ;;
    "") printf 'all' ;;
    *) die "未知目标: $1（backend|frontend|all）" ;;
  esac
}

run_build() {
  local target=$1
  local tag=$2
  require_cmd docker
  case "$target" in
    backend) build_backend "$tag" ;;
    frontend) build_frontend "$tag" ;;
    all) build_backend "$tag"; build_frontend "$tag" ;;
  esac
}

run_import_if_needed() {
  local target=$1
  local tag=$2
  local profile=$3
  if [ "$profile" != "k3s" ] || [ "${SKIP_IMPORT:-0}" = "1" ]; then
    return 0
  fi
  require_cmd docker
  case "$target" in
    backend) import_k3s_images "$tag" "$IMAGE_BACKEND" ;;
    frontend) import_k3s_images "$tag" "$IMAGE_FRONTEND" ;;
    all) import_k3s_images "$tag" "$IMAGE_BACKEND" "$IMAGE_FRONTEND" ;;
  esac
}

run_upgrade() {
  local target=$1
  local tag=$2
  local profile=$3
  require_cmd helm
  require_cmd kubectl
  clear_cluster_proxy
  case "$target" in
    backend)
      upgrade_backend "$tag" "$profile"
      check_backend_health
      ;;
    frontend)
      upgrade_frontend "$tag" "$profile"
      ;;
    all)
      upgrade_backend "$tag" "$profile"
      upgrade_frontend "$tag" "$profile"
      check_backend_health
      ;;
  esac
}

print_summary() {
  local target=$1
  local tag=$2
  local profile=$3
  log "完成: target=$target tag=$tag profile=$profile namespace=$NAMESPACE"
}

CMD="${1:-}"
ARG="${2:-}"

case "$CMD" in
  ""|-h|--help|help)
    usage
    exit 0
    ;;
  status)
    status_cmd
    ;;
  build)
    TARGET="$(normalize_target "$ARG")"
    TAG="${TAG:-$(default_tag)}"
    PROFILE_VALUE="$(detect_profile)"
    log "profile=$PROFILE_VALUE tag=$TAG"
    run_build "$TARGET" "$TAG"
    run_import_if_needed "$TARGET" "$TAG" "$PROFILE_VALUE"
    print_summary "$TARGET" "$TAG" "$PROFILE_VALUE"
    ;;
  upgrade)
    TARGET="$(normalize_target "$ARG")"
    TAG="${TAG:-latest}"
    PROFILE_VALUE="$(detect_profile)"
    log "profile=$PROFILE_VALUE tag=$TAG"
    run_upgrade "$TARGET" "$TAG" "$PROFILE_VALUE"
    print_summary "$TARGET" "$TAG" "$PROFILE_VALUE"
    ;;
  backend|frontend|all)
    TARGET="$CMD"
    TAG="${TAG:-$(default_tag)}"
    PROFILE_VALUE="$(detect_profile)"
    log "profile=$PROFILE_VALUE tag=$TAG"
    run_build "$TARGET" "$TAG"
    run_import_if_needed "$TARGET" "$TAG" "$PROFILE_VALUE"
    run_upgrade "$TARGET" "$TAG" "$PROFILE_VALUE"
    print_summary "$TARGET" "$TAG" "$PROFILE_VALUE"
    ;;
  *)
    usage
    die "未知命令: $CMD"
    ;;
esac
