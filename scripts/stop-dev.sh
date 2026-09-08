#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

stop_pid_file() {
  local file=$1
  local name=$2
  if [ -f "$file" ]; then
    local pid
    pid="$(cat "$file")"
    if kill -0 "$pid" 2>/dev/null; then
      log "停止 $name (pid $pid) ..."
      kill "$pid" 2>/dev/null || true
      sleep 1
      kill -9 "$pid" 2>/dev/null || true
    fi
    rm -f "$file"
  fi
}

stop_pid_file "$RUN_DIR/backend.pid" "后端"
stop_pid_file "$RUN_DIR/frontend.pid" "前端"

# 先停容器，让 Colima/Lima 自己拆端口转发；再杀残留的宿主机 Java/Node。
stop_stack

free_host_port "$BACKEND_PORT"
free_host_port "$FRONTEND_PORT"

log "开发服务已停止"
