#!/usr/bin/env bash
# ============================================================
# 构建本仓库镜像并推到阿里云杭州仓库（命名空间 dkyy）
# ============================================================
# 用法:
#   export ALIYUN_REGISTRY_PASSWORD='你的仓库密码'
#   ./scripts/push-aliyun.sh backend
#   ./scripts/push-aliyun.sh frontend
#   ./scripts/push-aliyun.sh all
#   ./scripts/push-aliyun.sh backend 1.2.0
#
# 推送结果示例:
#   registry.cn-hangzhou.aliyuncs.com/dkyy/devops-backend:1.2.0
#   registry.cn-hangzhou.aliyuncs.com/dkyy/devops-frontend:1.2.0
#
# 环境变量:
#   ALIYUN_REGISTRY          默认 registry.cn-hangzhou.aliyuncs.com
#   ALIYUN_REGISTRY_USERNAME 默认 dingtalk_eadbpj
#   ALIYUN_REGISTRY_PASSWORD 仓库密码。未设置时在终端询问，不回显、不写入仓库
#   ALIYUN_NAMESPACE         默认 dkyy
#   ALIYUN_IMAGE_BACKEND     远端仓库名，默认 devops-backend
#   ALIYUN_IMAGE_FRONTEND    远端仓库名，默认 devops-frontend
#   TAG                      镜像版本，默认 YYYYMMDD-HHMM；也可作为第 2 个参数
#   SKIP_LATEST              设为 1 时不额外推 :latest
#
# 密码不要写进本脚本，也不要提交到 git。
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

REGISTRY="${ALIYUN_REGISTRY:-registry.cn-hangzhou.aliyuncs.com}"
USERNAME="${ALIYUN_REGISTRY_USERNAME:-dingtalk_eadbpj}"
NAMESPACE="${ALIYUN_NAMESPACE:-dkyy}"
IMAGE_BACKEND="${ALIYUN_IMAGE_BACKEND:-devops-backend}"
IMAGE_FRONTEND="${ALIYUN_IMAGE_FRONTEND:-devops-frontend}"
SKIP_LATEST="${SKIP_LATEST:-0}"
export DOCKER_BUILDKIT="${DOCKER_BUILDKIT:-1}"

log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die() { log "ERROR: $*"; exit 1; }

usage() {
  sed -n '2,27p' "$0" | sed 's/^# \{0,1\}//'
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

default_tag() {
  date '+%Y%m%d-%H%M'
}

remote_ref() {
  local name=$1
  local tag=$2
  printf '%s/%s/%s:%s' "$REGISTRY" "$NAMESPACE" "$name" "$tag"
}

read_password() {
  if [ -n "${ALIYUN_REGISTRY_PASSWORD:-}" ]; then
    printf '%s' "$ALIYUN_REGISTRY_PASSWORD"
    return
  fi
  if [ ! -t 0 ]; then
    die "未设置 ALIYUN_REGISTRY_PASSWORD，且当前不是交互终端"
  fi
  local password
  printf 'Password for %s: ' "$REGISTRY" >&2
  read -rs password
  printf '\n' >&2
  [ -n "$password" ] || die "密码为空"
  printf '%s' "$password"
}

login_registry() {
  local password
  password="$(read_password)"
  log "=== docker login ${REGISTRY} (${USERNAME}) ==="
  printf '%s' "$password" | docker login --username "$USERNAME" --password-stdin "$REGISTRY"
  unset password
}

build_backend() {
  local tag=$1
  local ref latest
  ref="$(remote_ref "$IMAGE_BACKEND" "$tag")"
  log "=== docker build ${ref} ==="
  docker build --pull=false \
    -t "$ref" \
    -f "$ROOT_DIR/deploy/docker/backend/Dockerfile" \
    "$ROOT_DIR"
  if [ "$SKIP_LATEST" != "1" ]; then
    latest="$(remote_ref "$IMAGE_BACKEND" latest)"
    docker tag "$ref" "$latest"
  fi
}

build_frontend() {
  local tag=$1
  local ref latest
  ref="$(remote_ref "$IMAGE_FRONTEND" "$tag")"
  log "=== docker build ${ref} ==="
  docker build --pull=false \
    -t "$ref" \
    -f "$ROOT_DIR/deploy/docker/frontend/Dockerfile" \
    "$ROOT_DIR/frontend"
  if [ "$SKIP_LATEST" != "1" ]; then
    latest="$(remote_ref "$IMAGE_FRONTEND" latest)"
    docker tag "$ref" "$latest"
  fi
}

push_one() {
  local name=$1
  local tag=$2
  local ref
  ref="$(remote_ref "$name" "$tag")"
  log "=== docker push ${ref} ==="
  docker push "$ref"
  if [ "$SKIP_LATEST" != "1" ]; then
    log "=== docker push $(remote_ref "$name" latest) ==="
    docker push "$(remote_ref "$name" latest)"
  fi
}

main() {
  local target=${1:-}
  local tag=${2:-${TAG:-}}
  case "$target" in
    -h|--help|help|"")
      usage
      [ -n "$target" ] || exit 1
      exit 0
      ;;
    backend|frontend|all) ;;
    *)
      die "未知目标: $target（可用 backend、frontend、all）"
      ;;
  esac
  [ -n "$tag" ] || tag="$(default_tag)"

  require_cmd docker
  login_registry

  case "$target" in
    backend)
      build_backend "$tag"
      push_one "$IMAGE_BACKEND" "$tag"
      ;;
    frontend)
      build_frontend "$tag"
      push_one "$IMAGE_FRONTEND" "$tag"
      ;;
    all)
      build_backend "$tag"
      build_frontend "$tag"
      push_one "$IMAGE_BACKEND" "$tag"
      push_one "$IMAGE_FRONTEND" "$tag"
      ;;
  esac

  log "完成。拉取示例: docker pull $(remote_ref "$IMAGE_BACKEND" "$tag")"
}

main "$@"
