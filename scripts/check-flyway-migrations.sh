#!/usr/bin/env bash
# Flyway migration lint: naming, ordering, and SQLite-syntax guard.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
DIR="$ROOT/backend/db-migration/src/main/resources/db/migration"

if [[ ! -d "$DIR" ]]; then
  echo "migration directory not found: $DIR" >&2
  exit 1
fi

mapfile -t FILES < <(find "$DIR" -maxdepth 1 -name 'V*__*.sql' -print | sort)

if [[ ${#FILES[@]} -eq 0 ]]; then
  echo "no Flyway migration files found" >&2
  exit 1
fi

last=0
for f in "${FILES[@]}"; do
  name="$(basename "$f")"
  if [[ ! "$name" =~ ^V([0-9]+)__[a-z0-9_]+\.sql$ ]]; then
    echo "invalid migration name: $name" >&2
    exit 1
  fi
  version="${BASH_REMATCH[1]}"
  if (( version <= last )); then
    echo "migration versions must be strictly increasing: $name" >&2
    exit 1
  fi
  last="$version"
done

FORBIDDEN=(
  'PRAGMA'
  'sqlite_master'
  'AUTOINCREMENT'
  "datetime\\('now'\\)"
  'INSERT OR IGNORE'
  'json_each'
  'json_valid'
  'CREATE INDEX IF NOT EXISTS'
  'DROP TABLE'
  'TRUNCATE'
  'DELETE FROM'
  'CREATE DATABASE'
  '^[[:space:]]*USE[[:space:]]'
)

fail=0
for pattern in "${FORBIDDEN[@]}"; do
  if grep -RInE "$pattern" "$DIR" >/dev/null 2>&1; then
    echo "forbidden SQLite/destructive pattern '$pattern' found in migrations" >&2
    grep -RInE "$pattern" "$DIR" >&2 || true
    fail=1
  fi
done

V1="$DIR/V1__initial_schema.sql"
if [[ -f "$V1" ]]; then
  if ! grep -q 'trigger_type' "$V1"; then
    echo "V1 must define pipeline_run.trigger_type" >&2
    fail=1
  fi
  if grep -qE '^[[:space:]]+trigger[[:space:]]+(VARCHAR|TEXT)' "$V1"; then
    echo "V1 must not define a column named trigger" >&2
    fail=1
  fi
fi

if (( fail != 0 )); then
  exit 1
fi

echo "Flyway migrations OK (${#FILES[@]} files, latest V${last})"
