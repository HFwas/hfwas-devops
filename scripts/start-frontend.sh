#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

FORCE=false
INSTALL=false

usage() {
  cat <<EOF
用法: $(basename "$0") [选项]

  启动前端 Vite 开发服务器（端口 $FRONTEND_PORT，API 代理至 $BACKEND_PORT）

选项:
  --install  启动前执行 npm install
  --force    若端口被占用，先结束占用进程
  -h, --help 显示帮助
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --install) INSTALL=true ;;
    --force) FORCE=true ;;
    -h | --help)
      usage
      exit 0
      ;;
    *)
      die "未知参数: $1（使用 -h 查看帮助）"
      ;;
  esac
  shift
done

require_cmd npm

if [ -n "$(port_pids "$FRONTEND_PORT")" ]; then
  if [ "$FORCE" = true ]; then
    log "释放端口 $FRONTEND_PORT ..."
    # shellcheck disable=SC2046
    kill $(port_pids "$FRONTEND_PORT") 2>/dev/null || true
    sleep 1
  else
    die "端口 $FRONTEND_PORT 已被占用。使用 --force 强制重启，或先运行 scripts/stop-dev.sh"
  fi
fi

cd "$ROOT_DIR/frontend"

if [ "$INSTALL" = true ] || [ ! -d node_modules ]; then
  log "安装前端依赖 ..."
  npm install
fi

log "启动前端 (http://localhost:$FRONTEND_PORT) ..."
exec npm run dev
