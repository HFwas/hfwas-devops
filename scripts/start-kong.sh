#!/usr/bin/env bash
# ============================================================
# 本地开发：启动 Kong 网关（DB-less 模式，独立使用）
# 如果已通过 start-dev.sh --kong 启动，无需再运行此脚本。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

# 检查 Docker
require_docker

# 检查后端是否已启动
if ! curl -s -o /dev/null -w "%{http_code}" http://localhost:"${BACKEND_PORT}"/health/check 2>/dev/null | grep -qE '200|204'; then
  echo "⚠️  后端未检测到 (http://localhost:${BACKEND_PORT}/health/check)，请先启动后端"
  echo "   scripts/start-dev.sh --build [--kong]"
  echo ""
  echo "继续启动 Kong（但 API 路由会报 502）..."
fi

# 检查前端是否已启动
if ! curl -s -o /dev/null http://localhost:"${FRONTEND_PORT}" 2>/dev/null; then
  echo "⚠️  前端未检测到 (http://localhost:${FRONTEND_PORT})，请先启动前端"
  echo "   scripts/start-dev.sh --build [--kong]"
  echo ""
  echo "继续启动 Kong..."
fi

start_kong