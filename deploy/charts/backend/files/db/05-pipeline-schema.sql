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
    sort_order      INTEGER      NOT NULL DEFAULT 0,
    param_bindings  TEXT         NOT NULL DEFAULT '{}'
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
    runtime_params  TEXT         NOT NULL DEFAULT '',
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
-- 运行时参数定义：每个 job 的可调参数
-- ============================================================
CREATE TABLE IF NOT EXISTS pipeline_job_param (
    id                  INTEGER      NOT NULL PRIMARY KEY,
    pipeline_id         INTEGER      NOT NULL,
    job_id              INTEGER      NOT NULL,
    param_key           TEXT         NOT NULL,
    param_label         TEXT         NOT NULL,
    param_type          TEXT         NOT NULL DEFAULT 'input',
    value_mode          TEXT         NOT NULL DEFAULT 'runtime', -- fixed | runtime
    default_value       TEXT         NOT NULL DEFAULT '',
    required            INTEGER      NOT NULL DEFAULT 0,
    sort_order          INTEGER      NOT NULL DEFAULT 0,

    -- param_type = 'select'
    options_json        TEXT         NOT NULL DEFAULT '[]',

    -- param_type = 'api_select'
    api_url             TEXT         NOT NULL DEFAULT '',
    api_method          TEXT         NOT NULL DEFAULT 'GET',
    api_headers_json    TEXT         NOT NULL DEFAULT '{}',
    api_response_path   TEXT         NOT NULL DEFAULT '',

    -- param_type = 'input'
    placeholder         TEXT         NOT NULL DEFAULT '',

    deleted             INTEGER      NOT NULL DEFAULT 0,
    create_by           INTEGER,
    update_by           INTEGER,
    create_time         TEXT         NOT NULL DEFAULT (datetime('now')),
    update_time         TEXT         NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_job_param_job ON pipeline_job_param (job_id, deleted);
CREATE INDEX IF NOT EXISTS idx_job_param_pipeline ON pipeline_job_param (pipeline_id, deleted);

-- ============================================================
-- 任务市场：每种 Task 预置的环境变量（枚举 / 远程接口 / 静态值）
-- ============================================================
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

-- 初始化 14 种 Task 类型（含默认工具镜像地址）
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

INSERT OR IGNORE INTO pipeline_task_kind (kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image) VALUES
('KUBECTL', 'K8s 命令', '部署', '使用用户提供的 kubeconfig 执行 kubectl 命令',
 '填写任意 kubectl 命令，如 kubectl get pods -A',
 'kubectl get pods -A', 1, 1, 95, 'bitnami/kubectl:1.31.4', 'bitnami/kubectl:1.31.4');

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
DROP TABLE IF EXISTS dependency_component;
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
-- 初始化 command_template：定义各任务的完整 Shell 脚本行为
-- 用户可在任务市场编辑覆盖，留空则使用 DEFAULT_TEMPLATE
-- ============================================================

-- CLONE
UPDATE pipeline_task_kind SET command_template =
'set -eu
cd "$(workspaces.source.path)"
git config --global http.version HTTP/1.1
git config --global http.postBuffer 524288000
if [ -n "${GIT_HTTP_PROXY:-}" ]; then
  git config --global http.proxy "${GIT_HTTP_PROXY}"
  git config --global https.proxy "${GIT_HTTP_PROXY}"
fi
AUTH=""
if [ -n "${GIT_USERNAME:-}" ]; then
  AUTH="${GIT_USERNAME}:${GIT_PASSWORD}@"
elif [ -n "${GIT_EMBEDDED_AUTH:-}" ]; then
  AUTH="${GIT_EMBEDDED_AUTH}@"
fi
URL="${GIT_SCHEME}://${AUTH}${GIT_HOST}/${GIT_PATH}"
attempt=1
until git clone --depth 1 --branch "${GIT_REF}" "$URL" src; do
  attempt=$((attempt + 1))
  if [ "$attempt" -gt 3 ]; then
    echo "git clone failed after 3 attempts"
    exit 1
  fi
  echo "git clone retry ${attempt}/3 ..."
  rm -rf src
  sleep $((attempt * 2))
done
echo "HFWAS_GIT_REF=${GIT_REF}"
echo "HFWAS_COMMIT=$(git -C src rev-parse HEAD)"'
WHERE kind_value = 'CLONE';

-- DEPENDENCY_ANALYSIS
UPDATE pipeline_task_kind SET command_template =
'set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
${COMMAND}

if [ -n "${API_ENDPOINT:-}" ] && [ -n "${RUN_ID:-}" ]; then
  SBOM_FILE=""
  for f in target/sbom.json target/bom.json; do
    if [ -f "$f" ]; then SBOM_FILE="$f"; break; fi
  done
  if [ -z "$SBOM_FILE" ]; then
    SBOM_FILE=$(find . \( -path ''*/target/sbom.json'' -o -path ''*/target/bom.json'' \) 2>/dev/null | head -n 1 || true)
  fi
  if [ -n "$SBOM_FILE" ]; then
    if ! command -v curl >/dev/null 2>&1; then
      apt-get update -qq && DEBIAN_FRONTEND=noninteractive apt-get install -y -qq curl ca-certificates >/dev/null 2>&1 || true
    fi
    size=$(stat -f%z "$SBOM_FILE" 2>/dev/null || stat -c%s "$SBOM_FILE" 2>/dev/null || echo 0)
    echo "uploading SBOM (${size} bytes) from ${SBOM_FILE}"
    curl -fsS -X POST "${API_ENDPOINT}/pipeline/runs/${RUN_ID}/artifacts" \
      -F "type=sbom" -F "file=@${SBOM_FILE};filename=sbom.json" \
      --connect-timeout 10 --max-time 60 \
      && echo " SBOM uploaded" || echo " SBOM upload failed (non-fatal)"
  else
    echo "target/sbom.json not found, skip upload"
  fi
else
  echo "API_ENDPOINT not configured, skip SBOM upload"
fi'
WHERE kind_value = 'DEPENDENCY_ANALYSIS';

-- FORMAT
UPDATE pipeline_task_kind SET command_template =
'set -eu
cd "$(workspaces.source.path)/src"
eval "$(cat <<''HFWAS_USER''
${COMMAND}
HFWAS_USER
)"
if [ -z "$(git status --porcelain)" ]; then
  echo "no changes after formatter, skip commit"
  exit 0
fi
git add .
git config user.name "HFwas Pipeline"
git config user.email "pipeline@hfwas.com"
git commit -m "style: auto format code [skip ci]"
git pull --rebase
git push'
WHERE kind_value = 'FORMAT';

-- LINT_SONAR
UPDATE pipeline_task_kind SET command_template =
'set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
eval "$(cat <<''HFWAS_USER''
${COMMAND}
HFWAS_USER
)"
: "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
: "${SONAR_TOKEN:?SONAR_TOKEN is required}"
: "${SONAR_PROJECT_KEY:=app}"
sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey="$SONAR_PROJECT_KEY" -Dsonar.sources=.'
WHERE kind_value = 'LINT_SONAR';

-- IMAGE (buildah step)
UPDATE pipeline_task_kind SET command_template =
'set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
eval "$(cat <<''HFWAS_USER''
${COMMAND}
HFWAS_USER
)"

: "${DEST:?DEST is required}"
: "${IMAGE_PLATFORMS:=linux/amd64,linux/arm64}"
: "${DOCKERFILE:=Dockerfile}"

n=0
for p in $(echo "$IMAGE_PLATFORMS" | tr '','' ''); do
  p=$(echo "$p" | tr -d '' '')
  [ -n "$p" ] || continue
  n=$((n + 1))
done

if [ "$n" -eq 1 ]; then
  buildah build --file "$DOCKERFILE" --platform "$IMAGE_PLATFORMS" -t "$DEST" .
  buildah push "$DEST"
else
  buildah manifest create "$DEST"
  for p in $(echo "$IMAGE_PLATFORMS" | tr '','' ''); do
    p=$(echo "$p" | tr -d '' '')
    [ -n "$p" ] || continue
    buildah build --manifest "$DEST" --platform "$p" --file "$DOCKERFILE" .
  done
  buildah manifest push --all "$DEST" "docker://$DEST"
fi'
WHERE kind_value = 'IMAGE';

-- IMAGE_COSIGN (cosign signature step)
UPDATE pipeline_task_kind SET command_template =
'set -eu
: "${DEST:?DEST is required}"
if [ -z "${COSIGN_PRIVATE_KEY:-}" ]; then
  echo "skip cosign: COSIGN_PRIVATE_KEY empty"
  exit 0
fi
printf ''%s'' "$COSIGN_PRIVATE_KEY" > /tmp/cosign.key
cosign sign --key /tmp/cosign.key --yes "$DEST"'
WHERE kind_value = 'IMAGE_COSIGN';

-- KUBECTL
UPDATE pipeline_task_kind SET command_template =
'set -eu
mkdir -p /root/.kube
if [ -f /etc/kubeconfig/config ]; then
  cp /etc/kubeconfig/config /root/.kube/config
fi
cd "$(workspaces.source.path)/src"
${COMMAND}'
WHERE kind_value = 'KUBECTL';
