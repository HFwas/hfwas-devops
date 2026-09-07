-- Image processor history (SQLite). Metadata log only; original blobs stay in temp dirs with TTL.

CREATE TABLE IF NOT EXISTS image_convert_history (
    id               INTEGER      NOT NULL PRIMARY KEY,
    session_id       TEXT         NOT NULL,
    user_id          INTEGER,
    tenant_id        INTEGER,
    file_name        TEXT,
    source_mime      TEXT,
    target_format    TEXT,
    result_file_name TEXT,
    result_size      INTEGER,
    width            INTEGER,
    height           INTEGER,
    stripped_gps     INTEGER      NOT NULL DEFAULT 0,
    status           TEXT         NOT NULL DEFAULT 'completed',
    error_message    TEXT,
    deleted          INTEGER      NOT NULL DEFAULT 0,
    create_by        INTEGER,
    create_time      TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_image_history_user_time
    ON image_convert_history (user_id, create_time);
