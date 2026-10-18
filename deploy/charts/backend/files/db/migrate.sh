#!/bin/bash
# =============================================================
# migrate.sh — thin wrapper around migrate.py
#
# Usage:
#   ./migrate.sh <db-path> [sql-dir]
#
# Kept for existing manual callers; Helm/Compose should call migrate.py.
# =============================================================
set -euo pipefail

DB="${1:?Usage: $0 <db-path> [sql-dir]}"
SQL_DIR="${2:-$(cd "$(dirname "$0")" && pwd)}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

if ! command -v python3 &>/dev/null; then
    echo "ERROR: python3 is required" >&2
    exit 1
fi

exec python3 "${SCRIPT_DIR}/migrate.py" "${DB}" "${SQL_DIR}"
