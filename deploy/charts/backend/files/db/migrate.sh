#!/bin/bash
# =============================================================================
# migrate.sh — SQLite 迁移执行脚本（零依赖，仅需 bash + sqlite3）
#
# 用途：幂等地按编号顺序执行 *.sql 文件，自动跳过已执行的迁移。
#       兼容 Helm initContainer / docker-compose / 手动执行。
#
# 用法：
#   ./migrate.sh <db-path> [sql-dir]
#     默认 sql-dir 为脚本所在目录
#
# 幂等性保障：
#   - 维护 _schema_migrations 表记录已执行迁移的 checksum
#   - ALTER TABLE ADD COLUMN 重复执行时静默跳过（不终止脚本）
#   - 支持重复执行，仅运行新变更或 checksum 变化的文件
# =============================================================================
set -euo pipefail

DB="${1:?Usage: $0 <db-path> [sql-dir]}"
SQL_DIR="${2:-$(cd "$(dirname "$0")" && pwd)}"

if ! command -v sqlite3 &>/dev/null; then
    echo "ERROR: sqlite3 not found"
    exit 1
fi

# 确保 DB 父目录存在
mkdir -p "$(dirname "$DB")"

# 创建迁移追踪表（幂等：IF NOT EXISTS）
sqlite3 "$DB" "
CREATE TABLE IF NOT EXISTS _schema_migrations (
    filename    TEXT NOT NULL PRIMARY KEY,
    checksum    TEXT NOT NULL,
    applied_at  TEXT NOT NULL DEFAULT (datetime('now'))
);
"

apply_sql() {
    local file="$1"
    local name
    name=$(basename "$file")
    local digest
    digest=$(sha256sum "$file" | cut -d' ' -f1)

    # 检查是否已执行且 checksum 未变
    local row
    row=$(sqlite3 "$DB" "SELECT checksum FROM _schema_migrations WHERE filename = '$name'")

    if [ -n "$row" ] && [ "$row" = "$digest" ]; then
        echo "skip  $name"
        return 0
    fi

    echo "apply $name"

    # 执行 SQL，捕获 ALTER TABLE ADD COLUMN 的重复列错误
    # 其它错误仍然终止
    local tmpfile
    tmpfile=$(mktemp)
    sqlite3 "$DB" < "$file" > "$tmpfile" 2>&1 || {
        local rc=$?
        if grep -q "duplicate column name" "$tmpfile" 2>/dev/null; then
            # 幂等：ALTER TABLE 重复列是预期的，静默跳过
            :
        else
            cat "$tmpfile"
            rm -f "$tmpfile"
            return $rc
        fi
    }
    rm -f "$tmpfile"

    # 记录 / 更新 checksum
    sqlite3 "$DB" "
    INSERT INTO _schema_migrations (filename, checksum, applied_at)
    VALUES ('$name', '$digest', datetime('now'))
    ON CONFLICT(filename) DO UPDATE SET
        checksum = excluded.checksum,
        applied_at = excluded.applied_at;
    "
}

# 按编号顺序执行
for f in "$SQL_DIR"/*.sql; do
    [ -f "$f" ] || continue
    apply_sql "$f"
done

echo "schema ready: $DB"