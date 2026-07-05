#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BUILD=false
INSTALL=false
FORCE=false

usage() {
  cat <<EOF
用法: $(basename "$0") [选项]

  后台启动后端 + 前台启动前端（开发常用）

选项:
  --build    后端启动前先编译
  --install  前端启动前先 npm install
  --force    端口占用时先结束旧进程
  -h, --help 显示帮助

停止: scripts/stop-dev.sh
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --build) BUILD=true ;;
    --install) INSTALL=true ;;
    --force) FORCE=true ;;
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

BACKEND_ARGS=()
FRONTEND_ARGS=()
[ "$BUILD" = true ] && BACKEND_ARGS+=(--build)
[ "$INSTALL" = true ] && FRONTEND_ARGS+=(--install)
[ "$FORCE" = true ] && BACKEND_ARGS+=(--force) && FRONTEND_ARGS+=(--force)

cleanup() {
  log "停止开发服务 ..."
  "$SCRIPT_DIR/stop-dev.sh" 2>/dev/null || true
}

if [ "$FORCE" = true ]; then
  "$SCRIPT_DIR/stop-dev.sh" 2>/dev/null || true
fi

trap cleanup EXIT INT TERM

# 后台启动后端
log "后台启动后端 ..."
nohup "$SCRIPT_DIR/start-backend.sh" "${BACKEND_ARGS[@]}" >"$RUN_DIR/backend.log" 2>&1 &
echo $! >"$RUN_DIR/backend.pid"
wait_for_port "$BACKEND_PORT" "后端"

log "后端日志: $RUN_DIR/backend.log"
log "API: http://localhost:$BACKEND_PORT"
log "前端: http://localhost:$FRONTEND_PORT （启动中）"
echo ""

# 前台启动前端（Ctrl+C 会触发 cleanup 停止后端）
exec "$SCRIPT_DIR/start-frontend.sh" "${FRONTEND_ARGS[@]}"
