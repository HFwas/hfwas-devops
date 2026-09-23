#!/usr/bin/env bash
# =============================================================================
# delivery-platform 本地开发启动脚本
#
# 同时启动：
#   - Go 后端（:8180，SQLite 自动初始化）
#   - Vue 3 前端（:5174，Vite 开发服务器，/api 代理到后端）
#
# 用法：
#   cd delivery-platform && bash start-dev.sh
#
# 停服：Ctrl+C 即可（脚本会清理后端子进程）
# =============================================================================

set -euo pipefail

# ---- 颜色 ----
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
BACKEND_DIR="$SCRIPT_DIR/backend"
FRONTEND_DIR="$SCRIPT_DIR/frontend"
LOG_DIR="$SCRIPT_DIR/../logs/delivery-platform"
BACKEND_PID=""

# ---- 优雅退出 ----
cleanup() {
    echo ""
    echo -e "${YELLOW}正在停止服务...${NC}"
    if [ -n "$BACKEND_PID" ] && kill -0 "$BACKEND_PID" 2>/dev/null; then
        echo "  停止后端 (PID $BACKEND_PID)..."
        kill "$BACKEND_PID" 2>/dev/null
        wait "$BACKEND_PID" 2>/dev/null || true
    fi
    # 前端是前台进程，Ctrl+C 时它也会收到信号自动退出
    echo -e "${GREEN}已停止。${NC}"
    exit 0
}
trap cleanup SIGINT SIGTERM

# =============================================================================
# 1. 前置检查
# =============================================================================
echo -e "${CYAN}══════════════════════════════════════════════${NC}"
echo -e "${CYAN}  delivery-platform 本地开发启动${NC}"
echo -e "${CYAN}══════════════════════════════════════════════${NC}"
echo ""

# Go
if ! command -v go &>/dev/null; then
    echo -e "${RED}[错误] 未找到 go，请先安装 Go 1.20+${NC}"
    exit 1
fi
GO_VERSION=$(go version | grep -oE 'go[0-9]+\.[0-9]+')
echo -e "  ${GREEN}✓${NC} Go $GO_VERSION"

# Node
if ! command -v node &>/dev/null; then
    echo -e "${RED}[错误] 未找到 node，请先安装 Node 18+${NC}"
    exit 1
fi
NODE_VERSION=$(node --version)
echo -e "  ${GREEN}✓${NC} Node $NODE_VERSION"

# npm
if ! command -v npm &>/dev/null; then
    echo -e "${RED}[错误] 未找到 npm${NC}"
    exit 1
fi
echo -e "  ${GREEN}✓${NC} npm $(npm --version)"

echo ""

# =============================================================================
# 2. 准备环境
# =============================================================================

# 日志目录
mkdir -p "$LOG_DIR"

# 检查前端依赖
if [ ! -d "$FRONTEND_DIR/node_modules" ]; then
    echo -e "${YELLOW}前端依赖未安装，正在安装...${NC}"
    cd "$FRONTEND_DIR"
    npm install
    echo -e "${GREEN}前端依赖安装完成${NC}"
fi

echo -e "  ${GREEN}✓${NC} 日志目录 $LOG_DIR"
echo ""

# =============================================================================
# 3. 启动后端
# =============================================================================
echo -e "${CYAN}[1/2] 启动后端...${NC}"
cd "$BACKEND_DIR"

# 确保包已下载
if [ ! -f "go.sum" ]; then
    echo "  下载 Go 依赖..."
    go mod tidy
fi

BACKEND_LOG="$LOG_DIR/backend.log"
echo "  日志: $BACKEND_LOG"
echo "  端口: 8180"
echo ""

# CGO_ENABLED=1 是 mattn/go-sqlite3 的要求
CGO_ENABLED=1 go run ./cmd/server \
    >"$BACKEND_LOG" 2>&1 &
BACKEND_PID=$!

# 等待后端就绪
echo -n "  等待后端就绪"
for i in $(seq 1 30); do
    if curl -s http://localhost:8180/health >/dev/null 2>&1; then
        echo ""
        echo -e "  ${GREEN}✓${NC} 后端就绪 (PID $BACKEND_PID)"
        break
    fi
    echo -n "."
    sleep 1
done
if ! curl -s http://localhost:8180/health >/dev/null 2>&1; then
    echo ""
    echo -e "${RED}[错误] 后端启动超时，请查看日志: $BACKEND_LOG${NC}"
    kill "$BACKEND_PID" 2>/dev/null || true
    exit 1
fi

echo ""
echo -e "${CYAN}[2/2] 启动前端...${NC}"
FRONTEND_LOG="$LOG_DIR/frontend.log"
echo "  日志: $FRONTEND_LOG"
echo "  端口: 5174"
echo ""

# =============================================================================
# 4. 启动前端（前台，Vite 开发服务器）
# =============================================================================
cd "$FRONTEND_DIR"

echo -e "${GREEN}══════════════════════════════════════════════${NC}"
echo -e "${GREEN}  启动完成！${NC}"
echo -e "  ${NC}"
echo -e "  后端 API: ${CYAN}http://localhost:8180${NC}"
echo -e "  前端页面: ${CYAN}http://localhost:5174${NC}"
echo -e "  后端日志: ${CYAN}tail -f $BACKEND_LOG${NC}"
echo -e "  前端日志: ${CYAN}tail -f $FRONTEND_LOG${NC}"
echo -e "  ${NC}"
echo -e "  演示包:  data/packages/demo-app-1.0.0.zip"
echo -e "  ${NC}"
echo -e "  Ctrl+C 停止所有服务${NC}"
echo -e "${GREEN}══════════════════════════════════════════════${NC}"
echo ""

# 在前台运行前端开发服务器，输出到日志并同时显示在终端
npm run dev 2>&1 | tee -a "$FRONTEND_LOG"

# 如果前端退出（Ctrl+C 等），执行清理
cleanup