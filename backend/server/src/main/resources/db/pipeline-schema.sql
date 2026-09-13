-- Pipeline module (SQLite)

CREATE TABLE IF NOT EXISTS pipeline_credential (
    id          INTEGER      NOT NULL PRIMARY KEY,
    tenant_id   INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    kind        TEXT         NOT NULL,
    username    TEXT,
    secret_enc  TEXT         NOT NULL,
    deleted     INTEGER      NOT NULL DEFAULT 0,
    create_by   INTEGER,
    update_by   INTEGER,
    create_time TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pipeline (
    id              INTEGER      NOT NULL PRIMARY KEY,
    tenant_id       INTEGER      NOT NULL,
    name            TEXT         NOT NULL,
    repo_url        TEXT         NOT NULL DEFAULT '',
    git_ref         TEXT         NOT NULL DEFAULT 'main',
    credential_id   INTEGER,
    deleted         INTEGER      NOT NULL DEFAULT 0,
    create_by       INTEGER,
    update_by       INTEGER,
    create_time     TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time     TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pipeline_stage (
    id          INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id INTEGER      NOT NULL,
    name        TEXT         NOT NULL,
    sort_order  INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pipeline_job (
    id              INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id     INTEGER      NOT NULL,
    stage_id        INTEGER      NOT NULL,
    name            TEXT         NOT NULL,
    kind            TEXT         NOT NULL,
    command         TEXT,
    stack           TEXT,
    runtime_version TEXT,
    tool_version    TEXT,
    sort_order      INTEGER      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS pipeline_run (
    id              INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id     INTEGER      NOT NULL,
    tenant_id       INTEGER      NOT NULL,
    status          TEXT         NOT NULL,
    trigger         TEXT         NOT NULL DEFAULT 'MANUAL',
    git_ref         TEXT,
    commit_sha      TEXT,
    triggered_by_name TEXT,
    stack           TEXT,
    runtime_version TEXT,
    tool_version    TEXT,
    image           TEXT,
    tekton_name     TEXT,
    segment_index   INTEGER,
    error_message   TEXT,
    started_at      TEXT,
    finished_at     TEXT,
    create_by       INTEGER,
    create_time     TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS pipeline_run_job (
    id          INTEGER      NOT NULL PRIMARY KEY,
    run_id      INTEGER      NOT NULL,
    job_id      INTEGER,
    stage_name  TEXT,
    job_name    TEXT,
    kind        TEXT,
    command     TEXT,
    status      TEXT         NOT NULL,
    log_text    TEXT,
    started_at  TEXT,
    finished_at TEXT,
    pod_name    TEXT,
    namespace   TEXT,
    containers  TEXT
);

CREATE INDEX IF NOT EXISTS idx_pipeline_tenant ON pipeline (tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_pipeline_run_pipeline ON pipeline_run (pipeline_id, create_time);

-- ============================================================
-- 任务市场：存储平台支持的 Task 类型元数据
-- ============================================================
CREATE TABLE IF NOT EXISTS pipeline_task_kind (
    id               INTEGER      NOT NULL PRIMARY KEY,
    tenant_id        INTEGER      NOT NULL DEFAULT 0,
    kind_value       TEXT         NOT NULL UNIQUE,
    label            TEXT         NOT NULL,
    task_group       TEXT         NOT NULL,

    description      TEXT         NOT NULL DEFAULT '',
    hint             TEXT         NOT NULL DEFAULT '',
    default_command  TEXT         NOT NULL DEFAULT '',

    requires_command INTEGER      NOT NULL DEFAULT 1,
    enabled          INTEGER      NOT NULL DEFAULT 1,
    sort_order       INTEGER      NOT NULL DEFAULT 0,

    tool_image       TEXT         NOT NULL DEFAULT '',
    default_image    TEXT         NOT NULL DEFAULT '',
    command_template TEXT         NOT NULL DEFAULT '',
    cpu_request      TEXT         NOT NULL DEFAULT '',
    cpu_limit        TEXT         NOT NULL DEFAULT '',
    memory_request   TEXT         NOT NULL DEFAULT '',
    memory_limit     TEXT         NOT NULL DEFAULT '',

    deleted          INTEGER      NOT NULL DEFAULT 0,
    create_by        INTEGER,
    update_by        INTEGER,
    create_time      TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time      TEXT         NOT NULL DEFAULT (datetime('now'))
);

-- 初始化 15 种 Task 类型（含默认工具镜像地址）
INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('CLONE', '代码克隆', '代码', '从 Git 仓库拉取代码', 'Clone 命令由平台生成，在流水线里填写仓库与凭证即可。', '', 0, 1, 1, 'alpine/git:2.45.2', 'alpine/git:2.45.2');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('BUILD', '构建', '构建', '编译与打包源码', '', '', 1, 1, 10, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('IMAGE', '镜像构建', '构建', 'Buildah 多架构构建并签名镜像', '填写 DEST / IMAGE_PLATFORMS / DOCKERFILE；可选 COSIGN_PRIVATE_KEY。支持 linux/amd64,linux/arm64 等多架构。',
 'export DEST=registry.example.com/app:tag' || CHAR(10) ||
 'export IMAGE_PLATFORMS=linux/amd64,linux/arm64' || CHAR(10) ||
 'export DOCKERFILE=Dockerfile', 1, 1, 20, 'quay.io/containers/buildah:v1.37.0', 'quay.io/containers/buildah:v1.37.0');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('LINT_SEMGREP', 'Semgrep 检查', '质量控制', 'Semgrep 静态检查', '填写 Semgrep CLI。', 'semgrep scan --error --config=auto .', 1, 1, 30, 'semgrep/semgrep:1.97.0', 'semgrep/semgrep:1.97.0');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('FORMAT', '代码格式化', '质量控制', '自动格式化代码并提交回仓库', '选择技术栈（Node/Java/Go/Python）并填写格式化命令。会自动 add、commit（[skip ci]）、push。推荐：JS/TS → npx prettier --write .，Java → mvn spotless:apply，Go → gofmt -w .，Python → black .',
 'npx prettier --write .', 1, 1, 35, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('LINT_SONAR', 'Sonar 检查', '质量控制', 'SonarScanner 静态检查', '填写 SONAR_HOST_URL / SONAR_TOKEN / SONAR_PROJECT_KEY。',
 'export SONAR_HOST_URL=https://sonar.example.com' || CHAR(10) ||
 'export SONAR_TOKEN=' || CHAR(10) ||
 'export SONAR_PROJECT_KEY=app', 1, 1, 40, 'sonarsource/sonar-scanner-cli:11.2', 'sonarsource/sonar-scanner-cli:11.2');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('DEPENDENCY_ANALYSIS', '依赖分析', '质量控制',
 '生成 CycloneDX 格式的依赖清单（SBOM），为漏洞扫描提供精确的依赖树',
 'Java 项目使用 CycloneDX Maven Plugin，其他语言使用 cdxgen。产出 target/sbom.json。',
 'mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress',
 1, 1, 45, 'maven:3.9.9-eclipse-temurin-21', 'maven:3.9.9-eclipse-temurin-21');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('SCAN', '安全扫描', '质量控制', '依赖与文件系统漏洞扫描', '',
 'trivy fs --exit-code 1 --scanners vuln,secret,misconfig .', 1, 1, 50, 'aquasec/trivy:0.66.0', 'aquasec/trivy:0.66.0');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('PACKAGE', '打包', '制品', '产出可分发制品', '', '', 1, 1, 60, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('PUBLISH', '发布制品', '制品', '把制品发布到仓库', '', '', 1, 1, 70, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('UPLOAD', '上传对象存储', '制品', 'rclone 上传到对象存储', '',
 'rclone copy ./ :s3:bucket/prefix --s3-provider=Minio --s3-endpoint="${S3_ENDPOINT}" --s3-access-key-id="${S3_ACCESS_KEY}" --s3-secret-access-key="${S3_SECRET_KEY}"', 1, 1, 80, 'rclone/rclone:1.68.2', 'rclone/rclone:1.68.2');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('DEPLOY', '部署', '部署', '发布到 Kubernetes 或其他环境', '', 'kubectl apply -f k8s/', 1, 1, 90, 'bitnami/kubectl:1.31.4', 'bitnami/kubectl:1.31.4');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('TEST', '测试', '测试', '运行单元 / 集成测试', '', '', 1, 1, 100, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('CUSTOM', '自定义命令', '命令', '在工具链镜像里执行任意命令', '', 'echo ok', 1, 1, 110, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('APPROVAL', '人工卡点', '流程', '运行到此处暂停，需人工通过',
 '运行到此处会暂停，需在运行页点通过。审批节点必须单独成阶段。', '', 0, 1, 120, '', '');

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('NOTIFY', '通知', '流程', 'Webhook / HTTP 通知', '',
 'curl -fsS -X POST ''https://example.com/hook'' -H ''Content-Type: application/json'' -d ''{"status":"done"}''', 1, 1, 130, 'curlimages/curl:8.11.1', 'curlimages/curl:8.11.1');

CREATE INDEX IF NOT EXISTS idx_task_kind_tenant ON pipeline_task_kind (tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_task_kind_group ON pipeline_task_kind (task_group, sort_order);

-- ============================================================
-- 流水线运行产物表（SBOM / 测试报告 / 覆盖率报告等）
-- ============================================================
CREATE TABLE IF NOT EXISTS pipeline_run_artifact (
    id            INTEGER      NOT NULL PRIMARY KEY,
    run_id        INTEGER      NOT NULL REFERENCES pipeline_run(id),
    job_id        INTEGER      REFERENCES pipeline_run_job(id),
    artifact_type TEXT         NOT NULL DEFAULT 'sbom',
    file_name     TEXT         NOT NULL,
    file_size     INTEGER      NOT NULL DEFAULT 0,
    storage_path  TEXT         NOT NULL,
    content_type  TEXT         NOT NULL DEFAULT 'application/json',
    create_time   TEXT         NOT NULL DEFAULT (datetime('now'))
);
CREATE INDEX IF NOT EXISTS idx_artifact_run ON pipeline_run_artifact (run_id, artifact_type);

-- ============================================================
-- 依赖组件表（从 SBOM 解析入库，支撑集中依赖视图）
-- 同一流水线 + 同一组件（purl / GAV）只保留一行，重复跑分析走 upsert
-- ============================================================
CREATE TABLE IF NOT EXISTS dependency_component (
    id              INTEGER      NOT NULL PRIMARY KEY,
    artifact_id     INTEGER      NOT NULL REFERENCES pipeline_run_artifact(id),
    run_id          INTEGER      NOT NULL REFERENCES pipeline_run(id),
    pipeline_id     INTEGER      NOT NULL REFERENCES pipeline(id),
    identity_key    TEXT         NOT NULL,
    purl            TEXT         NOT NULL DEFAULT '',
    group_name      TEXT,
    name            TEXT         NOT NULL,
    version         TEXT         NOT NULL,
    license         TEXT,
    scope           TEXT,
    language        TEXT         NOT NULL,
    create_time     TEXT         NOT NULL DEFAULT (datetime('now'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_dep_comp_pipeline_identity ON dependency_component (pipeline_id, identity_key);
CREATE INDEX IF NOT EXISTS idx_dep_comp_purl ON dependency_component (purl);
CREATE INDEX IF NOT EXISTS idx_dep_comp_run  ON dependency_component (run_id);
CREATE INDEX IF NOT EXISTS idx_dep_comp_name ON dependency_component (name, version);

-- 依赖分析默认命令去掉 -q，便于流水线日志可见
UPDATE pipeline_task_kind
SET default_command = 'mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress'
WHERE kind_value = 'DEPENDENCY_ANALYSIS';

UPDATE pipeline_job
SET command = replace(command, ' -q', '')
WHERE kind = 'DEPENDENCY_ANALYSIS'
  AND instr(command, ' -q') > 0;

-- ============================================================
-- 迁移：pipeline_task_kind 追加资源配额列（v0.2）
-- CREATE TABLE 已包含这些列，ALTER TABLE 仅对旧库生效
-- ============================================================
ALTER TABLE pipeline_task_kind ADD COLUMN cpu_request TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind ADD COLUMN cpu_limit TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind ADD COLUMN memory_request TEXT NOT NULL DEFAULT '';
ALTER TABLE pipeline_task_kind ADD COLUMN memory_limit TEXT NOT NULL DEFAULT '';
