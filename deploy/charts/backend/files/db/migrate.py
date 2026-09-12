#!/usr/bin/env python3
"""Apply numbered *.sql files to SQLite. Re-runs a file only when its checksum changes."""
from __future__ import annotations

import hashlib
import os
import sqlite3
import sys
from pathlib import Path

SQL_DIR = Path(os.environ.get("SQL_DIR", "/sql"))
DB_PATH = Path(os.environ.get("DB_PATH", "/app/data/hfwas-devops.db"))


def log(msg: str) -> None:
    print(msg, flush=True)


def checksum(text: str) -> str:
    return hashlib.sha256(text.encode("utf-8")).hexdigest()


def main() -> int:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    files = sorted(p for p in SQL_DIR.glob("*.sql") if p.is_file())
    if not files:
        log(f"ERROR: no *.sql under {SQL_DIR}")
        return 1

    conn = sqlite3.connect(DB_PATH)
    try:
        conn.execute(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                filename   TEXT NOT NULL PRIMARY KEY,
                checksum   TEXT NOT NULL,
                applied_at TEXT NOT NULL DEFAULT (datetime('now'))
            )
            """
        )
        conn.commit()
        for path in files:
            sql = path.read_text(encoding="utf-8")
            digest = checksum(sql)
            row = conn.execute(
                "SELECT checksum FROM schema_migrations WHERE filename = ?",
                (path.name,),
            ).fetchone()
            if row and row[0] == digest:
                log(f"skip  {path.name}")
                continue
            log(f"apply {path.name}")
            conn.executescript(sql)
            conn.execute(
                """
                INSERT INTO schema_migrations (filename, checksum, applied_at)
                VALUES (?, ?, datetime('now'))
                ON CONFLICT(filename) DO UPDATE SET
                    checksum = excluded.checksum,
                    applied_at = excluded.applied_at
                """,
                (path.name, digest),
            )
            conn.commit()
        log(f"schema ready: {DB_PATH}")
        return 0
    finally:
        conn.close()


if __name__ == "__main__":
    sys.exit(main())
