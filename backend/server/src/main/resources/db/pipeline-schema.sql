-- Pipeline module (SQLite)

CREATE TABLE IF NOT EXISTS pipeline_credential (
    id          INTEGER      NOT NULL PRIMARY KEY,
    tenant_id   INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    kind        TEXT         NOT NULL,
    username    TEXT,
    secret_enc  TEXT         NOT NULL,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    create_by   INTEGER,
    update_by   INTEGER,
    create_time TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pipeline (
    id              INTEGER      NOT NULL PRIMARY KEY,
    tenant_id       INTEGER      NOT NULL,
    name            TEXT         NOT NULL,
    repo_url        TEXT         NOT NULL,
    git_ref         TEXT         NOT NULL DEFAULT 'main',
    credential_id   INTEGER,
    stack           TEXT         NOT NULL,
    runtime_version TEXT         NOT NULL,
    tool_version    TEXT,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    create_by       INTEGER,
    update_by       INTEGER,
    create_time     TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time     TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pipeline_stage (
    id          INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pipeline_job (
    id          INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id INTEGER      NOT NULL,
    stage_id    INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    kind        TEXT         NOT NULL,
    command     TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pipeline_run (
    id              INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id     INTEGER      NOT NULL,
    tenant_id       INTEGER      NOT NULL,
    status          TEXT         NOT NULL,
    trigger         TEXT         NOT NULL DEFAULT 'MANUAL',
    git_ref         TEXT,
    commit_sha      TEXT,
    stack           TEXT,
    runtime_version TEXT,
    tool_version    TEXT,
    image           TEXT,
    tekton_name     TEXT,
    error_message   TEXT,
    started_at      TEXT,
    finished_at     TEXT,
    create_by       INTEGER,
    create_time     TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pipeline_run_job (
    id          INTEGER      NOT NULL PRIMARY KEY,
    run_id      INTEGER      NOT NULL,
    job_id      INTEGER,
    stage_name  TEXT,
    job_name    TEXT,
    kind        TEXT,
    command     TEXT,
    status      TEXT         NOT NULL,
    log_text    TEXT,
    started_at  TEXT,
    finished_at TEXT
);

CREATE INDEX IF NOT EXISTS idx_pipeline_tenant ON pipeline (tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_pipeline_run_pipeline ON pipeline_run (pipeline_id, create_time);
