#!/usr/bin/env bash
# Shared helpers for dev scripts.

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
RUN_DIR="$ROOT_DIR/logs"
BACKEND_PORT="${BACKEND_PORT:-8089}"
FRONTEND_PORT="${FRONTEND_PORT:-5173}"

# Kong 网关（与 backend/frontend 同属 docker-compose.yml）
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
KONG_PORT="${KONG_PORT:-8000}"
KONG_ADMIN_PORT="${KONG_ADMIN_PORT:-8001}"

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

# 宿主机 git 能 clone GitHub，执行集群 Pod 默认不能（直连 443 超时）。
# 把 git http.proxy 传给 Compose backend；127.0.0.1 由 Java 改写成 Pod 可达地址。
export_pipeline_git_http_proxy() {
  if [ -z "${PIPELINE_GIT_HTTP_PROXY:-}" ]; then
    local p=""
    p=$(git config --global --get https.proxy 2>/dev/null || true)
    if [ -z "$p" ]; then
      p=$(git config --global --get http.proxy 2>/dev/null || true)
    fi
    if [ -n "$p" ]; then
      export PIPELINE_GIT_HTTP_PROXY="$p"
    fi
  else
    export PIPELINE_GIT_HTTP_PROXY
  fi
  if [ -n "${PIPELINE_GIT_HTTP_PROXY:-}" ]; then
    log "流水线 git 代理: $PIPELINE_GIT_HTTP_PROXY"
  fi
}

require_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1"
}

port_pids() {
  lsof -ti:"$1" 2>/dev/null || true
}

# 宿主机热更新进程（start-backend.sh / start-frontend.sh）。
# Colima/Lima 用 SSH/gvproxy 转发 compose 端口；误杀会拆掉 VM，docker.sock 立刻失效。
is_host_dev_process() {
  local pid=$1
  local blob
  blob=$(ps -p "$pid" -o comm= -o args= 2>/dev/null | tr '[:upper:]' '[:lower:]')
  [ -n "$blob" ] || return 1
  case "$blob" in
    *lima*|*colima*|*docker*|*qemu*|*gvproxy*|*vpnkit*|*vmnet*|*krunkit*|*vfkit*)
      return 1
      ;;
    *java*|*node*|*vite*|*python*)
      return 0
      ;;
  esac
  return 1
}

# 释放端口上的宿主机 Java/Node，不动 Docker/Colima 端口转发。
free_host_port() {
  local port=$1
  local pid comm
  local pids
  pids="$(port_pids "$port")"
  [ -n "$pids" ] || return 0
  for pid in $pids; do
    if ! is_host_dev_process "$pid"; then
      continue
    fi
    comm=$(ps -p "$pid" -o comm= 2>/dev/null | awk '{print $1}')
    log "释放端口 $port (${comm:-pid} $pid) ..."
    kill "$pid" 2>/dev/null || true
  done
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
  die "${name} 启动超时（端口 ${port}）"
}

# Poll HTTP until Spring Boot responds (avoids false-positive when port is briefly held by a dying process).
# 默认 240s：--build + 首次 pip + PP-OCRv6 冷启动经常超过 120s。
wait_for_backend() {
  local name=${1:-后端}
  local max=${2:-240}
  local url="http://localhost:${BACKEND_PORT}/health/check"
  local i=0
  while [ "$i" -lt "$max" ]; do
    local code=""
    code=$(curl -s -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || true)
    case "$code" in
      200|204)
        log "${name} 已就绪 (HTTP ${code})"
        return 0
        ;;
    esac
    sleep 1
    i=$((i + 1))
  done
  die "${name} 启动超时（${url}），请查看 ${RUN_DIR}/backend.log"
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
    # paddleocr 首次 import 会拉整棵 PaddleX + 可能重建 Matplotlib 字体缓存，可到 40s+。
    # 放到 pip 之后预热，避免阻塞 Spring Boot @PostConstruct。
    log "预热 PaddleOCR import（首次可能较慢）..."
    MPLBACKEND=Agg PADDLE_PDX_DISABLE_MODEL_SOURCE_CHECK=True PADDLE_PDX_EAGER_INIT=0 \
      HF_HUB_OFFLINE=1 TRANSFORMERS_OFFLINE=1 \
      "$py" -c "from paddleocr import PaddleOCR" >/dev/null
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

# ═══════════════════════════════════════════════
# Docker Compose
# ═══════════════════════════════════════════════

require_docker() {
  if ! docker info >/dev/null 2>&1; then
    die "Docker 未运行，请先启动 Colima（colima start）或 Docker Desktop"
  fi
}

ensure_compose_dirs() {
  mkdir -p \
    "$ROOT_DIR/logs/kong" \
    "$ROOT_DIR/logs/keycloak" \
    "$ROOT_DIR/logs/backend" \
    "$ROOT_DIR/logs/frontend" \
    "$ROOT_DIR/logs/dumps" \
    "$ROOT_DIR/keycloak/providers" \
    "$ROOT_DIR/artifacts/backend" \
    "$ROOT_DIR/artifacts/frontend" \
    "$ROOT_DIR/data/pipeline" \
    "$ROOT_DIR/data/tekton-offline"
}

compose() {
  docker compose -f "$COMPOSE_FILE" "$@"
}

print_stack_banner() {
  echo ""
  echo "==========================================="
  echo "  hfwas-devops 已启动"
  echo "==========================================="
  echo ""
  echo "  统一入口:   http://localhost:${KONG_PORT}"
  echo "  前端直连:   http://localhost:80"
  echo "  后端直连:   http://localhost:${BACKEND_PORT}"
  echo "  Admin API:  http://localhost:${KONG_ADMIN_PORT}"
  echo ""
  echo "  Keycloak 管理控制台:"
  echo "  ├─ 经 Kong:  http://localhost:${KONG_PORT}/auth/admin"
  echo "  └─ 直连:     http://localhost:8081/auth/admin"
  echo "     账号: admin / admin"
  echo ""
  echo "  日志:        $ROOT_DIR/logs/{backend,frontend,kong,keycloak}/"
  echo "  后端 JAR:    $ROOT_DIR/artifacts/backend/server.jar"
  echo "  前端 dist:   $ROOT_DIR/artifacts/frontend/"
  echo "  SPI JAR:     $ROOT_DIR/keycloak/providers/hfwas-keycloak-http-listener.jar"
  echo ""
  echo "  流水线执行集群: ./scripts/start-pipeline-cluster.sh"
  echo ""
}

wait_for_kong() {
  local max=${1:-180}
  local i=0
  while [ "$i" -lt "$max" ]; do
    if compose exec -T kong kong health 2>/dev/null | grep -q "healthy"; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

start_stack() {
  require_docker
  if [ ! -f "$COMPOSE_FILE" ]; then
    die "Compose 配置文件不存在: $COMPOSE_FILE"
  fi
  ensure_compose_dirs
  export_pipeline_git_http_proxy

  local extra=()
  local with_gateway=true
  while [ $# -gt 0 ]; do
    case "$1" in
      --build) extra+=(--build) ;;
      --no-kong) with_gateway=false ;;
      *) die "start_stack 未知参数: $1" ;;
    esac
    shift
  done

  local services=(backend frontend)
  if [ "$with_gateway" = true ]; then
    services+=(kong keycloak)
  fi

  log "启动 Compose: ${services[*]} ..."
  compose up -d "${extra[@]}" "${services[@]}"

  log "等待后端就绪 ..."
  wait_for_backend "后端" 420

  if [ "$with_gateway" = true ]; then
    log "等待 Kong 就绪 ..."
    wait_for_kong 180 || die "Kong 启动超时，请查看 logs/kong/ 或: docker compose -f $COMPOSE_FILE logs --tail=80"
  fi

  if [ ! -f "$ROOT_DIR/artifacts/backend/server.jar" ]; then
    log "WARN: 后端 JAR 未写出到 artifacts/backend/"
  fi
  if [ ! -f "$ROOT_DIR/artifacts/frontend/index.html" ]; then
    log "WARN: 前端 dist 未写出到 artifacts/frontend/"
  fi

  print_stack_banner
}

start_kong() {
  require_docker
  if [ ! -f "$COMPOSE_FILE" ]; then
    die "Compose 配置文件不存在: $COMPOSE_FILE"
  fi
  ensure_compose_dirs

  log "构建 Keycloak SPI JAR ..."
  compose up --build keycloak-spi
  if [ ! -f "$ROOT_DIR/keycloak/providers/hfwas-keycloak-http-listener.jar" ]; then
    die "SPI JAR 未写出: $ROOT_DIR/keycloak/providers/hfwas-keycloak-http-listener.jar"
  fi

  log "启动 Kong 网关 + Keycloak（将同时拉起 backend / frontend）..."
  compose up -d kong keycloak

  log "等待 Kong 就绪 ..."
  if ! wait_for_kong 300; then
    die "Kong 启动超时，请查看 logs/kong/ 或: docker compose -f $COMPOSE_FILE logs --tail=80 kong keycloak"
  fi
  print_stack_banner
}

stop_stack() {
  if [ ! -f "$COMPOSE_FILE" ]; then
    return 0
  fi
  if docker ps -a --format "{{.Names}}" 2>/dev/null | grep -qE '^devops-(backend|frontend|kong|keycloak)'; then
    log "停止 Docker Compose 服务 ..."
    # 只停开发栈，不动 k3s，也不 compose down（会误伤同项目其它容器）。
    compose stop backend frontend kong keycloak 2>/dev/null || true
    compose rm -f backend frontend kong keycloak 2>/dev/null || true
  fi
}

stop_kong() {
  if [ ! -f "$COMPOSE_FILE" ]; then
    return 0
  fi
  if docker ps --filter "name=devops-kong" --format "{{.Names}}" 2>/dev/null | grep -q "devops-kong" \
    || docker ps --filter "name=devops-keycloak" --format "{{.Names}}" 2>/dev/null | grep -q "devops-keycloak"; then
    log "停止 Kong 网关 + Keycloak ..."
    compose stop kong keycloak
    compose rm -f kong keycloak
  fi
}

kong_is_running() {
  docker ps --filter "name=devops-kong" --filter "status=running" --format "{{.Names}}" 2>/dev/null | grep -q "devops-kong"
}
