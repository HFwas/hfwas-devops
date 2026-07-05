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

for port in "$BACKEND_PORT" "$FRONTEND_PORT"; do
  pids="$(port_pids "$port")"
  if [ -n "$pids" ]; then
    log "释放端口 $port ..."
    # shellcheck disable=SC2046
    kill $pids 2>/dev/null || true
  fi
done

log "开发服务已停止"
