-- 交付运维平台 — 数据库初始化
-- SQLite

CREATE TABLE IF NOT EXISTS delivery_cluster (
    id                INTEGER      NOT NULL PRIMARY KEY,
    name              TEXT         NOT NULL,
    server_host       TEXT,
    kubeconfig_enc    TEXT         NOT NULL,       -- AES-256-GCM 加密
    is_current        INTEGER      NOT NULL DEFAULT 0,
    status            TEXT         NOT NULL DEFAULT 'UNKNOWN',  -- UNKNOWN / UP / DOWN
    version           TEXT,                                      -- Kubernetes version
    deleted           INTEGER      NOT NULL DEFAULT 0,
    create_by         INTEGER,
    update_by         INTEGER,
    create_time       TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time       TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_name
    ON delivery_cluster (name) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_cluster_current
    ON delivery_cluster (is_current) WHERE deleted = 0 AND is_current = 1;

CREATE TABLE IF NOT EXISTS delivery_product (
    id                   INTEGER      NOT NULL PRIMARY KEY,
    product_key          TEXT         NOT NULL,
    display_name         TEXT         NOT NULL,
    package_version      TEXT         NOT NULL,
    package_dir          TEXT         NOT NULL,
    manifest_json        TEXT         NOT NULL,
    -- CloudService 关联
    cloud_service_name   TEXT,
    cloud_service_ns     TEXT,
    -- 参数快照
    params_json          TEXT         NOT NULL DEFAULT '{}',
    global_params_json   TEXT         NOT NULL DEFAULT '{}',
    -- 部署状态
    status               TEXT         NOT NULL DEFAULT 'NOT_DEPLOYED',
    -- NOT_DEPLOYED / DEPLOYING / READY / DEGRADED / FAILED / UNINSTALLING
    cluster_id           INTEGER,
    target_ns            TEXT,
    deleted              INTEGER      NOT NULL DEFAULT 0,
    create_by            INTEGER,
    update_by            INTEGER,
    create_time          TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time          TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_product_key
    ON delivery_product (product_key) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_delivery_product_deleted
    ON delivery_product (deleted);

CREATE TABLE IF NOT EXISTS delivery_deployment (
    id                       INTEGER      NOT NULL PRIMARY KEY,
    product_id               INTEGER      NOT NULL,
    cluster_id               INTEGER      NOT NULL,
    action                   TEXT         NOT NULL,    -- DEPLOY / UPGRADE / ROLLBACK / UNINSTALL
    package_version          TEXT         NOT NULL,
    params_snapshot_json     TEXT         NOT NULL,
    global_params_snapshot   TEXT         NOT NULL,
    app_name                 TEXT,
    app_ns                   TEXT,
    status                   TEXT         NOT NULL DEFAULT 'PENDING',
    -- PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED
    error_message            TEXT,
    started_at               TEXT,
    finished_at              TEXT,
    create_by                INTEGER,
    create_time              TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_delivery_deployment_product
    ON delivery_deployment (product_id, id DESC);