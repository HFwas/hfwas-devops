#!/usr/bin/env bash
# ============================================================
# 停止本地 Kong 网关
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

COMPOSE_FILE="$ROOT_DIR/docker-compose.kong.yml"

if [ ! -f "$COMPOSE_FILE" ]; then
  echo "未找到 Kong 配置文件: $COMPOSE_FILE"
  exit 1
fi

docker compose -f "$COMPOSE_FILE" down
echo "Kong 网关已停止"