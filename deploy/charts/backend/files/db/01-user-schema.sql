-- User module schema (SQLite)
-- Also applied at runtime by UserSchemaMigration for existing databases.

CREATE TABLE IF NOT EXISTS sys_tenant (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    code            TEXT         NOT NULL UNIQUE,
    name            TEXT         NOT NULL,
    contact_name    TEXT,
    contact_phone   TEXT,
    status          INTEGER      DEFAULT 1,
    remark          TEXT,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_tenant_code ON sys_tenant(code);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_status ON sys_tenant(status);

CREATE TABLE IF NOT EXISTS sys_user (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    username        TEXT         NOT NULL UNIQUE,
    password        TEXT,
    display_name    TEXT         NOT NULL,
    email           TEXT,
    phone           TEXT,
    role            TEXT         NOT NULL DEFAULT 'user',
    enabled         INTEGER      DEFAULT 1,
    auth_source     TEXT         DEFAULT 'keycloak',
    external_id     TEXT,
    connector_id    INTEGER,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_user_external_id ON sys_user(external_id);

CREATE TABLE IF NOT EXISTS sys_tenant_member (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    tenant_id       INTEGER      NOT NULL,
    user_id         INTEGER      NOT NULL,
    tenant_role     TEXT         NOT NULL DEFAULT 'member',
    status          INTEGER      DEFAULT 1,
    join_time       TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_sys_tenant_member ON sys_tenant_member(tenant_id, user_id);
CREATE INDEX IF NOT EXISTS idx_sys_tenant_member_user ON sys_tenant_member(user_id);

CREATE TABLE IF NOT EXISTS sys_login_log (
    id            INTEGER      PRIMARY KEY AUTOINCREMENT,
    kc_event_id   TEXT         NOT NULL UNIQUE,
    user_id       INTEGER,
    kc_user_id    TEXT,
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

CREATE TABLE IF NOT EXISTS sys_oper_log (
    id            INTEGER      PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER,
    username      TEXT,
    display_name  TEXT,
    module        TEXT         NOT NULL,
    action        TEXT         NOT NULL,
    biz_type      TEXT,
    biz_id        TEXT,
    summary       TEXT         NOT NULL,
    status        TEXT         DEFAULT 'success',
    fail_reason   TEXT,
    request_ip    TEXT,
    user_agent    TEXT,
    client_info   TEXT,
    extra_json    TEXT,
    create_time   TEXT         DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_sys_oper_log_time ON sys_oper_log(create_time);
CREATE INDEX IF NOT EXISTS idx_sys_oper_log_module ON sys_oper_log(module);
CREATE INDEX IF NOT EXISTS idx_sys_oper_log_user ON sys_oper_log(username);
CREATE INDEX IF NOT EXISTS idx_sys_oper_log_action ON sys_oper_log(action);

CREATE TABLE IF NOT EXISTS sys_identity_connector (
    id                  INTEGER      PRIMARY KEY AUTOINCREMENT,
    name                TEXT         NOT NULL,
    type                TEXT         NOT NULL,
    config_json         TEXT         NOT NULL,
    enabled             INTEGER      DEFAULT 1,
    default_tenant_id   INTEGER,
    auto_create_member  INTEGER      DEFAULT 1,
    last_sync_time      TEXT,
    last_sync_status    TEXT,
    last_sync_message   TEXT,
    last_sync_count     INTEGER,
    create_time         TEXT         DEFAULT (datetime('now')),
    update_time         TEXT         DEFAULT (datetime('now')),
    del_flag            INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_identity_connector_type ON sys_identity_connector(type);

CREATE TABLE IF NOT EXISTS sys_user_message (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    user_id         INTEGER      NOT NULL,
    tenant_id       INTEGER,
    category        TEXT         NOT NULL DEFAULT 'operation',
    title           TEXT         NOT NULL,
    content         TEXT,
    read_flag       INTEGER      DEFAULT 0,
    sender_id       INTEGER,
    sender_name     TEXT,
    biz_type        TEXT,
    biz_id          TEXT,
    link_url        TEXT,
    create_time     TEXT         DEFAULT (datetime('now')),
    read_time       TEXT,
    del_flag        INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_sys_user_message_user ON sys_user_message(user_id, read_flag);
CREATE INDEX IF NOT EXISTS idx_sys_user_message_time ON sys_user_message(create_time);

CREATE TABLE IF NOT EXISTS sys_notify_channel (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    channel         TEXT         NOT NULL UNIQUE,
    enabled         INTEGER      DEFAULT 0,
    config_json     TEXT,
    remark          TEXT,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

INSERT OR IGNORE INTO sys_tenant (id, code, name, status, remark)
VALUES (1, 'default', '默认租户', 1, '系统内置默认租户');
