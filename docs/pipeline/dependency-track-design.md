# 流水线 Dependency-Track 漏洞识别任务设计方案

> 日期：2026-09-13
> 版本：v0.1
> 定位：在流水线中新增一个 **Dependency-Track 漏洞识别任务**，作为 DEPENDENCY_ANALYSIS 的下游消费方，将 SBOM 上传至自建 Dependency-Track 服务进行组件漏洞分析，按阈值控制流水线是否通过
> 状态：初稿

---

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：DEPENDENCY_TRACK 任务类型设计，SBOM 上传 + 轮询 + 阈值判断 |

---

## 1. 背景与目标

### 1.1 现状

当前流水线系统已有 17 种任务类型（JobKind），其中：

- **`DEPENDENCY_ANALYSIS`**：生成 CycloneDX JSON 格式的 SBOM，支持 Java/Maven（CycloneDX Maven Plugin）和其他语言（cdxgen）
- **`SCAN`**：使用 Trivy 做本地文件系统漏洞扫描

但缺少与 **OWASP Dependency-Track** 企业级组件分析平台的集成能力。

### 1.2 目标

- 在流水线中新增**Dependency-Track 漏洞识别**任务类型（`DEPENDENCY_TRACK`）
- **消费 SBOM**：读取 `target/sbom.json`，上传至 Dependency-Track 服务
- **自动项目管理**：按项目名+版本查找或自动创建 DT 项目
- **轮询分析结果**：等待 DT 完成组件解析和漏洞匹配
- **按阈值门禁**：支持按漏洞严重等级（critical/high/medium/low）控制流水线是否失败
- **纯 HTTP 对接**：通过 REST API 交互，无需 DT CLI，使用 curl 即可

### 1.3 核心设计原则

```
DEPENDENCY_ANALYSIS（生成 SBOM）
       │
       ▼  target/sbom.json（CycloneDX JSON）
       │
DEPENDENCY_TRACK（本任务）
       │
       ├── 1. 查找/创建 DT Project
       ├── 2. POST /api/v1/bom 上传 SBOM → 获取 token
       ├── 3. Poll /api/v1/bom/token/{token} 直到完成
       ├── 4. GET /api/v1/vulnerability/project/{uuid} 获取漏洞
       └── 5. 按 DT_FAIL_ON 阈值判断是否失败
```

### 1.4 流水线编排建议

```
[CLONE] → [BUILD] → [DEPENDENCY_ANALYSIS] → [DEPENDENCY_TRACK]
                                              ↑
                                  需要 DEPENDENCY_ANALYSIS
                                  先产出 target/sbom.json

更完整的流程：
[CLONE] → [BUILD] → [DEPENDENCY_ANALYSIS] → [DEPENDENCY_TRACK] → [IMAGE] → [DEPLOY]
```

---

## 2. 任务类型设计

### 2.1 新增 DEPENDENCY_TRACK

| 字段 | 值 |
|------|-----|
| `kind_value` | `DEPENDENCY_TRACK` |
| `label` | 依赖漏洞扫描 |
| `task_group` | 质量控制 |
| `description` | 上传 SBOM 到 Dependency-Track 进行组件漏洞分析，支持按严重等级控制流水线门禁 |
| `hint` | 需要先执行依赖分析（DEPENDENCY_ANALYSIS）生成 target/sbom.json。预置参数：DT_HOST_URL、DT_API_KEY、DT_PROJECT_NAME、DT_PROJECT_VERSION、DT_SBOM_PATH、DT_FAIL_ON |
| `default_command` | （空，使用预置参数） |
| `requires_command` | 1 |
| `enabled` | 1 |
| `tool_image` | `curlimages/curl:8.11.1` |
| `default_image` | `curlimages/curl:8.11.1` |
| `sort_order` | 55（介于 DEPENDENCY_ANALYSIS=45 和 SCAN=50 之间） |

### 2.2 预置参数

| param_key | param_label | param_type | default_value | required | sort_order |
|-----------|-------------|------------|---------------|----------|------------|
| `DT_HOST_URL` | DT 服务地址 | input | `http://dependency-track:8080` | 1 | 0 |
| `DT_API_KEY` | DT API Key | input | （空） | 1 | 1 |
| `DT_PROJECT_NAME` | 项目名称 | input | `${GIT_REPO_NAME}` | 1 | 2 |
| `DT_PROJECT_VERSION` | 项目版本 | input | `latest` | 0 | 3 |
| `DT_SBOM_PATH` | SBOM 文件路径 | input | `target/sbom.json` | 0 | 4 |
| `DT_FAIL_ON` | 失败阈值 | select | `none` | 0 | 5 |

`DT_FAIL_ON` 的 options_json：
```json
[
  {"value": "none", "label": "不检查（仅记录）"},
  {"value": "critical", "label": "Critical 以上"},
  {"value": "high", "label": "High 以上"},
  {"value": "medium", "label": "Medium 以上"},
  {"value": "low", "label": "Low 以上"}
]
```

### 2.3 command_template

```bash
set -eu
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

# 1) 查找或创建项目
PROJECT_INFO=$(curl -sfG "${API_BASE}/project" ${AUTH} \
  --data-urlencode "name=${DT_PROJECT_NAME}" \
  --data-urlencode "version=${DT_PROJECT_VERSION}" || echo "")
PROJECT_UUID=$(echo "${PROJECT_INFO}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)

if [ -z "${PROJECT_UUID}" ]; then
  echo "Creating project: ${DT_PROJECT_NAME}:${DT_PROJECT_VERSION}"
  BODY=$(cat <<ENDJSON
{"name":"${DT_PROJECT_NAME}","version":"${DT_PROJECT_VERSION}"}
ENDJSON
)
  CREATE_RESP=$(curl -sfX PUT "${API_BASE}/project" ${AUTH} \
    -H "Content-Type: application/json" -d "${BODY}" || echo "{}")
  PROJECT_UUID=$(echo "${CREATE_RESP}" | grep -o '"uuid":"[^"]*"' | head -1 | cut -d'"' -f4)
  echo "Created project UUID: ${PROJECT_UUID}"
else
  echo "Found project UUID: ${PROJECT_UUID}"
fi

if [ -z "${PROJECT_UUID}" ]; then
  echo "Failed to find or create project"
  exit 1
fi

# 2) 上传 SBOM
echo "Uploading SBOM: ${SBOM_FILE}"
SBOM_CONTENT=$(cat "${SBOM_FILE}")
UPLOAD_BODY=$(cat <<ENDJSON
{"project":"${PROJECT_UUID}","bom":${SBOM_CONTENT}}
ENDJSON
)
UPLOAD_RESP=$(curl -sfX POST "${API_BASE}/bom" ${AUTH} \
  -H "Content-Type: application/json" -d "${UPLOAD_BODY}" || echo "{}")
TOKEN=$(echo "${UPLOAD_RESP}" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
echo "Upload token: ${TOKEN}"

# 3) 轮询处理状态
MAX_RETRIES=30
RETRY=0
while [ "${RETRY}" -lt "${MAX_RETRIES}" ]; do
  RETRY=$((RETRY + 1))
  STATUS_RESP=$(curl -sf "${API_BASE}/bom/token/${TOKEN}" ${AUTH} || echo '{"processing":true}')
  PROCESSING=$(echo "${STATUS_RESP}" | grep -o '"processing":[a-z]*' | cut -d: -f2)
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

# 4) 获取漏洞汇总
echo ""
echo "=== Vulnerability Summary ==="
VULNS=$(curl -sf "${API_BASE}/vulnerability/project/${PROJECT_UUID}" ${AUTH} || echo "[]")

CRITICAL=$(echo "${VULNS}" | grep -o '"severity":"Critical"' | wc -l)
HIGH=$(echo "${VULNS}" | grep -o '"severity":"High"' | wc -l)
MEDIUM=$(echo "${VULNS}" | grep -o '"severity":"Medium"' | wc -l)
LOW=$(echo "${VULNS}" | grep -o '"severity":"Low"' | wc -l)
TOTAL=$((CRITICAL + HIGH + MEDIUM + LOW))
echo "Total vulnerabilities: ${TOTAL}"
echo "  Critical: ${CRITICAL}"
echo "  High:     ${HIGH}"
echo "  Medium:   ${MEDIUM}"
echo "  Low:      ${LOW}"
echo ""

# 5) 按阈值判断
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

echo "Dependency-Track analysis passed (threshold: ${DT_FAIL_ON})"
```

### 2.4 Docker 镜像策略

| 场景 | 推荐 tool_image | 说明 |
|------|----------------|------|
| 默认 | `curlimages/curl:8.11.1` | 使用 curl 通过 REST API 与 DT 交互，无需 DT 客户端 |
| 自定义 | 用户可覆盖 | 如需 jq 等工具可换为 `alpine:3.20` 等 |

> `curlimages/curl:8.11.1` 镜像约 5MB，仅包含 curl 和基础 shell，适合纯 HTTP 交互场景。

---

## 3. API 对接方案

### 3.1 Dependency-Track REST API

| 操作 | Method | Path | 请求参数 | 响应 |
|------|--------|------|----------|------|
| 查找项目 | `GET` | `/api/v1/project` | `name`, `version` | `[{uuid, name, version, ...}]` |
| 创建项目 | `PUT` | `/api/v1/project` | `{name, version}` | `{uuid, name, version, ...}` |
| 上传 BOM | `POST` | `/api/v1/bom` | `{project, bom}` | `{token}` |
| 查询状态 | `GET` | `/api/v1/bom/token/{token}` | — | `{processing, ...}` |
| 项目漏洞 | `GET` | `/api/v1/vulnerability/project/{uuid}` | — | `[{severity, ...}]` |

### 3.2 认证

Dependency-Track 使用 **X-Api-Key** HTTP Header 认证：

```bash
curl -H "X-Api-Key: ${DT_API_KEY}" https://dt.example.com/api/v1/project
```

API Key 在 DT 管理界面生成，需要 `BOM_UPLOAD` 和 `VIEW_VULNERABILITY` 权限。

### 3.3 处理流程

```
1. 查找项目
   GET /api/v1/project?name=my-app&version=v1.0
   └── 存在 → 获取 project UUID
   └── 不存在 → PUT /api/v1/project 创建 → 获取 UUID

2. 上传 SBOM
   POST /api/v1/bom {"project":"<uuid>","bom":<CycloneDX JSON>}
   └── 返回 token

3. 轮询处理
   GET /api/v1/bom/token/<token>
   └── processing: true → sleep 5s → 继续轮询
   └── processing: false → 分析完成

4. 获取漏洞
   GET /api/v1/vulnerability/project/<uuid>
   └── 汇总各严重等级数量

5. 阈值判断
   CRITICAL > 0 && DT_FAIL_ON=critical → exit 1
   HIGH > 0 && DT_FAIL_ON=high → exit 1
   ...
   DT_FAIL_ON=none → 始终通过
```

---

## 4. 边界情况处理

| 场景 | 处理方式 |
|------|---------|
| SBOM 文件不存在 | 提示用户先执行 DEPENDENCY_ANALYSIS，exit 1 |
| DT 服务不可达 | curl 失败 → exit 1（`set -eu` 确保脚本终止） |
| 项目创建失败 | UUID 为空 → exit 1 |
| 上传 BOM 失败 | curl 非零退出 → exit 1 |
| 轮询超时 | 30 次 × 5s = 150s 后继续（不 exit），打印警告 |
| DT 返回部分结果 | 轮询超时后仍有部分结果，按已有结果判断阈值 |
| DT_FAIL_ON=none | 仅输出漏洞汇总，不 exit（记录模式） |

---

## 5. 涉及修改的文件清单

| # | 文件 | 操作 | 说明 |
|---|------|------|------|
| 1 | `docs/pipeline/dependency-track-design.md` | **新增** | 本文 |
| 2 | `backend/pipeline-core/src/main/java/com/hfwas/devops/pipeline/graph/PipelineJobKind.java` | 修改 | 追加 `DEPENDENCY_TRACK` |
| 3 | `deploy/charts/backend/files/db/05-pipeline-schema.sql` | 修改 | INSERT + command_template + params |
| 4 | `backend/server/src/main/resources/db/pipeline-schema.sql` | 修改 | INSERT（对齐 Helm SQL） |
| 5 | `frontend/src/modules/pipeline/types/pipeline.ts` | 修改 | JobKind 追加 `DEPENDENCY_TRACK` |
| 6 | `frontend/src/modules/pipeline/graph/jobCatalog.ts` | 修改 | 追加分类条目 |
| 7 | `frontend/src/modules/pipeline/graph/jobIcons.ts` | 修改 | 追加图标映射 |

### 5.1 不变的文件

| 文件 | 原因 |
|------|------|
| `TektonCompiler.java` | 通用分支已能处理 `DEPENDENCY_TRACK`，无需新增条件分支 |
| `CompiledStep.java` | 无需新增字段 |
| `TektonManifests.java` | 无需变更 |
| `TektonPipelineExecutor.java` | 无需新增逻辑 |
| `PipelineCredentialService.java` | DT API Key 作为任务参数管理，不走 credential 系统 |

---

## 6. 附录

### 6.1 参考文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 依赖分析任务设计 | `docs/pipeline/dependency-analysis-design.md` | DEPENDENCY_ANALYSIS 的 SBOM 生成设计 |
| 任务市场设计 | `docs/pipeline/pipeline-task-marketplace-design.md` | 任务类型数据库设计与 API |
| 任务模板化方案 | `docs/pipeline/task-script-template-design.md` | command_template 模板机制 |
| Tekton 详解 | `docs/pipeline/tekton-intro.md` | Tekton 对象模型 |

### 6.2 外部链接

- [Dependency-Track Documentation](https://docs.dependencytrack.org/)
- [Dependency-Track API v1](https://docs.dependencytrack.org/integrations/rest-api/)
- [CycloneDX Specification](https://cyclonedx.org/specification/)

### 6.3 决策跟踪

| 决策项 | 结论 | 状态 |
|--------|------|------|
| 任务类型名 | `DEPENDENCY_TRACK`，明确指向 DT 平台 | ✅ 已定 |
| 镜像策略 | `curlimages/curl:8.11.1`，纯 HTTP 交互 | ✅ 已定 |
| 项目自动创建 | 按 name+version 查找，不存在则自动创建 | ✅ 已定 |
| JSON 解析方式 | 使用 `grep -o` + `cut` 提取字段（curl 镜像无需额外依赖） | ✅ 已定 |
| SBOM 读入方式 | `cat target/sbom.json` 嵌入 JSON body 的 `bom` 字段 | ✅ 已定 |
| 阈值判断 | `DT_FAIL_ON` 参数：none/critical/high/medium/low | ✅ 已定 |
| 轮询超时 | 30 次（约 150s），超时后继续不退出 | ✅ 已定 |
| 认证方式 | X-Api-Key Header，参数传入 | ✅ 已定 |