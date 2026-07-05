#!/usr/bin/env bash
# Shared helpers for dev scripts.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/.run"
BACKEND_PORT="${BACKEND_PORT:-8089}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

mkdir -p "$RUN_DIR"

log() {
  printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"
}

die() {
  log "ERROR: $*"
  exit 1
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

port_pids() {
  lsof -ti:"$1" 2>/dev/null || true
}

wait_for_port() {
  local port=$1
  local name=$2
  local max=${3:-90}
  local i=0
  while [ "$i" -lt "$max" ]; do
    if [ -n "$(port_pids "$port")" ]; then
      log "$name 已监听端口 $port"
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  die "$name 启动超时（端口 $port）"
}
