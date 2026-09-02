#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

BUILD=false
FORCE=false

usage() {
  cat <<EOF
用法: $(basename "$0") [选项]

  启动后端 Spring Boot（端口 $BACKEND_PORT）

选项:
  --build    启动前先编译 (mvn install -pl server -am -DskipTests)
  --force    若端口被占用，先结束占用进程
  -h, --help 显示帮助
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    --build) BUILD=true ;;
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

require_cmd mvn
require_cmd java

# 设置 Python 虚拟环境（文档生成依赖）
setup_python_venv

if [ -n "$(port_pids "$BACKEND_PORT")" ]; then
  if [ "$FORCE" = true ]; then
    log "释放端口 $BACKEND_PORT ..."
    # shellcheck disable=SC2046
    kill $(port_pids "$BACKEND_PORT") 2>/dev/null || true
    sleep 1
  else
    die "端口 $BACKEND_PORT 已被占用。使用 --force 强制重启，或先运行 scripts/stop-dev.sh"
  fi
fi

if [ "$BUILD" = true ]; then
  log "编译后端 ..."
  (cd "$ROOT_DIR/backend" && mvn install -pl server -am -DskipTests -q)
fi

log "启动后端 (http://localhost:$BACKEND_PORT) ..."
cd "$ROOT_DIR/backend/server"
exec mvn spring-boot:run -DskipTests \
  -Dspring-boot.run.jvmArguments="\
    -Xms512m -Xmx1g \
    -Dorg.bytedeco.javacpp.maxPhysicalBytes=6G \
    -Dorg.bytedeco.javacpp.maxphysicalbytes=6G \
    -XX:MaxDirectMemorySize=512m \
    -XX:+ExitOnOutOfMemoryError \
    -XX:+HeapDumpOnOutOfMemoryError \
    -XX:HeapDumpPath=$RUN_DIR/dumps" \
  -Dspring-boot.run.arguments="\
    --docgen.python-path=$VENV_DIR/bin/python3"
