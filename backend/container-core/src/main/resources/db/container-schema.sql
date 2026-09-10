-- container-core schema for SQLite (Phase 1)
-- cluster_info: 多集群纳管

CREATE TABLE IF NOT EXISTS cluster_info (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id   INTEGER NOT NULL,
    name        TEXT NOT NULL,
    alias       TEXT DEFAULT '',
    provider    TEXT DEFAULT 'self-hosted',
    version     TEXT DEFAULT '',
    kubeconfig  TEXT NOT NULL,       -- AES-256-GCM encrypted
    mode        TEXT DEFAULT 'proxy',-- Phase 1 only 'proxy'
    status      TEXT DEFAULT 'Unknown',  -- Connected / Degraded / Disconnected / Unknown
    labels      TEXT DEFAULT '{}',   -- JSON
    created_at  TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at  TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(tenant_id, name)
);

-- registry_info: 镜像仓库注册信息
CREATE TABLE IF NOT EXISTS registry_info (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id           INTEGER NOT NULL,
    name                TEXT NOT NULL,
    alias               TEXT DEFAULT '',
    type                TEXT NOT NULL DEFAULT 'harbor',  -- harbor / registry_v2
    url                 TEXT NOT NULL,
    insecure            INTEGER NOT NULL DEFAULT 0,
    credential_username TEXT DEFAULT '',
    credential_password TEXT DEFAULT '',       -- AES-256-GCM encrypted
    source              TEXT NOT NULL DEFAULT 'manual',  -- manual / builtin
    cluster_id          INTEGER,
    status              TEXT DEFAULT 'Unknown',  -- Connected / Error / Unknown
    last_error          TEXT DEFAULT '',
    labels              TEXT DEFAULT '{}',
    created_at          TEXT NOT NULL DEFAULT (datetime('now')),
    updated_at          TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(tenant_id, name),
    FOREIGN KEY (cluster_id) REFERENCES cluster_info(id) ON DELETE SET NULL
);