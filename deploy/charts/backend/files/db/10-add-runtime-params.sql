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
) VALUES
-- CLONE
(1,  'CLONE', 'GIT_REF',            '代码分支',        'input', 'main',                                 1, 0,  'main / develop / commit SHA'),
(2,  'CLONE', 'GIT_USERNAME',       'Git 用户名',      'input', '',                                     0, 1,  ''),
(3,  'CLONE', 'GIT_PASSWORD',       'Git 密码',        'input', '',                                     0, 2,  ''),
(4,  'CLONE', 'GIT_EMBEDDED_AUTH',  'Git Token',       'input', '',                                     0, 3,  ''),
(5,  'CLONE', 'GIT_HTTP_PROXY',     'Git HTTP 代理',   'input', '',                                     0, 4,  ''),
-- IMAGE
(10, 'IMAGE', 'DEST',               '镜像推送目标',     'input', 'registry.example.com/app:tag',          1, 0,  '如 registry.example.com/app:tag'),
(11, 'IMAGE', 'IMAGE_PLATFORMS',    '构建平台',         'input', 'linux/amd64,linux/arm64',              0, 1,  '逗号分隔，如 linux/amd64,linux/arm64'),
(12, 'IMAGE', 'DOCKERFILE',         'Dockerfile 路径',  'input', 'Dockerfile',                          0, 2,  ''),
(13, 'IMAGE', 'COSIGN_PRIVATE_KEY', 'Cosign 私钥',     'input', '',                                     0, 3,  ''),
-- LINT_SONAR
(20, 'LINT_SONAR', 'SONAR_HOST_URL',    'Sonar 服务地址','input', 'https://sonar.example.com',           1, 0,  ''),
(21, 'LINT_SONAR', 'SONAR_TOKEN',       'Sonar Token',   'input', '',                                   1, 1,  ''),
(22, 'LINT_SONAR', 'SONAR_PROJECT_KEY', 'Sonar 项目 Key','input', 'app',                                 0, 2,  ''),
-- UPLOAD
(30, 'UPLOAD', 'S3_ENDPOINT',   'S3 端点',        'input', '', 1, 0, ''),
(31, 'UPLOAD', 'S3_ACCESS_KEY', 'S3 Access Key', 'input', '', 1, 1, ''),
(32, 'UPLOAD', 'S3_SECRET_KEY', 'S3 Secret Key', 'input', '', 1, 2, '');
