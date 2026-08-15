-- API Test module schema (SQLite)

CREATE TABLE IF NOT EXISTS api_group (
    id          INTEGER      NOT NULL PRIMARY KEY,
    project_id  INTEGER      NOT NULL,
    parent_id   INTEGER,
    name        TEXT         NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    description TEXT,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    create_by   INTEGER      NOT NULL,
    update_by   INTEGER,
    create_time TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition (
    id           INTEGER      NOT NULL PRIMARY KEY,
    project_id   INTEGER      NOT NULL,
    group_id     INTEGER,
    name         TEXT         NOT NULL,
    path         TEXT         NOT NULL,
    method       TEXT         NOT NULL,
    status       TEXT         NOT NULL DEFAULT 'DRAFT',
    version      TEXT,
    tags         TEXT,
    description  TEXT,
    protocol     TEXT         DEFAULT 'HTTP',
    host         TEXT,
    content_type TEXT,
    deleted      INTEGER      NOT NULL DEFAULT 0,
    create_by    INTEGER      NOT NULL,
    update_by    INTEGER,
    create_time  TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time  TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition_param (
    id            INTEGER      NOT NULL PRIMARY KEY,
    definition_id INTEGER      NOT NULL,
    param_type    TEXT         NOT NULL,
    name          TEXT         NOT NULL,
    data_type     TEXT         DEFAULT 'string',
    required      INTEGER      DEFAULT 0,
    default_value TEXT,
    description   TEXT,
    parent_id     INTEGER,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    example       TEXT,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_by     INTEGER      NOT NULL,
    update_by     INTEGER,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition_response (
    id            INTEGER      NOT NULL PRIMARY KEY,
    definition_id INTEGER      NOT NULL,
    status_code   INTEGER      NOT NULL,
    content_type  TEXT,
    description   TEXT,
    body_schema   TEXT,
    body_example  TEXT,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_by     INTEGER      NOT NULL,
    update_by     INTEGER,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition_version (
    id                   INTEGER      NOT NULL PRIMARY KEY,
    definition_id        INTEGER      NOT NULL,
    version              TEXT         NOT NULL,
    change_log           TEXT,
    snapshot_name        TEXT,
    snapshot_path        TEXT,
    snapshot_method      TEXT,
    snapshot_params      TEXT,
    snapshot_responses   TEXT,
    snapshot_description TEXT,
    deleted              INTEGER      NOT NULL DEFAULT 0,
    create_by            INTEGER      NOT NULL,
    create_time          TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition_script (
    id            INTEGER      NOT NULL PRIMARY KEY,
    definition_id INTEGER      NOT NULL,
    script_type   TEXT         NOT NULL,
    content       TEXT,
    enabled       INTEGER      NOT NULL DEFAULT 1,
    description   TEXT,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_by     INTEGER      NOT NULL,
    update_by     INTEGER,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition_assertion (
    id             INTEGER      NOT NULL PRIMARY KEY,
    definition_id  INTEGER      NOT NULL,
    name           TEXT,
    source         TEXT         NOT NULL,
    compare_type   TEXT         NOT NULL,
    expression     TEXT,
    expected_value TEXT,
    enabled        INTEGER      NOT NULL DEFAULT 1,
    deleted        INTEGER      NOT NULL DEFAULT 0,
    create_by      INTEGER      NOT NULL,
    update_by      INTEGER,
    create_time    TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time    TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_definition_extract (
    id            INTEGER      NOT NULL PRIMARY KEY,
    definition_id INTEGER      NOT NULL,
    variable_name TEXT         NOT NULL,
    expression    TEXT         NOT NULL,
    source        TEXT         NOT NULL,
    enabled       INTEGER      NOT NULL DEFAULT 1,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_by     INTEGER      NOT NULL,
    update_by     INTEGER,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_environment (
    id          INTEGER      NOT NULL PRIMARY KEY,
    project_id  INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    description TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    create_by   INTEGER      NOT NULL,
    update_by   INTEGER,
    create_time TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_environment_variable (
    id             INTEGER      NOT NULL PRIMARY KEY,
    environment_id INTEGER      NOT NULL,
    name           TEXT         NOT NULL,
    value          TEXT,
    description    TEXT,
    is_secret      INTEGER      NOT NULL DEFAULT 0,
    sort_order     INTEGER      NOT NULL DEFAULT 0,
    deleted        INTEGER      NOT NULL DEFAULT 0,
    create_by      INTEGER      NOT NULL,
    update_by      INTEGER,
    create_time    TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time    TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_debug_history (
    id                    INTEGER      NOT NULL PRIMARY KEY,
    project_id            INTEGER      NOT NULL,
    definition_id         INTEGER,
    environment_id        INTEGER,
    name                  TEXT,
    request_url           TEXT,
    request_method        TEXT,
    request_headers       TEXT,
    request_query         TEXT,
    request_body          TEXT,
    request_content_type  TEXT,
    response_status_code  INTEGER,
    response_headers      TEXT,
    response_body         TEXT,
    response_content_type TEXT,
    response_size         INTEGER,
    duration_ms           INTEGER      NOT NULL DEFAULT 0,
    status                TEXT         NOT NULL DEFAULT 'SUCCESS',
    error_message         TEXT,
    pre_request_logs      TEXT,
    post_response_logs    TEXT,
    assertion_results     TEXT,
    all_assertions_passed INTEGER,
    extracted_variables   TEXT,
    deleted               INTEGER      NOT NULL DEFAULT 0,
    create_by             INTEGER      NOT NULL,
    create_time           TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_collection (
    id          INTEGER      NOT NULL PRIMARY KEY,
    project_id  INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    description TEXT,
    sort_order  INTEGER      NOT NULL DEFAULT 0,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    create_by   INTEGER      NOT NULL,
    update_by   INTEGER,
    create_time TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_collection_folder (
    id            INTEGER      NOT NULL PRIMARY KEY,
    collection_id INTEGER      NOT NULL,
    parent_id     INTEGER,
    name          TEXT         NOT NULL,
    description   TEXT,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_by     INTEGER      NOT NULL,
    update_by     INTEGER,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_collection_item (
    id            INTEGER      NOT NULL PRIMARY KEY,
    collection_id INTEGER      NOT NULL,
    folder_id     INTEGER,
    definition_id INTEGER      NOT NULL,
    name          TEXT,
    description   TEXT,
    enabled       INTEGER      NOT NULL DEFAULT 1,
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_by     INTEGER      NOT NULL,
    update_by     INTEGER,
    create_time   TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_collection_run (
    id             INTEGER      NOT NULL PRIMARY KEY,
    collection_id  INTEGER      NOT NULL,
    project_id     INTEGER      NOT NULL,
    environment_id INTEGER,
    name           TEXT,
    status         TEXT         NOT NULL DEFAULT 'RUNNING',
    total_count    INTEGER      NOT NULL DEFAULT 0,
    passed_count   INTEGER      NOT NULL DEFAULT 0,
    failed_count   INTEGER      NOT NULL DEFAULT 0,
    error_count    INTEGER      NOT NULL DEFAULT 0,
    duration_ms    INTEGER      NOT NULL DEFAULT 0,
    trigger_mode   TEXT         NOT NULL DEFAULT 'MANUAL',
    deleted        INTEGER      NOT NULL DEFAULT 0,
    create_by      INTEGER      NOT NULL,
    create_time    TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS api_collection_run_item (
    id                    INTEGER      NOT NULL PRIMARY KEY,
    run_id                INTEGER      NOT NULL,
    collection_item_id    INTEGER,
    definition_id         INTEGER,
    name                  TEXT,
    request_url           TEXT,
    request_method        TEXT,
    request_headers       TEXT,
    request_body          TEXT,
    response_status_code  INTEGER,
    response_headers      TEXT,
    response_body         TEXT,
    response_size         INTEGER,
    duration_ms           INTEGER      NOT NULL DEFAULT 0,
    status                TEXT         NOT NULL DEFAULT 'PENDING',
    error_message         TEXT,
    assertion_results     TEXT,
    all_assertions_passed INTEGER,
    extracted_variables   TEXT,
    sort_order            INTEGER      NOT NULL DEFAULT 0,
    deleted               INTEGER      NOT NULL DEFAULT 0,
    create_time           TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_group_project ON api_group(project_id, parent_id);
CREATE INDEX IF NOT EXISTS idx_group_sort ON api_group(project_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_def_project ON api_definition(project_id);
CREATE INDEX IF NOT EXISTS idx_def_group ON api_definition(group_id);
CREATE INDEX IF NOT EXISTS idx_def_path_method ON api_definition(path, method);
CREATE INDEX IF NOT EXISTS idx_def_status ON api_definition(status);
CREATE INDEX IF NOT EXISTS idx_param_definition ON api_definition_param(definition_id);
CREATE INDEX IF NOT EXISTS idx_response_definition ON api_definition_response(definition_id);
CREATE INDEX IF NOT EXISTS idx_version_definition ON api_definition_version(definition_id);
CREATE INDEX IF NOT EXISTS idx_script_definition ON api_definition_script(definition_id);
CREATE INDEX IF NOT EXISTS idx_assertion_definition ON api_definition_assertion(definition_id);
CREATE INDEX IF NOT EXISTS idx_extract_definition ON api_definition_extract(definition_id);
CREATE INDEX IF NOT EXISTS idx_env_project ON api_environment(project_id);
CREATE INDEX IF NOT EXISTS idx_env_var_env ON api_environment_variable(environment_id);
CREATE INDEX IF NOT EXISTS idx_debug_definition ON api_debug_history(definition_id);
CREATE INDEX IF NOT EXISTS idx_debug_project ON api_debug_history(project_id);
CREATE INDEX IF NOT EXISTS idx_collection_project ON api_collection(project_id);
CREATE INDEX IF NOT EXISTS idx_collection_folder ON api_collection_folder(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_item_collection ON api_collection_item(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_run_collection ON api_collection_run(collection_id);
CREATE INDEX IF NOT EXISTS idx_collection_run_item_run ON api_collection_run_item(run_id);
