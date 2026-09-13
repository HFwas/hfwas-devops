-- 迁移：pipeline_run 追加 runtime_params 列
-- 仅对旧库生效，新库 CREATE TABLE 已包含
ALTER TABLE pipeline_run ADD COLUMN runtime_params TEXT NOT NULL DEFAULT '';

-- 运行参数取值方式：fixed 写死 / runtime 运行时选择（旧表，定义已迁到任务市场）
ALTER TABLE pipeline_job_param ADD COLUMN value_mode TEXT NOT NULL DEFAULT 'runtime';

-- 流水线任务：绑定任务市场预置变量（写死 / 运行时选择）
ALTER TABLE pipeline_job ADD COLUMN param_bindings TEXT NOT NULL DEFAULT '{}';

CREATE TABLE IF NOT EXISTS pipeline_task_kind_param (
    id                  INTEGER      NOT NULL PRIMARY KEY,
    kind_value          TEXT         NOT NULL,
    param_key           TEXT         NOT NULL,
    param_label         TEXT         NOT NULL,
    param_type          TEXT         NOT NULL DEFAULT 'input',
    default_value       TEXT         NOT NULL DEFAULT '',
    required            INTEGER      NOT NULL DEFAULT 0,
    sort_order          INTEGER      NOT NULL DEFAULT 0,
    options_json        TEXT         NOT NULL DEFAULT '[]',
    api_url             TEXT         NOT NULL DEFAULT '',
    api_method          TEXT         NOT NULL DEFAULT 'GET',
    api_headers_json    TEXT         NOT NULL DEFAULT '{}',
    api_response_path   TEXT         NOT NULL DEFAULT '',
    placeholder         TEXT         NOT NULL DEFAULT '',
    deleted             INTEGER      NOT NULL DEFAULT 0,
    create_by           INTEGER,
    update_by           INTEGER,
    create_time         TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time         TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_task_kind_param ON pipeline_task_kind_param (kind_value, deleted);

INSERT OR IGNORE INTO pipeline_task_kind_param (
    id, kind_value, param_key, param_label, param_type, default_value, required, sort_order, placeholder
) VALUES (
    1, 'CLONE', 'GIT_REF', '代码分支', 'input', 'main', 1, 0, 'main / develop / commit SHA'
);
