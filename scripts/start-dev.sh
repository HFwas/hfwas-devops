#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BUILD=false
FORCE=false
KONG=true

usage() {
  cat <<EOF
用法: $(basename "$0") [选项]

  用 Docker Compose 启动后端、前端、Kong、Keycloak。
  构建产物写出到 artifacts/，日志写出到 logs/。

选项:
  --build         强制重建镜像（后端 JAR / 前端 dist / SPI）
  --install       同 --build（前端依赖在镜像内安装）
  --force         先停掉旧容器和占用端口的进程
  --kong          同时启动 Kong + Keycloak（默认开启）
  --no-kong       只启动 backend / frontend
  --skip-python   已忽略（Python 在后端镜像内）
  -h, --help      显示帮助

停止: scripts/stop-dev.sh

本地热更新（不走 Compose）:
  scripts/start-backend.sh
  scripts/start-frontend.sh
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --build) BUILD=true ;;
    --install) BUILD=true ;;
    --force) FORCE=true ;;
    --skip-python) ;;
    --kong) KONG=true ;;
    --no-kong) KONG=false ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      die "未知参数: $1"
      ;;
  esac
  shift
done

CLEANED=false
cleanup() {
  if [ "$CLEANED" = true ]; then
    return
  fi
  CLEANED=true
  trap - EXIT INT TERM
  log "停止开发服务 ..."
  "$SCRIPT_DIR/stop-dev.sh" 2>/dev/null || true
}

if [ "$FORCE" = true ]; then
  "$SCRIPT_DIR/stop-dev.sh" 2>/dev/null || true
  sleep 2
fi

trap cleanup EXIT INT TERM

STACK_ARGS=()
[ "$BUILD" = true ] && STACK_ARGS+=(--build)
[ "$KONG" = false ] && STACK_ARGS+=(--no-kong)

start_stack "${STACK_ARGS[@]}"

log "跟随容器日志（Ctrl+C 停止全部服务）..."
if [ "$KONG" = true ]; then
  compose logs -f backend frontend kong keycloak
else
  compose logs -f backend frontend
fi
