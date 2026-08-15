-- ============================================
-- 接口测试平台 — SQLite 内存数据库初始化脚本
-- 用于单元测试环境
-- ============================================

-- 启用外键约束
PRAGMA foreign_keys = ON;

-- 清空已有表（按依赖顺序删除）
DROP TABLE IF EXISTS api_collection_run_item;
DROP TABLE IF EXISTS api_collection_run;
DROP TABLE IF EXISTS api_collection_item;
DROP TABLE IF EXISTS api_collection_folder;
DROP TABLE IF EXISTS api_collection;
DROP TABLE IF EXISTS api_debug_history;
DROP TABLE IF EXISTS api_environment_variable;
DROP TABLE IF EXISTS api_environment;
DROP TABLE IF EXISTS api_definition_version;
DROP TABLE IF EXISTS api_definition_response;
DROP TABLE IF EXISTS api_definition_param;
DROP TABLE IF EXISTS api_definition_script;
DROP TABLE IF EXISTS api_definition_extract;
DROP TABLE IF EXISTS api_definition_assertion;
DROP TABLE IF EXISTS api_definition;
DROP TABLE IF EXISTS api_group;

-- ============================================
-- Phase1: 接口管理
-- ============================================

-- 分组表
CREATE TABLE api_group (
    id          BIGINT       NOT NULL PRIMARY KEY,
    project_id  BIGINT       NOT NULL,
    parent_id   BIGINT       NULL,
    name        VARCHAR(100) NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    description VARCHAR(500) NULL,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    create_by  BIGINT       NOT NULL,
    update_by  BIGINT       NULL,
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 接口定义表
CREATE TABLE api_definition (
    id              BIGINT        NOT NULL PRIMARY KEY,
    project_id      BIGINT        NOT NULL,
    group_id        BIGINT        NULL,
    name            VARCHAR(200)  NOT NULL,
    path            VARCHAR(500)  NOT NULL,
    method          VARCHAR(10)   NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',
    version         VARCHAR(20)   NULL,
    tags            TEXT          NULL,
    description     TEXT          NULL,
    protocol        VARCHAR(10)   NULL DEFAULT 'HTTP',
    host            VARCHAR(500)  NULL,
    content_type    VARCHAR(100)  NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 接口参数表
CREATE TABLE api_definition_param (
    id              BIGINT        NOT NULL PRIMARY KEY,
    definition_id   BIGINT        NOT NULL,
    param_type      VARCHAR(10)   NOT NULL,
    name            VARCHAR(100)  NOT NULL,
    data_type       VARCHAR(20)   NULL DEFAULT 'string',
    required        TINYINT       NULL DEFAULT 0,
    default_value   TEXT          NULL,
    description     TEXT          NULL,
    parent_id       BIGINT        NULL,
    sort_order      INT           NOT NULL DEFAULT 0,
    example         TEXT          NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 接口响应定义表
CREATE TABLE api_definition_response (
    id              BIGINT        NOT NULL PRIMARY KEY,
    definition_id   BIGINT        NOT NULL,
    status_code     INT           NOT NULL,
    content_type    VARCHAR(100)  NULL,
    description     TEXT          NULL,
    body_schema     TEXT          NULL,
    body_example    TEXT          NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 接口版本表
CREATE TABLE api_definition_version (
    id                  BIGINT        NOT NULL PRIMARY KEY,
    definition_id       BIGINT        NOT NULL,
    version             VARCHAR(20)   NOT NULL,
    change_log          VARCHAR(500)  NULL,
    snapshot_name       VARCHAR(200)  NULL,
    snapshot_path       VARCHAR(500)  NULL,
    snapshot_method     VARCHAR(10)   NULL,
    snapshot_params     JSON          NULL,
    snapshot_responses  JSON          NULL,
    snapshot_description TEXT         NULL,
    deleted             TINYINT       NOT NULL DEFAULT 0,
    create_by           BIGINT        NOT NULL,
    create_time         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- Phase2: 脚本/断言/提取/环境/调试历史
-- ============================================

-- 脚本表
CREATE TABLE api_definition_script (
    id              BIGINT        NOT NULL PRIMARY KEY,
    definition_id   BIGINT        NOT NULL,
    script_type     VARCHAR(20)   NOT NULL,
    content         TEXT          NULL,
    enabled         TINYINT       NOT NULL DEFAULT 1,
    description     TEXT          NULL,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 断言表
CREATE TABLE api_definition_assertion (
    id              BIGINT        NOT NULL PRIMARY KEY,
    definition_id   BIGINT        NOT NULL,
    name            VARCHAR(200)  NULL,
    source          VARCHAR(30)   NOT NULL,
    compare_type    VARCHAR(20)   NOT NULL,
    expression      TEXT          NULL,
    expected_value  TEXT          NULL,
    enabled         TINYINT       NOT NULL DEFAULT 1,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 变量提取表
CREATE TABLE api_definition_extract (
    id              BIGINT        NOT NULL PRIMARY KEY,
    definition_id   BIGINT        NOT NULL,
    variable_name   VARCHAR(100)  NOT NULL,
    expression      TEXT          NOT NULL,
    source          VARCHAR(30)   NOT NULL,
    enabled         TINYINT       NOT NULL DEFAULT 1,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 环境表
CREATE TABLE api_environment (
    id              BIGINT        NOT NULL PRIMARY KEY,
    project_id      BIGINT        NOT NULL,
    name            VARCHAR(200)  NOT NULL,
    description     TEXT          NULL,
    sort_order      INT           NOT NULL DEFAULT 0,
    deleted         TINYINT       NOT NULL DEFAULT 0,
    create_by      BIGINT        NOT NULL,
    update_by      BIGINT        NULL,
    create_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 环境变量表
CREATE TABLE api_environment_variable (
    id              BIGINT          NOT NULL PRIMARY KEY,
    environment_id  BIGINT          NOT NULL,
    name            VARCHAR(200)    NOT NULL,
    value           TEXT            NULL,
    description     TEXT            NULL,
    is_secret       TINYINT         NOT NULL DEFAULT 0,
    sort_order      INT             NOT NULL DEFAULT 0,
    deleted         TINYINT         NOT NULL DEFAULT 0,
    create_by      BIGINT          NOT NULL,
    update_by      BIGINT          NULL,
    create_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 调试历史表
CREATE TABLE api_debug_history (
    id                  BIGINT          NOT NULL PRIMARY KEY,
    project_id          BIGINT          NOT NULL,
    definition_id       BIGINT          NULL,
    environment_id      BIGINT          NULL,
    name                VARCHAR(200)    NULL,
    request_url         TEXT            NULL,
    request_method      VARCHAR(10)     NULL,
    request_headers     TEXT            NULL,
    request_query       TEXT            NULL,
    request_body        TEXT            NULL,
    request_content_type VARCHAR(100)   NULL,
    response_status_code INT            NULL,
    response_headers    TEXT            NULL,
    response_body       TEXT            NULL,
    response_content_type VARCHAR(100)  NULL,
    response_size       BIGINT          NULL,
    duration_ms         BIGINT          NOT NULL DEFAULT 0,
    status              VARCHAR(20)     NOT NULL DEFAULT 'SUCCESS',
    error_message       TEXT            NULL,
    pre_request_logs    TEXT            NULL,
    post_response_logs  TEXT            NULL,
    assertion_results   TEXT            NULL,
    all_assertions_passed TINYINT       NULL,
    extracted_variables TEXT            NULL,
    deleted             TINYINT         NOT NULL DEFAULT 0,
    create_by          BIGINT          NOT NULL,
    create_time         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- Phase3: 集合管理
-- ============================================

CREATE TABLE api_collection (
    id            BIGINT       NOT NULL PRIMARY KEY,
    project_id    BIGINT       NOT NULL,
    name          VARCHAR(200) NOT NULL,
    description   TEXT         NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_by    BIGINT       NOT NULL,
    update_by    BIGINT       NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_collection_folder (
    id            BIGINT       NOT NULL PRIMARY KEY,
    collection_id BIGINT       NOT NULL,
    parent_id     BIGINT       NULL,
    name          VARCHAR(200) NOT NULL,
    description   VARCHAR(500) NULL,
    sort_order    INT          NOT NULL DEFAULT 0,
    deleted       TINYINT      NOT NULL DEFAULT 0,
    create_by    BIGINT       NOT NULL,
    update_by    BIGINT       NULL,
    create_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_collection_item (
    id              BIGINT       NOT NULL PRIMARY KEY,
    collection_id   BIGINT       NOT NULL,
    folder_id       BIGINT       NULL,
    definition_id   BIGINT       NOT NULL,
    name            VARCHAR(200) NULL,
    description     VARCHAR(500) NULL,
    enabled         TINYINT      NOT NULL DEFAULT 1,
    sort_order      INT          NOT NULL DEFAULT 0,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_by      BIGINT       NOT NULL,
    update_by      BIGINT       NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_collection_run (
    id              BIGINT       NOT NULL PRIMARY KEY,
    collection_id   BIGINT       NOT NULL,
    project_id      BIGINT       NOT NULL,
    environment_id  BIGINT       NULL,
    name            VARCHAR(200) NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING',
    total_count     INT          NOT NULL DEFAULT 0,
    passed_count    INT          NOT NULL DEFAULT 0,
    failed_count    INT          NOT NULL DEFAULT 0,
    error_count     INT          NOT NULL DEFAULT 0,
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    trigger_mode    VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_by      BIGINT       NOT NULL,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE api_collection_run_item (
    id              BIGINT       NOT NULL PRIMARY KEY,
    run_id          BIGINT       NOT NULL,
    collection_item_id BIGINT    NULL,
    definition_id   BIGINT       NULL,
    name            VARCHAR(200) NULL,
    request_url     VARCHAR(2000) NULL,
    request_method  VARCHAR(10)  NULL,
    request_headers TEXT         NULL,
    request_body    TEXT         NULL,
    response_status_code INT     NULL,
    response_headers TEXT        NULL,
    response_body   TEXT         NULL,
    response_size   BIGINT       NULL,
    duration_ms     BIGINT       NOT NULL DEFAULT 0,
    status          VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    error_message   TEXT         NULL,
    assertion_results TEXT       NULL,
    all_assertions_passed TINYINT NULL,
    extracted_variables TEXT     NULL,
    sort_order      INT          NOT NULL DEFAULT 0,
    deleted         TINYINT      NOT NULL DEFAULT 0,
    create_time     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================
-- 索引
-- ============================================
CREATE INDEX idx_group_project ON api_group(project_id, parent_id);
CREATE INDEX idx_group_sort ON api_group(project_id, sort_order);
CREATE INDEX idx_def_project ON api_definition(project_id);
CREATE INDEX idx_def_group ON api_definition(group_id);
CREATE INDEX idx_def_path_method ON api_definition(path, method);
CREATE INDEX idx_def_status ON api_definition(status);
CREATE INDEX idx_param_definition ON api_definition_param(definition_id);
CREATE INDEX idx_response_definition ON api_definition_response(definition_id);
CREATE INDEX idx_version_definition ON api_definition_version(definition_id);
CREATE INDEX idx_script_definition ON api_definition_script(definition_id);
CREATE INDEX idx_assertion_definition ON api_definition_assertion(definition_id);
CREATE INDEX idx_extract_definition ON api_definition_extract(definition_id);
CREATE INDEX idx_env_project ON api_environment(project_id);
CREATE INDEX idx_env_var_env ON api_environment_variable(environment_id);
CREATE INDEX idx_debug_definition ON api_debug_history(definition_id);
CREATE INDEX idx_debug_project ON api_debug_history(project_id);
CREATE INDEX idx_collection_project ON api_collection(project_id);
CREATE INDEX idx_collection_folder ON api_collection_folder(collection_id);
CREATE INDEX idx_collection_item_collection ON api_collection_item(collection_id);
CREATE INDEX idx_collection_run_collection ON api_collection_run(collection_id);
CREATE INDEX idx_collection_run_item_run ON api_collection_run_item(run_id);