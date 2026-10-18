#!/bin/bash
# Migration gate:
#   - every *.sql must be present in scripts/migrations.lock with its SHA-256;
#   - historical checksum changes fail (add a new migration instead);
#   - destructive statements are rejected at statement start;
#   - UPDATE is only allowed in 16-upgrade-task-template-seeds.sql.
#
# Usage:
#   scripts/check-migrations.sh                  # check (CI)
#   scripts/check-migrations.sh --write-lock     # local: refresh the lock after a reviewed change
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SQL_DIR="${ROOT}/deploy/charts/backend/files/db"
LOCK="${ROOT}/scripts/migrations.lock"
MODE="${1:-check}"

python3 - "${SQL_DIR}" "${LOCK}" "${MODE}" <<'PY'
import hashlib
import sqlite3
import sys
from pathlib import Path

sql_dir = Path(sys.argv[1])
lock_path = Path(sys.argv[2])
mode = sys.argv[3]
files = sorted(sql_dir.glob("*.sql"))


def digest(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


if mode == "--write-lock":
    lock_path.write_text(
        "".join(f"{digest(path)}  {path.name}\n" for path in files),
        encoding="utf-8",
    )
    print(f"wrote {lock_path} ({len(files)} files)")
    raise SystemExit(0)

lock: dict[str, str] = {}
for line in lock_path.read_text(encoding="utf-8").splitlines():
    line = line.strip()
    if not line or line.startswith("#"):
        continue
    parts = line.split()
    if len(parts) != 2:
        raise SystemExit(f"invalid lock line: {line}")
    lock[parts[1]] = parts[0]

errors: list[str] = []


def check_statement(filename: str, statement: str) -> None:
    lines = statement.splitlines()
    idx = 0
    while idx < len(lines) and (not lines[idx].strip() or lines[idx].strip().startswith("--")):
        idx += 1
    keyword = "\n".join(lines[idx:]).lstrip().upper()
    if keyword.startswith(("DROP TABLE", "DROP COLUMN", "DELETE FROM", "TRUNCATE")):
        errors.append(f"{filename}: destructive statement: {keyword.splitlines()[0]}")
    if keyword.startswith("UPDATE") and filename != "16-upgrade-task-template-seeds.sql":
        errors.append(
            f"{filename}: UPDATE is only allowed in 16-upgrade-task-template-seeds.sql"
        )


for path in files:
    current = digest(path)
    if path.name not in lock:
        errors.append(f"{path.name}: not in migrations.lock; run --write-lock after review")
    elif lock[path.name] != current:
        errors.append(f"{path.name}: checksum changed; add a new migration instead")

    buffer: list[str] = []
    for char in path.read_text(encoding="utf-8"):
        buffer.append(char)
        if char == ";" and sqlite3.complete_statement("".join(buffer)):
            check_statement(path.name, "".join(buffer).strip())
            buffer = []
    tail = "".join(buffer).strip()
    if tail:
        check_statement(path.name, tail)

if errors:
    print("\n".join(errors), file=sys.stderr)
    raise SystemExit(1)

print(f"ok: {len(files)} migrations checked")
PY
