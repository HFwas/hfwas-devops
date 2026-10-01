#!/usr/bin/env bash
# ============================================================
# hfwas-devops 本地开发启动脚本
# ============================================================
# 用法:
#   ./scripts/start-dev.sh                      # 本地启动（前端 + 后端）
#   ./scripts/start-dev.sh docker               # Docker Compose 全量启动
#   ./scripts/start-dev.sh docker keycloak      # Docker 仅启动 Keycloak（供本地前端认证）
#   ./scripts/start-dev.sh backend              # 仅启动后端
#   ./scripts/start-dev.sh frontend             # 仅启动前端
#   ./scripts/start-dev.sh stop                 # 停止本地服务
# ============================================================
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

log()  { printf '[%s] %s\n' "$(date '+%H:%M:%S')" "$*"; }
die()  { log "ERROR: $*"; exit 1; }
require_cmd() { command -v "$1" >/dev/null 2>&1 || die "未找到命令: $1 请先安装"; }

# 颜色
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ============================================================
# Docker 全量启动
# ============================================================
docker_start() {
  log "=== Docker Compose 全量启动 ==="
  require_cmd docker

  COMPOSE=$(detect_compose)

  log "构建并启动所有服务..."
  $COMPOSE up -d --build

  log ""
  log "Docker 启动完成"
  log "  Frontend: http://localhost:8000"
  log "  Backend:  http://localhost:8089"
  log "  Keycloak: http://localhost:8081"
  log "日志: $COMPOSE logs -f"
  log "停止: $COMPOSE down"
}

# ============================================================
# Docker 仅启动 Keycloak（供本地前后端认证）
# ============================================================
docker_keycloak() {
  log "=== Docker 启动 Keycloak ==="
  require_cmd docker

  COMPOSE=$(detect_compose)

  # 先构建 SPI 镜像
  log "构建 keycloak-spi ..."
  $COMPOSE build keycloak-spi 2>&1 | tail -3

  # 确保日志目录存在
  mkdir -p logs/keycloak logs/kong

  log "启动 keycloak-spi + keycloak + postgres ..."
  $COMPOSE up -d keycloak-spi keycloak

  log ""
  log "等待 Keycloak 就绪..."
  for i in $(seq 1 30); do
    if curl -sf http://localhost:8081/auth/realms/hfwas-devops/.well-known/openid-configuration >/dev/null 2>&1; then
      log "Keycloak 就绪 http://localhost:8081/auth"
      log "  admin: http://localhost:8081/auth/admin (admin/admin)"
      break
    fi
    sleep 3
  done

  log ""
  log "现在可以本地启动前端和后端了:"
  log "  ./scripts/start-dev.sh backend"
  log "  ./scripts/start-dev.sh frontend"
}

detect_compose() {
  if docker compose version >/dev/null 2>&1; then
    echo "docker compose"
  elif docker-compose version >/dev/null 2>&1; then
    echo "docker-compose"
  else
    die "未找到 docker compose 命令"
  fi
}

# ============================================================
# 本地启动后端
# ============================================================
backend_start() {
  log "=== 本地启动后端 ==="
  require_cmd java
  require_cmd mvn

  log "Java 版本: $(java -version 2>&1 | head -1)"
  log ""

  log "编译后端依赖..."
  cd "$ROOT_DIR"
  mvn compile -pl backend/server -am -DskipTests -q || die "后端编译失败"

  log "启动 Spring Boot (端口 8089)..."
  # -pl backend/server 只启动 server 模块
  # 不加 -am 避免 root pom 也被 spring-boot 插件执行
  mvn -pl backend/server \
    org.springframework.boot:spring-boot-maven-plugin:run \
    -Dspring-boot.run.profiles=dev \
    -Dspring-boot.run.jvmArguments="-Xmx512m" &
  BACKEND_PID=$!

  log "后端 PID: $BACKEND_PID"
  echo "$BACKEND_PID" > /tmp/devops-backend.pid
  log "等待后端就绪..."

  for i in $(seq 1 60); do
    if curl -sf http://localhost:8089/health/check >/dev/null 2>&1; then
      log "后端就绪 http://localhost:8089"
      break
    fi
    if ! kill -0 "$BACKEND_PID" 2>/dev/null; then
      die "后端进程已退出"
    fi
    sleep 2
  done
}

# ============================================================
# 本地启动前端
# ============================================================
frontend_start() {
  log "=== 本地启动前端 ==="
  require_cmd node

  log "Node $(node -v) / npm $(npm -v)"

  cd "$ROOT_DIR/frontend"

  if [ ! -d node_modules ]; then
    log "安装前端依赖..."
    npm install || die "npm install 失败"
  fi

  log "启动 Vite 开发服务器 (端口 5173)..."
  log "API 代理 -> http://localhost:8089"
  log ""

  npx vite --host 0.0.0.0 &
  FRONTEND_PID=$!
  echo "$FRONTEND_PID" > /tmp/devops-frontend.pid
  cd "$ROOT_DIR"

  log "前端 PID: $FRONTEND_PID"
}

# ============================================================
# 本地启动（前后端一起）
# ============================================================
local_start() {
  log "=============================="
  log "  hfwas-devops 本地开发启动"
  log "=============================="
  log ""

  require_cmd java
  require_cmd mvn
  require_cmd node

  # 清理旧进程
  for pid_file in /tmp/devops-backend.pid /tmp/devops-frontend.pid; do
    if [ -f "$pid_file" ]; then
      OLD_PID=$(cat "$pid_file")
      if kill -0 "$OLD_PID" 2>/dev/null; then
        log "停止旧进程 PID=$OLD_PID..."
        kill "$OLD_PID" 2>/dev/null || true
        sleep 1
      fi
      rm -f "$pid_file"
    fi
  done

  backend_start
  frontend_start

  log ""
  log "=============================="
  echo -e "${GREEN}全部启动完成${NC}"
  echo -e "${BLUE}  Frontend:${NC}  http://localhost:5173"
  echo -e "${BLUE}  Backend:${NC}   http://localhost:8089"
  echo -e "${BLUE}  API:${NC}  http://localhost:5173/api/ -> :8089"
  echo -e "${YELLOW}  停止:${NC}  ./scripts/start-dev.sh stop 或 kill PID"
  log "=============================="
  log ""
  log "提示: 如果 Keycloak 未启动，浏览器打开时会跳转到 Keycloak 登录页。"
  log "先运行: ./scripts/start-dev.sh docker keycloak"
}

# ============================================================
# 停止
# ============================================================
stop_all() {
  log "停止所有本地服务..."
  for pid_file in /tmp/devops-backend.pid /tmp/devops-frontend.pid; do
    if [ -f "$pid_file" ]; then
      PID=$(cat "$pid_file")
      if kill -0 "$PID" 2>/dev/null; then
        kill "$PID" 2>/dev/null || true
        log "已停止 PID=$PID"
      fi
      rm -f "$pid_file"
    fi
  done
  log "已全部停止"
}

# ============================================================
# 帮助
# ============================================================
show_help() {
  echo "用法: $0 [选项]"
  echo ""
  echo "选项:"
  echo "  (无参数)            本地启动前端 + 后端"
  echo "  docker              使用 Docker Compose 全量启动"
  echo "  docker keycloak     Docker 仅启动 Keycloak（本地开发认证用）"
  echo "  backend             仅本地启动后端"
  echo "  frontend            仅本地启动前端"
  echo "  stop                停止本地服务"
  echo "  restart             重启本地服务"
  echo "  -h, --help          显示此帮助"
}

# ============================================================
# 入口
# ============================================================
case "${1:-}" in
  docker)
    if [ "${2:-}" = "keycloak" ]; then
      docker_keycloak
    else
      docker_start
    fi
    ;;
  backend)    backend_start ;;
  frontend)   frontend_start ;;
  stop)       stop_all ;;
  restart)    stop_all; sleep 2; local_start ;;
  -h|--help)  show_help ;;
  "")         local_start ;;
  *)          echo "未知参数: $1"; show_help; exit 1 ;;
esac