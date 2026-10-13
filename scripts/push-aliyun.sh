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
#   ALIYUN_REGISTRY_PASSWORD 仓库密码。未设置时在终端询问（输入不回显），不写入仓库
#   ALIYUN_NAMESPACE         默认 dkyy
#   ALIYUN_IMAGE_BACKEND     远端仓库名，默认 devops-backend
#   ALIYUN_IMAGE_FRONTEND    远端仓库名，默认 devops-frontend
#   TAG                      镜像版本，默认 YYYYMMDD-HHMM；也可作为第 2 个参数
#   SKIP_LATEST              设为 1 时不额外推 :latest
#   PLATFORM                 目标平台，默认 linux/amd64（云主机常见 x86）
#                            Apple Silicon 本机调试可设 PLATFORM=linux/arm64
#   DOCKER_BUILDKIT          交叉架构时强制 1；同架构无 buildx 时可回退 0
#
# 密码不要写进本脚本，也不要提交到 git。
# DOCKER_BUILDKIT / PLATFORM 请写成环境变量前缀，不要当第 2 个参数：
#   PLATFORM=linux/arm64 ./scripts/push-aliyun.sh all
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

REGISTRY="${ALIYUN_REGISTRY:-registry.cn-hangzhou.aliyuncs.com}"
USERNAME="${ALIYUN_REGISTRY_USERNAME:-dingtalk_eadbpj}"
NAMESPACE="${ALIYUN_NAMESPACE:-dkyy}"
IMAGE_BACKEND="${ALIYUN_IMAGE_BACKEND:-devops-backend}"
IMAGE_FRONTEND="${ALIYUN_IMAGE_FRONTEND:-devops-frontend}"
SKIP_LATEST="${SKIP_LATEST:-0}"
PLATFORM="${PLATFORM:-linux/amd64}"

# 日志走 stderr，避免被 $(...) 吞掉
log() { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*" >&2; }
die() { log "ERROR: $*"; exit 1; }

usage() {
  sed -n '2,35p' "$0" | sed 's/^# \{0,1\}//'
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

platform_arch() {
  # linux/amd64 -> amd64；linux/arm64/v8 -> arm64
  local p=$1
  local arch=${p#*/}
  arch=${arch%%/*}
  case "$arch" in
    amd64|x86_64) printf 'amd64' ;;
    arm64|aarch64) printf 'arm64' ;;
    *) printf '%s' "$arch" ;;
  esac
}

daemon_arch() {
  local a
  a="$(docker info --format '{{.Architecture}}' 2>/dev/null || true)"
  case "$a" in
    x86_64|amd64) printf 'amd64' ;;
    aarch64|arm64) printf 'arm64' ;;
    *) printf '%s' "${a:-unknown}" ;;
  esac
}

has_buildx() {
  docker buildx version >/dev/null 2>&1
}

# 交叉架构（如 ARM Mac -> linux/amd64）必须 buildx；同架构可回退 legacy。
configure_docker_builder() {
  local want host
  want="$(platform_arch "$PLATFORM")"
  host="$(daemon_arch)"
  CROSS=0
  if [ "$want" != "$host" ]; then
    CROSS=1
  fi

  if [ "$CROSS" = "1" ]; then
    if ! has_buildx; then
      die "目标 PLATFORM=${PLATFORM}，本机 Docker 是 ${host}，交叉构建需要 docker buildx。安装: brew install docker-buildx && mkdir -p ~/.docker/cli-plugins && ln -sfn \"\$(brew --prefix)/opt/docker-buildx/bin/docker-buildx\" ~/.docker/cli-plugins/docker-buildx"
    fi
    export DOCKER_BUILDKIT=1
    ensure_buildx_builder
    log "交叉构建: daemon=${host} → PLATFORM=${PLATFORM}（buildx）"
    return
  fi

  if [ -n "${DOCKER_BUILDKIT+x}" ]; then
    if [ "$DOCKER_BUILDKIT" = "1" ] && ! has_buildx; then
      die "DOCKER_BUILDKIT=1 但本机没有可用的 docker buildx。请安装 buildx，或改用 DOCKER_BUILDKIT=0"
    fi
    export DOCKER_BUILDKIT
    log "同架构构建 PLATFORM=${PLATFORM}，DOCKER_BUILDKIT=${DOCKER_BUILDKIT}"
    return
  fi

  if has_buildx; then
    export DOCKER_BUILDKIT=1
    log "同架构构建 PLATFORM=${PLATFORM}（BuildKit）"
  else
    export DOCKER_BUILDKIT=0
    log "同架构构建 PLATFORM=${PLATFORM}；无 buildx，回退 legacy（DOCKER_BUILDKIT=0）"
  fi
}

ensure_buildx_builder() {
  local name want_arch platforms
  want_arch="$(platform_arch "$PLATFORM")"
  # Colima 自带 builder 常已宣告 linux/amd64 + linux/arm64，优先复用，少拉外网镜像
  if [ -n "${BUILDX_BUILDER:-}" ]; then
    name="$BUILDX_BUILDER"
    docker buildx use "$name" >/dev/null
  else
    name="$(docker buildx ls 2>/dev/null | awk '/\*/{print $1; exit}' | tr -d '*')"
    [ -n "$name" ] || name="default"
  fi
  platforms="$(docker buildx inspect "$name" 2>/dev/null | awk -F': ' '/Platforms:/{print $2; exit}' || true)"
  # Colima 常写成 "linux/amd64 (+2), linux/arm64, ..."
  if echo "$platforms" | grep -Fq "linux/${want_arch}"; then
    log "使用 buildx builder=${name}（已支持 ${PLATFORM}）"
    return
  fi
  name="${BUILDX_BUILDER:-hfwas-multiarch}"
  if ! docker buildx inspect "$name" >/dev/null 2>&1; then
    log "=== docker buildx create --name ${name} --driver docker-container --use ==="
    docker buildx create --name "$name" --driver docker-container --use >/dev/null
  else
    docker buildx use "$name" >/dev/null
  fi
  docker buildx inspect --bootstrap >/dev/null
  log "使用 buildx builder=${name}"
}

# 写入全局 REGISTRY_PASSWORD，避免 $(read) 吞掉提示/错误
ask_password() {
  if [ -n "${ALIYUN_REGISTRY_PASSWORD:-}" ]; then
    REGISTRY_PASSWORD="$ALIYUN_REGISTRY_PASSWORD"
    return
  fi
  if [ ! -t 0 ] && [ ! -r /dev/tty ]; then
    die "未设置 ALIYUN_REGISTRY_PASSWORD，且当前不是交互终端。请先: export ALIYUN_REGISTRY_PASSWORD='你的密码'"
  fi
  log "请输入 ${REGISTRY} 的仓库密码（用户 ${USERNAME}；输入不回显，输完按回车）"
  printf 'Password: ' >&2
  if [ -r /dev/tty ]; then
    read -rs REGISTRY_PASSWORD </dev/tty
  else
    read -rs REGISTRY_PASSWORD
  fi
  printf '\n' >&2
  [ -n "${REGISTRY_PASSWORD:-}" ] || die "密码为空。也可先 export ALIYUN_REGISTRY_PASSWORD='你的密码' 再运行"
}

login_registry() {
  ask_password
  log "=== docker login ${REGISTRY} (${USERNAME}) ==="
  printf '%s' "$REGISTRY_PASSWORD" | docker login --username "$USERNAME" --password-stdin "$REGISTRY"
  unset REGISTRY_PASSWORD
}

docker_build() {
  local ref=$1
  local dockerfile=$2
  local context=$3

  if [ "${CROSS:-0}" = "1" ] || { has_buildx && [ "${DOCKER_BUILDKIT:-0}" = "1" ]; }; then
    log "=== docker buildx build --platform ${PLATFORM} ${ref} ==="
    docker buildx build \
      --platform "$PLATFORM" \
      --pull=false \
      --load \
      -t "$ref" \
      -f "$dockerfile" \
      "$context"
  else
    log "=== docker build --platform ${PLATFORM} ${ref} ==="
    docker build \
      --platform "$PLATFORM" \
      --pull=false \
      -t "$ref" \
      -f "$dockerfile" \
      "$context"
  fi
}

build_backend() {
  local tag=$1
  local ref latest
  ref="$(remote_ref "$IMAGE_BACKEND" "$tag")"
  docker_build "$ref" "$ROOT_DIR/deploy/docker/backend/Dockerfile" "$ROOT_DIR"
  if [ "$SKIP_LATEST" != "1" ]; then
    latest="$(remote_ref "$IMAGE_BACKEND" latest)"
    docker tag "$ref" "$latest"
  fi
  log "镜像架构: $(docker image inspect "$ref" --format '{{.Os}}/{{.Architecture}}')"
}

build_frontend() {
  local tag=$1
  local ref latest
  ref="$(remote_ref "$IMAGE_FRONTEND" "$tag")"
  docker_build "$ref" "$ROOT_DIR/deploy/docker/frontend/Dockerfile" "$ROOT_DIR/frontend"
  if [ "$SKIP_LATEST" != "1" ]; then
    latest="$(remote_ref "$IMAGE_FRONTEND" latest)"
    docker tag "$ref" "$latest"
  fi
  log "镜像架构: $(docker image inspect "$ref" --format '{{.Os}}/{{.Architecture}}')"
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
  if [[ "$tag" == *=* ]]; then
    die "第 2 个参数应是镜像版本（如 1.2.0），不是环境变量。请写成: PLATFORM=linux/amd64 ./scripts/push-aliyun.sh all"
  fi
  [ -n "$tag" ] || tag="$(default_tag)"

  require_cmd docker
  configure_docker_builder
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

  log "完成。PLATFORM=${PLATFORM} 拉取示例: docker pull $(remote_ref "$IMAGE_BACKEND" "$tag")"
}

main "$@"
