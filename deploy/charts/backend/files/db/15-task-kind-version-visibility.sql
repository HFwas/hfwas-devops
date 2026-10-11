-- 任务市场：当前启用版本、参数可见性、版本快照
-- 幂等：重复列由 migrate.sh / migrate.py 跳过；建表使用 IF NOT EXISTS
ALTER TABLE pipeline_task_kind ADD COLUMN version TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind_param ADD COLUMN visibility TEXT NOT NULL DEFAULT 'user';

CREATE TABLE IF NOT EXISTS pipeline_task_kind_version (
    id            INTEGER      NOT NULL PRIMARY KEY,
    kind_value    TEXT         NOT NULL,
    version       TEXT         NOT NULL,
    note          TEXT         NOT NULL DEFAULT '',
    snapshot_json TEXT         NOT NULL,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    UNIQUE (kind_value, version)
);
CREATE INDEX IF NOT EXISTS idx_task_kind_version_kind ON pipeline_task_kind_version (kind_value, create_time);
