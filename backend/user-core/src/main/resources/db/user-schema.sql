-- User module schema (SQLite)
-- Also applied at runtime by UserSchemaMigration for existing databases.

CREATE TABLE IF NOT EXISTS sys_user (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    username        TEXT         NOT NULL UNIQUE,
    password        TEXT         NOT NULL,
    display_name    TEXT         NOT NULL,
    email           TEXT,
    phone           TEXT,
    role            TEXT         NOT NULL DEFAULT 'user',
    enabled         INTEGER      DEFAULT 1,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_user_username ON sys_user(username);

CREATE TABLE IF NOT EXISTS sys_user_session (
    id               INTEGER      PRIMARY KEY AUTOINCREMENT,
    user_id          INTEGER      NOT NULL,
    jti              TEXT         NOT NULL UNIQUE,
    login_ip         TEXT,
    user_agent       TEXT,
    login_time       TEXT         DEFAULT (datetime('now')),
    last_active_time TEXT         DEFAULT (datetime('now')),
    expire_time      TEXT         NOT NULL,
    revoked          INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_user_session_user ON sys_user_session(user_id);
CREATE INDEX IF NOT EXISTS idx_sys_user_session_jti ON sys_user_session(jti);
CREATE INDEX IF NOT EXISTS idx_sys_user_session_active ON sys_user_session(revoked, expire_time);

CREATE TABLE IF NOT EXISTS sys_login_log (
    id            INTEGER      PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER,
    username      TEXT         NOT NULL,
    display_name  TEXT,
    action        TEXT         NOT NULL,
    login_ip      TEXT,
    user_agent    TEXT,
    client_info   TEXT,
    fail_reason   TEXT,
    create_time   TEXT         DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_sys_login_log_time ON sys_login_log(create_time);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_user ON sys_login_log(username);
CREATE INDEX IF NOT EXISTS idx_sys_login_log_action ON sys_login_log(action);
