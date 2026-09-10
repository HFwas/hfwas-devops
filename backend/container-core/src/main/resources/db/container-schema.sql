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