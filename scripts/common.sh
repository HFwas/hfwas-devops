#!/usr/bin/env bash
# Shared helpers for dev scripts.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/logs"
BACKEND_PORT="${BACKEND_PORT:-8089}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
VENV_DIR="$ROOT_DIR/backend/scripts/.venv"

mkdir -p "$RUN_DIR" "$RUN_DIR/dumps"

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

# 设置 Python 虚拟环境并安装文档生成依赖
# 在启动后端前调用，确保 generate_doc.py 的依赖已就绪
setup_python_venv() {
  require_cmd python3

  if [ ! -f "$VENV_DIR/bin/python3" ]; then
    log "创建 Python 虚拟环境 ..."
    python3 -m venv "$VENV_DIR"
  fi

  # 检查依赖是否已安装（以 python-docx 为探针）
  if ! "$VENV_DIR/bin/python3" -c "import docx" 2>/dev/null; then
    log "安装 Python 文档生成依赖 ..."
    "$VENV_DIR/bin/pip3" install -r "$ROOT_DIR/backend/scripts/requirements.txt" -q
  fi

  log "Python 虚拟环境就绪: $VENV_DIR"
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

# Poll HTTP until Spring Boot responds (avoids false-positive when port is briefly held by a dying process).
wait_for_backend() {
  local name=${1:-后端}
  local max=${2:-120}
  local url="http://localhost:$BACKEND_PORT/health/check"
  local i=0
  while [ "$i" -lt "$max" ]; do
    local code
    code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || true)
    case "$code" in
      200|204)
        log "$name 已就绪 (HTTP $code)"
        return 0
        ;;
    esac
    sleep 1
    i=$((i + 1))
  done
  die "$name 启动超时（$url），请查看 $RUN_DIR/backend.log"
}
