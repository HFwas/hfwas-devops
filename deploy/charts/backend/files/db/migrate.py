#!/usr/bin/env python3
"""SQLite migration runner.

Usage:
    migrate.py [db-path] [sql-dir]

Environment fallbacks: DB_PATH, SQL_DIR.

Behaviour:
- applies *.sql in filename order;
- records SHA-256 in _schema_migrations;
- executes statement by statement (never executescript), so a duplicate
  ALTER TABLE ADD COLUMN does not abort the rest of the file;
- only records the checksum after the whole file succeeds;
- refuses destructive statements (DROP/DELETE/TRUNCATE/UPDATE) except the
  explicitly reviewed 16-upgrade-task-template-seeds.sql;
- upgrades a stale dependency_component table by keeping a legacy backup.
"""

from __future__ import annotations

import hashlib
import os
import sqlite3
import sys
from pathlib import Path
from typing import Iterator

LEDGER = "_schema_migrations"
LEGACY_LEDGER = "schema_migrations"
ALLOW_UPDATE_FILE = "16-upgrade-task-template-seeds.sql"
DEPENDENCY_COMPONENT_FILE = "05-pipeline-schema.sql"
BUSY_TIMEOUT_MS = 5000

REQUIRED_COLUMNS = {
    "pipeline_task_kind": {
        "kind_value", "command_template", "cpu_request", "cpu_limit",
        "memory_request", "memory_limit",
    },
    "pipeline_task_kind_param": {"options_json"},
    "pipeline_job": {"param_bindings"},
    "pipeline_run": {"runtime_params"},
}


class MigrationError(RuntimeError):
    pass


def log(message: str) -> None:
    print(message, flush=True)


def table_exists(conn: sqlite3.Connection, table: str) -> bool:
    row = conn.execute(
        "SELECT 1 FROM sqlite_master WHERE type='table' AND name=?", (table,)
    ).fetchone()
    return row is not None


def columns(conn: sqlite3.Connection, table: str) -> set[str]:
    return {row[1] for row in conn.execute(f'PRAGMA table_info("{table}")')}


def checksum(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def meaningful(statement: str) -> bool:
    for line in statement.splitlines():
        stripped = line.strip()
        if stripped and not stripped.startswith("--"):
            return True
    return False


def leading_keyword(statement: str) -> str:
    lines = statement.splitlines()
    idx = 0
    while idx < len(lines):
        stripped = lines[idx].strip()
        if not stripped or stripped.startswith("--"):
            idx += 1
            continue
        break
    return "\n".join(lines[idx:]).lstrip().upper()


def iter_statements(sql: str) -> Iterator[str]:
    """Yield one statement at a time.

    Character-based so multiple statements on one line are split correctly,
    while semicolons inside strings/triggers are left alone by
    sqlite3.complete_statement.
    """
    buffer: list[str] = []
    for char in sql:
        buffer.append(char)
        if char == ";" and sqlite3.complete_statement("".join(buffer)):
            yield "".join(buffer).strip()
            buffer = []
    tail = "".join(buffer).strip()
    if tail:
        yield tail


def assert_safe(filename: str, statement: str) -> None:
    keyword = leading_keyword(statement)
    if keyword.startswith(("DROP TABLE", "DROP COLUMN", "DELETE FROM", "TRUNCATE")):
        raise MigrationError(
            f"{filename}: destructive statement is not allowed: {keyword.splitlines()[0]}"
        )
    if keyword.startswith("UPDATE") and filename != ALLOW_UPDATE_FILE:
        raise MigrationError(
            f"{filename}: UPDATE is only allowed in {ALLOW_UPDATE_FILE}"
        )


def ensure_ledger(conn: sqlite3.Connection) -> None:
    conn.execute(
        f"""
        CREATE TABLE IF NOT EXISTS {LEDGER} (
            filename    TEXT NOT NULL PRIMARY KEY,
            checksum    TEXT NOT NULL,
            applied_at  TEXT NOT NULL DEFAULT (datetime('now'))
        )
        """
    )
    conn.commit()


def merge_legacy_ledger(conn: sqlite3.Connection) -> None:
    """Merge schema_migrations into _schema_migrations without dropping the old table."""
    if not table_exists(conn, LEGACY_LEDGER):
        return
    rows = conn.execute(
        f"SELECT filename, checksum, applied_at FROM {LEGACY_LEDGER}"
    ).fetchall()
    for filename, digest, applied_at in rows:
        existing = conn.execute(
            f"SELECT checksum, applied_at FROM {LEDGER} WHERE filename=?", (filename,)
        ).fetchone()
        if existing is None:
            conn.execute(
                f"INSERT INTO {LEDGER}(filename, checksum, applied_at) VALUES (?, ?, COALESCE(?, datetime('now')))",
                (filename, digest, applied_at),
            )
        elif applied_at and existing[1] and str(applied_at) > str(existing[1]):
            conn.execute(
                f"UPDATE {LEDGER} SET checksum=?, applied_at=? WHERE filename=?",
                (digest, applied_at, filename),
            )
    conn.commit()


def verify_known_schema(conn: sqlite3.Connection) -> None:
    for table, expected in REQUIRED_COLUMNS.items():
        if not table_exists(conn, table):
            continue
        missing = sorted(expected - columns(conn, table))
        if missing:
            raise MigrationError(
                f"{table} is missing columns {missing}; "
                "a previous migration was recorded without being fully applied. "
                "Repair the schema or restore a backup before retrying."
            )


def ensure_dependency_component_schema(conn: sqlite3.Connection) -> None:
    """Back up and rebuild a stale dependency_component table.

    Transaction 1 commits the legacy copy. Transaction 2 drops the old table;
    if it fails, ROLLBACK restores the original table and the legacy copy stays.
    The 05 record is removed so the file recreates the new table.
    """
    if not table_exists(conn, "dependency_component"):
        return
    required = {"pipeline_id", "identity_key", "run_id", "purl", "name", "version"}
    if required <= columns(conn, "dependency_component"):
        return

    log("dependency_component is missing columns; rebuilding with legacy backup")
    conn.execute("BEGIN IMMEDIATE")
    try:
        conn.execute("DROP TABLE IF EXISTS dependency_component_legacy")
        conn.execute(
            "CREATE TABLE dependency_component_legacy AS SELECT * FROM dependency_component"
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise

    conn.execute("BEGIN IMMEDIATE")
    try:
        conn.execute("DROP TABLE dependency_component")
        conn.execute(
            f"DELETE FROM {LEDGER} WHERE filename=?", (DEPENDENCY_COMPONENT_FILE,)
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise


def apply_file(conn: sqlite3.Connection, path: Path, digest: str, allow_rerun: bool) -> None:
    filename = path.name
    sql = path.read_text(encoding="utf-8")
    conn.execute("BEGIN IMMEDIATE")
    try:
        # Re-check under the migration lock so two concurrent runners cannot both apply.
        row = conn.execute(
            f"SELECT checksum FROM {LEDGER} WHERE filename=?", (filename,)
        ).fetchone()
        if row is not None:
            if row[0] == digest:
                conn.execute("COMMIT")
                log(f"skip  {filename}")
                return
            if not allow_rerun:
                raise MigrationError(
                    f"{filename} changed after it was applied; add a new migration instead"
                )
            log(f"warn  {filename} checksum changed; re-applying zero-UPDATE baseline")

        log(f"apply {filename}")
        for statement in iter_statements(sql):
            if not meaningful(statement):
                continue
            assert_safe(filename, statement)
            try:
                conn.execute(statement)
            except sqlite3.OperationalError as exc:
                if (
                    "duplicate column name" in str(exc)
                    and leading_keyword(statement).startswith("ALTER TABLE")
                ):
                    log(f"  warn: {exc} — skipped")
                    continue
                raise
        conn.execute(
            f"""
            INSERT INTO {LEDGER}(filename, checksum, applied_at)
            VALUES (?, ?, datetime('now'))
            ON CONFLICT(filename) DO UPDATE SET
                checksum = excluded.checksum,
                applied_at = excluded.applied_at
            """,
            (filename, digest),
        )
        conn.execute("COMMIT")
    except Exception:
        conn.execute("ROLLBACK")
        raise


def run(db_path: str, sql_dir: str) -> int:
    conn = sqlite3.connect(db_path, isolation_level=None)
    try:
        conn.execute(f"PRAGMA busy_timeout = {BUSY_TIMEOUT_MS}")
        ensure_ledger(conn)
        merge_legacy_ledger(conn)
        ensure_dependency_component_schema(conn)

        files = sorted(Path(sql_dir).glob("*.sql"))
        for path in files:
            digest = checksum(path)
            # 05 is a zero-UPDATE baseline: its checksum changed in this release
            # and re-applying it is safe. Every other historical file is frozen.
            apply_file(conn, path, digest, allow_rerun=path.name == DEPENDENCY_COMPONENT_FILE)

        verify_known_schema(conn)
        log(f"schema ready: {db_path}")
        return 0
    finally:
        conn.close()


def main() -> int:
    db_path = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("DB_PATH")
    sql_dir = (
        sys.argv[2]
        if len(sys.argv) > 2
        else os.environ.get("SQL_DIR", os.path.dirname(os.path.abspath(__file__)))
    )
    if not db_path:
        log("ERROR: db-path is required (argument or DB_PATH)")
        return 2
    try:
        return run(db_path, sql_dir)
    except MigrationError as exc:
        log(f"ERROR: {exc}")
        return 1
    except sqlite3.Error as exc:
        log(f"ERROR: sqlite failure: {exc}")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
