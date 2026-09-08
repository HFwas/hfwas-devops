#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BUILD=false
FORCE=false
SKIP_PYTHON=false

usage() {
  cat <<EOF
用法: $(basename "$0") [选项]

  启动后端 Spring Boot（端口 ${BACKEND_PORT}）

选项:
  --build         启动前先编译 (mvn install -pl server -am -DskipTests)
  --force         若端口被占用，先结束占用进程
  --skip-python   跳过 Python 虚拟环境（OCR v6 / 文档生成将使用系统 python3）
  -h, --help      显示帮助
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --build) BUILD=true ;;
    --force) FORCE=true ;;
    --skip-python) SKIP_PYTHON=true ;;
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

require_cmd mvn
require_cmd java

if [ -n "$(port_pids "$BACKEND_PORT")" ]; then
  if [ "$FORCE" = true ]; then
    free_host_port "$BACKEND_PORT"
    sleep 1
  fi
  if [ -n "$(port_pids "$BACKEND_PORT")" ]; then
    die "端口 $BACKEND_PORT 已被占用。使用 --force 强制重启，或先运行 scripts/stop-dev.sh"
  fi
fi

if [ "$BUILD" = true ]; then
  log "编译后端 ..."
  # 从仓库根安装，确保 hfwas-devops 的 dependencyManagement 也写入本地仓库
  (cd "$ROOT_DIR" && mvn install -pl backend/server -am -DskipTests -q)
fi

if [ "$SKIP_PYTHON" = true ]; then
  log "已跳过 Python 虚拟环境"
else
  ensure_python_env
fi

BOOT_ARGS="--docgen.python-path=$VENV_DIR/bin/python3"
PIPELINE_KUBECONFIG_FILE="$ROOT_DIR/data/pipeline/kubeconfig.yaml"
if [ -f "$PIPELINE_KUBECONFIG_FILE" ]; then
  BOOT_ARGS="$BOOT_ARGS --pipeline.kubeconfig=$PIPELINE_KUBECONFIG_FILE"
  log "已启用流水线执行集群: $PIPELINE_KUBECONFIG_FILE"
fi
export_pipeline_git_http_proxy
if [ -n "${PIPELINE_GIT_HTTP_PROXY:-}" ]; then
  BOOT_ARGS="$BOOT_ARGS --pipeline.git-http-proxy=$PIPELINE_GIT_HTTP_PROXY"
fi
if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'devops-k3s'; then
  host_ip=$(docker exec devops-k3s sh -c 'ping -c 1 -W 1 host.docker.internal' 2>/dev/null | sed -n 's/PING host.docker.internal (\([0-9.]*\)).*/\1/p' | head -1 || true)
  if [ -n "${host_ip:-}" ]; then
    BOOT_ARGS="$BOOT_ARGS --pipeline.git-docker-host=$host_ip"
  fi
fi

log "启动后端 (http://localhost:$BACKEND_PORT) ..."
cd "$ROOT_DIR/backend/server"
exec mvn spring-boot:run -DskipTests \
  -Dspring-boot.run.jvmArguments="\
    -Xms512m -Xmx4g \
    -XX:MaxDirectMemorySize=1g \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=$RUN_DIR/dumps" \
  -Dspring-boot.run.arguments="$BOOT_ARGS"
