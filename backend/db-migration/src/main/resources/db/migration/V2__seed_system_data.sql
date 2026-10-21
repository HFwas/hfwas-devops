-- MySQL seed data (rebuild only)
SET NAMES utf8mb4;
SET SESSION sql_mode = CONCAT(@@sql_mode, ',NO_BACKSLASH_ESCAPES');

INSERT INTO sys_tenant (id, code, name, status, remark)
VALUES (1, 'default', '默认租户', 1, '系统内置默认租户');

INSERT INTO pm_work_item_type (id, code, name, color, sort_order) VALUES
(1, 'requirement', '需求', '#2080f0', 1),
(2, 'task', '任务', '#18a058', 2),
(3, 'bug', '缺陷', '#d03050', 3),
(4, 'test_case', '测试用例', '#f0a020', 4);

INSERT INTO pm_status_definition (id, project_id, type_code, status_code, status_name, sort_order, is_initial, is_final, transitions) VALUES
(1, NULL, 'task', 'open', '待处理', 1, 1, 0, '[{"id":"task-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"task-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(2, NULL, 'task', 'in_progress', '进行中', 2, 0, 0, '[{"id":"task-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"task-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
(3, NULL, 'task', 'done', '已完成', 3, 0, 0, '[{"id":"task-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(4, NULL, 'task', 'closed', '已关闭', 4, 0, 1, '[]'),
(6, NULL, 'bug', 'open', '待处理', 1, 1, 0, '[{"id":"bug-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"bug-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(7, NULL, 'bug', 'in_progress', '修复中', 2, 0, 0, '[{"id":"bug-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]}]'),
(8, NULL, 'bug', 'done', '已修复', 3, 0, 0, '[{"id":"bug-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(9, NULL, 'bug', 'closed', '已关闭', 4, 0, 1, '[]'),
(11, NULL, 'requirement', 'open', '待处理', 1, 1, 0, '[{"id":"requirement-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"requirement-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(12, NULL, 'requirement', 'in_progress', '进行中', 2, 0, 0, '[{"id":"requirement-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"requirement-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
(13, NULL, 'requirement', 'done', '已完成', 3, 0, 0, '[{"id":"requirement-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(14, NULL, 'requirement', 'closed', '已关闭', 4, 0, 1, '[]'),
(16, NULL, 'test_case', 'open', '待执行', 1, 1, 0, '[{"id":"test_case-open-in_progress","name":"开始处理","toStatus":"in_progress","validators":[],"postFunctions":[]},{"id":"test_case-open-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(17, NULL, 'test_case', 'in_progress', '执行中', 2, 0, 0, '[{"id":"test_case-in_progress-done","name":"完成","toStatus":"done","validators":[],"postFunctions":[]},{"id":"test_case-in_progress-open","name":"重新打开","toStatus":"open","validators":[],"postFunctions":[]}]'),
(18, NULL, 'test_case', 'done', '已通过', 3, 0, 0, '[{"id":"test_case-done-closed","name":"关闭","toStatus":"closed","validators":[],"postFunctions":[]}]'),
(19, NULL, 'test_case', 'closed', '已关闭', 4, 0, 1, '[]');

INSERT INTO pipeline_task_kind_param (
    id, kind_value, param_key, param_label, param_type, default_value, required, sort_order, placeholder, options_json
) VALUES
-- CLONE
(6,  'CLONE', 'REPO_URL',           '仓库地址',        'input', '',                                     1, 0,  'https://github.com/org/repo.git', '[]'),
(1,  'CLONE', 'GIT_REF',            '代码分支',        'input', 'main',                                 1, 0,  'main / develop / commit SHA', '[]'),
(7,  'CLONE', 'CREDENTIAL_ID',      '凭证',            'input', '',                               0, 2,  '', '[]'),
(2,  'CLONE', 'GIT_USERNAME',       'Git 用户名',      'input', '',                                     0, 1,  '', '[]'),
(3,  'CLONE', 'GIT_PASSWORD',       'Git 密码',        'input', '',                                     0, 2,  '', '[]'),
(4,  'CLONE', 'GIT_EMBEDDED_AUTH',  'Git Token',       'input', '',                                     0, 3,  '', '[]'),
(5,  'CLONE', 'GIT_HTTP_PROXY',     'Git HTTP 代理',   'input', '',                                     0, 4,  '', '[]'),
-- IMAGE
(10, 'IMAGE', 'DEST',               '镜像推送目标',     'input', 'registry.example.com/app:tag',          1, 0,  '如 registry.example.com/app:tag', '[]'),
(11, 'IMAGE', 'IMAGE_PLATFORMS',    '构建平台',         'input', 'linux/amd64,linux/arm64',              0, 1,  '逗号分隔，如 linux/amd64,linux/arm64', '[]'),
(12, 'IMAGE', 'DOCKERFILE',         'Dockerfile 路径',  'input', 'Dockerfile',                          0, 2,  '', '[]'),
(13, 'IMAGE', 'COSIGN_PRIVATE_KEY', 'Cosign 私钥',     'input', '',                                     0, 3,  '', '[]'),
-- LINT_SONAR
(20, 'LINT_SONAR', 'SONAR_HOST_URL',    'Sonar 服务地址','input', 'https://sonar.example.com',           1, 0,  '', '[]'),
(21, 'LINT_SONAR', 'SONAR_TOKEN',       'Sonar Token',   'input', '',                                   1, 1,  '', '[]'),
(22, 'LINT_SONAR', 'SONAR_PROJECT_KEY', 'Sonar 项目 Key','input', 'app',                                 0, 2,  '', '[]'),
-- UPLOAD
(30, 'UPLOAD', 'S3_ENDPOINT',   'S3 端点',        'input', '', 1, 0, '', '[]'),
(31, 'UPLOAD', 'S3_ACCESS_KEY', 'S3 Access Key', 'input', '', 1, 1, '', '[]'),
(32, 'UPLOAD', 'S3_SECRET_KEY', 'S3 Secret Key', 'input', '', 1, 2, '', '[]'),
(40, 'DEPENDENCY_TRACK', 'DT_HOST_URL',        'DT 服务地址',       'input', 'http://dependency-track:8080', 1, 0, 'http://dependency-track:8080', '[]'),
(41, 'DEPENDENCY_TRACK', 'DT_API_KEY',         'DT API Key',        'input', '',                             1, 1, '', '[]'),
(42, 'DEPENDENCY_TRACK', 'DT_PROJECT_NAME',    '项目名称',          'input', '',                             1, 2, '', '[]'),
(43, 'DEPENDENCY_TRACK', 'DT_PROJECT_VERSION', '项目版本',          'input', 'latest',                       0, 3, 'latest', '[]'),
(44, 'DEPENDENCY_TRACK', 'DT_SBOM_PATH',       'SBOM 文件路径',     'input', 'target/sbom.json',              0, 4, 'target/sbom.json', '[]'),
(45, 'DEPENDENCY_TRACK', 'DT_FAIL_ON',         '失败阈值',          'select', 'none',                        0, 5, '', '[{"value":"none","label":"不检查（仅记录）"},{"value":"critical","label":"Critical 以上"},{"value":"high","label":"High 以上"},{"value":"medium","label":"Medium 以上"},{"value":"low","label":"Low 以上"}]');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (1, 'CLONE', '代码克隆', '代码', '从 Git 仓库拉取代码', 'Clone 命令由平台生成，在流水线里填写仓库与凭证即可。', '', 0, 1, 1, 'alpine/git:2.45.2', 'alpine/git:2.45.2', 'set -eu
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
echo "HFWAS_COMMIT=$(git -C src rev-parse HEAD)"');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (2, 'BUILD', '构建', '构建', '编译与打包源码', '', '', 1, 1, 10, '', '', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (3, 'IMAGE', '镜像构建', '构建', 'Buildah 多架构构建并签名镜像', '使用任务市场预置的 DEST / IMAGE_PLATFORMS / DOCKERFILE / COSIGN_PRIVATE_KEY 参数。支持 linux/amd64,linux/arm64 等多架构。',
 '', 0, 1, 20, 'quay.io/containers/buildah:v1.37.0', 'quay.io/containers/buildah:v1.37.0', 'set -eu
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
fi');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (4, 'LINT_SEMGREP', 'Semgrep 检查', '质量控制', 'Semgrep 静态检查', '填写 Semgrep CLI。', 'semgrep scan --error --config=auto .', 1, 1, 30, 'semgrep/semgrep:1.97.0', 'semgrep/semgrep:1.97.0', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (5, 'FORMAT', '代码格式化', '质量控制', '自动格式化代码并提交回仓库', '选择技术栈（Node/Java/Go/Python）并填写格式化命令。会自动 add、commit（[skip ci]）、push。推荐：JS/TS → npx prettier --write .，Java → mvn spotless:apply，Go → gofmt -w .，Python → black .',
 'npx prettier --write .', 1, 1, 35, '', '', 'set -eu
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
git push');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (6, 'LINT_SONAR', 'Sonar 检查', '质量控制', 'SonarScanner 静态检查', '使用任务市场预置的 SONAR_HOST_URL / SONAR_TOKEN / SONAR_PROJECT_KEY 参数。',
 '', 0, 1, 40, 'sonarsource/sonar-scanner-cli:11.2', 'sonarsource/sonar-scanner-cli:11.2', 'set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
eval "$(cat <<''HFWAS_USER''
${COMMAND}
HFWAS_USER
)"
: "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
: "${SONAR_TOKEN:?SONAR_TOKEN is required}"
: "${SONAR_PROJECT_KEY:=app}"
sonar-scanner -Dsonar.host.url="$SONAR_HOST_URL" -Dsonar.token="$SONAR_TOKEN" -Dsonar.projectKey="$SONAR_PROJECT_KEY" -Dsonar.sources=.');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (7, 'DEPENDENCY_ANALYSIS', '依赖分析', '质量控制',
 '生成 CycloneDX 格式的依赖清单（SBOM），为漏洞扫描提供精确的依赖树',
 'Java 项目使用 CycloneDX Maven Plugin，其他语言使用 cdxgen。产出 target/sbom.json。',
 'mvn org.cyclonedx:cyclonedx-maven-plugin:2.9.3:makeAggregateBom -Dcyclonedx.outputFormat=json -DoutputName=sbom --no-transfer-progress',
 1, 1, 45, 'maven:3.9.9-eclipse-temurin-21', 'maven:3.9.9-eclipse-temurin-21', 'set -eu
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
fi');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (8, 'SCAN', '安全扫描', '质量控制', '依赖与文件系统漏洞扫描', '',
 'trivy fs --exit-code 1 --scanners vuln,secret,misconfig .', 1, 1, 50, 'aquasec/trivy:0.66.0', 'aquasec/trivy:0.66.0', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (9, 'PACKAGE', '打包', '制品', '产出可分发制品', '', '', 1, 1, 60, '', '', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (10, 'PUBLISH', '发布制品', '制品', '把制品发布到仓库', '', '', 1, 1, 70, '', '', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (11, 'UPLOAD', '上传对象存储', '制品', 'rclone 上传到对象存储', '',
 'rclone copy ./ :s3:bucket/prefix --s3-provider=Minio --s3-endpoint="${S3_ENDPOINT}" --s3-access-key-id="${S3_ACCESS_KEY}" --s3-secret-access-key="${S3_SECRET_KEY}"', 1, 1, 80, 'rclone/rclone:1.68.2', 'rclone/rclone:1.68.2', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (12, 'DEPLOY', '部署', '部署', '发布到 Kubernetes 或其他环境', '', 'kubectl apply -f k8s/', 1, 1, 90, 'bitnami/kubectl:1.31.4', 'bitnami/kubectl:1.31.4', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (13, 'TEST', '测试', '测试', '运行单元 / 集成测试', '', '', 1, 1, 100, '', '', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (14, 'CUSTOM', '自定义命令', '命令', '在工具链镜像里执行任意命令', '', 'echo ok', 1, 1, 110, '', '', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (15, 'APPROVAL', '人工卡点', '流程', '运行到此处暂停，需人工通过',
 '运行到此处会暂停，需在运行页点通过。审批节点必须单独成阶段。', '', 0, 1, 120, '', '', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (16, 'NOTIFY', '通知', '流程', 'Webhook / HTTP 通知', '',
 'curl -fsS -X POST ''https://example.com/hook'' -H ''Content-Type: application/json'' -d ''{"status":"done"}''', 1, 1, 130, 'curlimages/curl:8.11.1', 'curlimages/curl:8.11.1', '');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (17, 'DEPENDENCY_TRACK', '依赖漏洞扫描', '质量控制',
 '上传 SBOM 到 Dependency-Track 进行组件漏洞分析，支持按严重等级控制流水线门禁',
 '需要先执行依赖分析（DEPENDENCY_ANALYSIS）生成 target/sbom.json。预置参数：DT_HOST_URL / DT_API_KEY / DT_PROJECT_NAME / DT_PROJECT_VERSION / DT_SBOM_PATH / DT_FAIL_ON',
 '', 1, 1, 55, 'curlimages/curl:8.11.1', 'curlimages/curl:8.11.1', 'set -eu
cd "$(workspaces.source.path)/src"

: "${DT_HOST_URL:?DT_HOST_URL is required}"
: "${DT_API_KEY:?DT_API_KEY is required}"
: "${DT_PROJECT_NAME:?DT_PROJECT_NAME is required}"
: "${DT_PROJECT_VERSION:=latest}"
: "${DT_SBOM_PATH:=target/sbom.json}"
: "${DT_FAIL_ON:=none}"

SBOM_FILE="${DT_SBOM_PATH}"
if [ ! -f "$SBOM_FILE" ]; then
  echo "SBOM file not found: ${SBOM_FILE}"
  echo "Please run DEPENDENCY_ANALYSIS task first, or check DT_SBOM_PATH"
  exit 1
fi

API_BASE="${DT_HOST_URL%/}/api/v1"
AUTH="-H X-Api-Key: ${DT_API_KEY}"

PROJECT_INFO=$(curl -sfG "${API_BASE}/project" ${AUTH} \
  --data-urlencode "name=${DT_PROJECT_NAME}" \
  --data-urlencode "version=${DT_PROJECT_VERSION}" || echo "")
PROJECT_UUID=$(echo "${PROJECT_INFO}" | grep -o ''"uuid":"[^"]*"'' | head -1 | cut -d''"'' -f4)

if [ -z "${PROJECT_UUID}" ]; then
  echo "Creating project: ${DT_PROJECT_NAME}:${DT_PROJECT_VERSION}"
  BODY=$(cat <<ENDJSON
{"name":"${DT_PROJECT_NAME}","version":"${DT_PROJECT_VERSION}"}
ENDJSON
)
  CREATE_RESP=$(curl -sfX PUT "${API_BASE}/project" ${AUTH} \
    -H "Content-Type: application/json" -d "${BODY}" || echo "{}")
  PROJECT_UUID=$(echo "${CREATE_RESP}" | grep -o ''"uuid":"[^"]*"'' | head -1 | cut -d''"'' -f4)
  echo "Created project UUID: ${PROJECT_UUID}"
else
  echo "Found project UUID: ${PROJECT_UUID}"
fi

if [ -z "${PROJECT_UUID}" ]; then
  echo "Failed to find or create project"
  exit 1
fi

echo "Uploading SBOM: ${SBOM_FILE}"
SBOM_CONTENT=$(cat "${SBOM_FILE}")
UPLOAD_BODY=$(cat <<ENDJSON
{"project":"${PROJECT_UUID}","bom":${SBOM_CONTENT}}
ENDJSON
)
UPLOAD_RESP=$(curl -sfX POST "${API_BASE}/bom" ${AUTH} \
  -H "Content-Type: application/json" -d "${UPLOAD_BODY}" || echo "{}")
TOKEN=$(echo "${UPLOAD_RESP}" | grep -o ''"token":"[^"]*"'' | cut -d''"'' -f4)
echo "Upload token: ${TOKEN}"

MAX_RETRIES=30
RETRY=0
while [ "${RETRY}" -lt "${MAX_RETRIES}" ]; do
  RETRY=$((RETRY + 1))
  STATUS_RESP=$(curl -sf "${API_BASE}/bom/token/${TOKEN}" ${AUTH} || echo ''{"processing":true}'')
  PROCESSING=$(echo "${STATUS_RESP}" | grep -o ''"processing":[a-z]*'' | cut -d: -f2)
  if [ "${PROCESSING}" = "false" ]; then
    echo "Analysis complete (after ${RETRY} polls)"
    break
  fi
  echo "Waiting for analysis... (${RETRY}/${MAX_RETRIES})"
  sleep 5
done

if [ "${RETRY}" -ge "${MAX_RETRIES}" ]; then
  echo "Warning: Analysis did not finish within timeout, continuing with partial results"
fi

echo ""
echo "=== Vulnerability Summary ==="
VULNS=$(curl -sf "${API_BASE}/vulnerability/project/${PROJECT_UUID}" ${AUTH} || echo "[]")

CRITICAL=$(echo "${VULNS}" | grep -o ''"severity":"Critical"'' | wc -l)
HIGH=$(echo "${VULNS}" | grep -o ''"severity":"High"'' | wc -l)
MEDIUM=$(echo "${VULNS}" | grep -o ''"severity":"Medium"'' | wc -l)
LOW=$(echo "${VULNS}" | grep -o ''"severity":"Low"'' | wc -l)
TOTAL=$((CRITICAL + HIGH + MEDIUM + LOW))
echo "Total vulnerabilities: ${TOTAL}"
echo "  Critical: ${CRITICAL}"
echo "  High:     ${HIGH}"
echo "  Medium:   ${MEDIUM}"
echo "  Low:      ${LOW}"
echo ""

FAIL_THRESHOLD=99
case "${DT_FAIL_ON}" in
  critical) FAIL_THRESHOLD=0 ;;
  high)     FAIL_THRESHOLD=1 ;;
  medium)   FAIL_THRESHOLD=2 ;;
  low)      FAIL_THRESHOLD=3 ;;
  none|*)   FAIL_THRESHOLD=99 ;;
esac

if [ "${FAIL_THRESHOLD}" -le 0 ] && [ "${CRITICAL}" -gt 0 ]; then
  echo "FAIL: ${CRITICAL} critical vulnerabilities found (threshold: ${DT_FAIL_ON})"
  exit 1
fi
if [ "${FAIL_THRESHOLD}" -le 1 ] && [ "${HIGH}" -gt 0 ]; then
  echo "FAIL: ${HIGH} high vulnerabilities found (threshold: ${DT_FAIL_ON})"
  exit 1
fi
if [ "${FAIL_THRESHOLD}" -le 2 ] && [ "${MEDIUM}" -gt 0 ]; then
  echo "FAIL: ${MEDIUM} medium vulnerabilities found (threshold: ${DT_FAIL_ON})"
  exit 1
fi
if [ "${FAIL_THRESHOLD}" -le 3 ] && [ "${LOW}" -gt 0 ]; then
  echo "FAIL: ${LOW} low vulnerabilities found (threshold: ${DT_FAIL_ON})"
  exit 1
fi

echo "Dependency-Track analysis passed (threshold: ${DT_FAIL_ON})"');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (18, 'KUBECTL', 'K8s 命令', '部署', '使用用户提供的 kubeconfig 执行 kubectl 命令',
 '填写任意 kubectl 命令，如 kubectl get pods -A',
 'kubectl get pods -A', 1, 1, 95, 'bitnami/kubectl:1.31.4', 'bitnami/kubectl:1.31.4', 'set -eu
mkdir -p /root/.kube
if [ -f /etc/kubeconfig/config ]; then
  cp /etc/kubeconfig/config /root/.kube/config
fi
cd "$(workspaces.source.path)/src"
${COMMAND}');

INSERT INTO pipeline_task_kind (id, kind_value, label, task_group, description, hint, default_command, requires_command, enabled, sort_order, tool_image, default_image, command_template) VALUES (19, 'IMAGE_COSIGN', '镜像签名', '构建', '使用 cosign 对镜像签名', 'IMAGE 任务的子步骤，不单独出现在任务市场。', '', 0, 0, 25, '', '', 'set -eu
: "${DEST:?DEST is required}"
if [ -z "${COSIGN_PRIVATE_KEY:-}" ]; then
  echo "skip cosign: COSIGN_PRIVATE_KEY empty"
  exit 0
fi
printf ''%s'' "$COSIGN_PRIVATE_KEY" > /tmp/cosign.key
cosign sign --key /tmp/cosign.key --yes "$DEST"');
