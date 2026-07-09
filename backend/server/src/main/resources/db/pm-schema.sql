-- PM module schema (SQLite)

CREATE TABLE IF NOT EXISTS pm_project (
    id              INTEGER      NOT NULL PRIMARY KEY,
    tenant_id       INTEGER      NOT NULL DEFAULT 1,
    code            TEXT         NOT NULL,
    name            TEXT         NOT NULL,
    description     TEXT,
    settings        TEXT,
    create_by       INTEGER,
    update_by       INTEGER,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_pm_project_tenant_code ON pm_project(tenant_id, code);
CREATE INDEX IF NOT EXISTS idx_pm_project_tenant ON pm_project(tenant_id);

CREATE TABLE IF NOT EXISTS pm_project_member (
    id          INTEGER      NOT NULL PRIMARY KEY,
    project_id  INTEGER      NOT NULL,
    user_id     INTEGER      NOT NULL,
    role        TEXT         NOT NULL DEFAULT 'member',
    create_time TEXT         DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_pm_project_member ON pm_project_member(project_id, user_id);

CREATE TABLE IF NOT EXISTS pm_work_item_type (
    id          INTEGER      NOT NULL PRIMARY KEY,
    code        TEXT         NOT NULL UNIQUE,
    name        TEXT         NOT NULL,
    icon        TEXT,
    sort_order  INTEGER      DEFAULT 0,
    enabled     INTEGER      DEFAULT 1
);

CREATE TABLE IF NOT EXISTS pm_work_item (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER      NOT NULL,
    item_no         INTEGER      NOT NULL,
    type_code       TEXT         NOT NULL,
    title           TEXT         NOT NULL,
    description     TEXT,
    status          TEXT         NOT NULL DEFAULT 'open',
    priority        TEXT         DEFAULT 'medium',
    assignee_id     INTEGER,
    reporter_id     INTEGER,
    parent_id       INTEGER,
    module_id       INTEGER,
    sprint_id       INTEGER,
    custom_fields   TEXT,
    create_by       INTEGER,
    update_by       INTEGER,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0,
    UNIQUE(project_id, item_no)
);

CREATE TABLE IF NOT EXISTS pm_project_sequence (
    project_id    INTEGER      NOT NULL PRIMARY KEY,
    next_item_no  INTEGER      NOT NULL DEFAULT 1
);

CREATE INDEX IF NOT EXISTS idx_pm_work_item_project ON pm_work_item(project_id, type_code);
CREATE INDEX IF NOT EXISTS idx_pm_work_item_status ON pm_work_item(status);
CREATE INDEX IF NOT EXISTS idx_pm_work_item_module ON pm_work_item(module_id);

CREATE TABLE IF NOT EXISTS pm_project_module (
    id              INTEGER      PRIMARY KEY AUTOINCREMENT,
    project_id      INTEGER      NOT NULL,
    parent_id       INTEGER,
    name            TEXT         NOT NULL,
    description     TEXT,
    sort_order      INTEGER      DEFAULT 0,
    enabled         INTEGER      DEFAULT 1,
    create_time     TEXT         DEFAULT (datetime('now')),
    update_time     TEXT         DEFAULT (datetime('now')),
    del_flag        INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pm_project_module ON pm_project_module(project_id, parent_id);

CREATE TABLE IF NOT EXISTS pm_field_definition (
    id                INTEGER      NOT NULL PRIMARY KEY,
    project_id        INTEGER,
    scope             TEXT         NOT NULL DEFAULT 'project',
    field_key         TEXT         NOT NULL,
    field_name        TEXT         NOT NULL,
    field_type        TEXT         NOT NULL,
    config            TEXT,
    applicable_types  TEXT,
    required_flag     INTEGER      DEFAULT 0,
    sort_order        INTEGER      DEFAULT 0,
    system_flag       INTEGER      DEFAULT 0,
    create_time       TEXT         DEFAULT (datetime('now')),
    update_time       TEXT         DEFAULT (datetime('now')),
    del_flag          INTEGER      DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pm_field_option (
    id           INTEGER      NOT NULL PRIMARY KEY,
    field_id     INTEGER      NOT NULL,
    option_key   TEXT         NOT NULL,
    option_label TEXT         NOT NULL,
    sort_order   INTEGER      DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pm_work_item_link (
    id          INTEGER      NOT NULL PRIMARY KEY,
    source_id   INTEGER      NOT NULL,
    target_id   INTEGER      NOT NULL,
    link_type   TEXT         NOT NULL,
    create_time TEXT         DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pm_work_item_comment (
    id            INTEGER      NOT NULL PRIMARY KEY,
    work_item_id  INTEGER      NOT NULL,
    parent_id     INTEGER,
    content       TEXT         NOT NULL,
    author_name   TEXT         NOT NULL,
    create_by     INTEGER,
    create_time   TEXT         DEFAULT (datetime('now')),
    del_flag      INTEGER      DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_pm_work_item_comment ON pm_work_item_comment(work_item_id, create_time);

CREATE TABLE IF NOT EXISTS pm_work_item_activity (
    id              INTEGER      NOT NULL PRIMARY KEY,
    work_item_id    INTEGER      NOT NULL,
    batch_id        TEXT,
    event_type      TEXT         NOT NULL,
    actor_id        INTEGER,
    actor_name      TEXT         NOT NULL,
    field_key       TEXT,
    field_name      TEXT,
    field_type      TEXT,
    old_value       TEXT,
    new_value       TEXT,
    old_label       TEXT,
    new_label       TEXT,
    extra_json      TEXT,
    create_time     TEXT         DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_pm_work_item_activity ON pm_work_item_activity(work_item_id, create_time);

CREATE TABLE IF NOT EXISTS pm_status_definition (
    id          INTEGER      NOT NULL PRIMARY KEY,
    project_id  INTEGER,
    type_code   TEXT         NOT NULL,
    status_code TEXT         NOT NULL,
    status_name TEXT         NOT NULL,
    sort_order  INTEGER      DEFAULT 0,
    is_initial  INTEGER      DEFAULT 0,
    is_final    INTEGER      DEFAULT 0,
    transitions TEXT
);

CREATE TABLE IF NOT EXISTS pm_saved_view (
    id          INTEGER      NOT NULL PRIMARY KEY,
    project_id  INTEGER      NOT NULL,
    user_id     INTEGER,
    name        TEXT         NOT NULL,
    type_code   TEXT,
    query_spec  TEXT         NOT NULL,
    columns     TEXT,
    is_default  INTEGER      DEFAULT 0,
    create_time TEXT         DEFAULT (datetime('now')),
    update_time TEXT         DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pm_type_field_layout (
    id            INTEGER      NOT NULL PRIMARY KEY,
    project_id    INTEGER      NOT NULL,
    type_code     TEXT         NOT NULL,
    layout_config TEXT         NOT NULL,
    create_time   TEXT         DEFAULT (datetime('now')),
    update_time   TEXT         DEFAULT (datetime('now')),
    UNIQUE(project_id, type_code)
);

INSERT OR IGNORE INTO pm_work_item_type (id, code, name, sort_order) VALUES
(1, 'requirement', '需求', 1),
(2, 'task', '任务', 2),
(3, 'bug', '缺陷', 3),
(4, 'test_case', '测试用例', 4);

INSERT OR IGNORE INTO pm_status_definition (id, project_id, type_code, status_code, status_name, sort_order, is_initial, is_final, transitions) VALUES
(1, NULL, 'task', 'open', '待处理', 1, 1, 0, '[{"id":"task-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"task-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(2, NULL, 'task', 'in_progress', '进行中', 2, 0, 0, '[{"id":"task-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"task-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
(3, NULL, 'task', 'done', '已完成', 3, 0, 0, '[{"id":"task-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(4, NULL, 'task', 'closed', '已关闭', 4, 0, 1, '[]'),
(5, NULL, 'task', '__any__', '任何状态', 99, 0, 0, '[]'),
(6, NULL, 'bug', 'open', '待处理', 1, 1, 0, '[{"id":"bug-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"bug-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(7, NULL, 'bug', 'in_progress', '修复中', 2, 0, 0, '[{"id":"bug-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]}]'),
(8, NULL, 'bug', 'done', '已修复', 3, 0, 0, '[{"id":"bug-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(9, NULL, 'bug', 'closed', '已关闭', 4, 0, 1, '[]'),
(10, NULL, 'bug', '__any__', '任何状态', 99, 0, 0, '[]'),
(11, NULL, 'requirement', 'open', '待处理', 1, 1, 0, '[{"id":"requirement-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"requirement-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(12, NULL, 'requirement', 'in_progress', '进行中', 2, 0, 0, '[{"id":"requirement-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"requirement-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
(13, NULL, 'requirement', 'done', '已完成', 3, 0, 0, '[{"id":"requirement-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(14, NULL, 'requirement', 'closed', '已关闭', 4, 0, 1, '[]'),
(15, NULL, 'requirement', '__any__', '任何状态', 99, 0, 0, '[]'),
(16, NULL, 'test_case', 'open', '待执行', 1, 1, 0, '[{"id":"test_case-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"test_case-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(17, NULL, 'test_case', 'in_progress', '执行中', 2, 0, 0, '[{"id":"test_case-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"test_case-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
(18, NULL, 'test_case', 'done', '已通过', 3, 0, 0, '[{"id":"test_case-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(19, NULL, 'test_case', 'closed', '已关闭', 4, 0, 1, '[]'),
(20, NULL, 'test_case', '__any__', '任何状态', 99, 0, 0, '[]');

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
