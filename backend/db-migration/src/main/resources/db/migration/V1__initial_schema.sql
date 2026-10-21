-- MySQL 8 baseline schema for hfwas-devops
-- Generated from the SQLite module schemas; rebuild-only, no data migration.
SET NAMES utf8mb4;
SET SESSION sql_mode = CONCAT(@@sql_mode, ',NO_BACKSLASH_ESCAPES');

CREATE TABLE sys_tenant (

    id BIGINT NOT NULL AUTO_INCREMENT,
    code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(255),
    contact_phone VARCHAR(255),
    status TINYINT DEFAULT 1,
    remark MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    UNIQUE KEY uk_sys_tenant_code (code),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_user (

    id BIGINT NOT NULL AUTO_INCREMENT,
    username VARCHAR(100) COLLATE utf8mb4_bin NOT NULL,
    password VARCHAR(255),
    display_name VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    phone VARCHAR(255),
    role VARCHAR(64) NOT NULL DEFAULT 'user',
    enabled TINYINT DEFAULT 1,
    auth_source VARCHAR(255) DEFAULT 'keycloak',
    external_id VARCHAR(255) COLLATE utf8mb4_bin,
    connector_id BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    UNIQUE KEY uk_sys_user_username (username),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_tenant_member (

    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    tenant_role VARCHAR(64) NOT NULL DEFAULT 'member',
    status TINYINT DEFAULT 1,
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_login_log (

    id BIGINT NOT NULL AUTO_INCREMENT,
    kc_event_id VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    user_id BIGINT,
    kc_user_id VARCHAR(64),
    username VARCHAR(100) NOT NULL,
    display_name VARCHAR(255),
    action VARCHAR(64) NOT NULL,
    login_ip VARCHAR(255),
    user_agent MEDIUMTEXT,
    client_info MEDIUMTEXT,
    fail_reason MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_sys_login_log_kc_event_id (kc_event_id),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_oper_log (

    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT,
    username VARCHAR(100),
    display_name VARCHAR(255),
    module VARCHAR(64) NOT NULL,
    action VARCHAR(64) NOT NULL,
    biz_type VARCHAR(255),
    biz_id VARCHAR(64),
    summary MEDIUMTEXT NOT NULL,
    status VARCHAR(64) DEFAULT 'success',
    fail_reason MEDIUMTEXT,
    request_ip VARCHAR(255),
    user_agent MEDIUMTEXT,
    client_info MEDIUMTEXT,
    extra_json MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_identity_connector (

    id BIGINT NOT NULL AUTO_INCREMENT,
    name VARCHAR(128) NOT NULL,
    type VARCHAR(64) NOT NULL,
    config_json MEDIUMTEXT NOT NULL,
    enabled TINYINT DEFAULT 1,
    default_tenant_id BIGINT,
    auto_create_member TINYINT DEFAULT 1,
    last_sync_time DATETIME,
    last_sync_status VARCHAR(255),
    last_sync_message VARCHAR(255),
    last_sync_count INT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_user_message (

    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    tenant_id BIGINT,
    category VARCHAR(255) NOT NULL DEFAULT 'operation',
    title VARCHAR(255) NOT NULL,
    content MEDIUMTEXT,
    read_flag TINYINT DEFAULT 0,
    sender_id BIGINT,
    sender_name VARCHAR(255),
    biz_type VARCHAR(255),
    biz_id VARCHAR(64),
    link_url MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    read_time DATETIME,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE sys_notify_channel (

    id BIGINT NOT NULL AUTO_INCREMENT,
    channel VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    enabled TINYINT DEFAULT 0,
    config_json MEDIUMTEXT,
    remark MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    UNIQUE KEY uk_sys_notify_channel_channel (channel),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_project (

    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 1,
    code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    name VARCHAR(128) NOT NULL,
    description MEDIUMTEXT,
    settings MEDIUMTEXT,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_project_member (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(64) NOT NULL DEFAULT 'member',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_work_item_type (

    id BIGINT NOT NULL,
    code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    name VARCHAR(255) NOT NULL,
    icon VARCHAR(255),
    color VARCHAR(255),
    sort_order INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    UNIQUE KEY uk_pm_work_item_type_code (code),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_project_issue_type (

    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    type_code VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    sort_order INT DEFAULT 0,
    UNIQUE KEY uk_pm_project_issue_type_project_id_type_code (project_id, type_code),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_work_item (

    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    item_no INT NOT NULL,
    type_code VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description MEDIUMTEXT,
    status VARCHAR(64) NOT NULL DEFAULT 'open',
    priority VARCHAR(255) DEFAULT 'medium',
    assignee_id BIGINT,
    reporter_id BIGINT,
    parent_id BIGINT,
    module_id BIGINT,
    sprint_id BIGINT,
    custom_fields MEDIUMTEXT,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    UNIQUE KEY uk_pm_work_item_project_id_item_no (project_id, item_no),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_project_sequence (

    project_id BIGINT NOT NULL,
    next_item_no INT NOT NULL DEFAULT 1,
    PRIMARY KEY (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_project_module (

    id BIGINT NOT NULL AUTO_INCREMENT,
    project_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(128) NOT NULL,
    description MEDIUMTEXT,
    sort_order INT DEFAULT 0,
    enabled TINYINT DEFAULT 1,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_field_definition (

    id BIGINT NOT NULL,
    project_id BIGINT,
    scope VARCHAR(255) NOT NULL DEFAULT 'project',
    field_key VARCHAR(255) NOT NULL,
    field_name VARCHAR(255) NOT NULL,
    field_type VARCHAR(255) NOT NULL,
    config MEDIUMTEXT,
    applicable_types MEDIUMTEXT,
    required_flag TINYINT DEFAULT 0,
    sort_order INT DEFAULT 0,
    system_flag TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_field_option (

    id BIGINT NOT NULL,
    field_id BIGINT NOT NULL,
    option_key VARCHAR(255) NOT NULL,
    option_label VARCHAR(255) NOT NULL,
    sort_order INT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_work_item_link (

    id BIGINT NOT NULL,
    source_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    link_type VARCHAR(255) NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_work_item_comment (

    id BIGINT NOT NULL,
    work_item_id BIGINT NOT NULL,
    parent_id BIGINT,
    content MEDIUMTEXT NOT NULL,
    author_name VARCHAR(255) NOT NULL,
    create_by BIGINT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    del_flag TINYINT DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_work_item_activity (

    id BIGINT NOT NULL,
    work_item_id BIGINT NOT NULL,
    batch_id BIGINT,
    event_type VARCHAR(255) NOT NULL,
    actor_id BIGINT,
    actor_name VARCHAR(255) NOT NULL,
    field_key VARCHAR(128),
    field_name VARCHAR(128),
    field_type VARCHAR(255),
    old_value VARCHAR(255),
    new_value VARCHAR(255),
    old_label VARCHAR(255),
    new_label VARCHAR(255),
    extra_json MEDIUMTEXT,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_status_definition (

    id BIGINT NOT NULL,
    project_id BIGINT,
    type_code VARCHAR(255) NOT NULL,
    status_code VARCHAR(255) NOT NULL,
    status_name VARCHAR(255) NOT NULL,
    sort_order INT DEFAULT 0,
    is_initial TINYINT DEFAULT 0,
    is_final TINYINT DEFAULT 0,
    layout_x DOUBLE,
    layout_y DOUBLE,
    transitions MEDIUMTEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_saved_view (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    user_id BIGINT,
    name VARCHAR(255) NOT NULL,
    type_code VARCHAR(255),
    query_spec MEDIUMTEXT NOT NULL,
    columns MEDIUMTEXT,
    is_default TINYINT DEFAULT 0,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pm_type_field_layout (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    type_code VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    layout_config MEDIUMTEXT NOT NULL,
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pm_type_field_layout_project_id_type_code (project_id, type_code),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_group (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(128) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    description MEDIUMTEXT,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    group_id BIGINT,
    name VARCHAR(128) NOT NULL,
    path VARCHAR(512) NOT NULL,
    method VARCHAR(255) NOT NULL,
    status VARCHAR(64) NOT NULL DEFAULT 'DRAFT',
    version VARCHAR(64),
    tags VARCHAR(255),
    description MEDIUMTEXT,
    protocol VARCHAR(255) DEFAULT 'HTTP',
    host VARCHAR(255),
    content_type VARCHAR(255),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition_param (

    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    param_type VARCHAR(255) NOT NULL,
    name VARCHAR(128) NOT NULL,
    data_type VARCHAR(255) DEFAULT 'string',
    required TINYINT DEFAULT 0,
    default_value VARCHAR(255),
    description MEDIUMTEXT,
    parent_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    example VARCHAR(255),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition_response (

    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    status_code INT NOT NULL,
    content_type VARCHAR(255),
    description MEDIUMTEXT,
    body_schema VARCHAR(255),
    body_example VARCHAR(255),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition_version (

    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    version VARCHAR(64) NOT NULL,
    change_log VARCHAR(255),
    snapshot_name VARCHAR(255),
    snapshot_path VARCHAR(255),
    snapshot_method VARCHAR(255),
    snapshot_params VARCHAR(255),
    snapshot_responses VARCHAR(255),
    snapshot_description VARCHAR(255),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition_script (

    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    script_type VARCHAR(255) NOT NULL,
    content MEDIUMTEXT,
    enabled TINYINT NOT NULL DEFAULT 1,
    description MEDIUMTEXT,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition_assertion (

    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    name VARCHAR(128),
    source VARCHAR(255) NOT NULL,
    compare_type VARCHAR(255) NOT NULL,
    expression VARCHAR(255),
    expected_value VARCHAR(255),
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_definition_extract (

    id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    variable_name VARCHAR(255) NOT NULL,
    expression VARCHAR(255) NOT NULL,
    source VARCHAR(255) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_environment (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description MEDIUMTEXT,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_environment_variable (

    id BIGINT NOT NULL,
    environment_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    value VARCHAR(255),
    description MEDIUMTEXT,
    is_secret INT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_debug_history (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    definition_id BIGINT,
    environment_id BIGINT,
    name VARCHAR(128),
    request_url VARCHAR(255),
    request_method VARCHAR(255),
    request_headers VARCHAR(255),
    request_query VARCHAR(255),
    request_body MEDIUMTEXT,
    request_content_type VARCHAR(255),
    response_status_code INT,
    response_headers VARCHAR(255),
    response_body MEDIUMTEXT,
    response_content_type VARCHAR(255),
    response_size INT,
    duration_ms INT NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL DEFAULT 'SUCCESS',
    error_message MEDIUMTEXT,
    pre_request_logs VARCHAR(255),
    post_response_logs VARCHAR(255),
    assertion_results VARCHAR(255),
    all_assertions_passed INT,
    extracted_variables VARCHAR(255),
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_collection (

    id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description MEDIUMTEXT,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_collection_folder (

    id BIGINT NOT NULL,
    collection_id BIGINT NOT NULL,
    parent_id BIGINT,
    name VARCHAR(128) NOT NULL,
    description MEDIUMTEXT,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_collection_item (

    id BIGINT NOT NULL,
    collection_id BIGINT NOT NULL,
    folder_id BIGINT,
    definition_id BIGINT NOT NULL,
    name VARCHAR(128),
    description MEDIUMTEXT,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_collection_run (

    id BIGINT NOT NULL,
    collection_id BIGINT NOT NULL,
    project_id BIGINT NOT NULL,
    environment_id BIGINT,
    name VARCHAR(128),
    status VARCHAR(64) NOT NULL DEFAULT 'RUNNING',
    total_count INT NOT NULL DEFAULT 0,
    passed_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    error_count INT NOT NULL DEFAULT 0,
    duration_ms INT NOT NULL DEFAULT 0,
    trigger_mode VARCHAR(255) NOT NULL DEFAULT 'MANUAL',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE api_collection_run_item (

    id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    collection_item_id BIGINT,
    definition_id BIGINT,
    name VARCHAR(128),
    request_url VARCHAR(255),
    request_method VARCHAR(255),
    request_headers VARCHAR(255),
    request_body MEDIUMTEXT,
    response_status_code INT,
    response_headers VARCHAR(255),
    response_body MEDIUMTEXT,
    response_size INT,
    duration_ms INT NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL DEFAULT 'PENDING',
    error_message MEDIUMTEXT,
    assertion_results VARCHAR(255),
    all_assertions_passed INT,
    extracted_variables VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE image_convert_history (

    id BIGINT NOT NULL,
    session_id BIGINT NOT NULL,
    user_id BIGINT,
    tenant_id BIGINT,
    file_name VARCHAR(255),
    source_mime VARCHAR(255),
    target_format VARCHAR(255),
    result_file_name VARCHAR(255),
    result_size INT,
    width INT,
    height INT,
    stripped_gps INT NOT NULL DEFAULT 0,
    status VARCHAR(64) NOT NULL DEFAULT 'completed',
    error_message MEDIUMTEXT,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_credential (

    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    kind VARCHAR(255) NOT NULL,
    username VARCHAR(255),
    secret_enc VARCHAR(255) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline (

    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    repo_url VARCHAR(512) NOT NULL DEFAULT '',
    git_ref VARCHAR(255) NOT NULL DEFAULT 'main',
    credential_id BIGINT,
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_stage (

    id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_job (

    id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    stage_id BIGINT NOT NULL,
    name VARCHAR(255) NOT NULL,
    kind VARCHAR(255) NOT NULL,
    command MEDIUMTEXT,
    stack VARCHAR(255),
    runtime_version VARCHAR(255),
    tool_version VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    param_bindings MEDIUMTEXT NOT NULL DEFAULT ('{}'),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_run (

    id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    status VARCHAR(64) NOT NULL,
    trigger_type VARCHAR(255) NOT NULL DEFAULT 'MANUAL',
    git_ref VARCHAR(255),
    commit_sha VARCHAR(255),
    triggered_by_name VARCHAR(255),
    stack VARCHAR(255),
    runtime_version VARCHAR(255),
    tool_version VARCHAR(255),
    image VARCHAR(255),
    tekton_name VARCHAR(255),
    segment_index INT,
    error_message MEDIUMTEXT,
    runtime_params MEDIUMTEXT NOT NULL DEFAULT (''),
    started_at DATETIME,
    finished_at DATETIME,
    create_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_run_job (

    id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    job_id BIGINT,
    stage_name VARCHAR(255),
    job_name VARCHAR(255),
    kind VARCHAR(255),
    command MEDIUMTEXT,
    status VARCHAR(255) NOT NULL,
    log_text MEDIUMTEXT,
    started_at DATETIME,
    finished_at DATETIME,
    pod_name VARCHAR(255),
    namespace VARCHAR(255),
    containers MEDIUMTEXT,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_job_param (

    id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    job_id BIGINT NOT NULL,
    param_key VARCHAR(128) NOT NULL,
    param_label VARCHAR(128) NOT NULL,
    param_type VARCHAR(255) NOT NULL DEFAULT 'input',
    value_mode VARCHAR(255) NOT NULL DEFAULT 'runtime',
    default_value VARCHAR(255) NOT NULL DEFAULT '',
    required TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    options_json MEDIUMTEXT NOT NULL DEFAULT ('[]'),
    api_url MEDIUMTEXT NOT NULL DEFAULT (''),
    api_method VARCHAR(255) NOT NULL DEFAULT 'GET',
    api_headers_json MEDIUMTEXT NOT NULL DEFAULT ('{}'),
    api_response_path VARCHAR(255) NOT NULL DEFAULT '',
    placeholder VARCHAR(255) NOT NULL DEFAULT '',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_task_kind_param (

    id BIGINT NOT NULL,
    kind_value VARCHAR(64) NOT NULL,
    param_key VARCHAR(128) NOT NULL,
    param_label VARCHAR(128) NOT NULL,
    param_type VARCHAR(255) NOT NULL DEFAULT 'input',
    default_value VARCHAR(255) NOT NULL DEFAULT '',
    required TINYINT NOT NULL DEFAULT 0,
    sort_order INT NOT NULL DEFAULT 0,
    options_json MEDIUMTEXT NOT NULL DEFAULT ('[]'),
    api_url MEDIUMTEXT NOT NULL DEFAULT (''),
    api_method VARCHAR(255) NOT NULL DEFAULT 'GET',
    api_headers_json MEDIUMTEXT NOT NULL DEFAULT ('{}'),
    api_response_path VARCHAR(255) NOT NULL DEFAULT '',
    placeholder VARCHAR(255) NOT NULL DEFAULT '',
    visibility VARCHAR(255) NOT NULL DEFAULT 'user',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_task_kind (

    id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL DEFAULT 0,
    kind_value VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    label VARCHAR(128) NOT NULL,
    task_group VARCHAR(255) NOT NULL,
    description MEDIUMTEXT NOT NULL DEFAULT (''),
    hint MEDIUMTEXT NOT NULL DEFAULT (''),
    default_command MEDIUMTEXT NOT NULL DEFAULT (''),
    requires_command TINYINT NOT NULL DEFAULT 1,
    enabled TINYINT NOT NULL DEFAULT 1,
    sort_order INT NOT NULL DEFAULT 0,
    tool_image VARCHAR(255) NOT NULL DEFAULT '',
    default_image VARCHAR(255) NOT NULL DEFAULT '',
    command_template MEDIUMTEXT NOT NULL DEFAULT (''),
    cpu_request VARCHAR(255) NOT NULL DEFAULT '',
    cpu_limit VARCHAR(255) NOT NULL DEFAULT '',
    memory_request VARCHAR(255) NOT NULL DEFAULT '',
    memory_limit VARCHAR(255) NOT NULL DEFAULT '',
    version VARCHAR(64) NOT NULL DEFAULT '',
    deleted TINYINT NOT NULL DEFAULT 0,
    create_by BIGINT,
    update_by BIGINT,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pipeline_task_kind_kind_value (kind_value),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_task_kind_version (

    id BIGINT NOT NULL,
    kind_value VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    note MEDIUMTEXT NOT NULL DEFAULT (''),
    snapshot_json MEDIUMTEXT NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_pipeline_task_kind_version_kind_value_version (kind_value, version),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE pipeline_run_artifact (

    id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    job_id BIGINT,
    artifact_type VARCHAR(255) NOT NULL DEFAULT 'sbom',
    file_name VARCHAR(255) NOT NULL,
    file_size INT NOT NULL DEFAULT 0,
    storage_path MEDIUMTEXT NOT NULL,
    content_type VARCHAR(255) NOT NULL DEFAULT 'application/json',
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pipeline_run_artifact_run_id FOREIGN KEY (run_id) REFERENCES pipeline_run(id),
    CONSTRAINT fk_pipeline_run_artifact_job_id FOREIGN KEY (job_id) REFERENCES pipeline_run_job(id),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE dependency_component (

    id BIGINT NOT NULL,
    artifact_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    pipeline_id BIGINT NOT NULL,
    identity_key VARCHAR(255) NOT NULL,
    purl VARCHAR(512) NOT NULL DEFAULT '',
    group_name VARCHAR(255),
    name VARCHAR(128) NOT NULL,
    version VARCHAR(64) NOT NULL,
    license VARCHAR(255),
    scope VARCHAR(255),
    language VARCHAR(255) NOT NULL,
    create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_dependency_component_artifact_id FOREIGN KEY (artifact_id) REFERENCES pipeline_run_artifact(id),
    CONSTRAINT fk_dependency_component_run_id FOREIGN KEY (run_id) REFERENCES pipeline_run(id),
    CONSTRAINT fk_dependency_component_pipeline_id FOREIGN KEY (pipeline_id) REFERENCES pipeline(id),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE cluster_info (

    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    alias VARCHAR(255) DEFAULT '',
    provider VARCHAR(255) DEFAULT 'self-hosted',
    version VARCHAR(64) DEFAULT '',
    kubeconfig MEDIUMTEXT NOT NULL,
    mode VARCHAR(255) DEFAULT 'proxy',
    status VARCHAR(64) DEFAULT 'Unknown',
    labels MEDIUMTEXT DEFAULT ('{}'),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_cluster_info_tenant_id_name (tenant_id, name),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE registry_info (

    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    alias VARCHAR(255) DEFAULT '',
    type VARCHAR(64) NOT NULL DEFAULT 'harbor',
    url VARCHAR(255) NOT NULL,
    insecure TINYINT NOT NULL DEFAULT 0,
    credential_username VARCHAR(255) DEFAULT '',
    credential_password VARCHAR(255) DEFAULT '',
    source VARCHAR(255) NOT NULL DEFAULT 'manual',
    cluster_id BIGINT,
    status VARCHAR(64) DEFAULT 'Unknown',
    last_error VARCHAR(255) DEFAULT '',
    labels MEDIUMTEXT DEFAULT ('{}'),
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_registry_info_tenant_id_name (tenant_id, name),
    CONSTRAINT fk_registry_info_cluster_id FOREIGN KEY (cluster_id) REFERENCES cluster_info(id) ON DELETE SET NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE helm_chart_repository (

    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    name VARCHAR(128) COLLATE utf8mb4_bin NOT NULL,
    type VARCHAR(64) NOT NULL DEFAULT 'oci',
    url VARCHAR(255) NOT NULL,
    insecure TINYINT NOT NULL DEFAULT 0,
    credential_id BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_helm_chart_repository_tenant_id_name (tenant_id, name),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE TABLE helm_chart_artifact (

    id BIGINT NOT NULL AUTO_INCREMENT,
    repository_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    chart_name VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
    version VARCHAR(64) COLLATE utf8mb4_bin NOT NULL,
    digest VARCHAR(255) NOT NULL DEFAULT '',
    size_bytes BIGINT NOT NULL,
    chart_ref VARCHAR(255) NOT NULL,
    description MEDIUMTEXT NOT NULL DEFAULT (''),
    app_version VARCHAR(255) NOT NULL DEFAULT '',
    keywords MEDIUMTEXT NOT NULL DEFAULT ('[]'),
    readme MEDIUMTEXT NOT NULL DEFAULT (''),
    values_yaml MEDIUMTEXT NOT NULL DEFAULT (''),
    content_cached TINYINT NOT NULL DEFAULT 0,
    uploaded_by BIGINT,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_helm_chart_artifact_repository_id_chart_name_version (repository_id, chart_name, version),
    CONSTRAINT fk_helm_chart_artifact_repository_id FOREIGN KEY (repository_id) REFERENCES helm_chart_repository(id),
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC;

CREATE INDEX idx_sys_tenant_code ON sys_tenant (code);
CREATE INDEX idx_sys_tenant_status ON sys_tenant (status);
CREATE UNIQUE INDEX idx_sys_user_external_id ON sys_user (external_id);
CREATE UNIQUE INDEX idx_sys_tenant_member ON sys_tenant_member (tenant_id, user_id);
CREATE INDEX idx_sys_tenant_member_user ON sys_tenant_member (user_id);
CREATE INDEX idx_sys_login_log_time ON sys_login_log (create_time);
CREATE INDEX idx_sys_login_log_user ON sys_login_log (username);
CREATE INDEX idx_sys_login_log_action ON sys_login_log (action);
CREATE INDEX idx_sys_oper_log_time ON sys_oper_log (create_time);
CREATE INDEX idx_sys_oper_log_module ON sys_oper_log (module);
CREATE INDEX idx_sys_oper_log_user ON sys_oper_log (username);
CREATE INDEX idx_sys_oper_log_action ON sys_oper_log (action);
CREATE INDEX idx_sys_identity_connector_type ON sys_identity_connector (type);
CREATE INDEX idx_sys_user_message_user ON sys_user_message (user_id, read_flag);
CREATE INDEX idx_sys_user_message_time ON sys_user_message (create_time);
CREATE UNIQUE INDEX idx_pm_project_tenant_code ON pm_project (tenant_id, code);
CREATE INDEX idx_pm_project_tenant ON pm_project (tenant_id);
CREATE INDEX idx_pm_project_member ON pm_project_member (project_id, user_id);
CREATE INDEX idx_pm_project_issue_type ON pm_project_issue_type (project_id);
CREATE INDEX idx_pm_work_item_project ON pm_work_item (project_id, type_code);
CREATE INDEX idx_pm_work_item_status ON pm_work_item (status);
CREATE INDEX idx_pm_work_item_module ON pm_work_item (module_id);
CREATE INDEX idx_pm_project_module ON pm_project_module (project_id, parent_id);
CREATE INDEX idx_pm_work_item_comment ON pm_work_item_comment (work_item_id, create_time);
CREATE INDEX idx_pm_work_item_activity ON pm_work_item_activity (work_item_id, create_time);
CREATE INDEX idx_sys_user_username ON sys_user (username);
CREATE INDEX idx_group_project ON api_group (project_id, parent_id);
CREATE INDEX idx_group_sort ON api_group (project_id, sort_order);
CREATE INDEX idx_def_project ON api_definition (project_id);
CREATE INDEX idx_def_group ON api_definition (group_id);
CREATE INDEX idx_def_path_method ON api_definition (path, method);
CREATE INDEX idx_def_status ON api_definition (status);
CREATE INDEX idx_param_definition ON api_definition_param (definition_id);
CREATE INDEX idx_response_definition ON api_definition_response (definition_id);
CREATE INDEX idx_version_definition ON api_definition_version (definition_id);
CREATE INDEX idx_script_definition ON api_definition_script (definition_id);
CREATE INDEX idx_assertion_definition ON api_definition_assertion (definition_id);
CREATE INDEX idx_extract_definition ON api_definition_extract (definition_id);
CREATE INDEX idx_env_project ON api_environment (project_id);
CREATE INDEX idx_env_var_env ON api_environment_variable (environment_id);
CREATE INDEX idx_debug_definition ON api_debug_history (definition_id);
CREATE INDEX idx_debug_project ON api_debug_history (project_id);
CREATE INDEX idx_collection_project ON api_collection (project_id);
CREATE INDEX idx_collection_folder ON api_collection_folder (collection_id);
CREATE INDEX idx_collection_item_collection ON api_collection_item (collection_id);
CREATE INDEX idx_collection_run_collection ON api_collection_run (collection_id);
CREATE INDEX idx_collection_run_item_run ON api_collection_run_item (run_id);
CREATE INDEX idx_image_history_user_time ON image_convert_history (user_id, create_time);
CREATE INDEX idx_pipeline_tenant ON pipeline (tenant_id, deleted);
CREATE INDEX idx_pipeline_run_pipeline ON pipeline_run (pipeline_id, create_time);
CREATE INDEX idx_job_param_job ON pipeline_job_param (job_id, deleted);
CREATE INDEX idx_job_param_pipeline ON pipeline_job_param (pipeline_id, deleted);
CREATE INDEX idx_task_kind_param ON pipeline_task_kind_param (kind_value, deleted);
CREATE INDEX idx_task_kind_tenant ON pipeline_task_kind (tenant_id, deleted);
CREATE INDEX idx_task_kind_group ON pipeline_task_kind (task_group, sort_order);
CREATE INDEX idx_task_kind_version_kind ON pipeline_task_kind_version (kind_value, create_time);
CREATE INDEX idx_artifact_run ON pipeline_run_artifact (run_id, artifact_type);
CREATE UNIQUE INDEX uk_dep_comp_pipeline_identity ON dependency_component (pipeline_id, identity_key);
CREATE INDEX idx_dep_comp_purl ON dependency_component (purl);
CREATE INDEX idx_dep_comp_run ON dependency_component (run_id);
CREATE INDEX idx_dep_comp_name ON dependency_component (name, version);
CREATE INDEX idx_helm_chart_artifact_tenant_name ON helm_chart_artifact (tenant_id, chart_name);

-- PM 10M 容量索引
CREATE INDEX idx_pm_work_item_list_time ON pm_work_item (project_id, del_flag, update_time);
CREATE INDEX idx_pm_work_item_type_time ON pm_work_item (project_id, type_code, del_flag, create_time);
CREATE INDEX idx_pm_work_item_assignee ON pm_work_item (assignee_id);
CREATE INDEX idx_pm_work_item_sprint ON pm_work_item (sprint_id);
CREATE INDEX idx_pm_work_item_parent ON pm_work_item (parent_id);
