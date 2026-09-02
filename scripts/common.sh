#!/usr/bin/env bash
# Shared helpers for dev scripts.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/logs"
BACKEND_PORT="${BACKEND_PORT:-8089}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"
VENV_DIR="${PYTHON_VENV:-$ROOT_DIR/backend/scripts/.venv}"
OCR_WORKER="$ROOT_DIR/backend/file-parser/src/main/resources/ocr/ocr_worker.py"
OCR_MODEL_ROOT="$ROOT_DIR/backend/file-parser/src/main/resources/ocr/models"
OCR_REQUIREMENTS="$ROOT_DIR/backend/scripts/requirements-ocr.txt"
DOCGEN_REQUIREMENTS="$ROOT_DIR/backend/scripts/requirements.txt"

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

# 准备文档生成 + PP-OCRv6 worker 共用的虚拟环境，并导出 Java 侧用到的路径。
ensure_python_env() {
  require_cmd python3
  if ! python3 -c "import venv" 2>/dev/null; then
    die "python3 缺少 venv 模块，无法创建 $VENV_DIR"
  fi
  if [ ! -x "$VENV_DIR/bin/python" ] && [ ! -x "$VENV_DIR/bin/python3" ]; then
    log "创建 Python 虚拟环境: $VENV_DIR"
    python3 -m venv "$VENV_DIR"
  fi
  local py
  if [ -x "$VENV_DIR/bin/python" ]; then
    py="$VENV_DIR/bin/python"
  else
    py="$VENV_DIR/bin/python3"
  fi
  local stamp="$VENV_DIR/.deps.stamp"
  local need_install=false
  if [ ! -f "$stamp" ]; then
    need_install=true
  elif [ -f "$DOCGEN_REQUIREMENTS" ] && [ "$DOCGEN_REQUIREMENTS" -nt "$stamp" ]; then
    need_install=true
  elif [ -f "$OCR_REQUIREMENTS" ] && [ "$OCR_REQUIREMENTS" -nt "$stamp" ]; then
    need_install=true
  elif ! "$py" -c "import docx" 2>/dev/null; then
    need_install=true
  fi
  if [ "$need_install" = true ]; then
    log "安装 Python 依赖（文档生成 + OCR，首次可能较慢）..."
    "$py" -m pip install -q -U pip
    [ -f "$DOCGEN_REQUIREMENTS" ] && "$py" -m pip install -q -r "$DOCGEN_REQUIREMENTS"
    [ -f "$OCR_REQUIREMENTS" ] && "$py" -m pip install -q -r "$OCR_REQUIREMENTS"
    date >"$stamp"
  fi
  export PATH="$VENV_DIR/bin:$PATH"
  export FILE_PARSER_OCR_PYTHON="$py"
  export DOCGEN_PYTHON_PATH="$py"
  if [ -f "$OCR_WORKER" ]; then
    export FILE_PARSER_OCR_PYTHON_WORKER="$OCR_WORKER"
  fi
  ensure_ocr_models "$py"
  log "Python 虚拟环境就绪: $VENV_DIR"
}

# 将 PP-OCRv6 ONNX 落到 file-parser resources/ocr/models，随代码打包，启动只读本地文件。
ensure_ocr_models() {
  local py="${1:-$VENV_DIR/bin/python}"
  local script="$ROOT_DIR/backend/scripts/prepare_ocr_models.py"
  local root="${FILE_PARSER_OCR_MODEL_ROOT:-$OCR_MODEL_ROOT}"
  export FILE_PARSER_OCR_MODEL_ROOT="$root"
  export FILE_PARSER_OCR_DET_MODEL_DIR="${FILE_PARSER_OCR_DET_MODEL_DIR:-$root/PP-OCRv6_medium_det_onnx}"
  export FILE_PARSER_OCR_REC_MODEL_DIR="${FILE_PARSER_OCR_REC_MODEL_DIR:-$root/PP-OCRv6_medium_rec_onnx}"
  if [ ! -f "$script" ]; then
    log "未找到 $script，跳过 OCR 模型准备"
    return 0
  fi
  if "$py" "$script" --check >/dev/null 2>&1; then
    log "OCR 模型: $FILE_PARSER_OCR_DET_MODEL_DIR"
    return 0
  fi
  log "准备 PP-OCRv6 模型到 resources（首次需联网）..."
  if ! "$py" "$script" >/dev/null; then
    die "OCR 模型准备失败，请检查网络或手动运行: $py $script"
  fi
  log "OCR 模型: $FILE_PARSER_OCR_DET_MODEL_DIR"
}
