-- Helm chart 上传制品（Harbor OCI）
-- P0：仓库登记 + 每次上传一条 artifact。凭据来自 helm.chart.oci 配置，不写入本表。
-- credential_id 预留给后续绑定流水线/镜像凭据，当前为空。

CREATE TABLE IF NOT EXISTS helm_chart_repository (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    tenant_id     INTEGER NOT NULL,
    name          TEXT NOT NULL,
    type          TEXT NOT NULL DEFAULT 'oci',
    url           TEXT NOT NULL,
    insecure      INTEGER NOT NULL DEFAULT 0,
    credential_id TEXT,
    created_at    TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(tenant_id, name)
);

CREATE TABLE IF NOT EXISTS helm_chart_artifact (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    repository_id   INTEGER NOT NULL,
    tenant_id       INTEGER NOT NULL,
    chart_name      TEXT NOT NULL,
    version         TEXT NOT NULL,
    digest          TEXT NOT NULL DEFAULT '',
    size_bytes      INTEGER NOT NULL,
    chart_ref       TEXT NOT NULL,
    description     TEXT NOT NULL DEFAULT '',
    app_version     TEXT NOT NULL DEFAULT '',
    uploaded_by     INTEGER,
    created_at      TEXT NOT NULL DEFAULT (datetime('now')),
    UNIQUE(repository_id, chart_name, version),
    FOREIGN KEY (repository_id) REFERENCES helm_chart_repository(id)
);

CREATE INDEX IF NOT EXISTS idx_helm_chart_artifact_tenant_name
    ON helm_chart_artifact(tenant_id, chart_name);
