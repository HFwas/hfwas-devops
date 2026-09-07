#!/usr/bin/env bash
# ============================================================
# 本地开发：启动 Kong + Keycloak（会按依赖拉起 backend / frontend）
# 默认已包含在 start-dev.sh 中。
# ============================================================
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=common.sh
source "$SCRIPT_DIR/common.sh"

start_kong
