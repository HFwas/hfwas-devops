# Tekton 任务脚本模板化方案

> 日期：2026-09-13
> 版本：v0.2
> 定位：将 TektonCompiler 中硬编码的脚本逻辑（`*Script()` 方法）全部抽取到 `pipeline_task_kind.command_template` 字段中，实现任务行为的数据库化管理，所有 14 种任务脚本全由数据库控制，用户可在前端编辑并验证
> 状态：待实施

---

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：模板化方案设计 |
| v0.2 | 2026-09-13 | CLONE 也进模板；新增前端 CodeMirror 脚本编辑器设计；新增 `bash -n` 服务端 shell 校验设计 |

---

## 1. 背景与目标

### 1.1 现状问题

`TektonCompiler.java` 中有 6 套硬编码脚本 + 1 套通用脚本，内联在 `toSteps()` 方法和专门的 `*Script()` 私有方法中：

| 方法 | 绑定任务 | 功能 | ~行数 |
|------|---------|------|:-:|
| `dependencyAnalysisScript()` | DEPENDENCY_ANALYSIS | SBOM 生成 + 自动上传 | 32 |
| `imageBuildahScript()` | IMAGE（Step 1） | 多架构 buildah 构建 | 35 |
| `imageCosignScript()` | IMAGE（Step 2） | cosign 签名 | 15 |
| `lintSonarScript()` | LINT_SONAR | env 校验 + sonar-scanner 调用 | 15 |
| `formatCommandScript()` | FORMAT | 格式化 + git commit/push | 15 |
| `commandScript()` | 通用兜底 | `cd workspace + 执行命令` | 5 |
| `evalPrefix()` | IMAGE / LINT_SONAR 共用 | eval 前缀 | 5 |
| CLONE 内联脚本 | CLONE | git clone 重试 + 代理 | 30+ |

所有脚本逻辑都锁死在 Java 代码中，带来的问题：

1. **用户无法自定义**：用户想调整上传逻辑、git commit 信息、sonar-scanner 参数、clone 重试策略，必须改代码
2. **新增任务必须改 Java**：即使新增一种与现有逻辑相似的任务类型，也需要修改 `TektonCompiler.java`
3. **发布周期长**：任何脚本改动都需要编译 → 打包 → 部署全流程
4. **运维成本高**：不同环境的脚本差异需要不同分支维护

### 1.2 基础设施现状

`pipeline_task_kind` 表已有 `command_template` 字段：

```sql
CREATE TABLE IF NOT EXISTS pipeline_task_kind (
    -- ... 现有字段 ...
    default_command  TEXT NOT NULL DEFAULT '',  -- 用户命令
    command_template TEXT NOT NULL DEFAULT '',  -- 脚本模板（含 ${COMMAND} 占位符）
    -- ... 其他字段 ...
);
```

Entity 和 DTO 也已包含 `commandTemplate` 字段，前端 `TaskKindVO` 接口也已定义：

| 层 | 文件 | `commandTemplate` 状态 |
|:--:|------|:---------------------:|
| 数据库 | `pipeline_task_kind.command_template` | ✅ 列已存在（值为空） |
| 后端 Entity | `PipelineTaskKindEntity.commandTemplate` | ✅ 已映射 |
| 后端 VO | `TaskKindVO.commandTemplate` | ✅ 已有 |
| 后端 DTO | `TaskKindUpdateDTO.commandTemplate` | ✅ 已有（可写） |
| 前端 API | `pipelineTaskKindApi.update()` | ✅ PUT 已支持 |
| 前端类型 | `TaskKindVO.commandTemplate` | ✅ 已定义 |

**但**：SQL 初始化 INSERT **未写入** `command_template`，`TektonCompiler` **完全未使用**，前端编辑抽屉 **有绑定但未展示**。

### 1.3 设计目标

- **完全模板化**：所有 14 种任务的 shell 脚本存入 `command_template`，Java 端 0 行硬编码脚本
- **CLONE 也进模板**：保留 env 注入逻辑（8 个变量），脚本内容放到数据库
- **用户可自定义 + 验证**：前端用 CodeMirror 6 编辑，保存时服务端 `bash -n` 校验语法
- **新增任务零代码**：插入一条 SQL 记录即可定义新任务类型的完整行为
- **向后兼容**：`command_template` 为空时 fallback 到通用缺省模板

---

## 2. 模板方案设计

### 2.1 核心思想

```
数据库 pipeline_task_kind.command_template  →  完整的 Shell 脚本模板（含 ${COMMAND}）
                                                         │
用户修改 default_command  ────────────────→  替换 ${COMMAND}
用户修改 command_template  ──────────────→  替换整个脚本行为
新增任务类型  ────────────────────────────→  只需 SQL INSERT，不改 Java
```

### 2.2 模板变量

| 变量 | 替换时机 | 来源 | 说明 |
|------|----------|------|------|
| `${COMMAND}` | **编译期** Java 字符串替换 | `job.command()` | 用户的命令（`default_command` 的实际值） |
| `$GIT_SCHEME` | 运行时 | Tekton Step env（编译器注入） | git clone 协议 |
| `$GIT_HOST` | 运行时 | Tekton Step env（编译器注入） | git 主机地址 |
| `$GIT_PATH` | 运行时 | Tekton Step env（编译器注入） | git 仓库路径 |
| `$GIT_REF` | 运行时 | Tekton Step env（编译器注入） | git 分支/标签 |
| `$GIT_USERNAME` | 运行时 | Tekton Step env（条件注入） | git 用户名 |
| `$GIT_PASSWORD` | 运行时 | Tekton Step env（条件注入） | git 密码 |
| `$GIT_EMBEDDED_AUTH` | 运行时 | Tekton Step env（条件注入） | git 内嵌认证 |
| `$GIT_HTTP_PROXY` | 运行时 | Tekton Step env（条件注入） | git 代理 |
| `$API_ENDPOINT` | 运行时 | Tekton Step env（条件注入） | 平台 API 地址 |
| `$RUN_ID` | 运行时 | Tekton Step env（条件注入） | 流水线运行 ID |

**变量选择原则**：

- `${COMMAND}` 在 Java 编译期用 `String.replace()` 替换 → 因为 `default_command` 是作业定义的一部分，编译时确定
- `$GIT_*` / `$API_ENDPOINT` / `$RUN_ID` 作为 Tekton Step 的环境变量注入 → 因为它们是运行时上下文
- 模板中通过 bash 变量引用环境变量：`$GIT_SCHEME`、`${GIT_REF}`（注意 `${}` 和 `${COMMAND}` 的区别）

### 2.3 模板分层

```
┌─────────────────────────────────────────┐
│   缺省模板（DEFAULT_TEMPLATE）           │ ← command_template 为空时使用
│   set -eu                               │
│   mkdir -p "$(workspaces...)/src"        │
│   cd "$(workspaces...)/src"             │
│   ${COMMAND}                            │
├─────────────────────────────────────────┤
│   任务默认模板（SQL INIT 写入）            │ ← 每种任务有自己的框架行为
│   CLONE: git clone + retry              │
│   FORMAT: git commit/push               │
│   DEPENDENCY_ANALYSIS: SBOM upload      │
│   ...                                   │
├─────────────────────────────────────────┤
│   用户修改模板（任务市场编辑）             │ ← 用户自定义
│   完全替换脚本行为                       │
└─────────────────────────────────────────┘
```

---

## 3. 各任务模板定义

### 3.1 缺省模板（通用 fallback）

适用于 `command_template` 为空的任何任务：

```bash
set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
${COMMAND}
```

> 对应删除前的 `commandScript()`。`BUILD`/`LINT_SEMGREP`/`SCAN`/`UPLOAD`/`DEPLOY`/`NOTIFY`/`PACKAGE`/`PUBLISH`/`TEST`/`CUSTOM` 可留空使用此缺省。

### 3.2 CLONE

```bash
set -eu
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
echo "HFWAS_COMMIT=$(git -C src rev-parse HEAD)"
```

> `$GIT_SCHEME`、`$GIT_HOST` 等全部环境变量由 `TektonCompiler` 在编译时注入为 Tekton Step 的 env。`${GIT_REF}` 等是 bash 变量引用，不是 `${COMMAND}` 占位符。

**用户可修改的场景**：

| 修改点 | 修改字段 | 效果 |
|--------|---------|------|
| 改 clone 深度 | `command_template` | `--depth 1` → `--depth 50` |
| 改重试次数 | `command_template` | `-gt 3` → `-gt 5` |
| 加 submodule 拉取 | `command_template` | clone 后加 `git submodule update --init` |
| 改 git 配置 | `command_template` | 改 `http.postBuffer` 大小 |

### 3.3 DEPENDENCY_ANALYSIS

```bash
set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
${COMMAND}

# ---------- SBOM 自动上传 ----------
if [ -n "${API_ENDPOINT:-}" ] && [ -n "${RUN_ID:-}" ]; then
  SBOM_FILE=""
  for f in target/sbom.json target/bom.json; do
    if [ -f "$f" ]; then SBOM_FILE="$f"; break; fi
  done
  if [ -z "$SBOM_FILE" ]; then
    SBOM_FILE=$(find . \( -path '*/target/sbom.json' -o -path '*/target/bom.json' \) 2>/dev/null | head -n 1 || true)
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
fi
```

**用户可修改的场景**：

| 修改点 | 修改字段 | 效果 |
|--------|---------|------|
| 换扫描工具 | `default_command` | `cdxgen -o ...` → `trivy fs --format cyclonedx ...` |
| 改上传路径 | `command_template` | 改 `target/sbom.json` 为 `build/report/bom.json` |
| 加压缩步骤（gzip 后上传） | `command_template` | 上传前加 `gzip` 命令 |
| 改上传目标平台 | `command_template` | 从本平台 API 改为 Dependency-Track API |

### 3.4 FORMAT

```bash
set -eu
cd "$(workspaces.source.path)/src"
eval "$(cat <<'HFWAS_USER'
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
git push
```

**用户可修改的场景**：

| 修改点 | 修改字段 | 效果 |
|--------|---------|------|
| 换格式化工具 | `default_command` | `npx prettier --write .` → `mvn spotless:apply` |
| 改 commit message | `command_template` | `[skip ci]` → `[auto]` |
| 改 git 用户 | `command_template` | 改 `user.name` / `user.email` |
| 不加 `pull --rebase` | `command_template` | 直接 git push（force） |

### 3.5 LINT_SONAR

```bash
set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
eval "$(cat <<'HFWAS_USER'
${COMMAND}
HFWAS_USER
)"
: "${SONAR_HOST_URL:?SONAR_HOST_URL is required}"
: "${SONAR_TOKEN:?SONAR_TOKEN is required}"
: "${SONAR_PROJECT_KEY:=app}"
sonar-scanner \
  -Dsonar.host.url="$SONAR_HOST_URL" \
  -Dsonar.token="$SONAR_TOKEN" \
  -Dsonar.projectKey="$SONAR_PROJECT_KEY" \
  -Dsonar.sources=.
```

**用户可修改的场景**：

| 修改点 | 修改字段 | 效果 |
|--------|---------|------|
| 改扫描源目录 | `command_template` | `-Dsonar.sources=src/main` |
| 加质量阈 | `command_template` | 追加 `-Dsonar.qualitygate.wait=true` |
| 换 sonar-scanner 版本 | `tool_image` | 镜像改 `sonarsource/sonar-scanner-cli:10.0` |
| 改 SONAR_HOST_URL 默认值 | `command_template` | 改 `:?` 为 `:=默认值` |

### 3.6 IMAGE（两个 Step）

IMAGE 的编排逻辑（两个 Step）保留在 Java 端，但每个 Step 的脚本从模板加载。

**IMAGE_BUILDAH 模板**（Step 1）：

```bash
set -eu
mkdir -p "$(workspaces.source.path)/src"
cd "$(workspaces.source.path)/src"
eval "$(cat <<'HFWAS_USER'
${COMMAND}
HFWAS_USER
)"

: "${DEST:?DEST is required}"
: "${IMAGE_PLATFORMS:=linux/amd64,linux/arm64}"
: "${DOCKERFILE:=Dockerfile}"

n=0
for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
  p=$(echo "$p" | tr -d ' ')
  [ -n "$p" ] || continue
  n=$((n + 1))
done

if [ "$n" -eq 1 ]; then
  buildah build --file "$DOCKERFILE" --platform "$IMAGE_PLATFORMS" -t "$DEST" .
  buildah push "$DEST"
else
  buildah manifest create "$DEST"
  for p in $(echo "$IMAGE_PLATFORMS" | tr ',' ' '); do
    p=$(echo "$p" | tr -d ' ')
    [ -n "$p" ] || continue
    buildah build \
      --manifest "$DEST" \
      --platform "$p" \
      --file "$DOCKERFILE" \
      .
  done
  buildah manifest push --all "$DEST" "docker://$DEST"
fi
```

**IMAGE_COSIGN 模板**（Step 2）：

```bash
set -eu
: "${DEST:?DEST is required}"
if [ -z "${COSIGN_PRIVATE_KEY:-}" ]; then
  echo "skip cosign: COSIGN_PRIVATE_KEY empty"
  exit 0
fi
printf '%s' "$COSIGN_PRIVATE_KEY" > /tmp/cosign.key
cosign sign --key /tmp/cosign.key --yes "$DEST"
```

> `IMAGE_COSIGN` 在 `pipeline_task_kind` 中作为一条独立记录，`kind_value = 'IMAGE_COSIGN'`，`tool_image = 'ghcr.io/sigstore/cosign:v2.4.3'`，前端操作 IMAGE 任务时编译器自动生成 cosign Step。

---

## 4. 编译器改造

### 4.1 `CompileRequest` 扩展

```java
public record CompileRequest(
    long runId,
    long pipelineId,
    String repoUrl,
    String gitRef,
    boolean hasCredential,
    PipelineGraphSpec graph,
    String gitHttpProxy,
    Map<String, String> taskImages,
    Map<String, String> taskScripts,    // ← 新增：kindValue → command_template
    String apiEndpoint
) {}
```

### 4.2 `TektonCompiler` 改造

**核心变化**：

```
改造前：6 个 *Script() 硬编码方法 + CLONE 内联脚本  + 通用兜底     =  ~140 行
改造后：1 个 resolveScript() 通用方法 + DEFAULT_TEMPLATE fallback  =  ~15 行
```

```java
// ============ 删除以下全部方法 ============
// dependencyAnalysisScript()    — 删除
// imageBuildahScript()          — 删除
// imageCosignScript()           — 删除
// lintSonarScript()             — 删除
// formatCommandScript()         — 删除
// commandScript()               — 删除
// evalPrefix()                  — 删除

// ============ 新增以下内容 ============

/** 缺省通用模板 — 仅当 command_template 为空时使用，不含任何任务逻辑 */
private static final String DEFAULT_TEMPLATE = """
        set -eu
        mkdir -p "$(workspaces.source.path)/src"
        cd "$(workspaces.source.path)/src"
        ${COMMAND}
        """.stripIndent();

/**
 * 解析任务的最终执行脚本。
 * 优先级：taskScripts 模板替换 > 缺省通用模板
 */
private static String resolveScript(String kindValue, Map<String, String> taskScripts, String command) {
    String template = taskScripts != null ? taskScripts.get(kindValue) : null;
    if (template == null || template.isBlank()) {
        template = DEFAULT_TEMPLATE;
    }
    return template.replace("${COMMAND}", command != null ? command : "");
}
```

**`toSteps()` 分支逻辑**：

```java
// 改造后的 toSteps() — 只保留 3 个特殊分支（全部不包含脚本内容）

if (job.kind() == PipelineJobKind.APPROVAL) {
    return List.of();  // 审批节点不产生 Tekton Step
}

// ---- 分支 1: CLONE — 保留 env 注入，脚本走模板 ----
if (job.kind() == PipelineJobKind.CLONE) {
    env.put("GIT_SCHEME", remote.scheme());
    env.put("GIT_HOST", remote.hostAuthority());
    env.put("GIT_PATH", remote.path());
    env.put("GIT_REF", request.gitRef() == null || request.gitRef().isBlank() ? "main" : request.gitRef());
    if (remote.hasEmbeddedCredentials()) {
        env.put("GIT_EMBEDDED_AUTH", remote.userInfo());
    }
    if (request.gitHttpProxy() != null && !request.gitHttpProxy().isBlank()) {
        env.put("GIT_HTTP_PROXY", request.gitHttpProxy().trim());
    }
    String script = resolveScript("CLONE", request.taskScripts(), "");
    return List.of(new CompiledStep(base, cloneImage, script, env, request.hasCredential(), false));
}

// ---- 分支 2: IMAGE — 保留多步骤编排，每个 Step 脚本走模板 ----
if (job.kind() == PipelineJobKind.IMAGE) {
    String buildahScript = resolveScript("IMAGE", request.taskScripts(), command);
    String cosignScript = resolveScript("IMAGE_COSIGN", request.taskScripts(), command);
    return List.of(
        new CompiledStep(base, buildahImage, buildahScript, env, false, false),
        new CompiledStep(base + "-cosign", cosignImage, cosignScript, env, false, false)
    );
}

// ---- 分支 3: DEPENDENCY_ANALYSIS — 保留 API_ENDPOINT env 注入，脚本走模板 ----
if (job.kind() == PipelineJobKind.DEPENDENCY_ANALYSIS) {
    if (request.apiEndpoint() != null && !request.apiEndpoint().isBlank()) {
        env.put("API_ENDPOINT", request.apiEndpoint());
        env.put("RUN_ID", String.valueOf(request.runId()));
    }
    String script = resolveScript("DEPENDENCY_ANALYSIS", request.taskScripts(), command);
    return List.of(new CompiledStep(base, depImage, script, env, false, false));
}

// ---- 通用分支：所有其他任务（FORMAT / LINT_SONAR / SCAN / BUILD ...）----
String script = resolveScript(job.kind().name(), request.taskScripts(), command);
String image = resolveImage(job.kind().name(), request.taskImages(), defaultImage);
return List.of(new CompiledStep(base, image, script, env, false, false));
```

### 4.3 关键设计说明

**为什么 CLONE 的 env 注入还留在 Java？**

CLONE 的 8 个环境变量涉及条件判断（是否有 credential、是否有 embedded auth、是否有 proxy），这些逻辑依赖 `GitRemote` 解析结果和 request 参数。把条件判断放到 shell 脚本里会大大增加脚本复杂度：

```bash
# ❌ 如果全放 shell，需要：
if [ "${HAS_CREDENTIAL}" = "true" ]; then
  # 从 PATH 解析
elif [ -n "${GIT_EMBEDDED_AUTH:-}" ]; then
  # ...
fi
```

而 Java 端 `GitRemote.parse()` 已经做了完整的 URL 解析。注入 env 是最干净的边界：**Java 负责条件逻辑，shell 负责执行流程**。

**${COMMAND} 与 $GIT_REF 的冲突？**

不会。`${COMMAND}` 在编译期由 Java `String.replace()` 替换，运行时 shell 看到的是字面量。`$GIT_REF` 是运行时 shell 变量引用。两者在不同阶段求值。

### 4.4 `TektonPipelineExecutor` 改造

在已有 `taskImages` 查询后，追加 `taskScripts` 查询：

```java
// 解析任务镜像（现有）
Map<String, String> taskImages = new HashMap<>();
// 解析任务模板（新增）
Map<String, String> taskScripts = new HashMap<>();

for (PipelineTaskKindEntity kind : taskKindMapper.selectList(null)) {
    // 镜像
    String effective = kind.getToolImage();
    if (effective == null || effective.isBlank()) {
        effective = kind.getDefaultImage();
    }
    if (effective != null && !effective.isBlank()) {
        taskImages.put(kind.getKindValue(), effective);
    }
    // 脚本模板（新增）
    if (kind.getCommandTemplate() != null && !kind.getCommandTemplate().isBlank()) {
        taskScripts.put(kind.getKindValue(), kind.getCommandTemplate());
    }
}

CompiledTekton compiled = TektonCompiler.compile(new CompileRequest(
    // ... 现有参数 ...
    taskImages,
    taskScripts,      // ← 新增
    apiEndpoint
));
```

---

## 5. 前端：脚本编辑器

### 5.1 编辑抽屉扩展

当前 `TaskMarketView.vue` 已有 `editForm.commandTemplate` 绑定，只是 UI 没展示。在"默认命令"下方新增脚本模板编辑区：

```vue
<!-- 当前编辑抽屉已有字段... -->

<n-form-item label="默认命令">
  <n-input v-model:value="editForm.defaultCommand" type="textarea" :rows="4" :autosize="{ minRows: 4 }" />
</n-form-item>

<!-- ===== 新增：脚本模板编辑区 ===== -->
<n-form-item label="脚本模板">
  <div class="tm-template-editor">
    <ShellEditor
      v-model="editForm.commandTemplate"
      :kind-value="editingItem.kindValue"
      :default-template="defaultTemplateForKind(editingItem.kindValue)"
      height="300px"
    />
    <div style="font-size:12px;color:var(--wb-muted,#888);margin-top:4px">
      使用 <code>${COMMAND}</code> 引用用户填写的默认命令。留空使用系统缺省模板。
      <n-button size="tiny" quaternary @click="resetTemplate" style="margin-left:8px">重置为默认</n-button>
    </div>
  </div>
</n-form-item>

<!-- 保存按钮... -->
```

### 5.2 `ShellEditor.vue` 组件

基于 CodeMirror 6 的 shell 脚本编辑器。CodeMirror 6 已经通过 `md-editor-v3` 传递依赖存在于项目中（`@codemirror/autocomplete`、`@codemirror/commands`、`@codemirror/language` 等），只需显式安装 shell 语言包：

```bash
npm install @codemirror/lang-shell
```

**组件结构**：

```vue
<script setup lang="ts">
import { EditorView, basicSetup } from 'codemirror'
import { EditorState } from '@codemirror/state'
import { shell } from '@codemirror/lang-shell'
import { lintGutter } from '@codemirror/lint'
import { oneDark } from '@codemirror/theme-one-dark'
import { useMessage } from 'naive-ui'
import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'

const props = defineProps<{
  modelValue: string
  kindValue: string
  defaultTemplate?: string
  height?: string
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string]
}>()

const message = useMessage()
const editorRef = ref<HTMLDivElement>()
const validating = ref(false)
let view: EditorView | null = null

// 初始化 CodeMirror
onMounted(() => {
  if (!editorRef.value) return
  const startState = EditorState.create({
    doc: props.modelValue,
    extensions: [
      basicSetup,
      shell(),                           // Shell 语法高亮 + 括号匹配
      lintGutter(),                      // 左侧 lint 标记
      EditorView.updateListener.of((update) => {
        if (update.docChanged) {
          emit('update:modelValue', update.state.doc.toString())
        }
      }),
      EditorView.theme({
        '&': { height: props.height || '200px' },
        '.cm-scroller': { overflow: 'auto' },
      }),
    ],
  })
  view = new EditorView({ state: startState, parent: editorRef.value })
})

onUnmounted(() => view?.destroy())

// 监听外部 modelValue 变化（如重置模板）
watch(() => props.modelValue, (val) => {
  if (view && val !== view.state.doc.toString()) {
    view.dispatch({
      changes: { from: 0, to: view.state.doc.length, insert: val }
    })
  }
})

// 服务端语法校验
async function validateSyntax(): Promise<boolean> {
  validating.value = true
  try {
    const result = await pipelineTaskKindApi.validateTemplate(props.kindValue, {
      script: view?.state.doc.toString() || ''
    })
    if (result.valid) {
      message.success('语法检查通过 ✓')
      return true
    } else {
      message.error('语法错误：\n' + result.errors.join('\n'))
      return false
    }
  } catch (e: unknown) {
    message.error('验证请求失败')
    return false
  } finally {
    validating.value = false
  }
}

// 重置为默认模板
function resetToDefault() {
  if (props.defaultTemplate) {
    emit('update:modelValue', props.defaultTemplate)
  }
}

defineExpose({ validateSyntax })
</script>

<template>
  <div class="shell-editor">
    <div ref="editorRef" class="shell-editor-cm"></div>
    <div class="shell-editor-toolbar">
      <n-button size="tiny" quaternary :loading="validating" @click="validateSyntax">
        <template #icon><CheckCircle :size="14" /></template>
        验证语法
      </n-button>
      <n-button size="tiny" quaternary @click="resetToDefault">
        <template #icon><RotateCcw :size="14" /></template>
        重置默认
      </n-button>
    </div>
  </div>
</template>

<style scoped>
.shell-editor {
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 6px;
  overflow: hidden;
}
.shell-editor-cm {
  /* CodeMirror 占位 */
}
.shell-editor-toolbar {
  display: flex;
  gap: 4px;
  padding: 4px 8px;
  border-top: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-th, #fafbfc);
}
</style>
```

### 5.3 业界选型对比

**代码编辑器**：

| 方案 | 体积（gzip） | Shell 语法支持 | 本项目适配 | 结论 |
|------|:-----------:|:-------------:|:---------:|:----:|
| **CodeMirror 6** + `@codemirror/lang-shell` | ~30KB | 高亮 + 括号匹配 + fold + lint gutter | ✅ 已间接依赖 | ⭐ **推荐** |
| Monaco Editor | ~800KB | 完整 IDE 体验（高亮 + 补全 + 错误标记） | ❌ 需新依赖 | × 过重 |
| Ace Editor | ~300KB | 基础高亮 | ❌ 需新依赖 | × |
| `<textarea>` + `n-input` | 0 | 无 | ✅ 已内置 | × 无编辑体验 |

**Shell 语法校验**：

| 方案 | 位置 | 准确性 | 可行性 | 结论 |
|------|:----:|:------:|:------:|:----:|
| **`bash -n`** 服务端 | Server | ✅ 准确（bash 自带） | `Runtime.exec("bash -n " + tmpFile)` | ⭐ **推荐** |
| ShellCheck WASM | Client | ✅ 非常准确 | 需加载 ~2MB WASM 包 | ○ 可选增强 |
| CodeMirror parse error | Client | ❌ 仅基础 | lang-shell 内置 | △ 辅助用 |

**一线 DevOps 平台参考**：

| 平台 | 编辑器 | 校验方式 |
|------|--------|---------|
| Jenkins Pipeline ("Replay") | Monaco Editor | 服务端 Groovy 编译 |
| GitLab CI Lint | CodeMirror | 服务端 CI Lint API |
| GitHub Actions | Monaco Editor | 服务端 workflow parser |
| 本项目 | **CodeMirror 6** | **`bash -n` 服务端 API** |

### 5.4 后端校验 API

```java
@RestController
@RequestMapping("/pipeline/task-kinds")
public class TaskKindController {

    @PostMapping("/{kind}/validate")
    public BaseResult<ValidationResult> validateTemplate(
            @PathVariable String kind,
            @RequestBody Map<String, String> body
    ) {
        String script = body.get("script");
        if (script == null || script.isBlank()) {
            return BaseResult.ok(new ValidationResult(true, List.of()));
        }
        return BaseResult.ok(validateShell(script));
    }

    private ValidationResult validateShell(String script) {
        try {
            // 写入临时文件
            Path tmp = Files.createTempFile("sh-validate-", ".sh");
            Files.writeString(tmp, script);
            // bash -n 语法检查
            Process process = new ProcessBuilder("bash", "-n", tmp.toString())
                .redirectErrorStream(true)
                .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            boolean valid = process.waitFor() == 0;
            Files.deleteIfExists(tmp);
            List<String> errors = valid ? List.of() :
                Arrays.stream(output.split("\n"))
                    .filter(line -> line.contains("error:") || line.contains("line "))
                    .map(String::trim)
                    .toList();
            return new ValidationResult(valid, errors);
        } catch (Exception e) {
            return new ValidationResult(false, List.of("验证失败: " + e.getMessage()));
        }
    }

    record ValidationResult(boolean valid, List<String> errors) {}
}
```

> 安全注意：`bash -n` 只做语法检查，不执行代码，但需控制并发和临时文件清理。

---

## 6. SQL 初始化数据

### 6.1 表结构

现有表结构无需变更（`command_template` 已存在）。

### 6.2 初始化 INSERT 追加 `command_template` 列

```sql
INSERT OR IGNORE INTO pipeline_task_kind (
    kind_value, label, task_group, description, hint,
    default_command, requires_command, enabled, sort_order,
    tool_image, default_image, command_template
) VALUES (..., ?);
```

### 6.3 各任务模板初始化

**通用任务**（BUILD / LINT_SEMGREP / SCAN / UPLOAD / DEPLOY / NOTIFY / PACKAGE / PUBLISH / TEST / CUSTOM）：

`command_template` 留空，编译器 fallback 到 `DEFAULT_TEMPLATE`。

**CLONE、DEPENDENCY_ANALYSIS、FORMAT、LINT_SONAR、IMAGE_BUILDAH、IMAGE_COSIGN**：各写入第 3 节定义的对应模板。

---

## 7. 涉及修改的文件清单

| 文件 | 改动 | 说明 |
|------|------|------|
| **后端** | | |
| `CompileRequest.java` | 修改 | 新增 `Map<String, String> taskScripts` 字段 |
| `TektonPipelineExecutor.java` | 修改 | 查询 `command_template` 并传入 `taskScripts` |
| `TektonCompiler.java` | **大改** | 删除全部 `*Script()` 方法（~120 行）；新增 `DEFAULT_TEMPLATE` + `resolveScript()`；`toSteps()` 精简为 3 个分支（CLONE / IMAGE / DEPENDENCY_ANALYSIS）+ 通用模板替换 |
| `TaskKindController.java` | **新增** | `POST /{kind}/validate` 端点，服务端 `bash -n` 校验 |
| `05-pipeline-schema.sql` | 修改 | INSERT 追加 `command_template` 列；初始化 6 个任务的模板 |
| | | |
| **前端** | | |
| `TaskMarketView.vue` | 修改 | 编辑抽屉新增脚本模板编辑区，引入 `ShellEditor` 组件 |
| `ShellEditor.vue` | **新建** | CodeMirror 6 + shell 语言编辑器组件，含验证/重置按钮 |
| `pipeline.ts`（API） | 修改 | 新增 `validateTemplate()` 方法 |
| `package.json` | 修改 | 新增 `@codemirror/lang-shell` 依赖 |
| | | |
| **文档** | | |
| `task-script-template-design.md` | 修改 | 本文（v0.2） |

---

## 8. 实施计划

### Phase 1：后端模板替换

| 步骤 | 内容 |
|------|------|
| 1.1 | `CompileRequest.java` 加 `taskScripts` |
| 1.2 | `TektonPipelineExecutor.java` 查询 `command_template` |
| 1.3 | `TektonCompiler.java` 新增 `resolveScript()` + `DEFAULT_TEMPLATE` |
| 1.4 | `TektonCompiler.java` 删除 7 个 `*Script()` 方法 |
| 1.5 | `TektonCompiler.java` 改造 `toSteps()` 分支 |
| 1.6 | `TektonCompilerTest.java` 更新测试 |
| 1.7 | `05-pipeline-schema.sql` 写入初始化模板 |

### Phase 2：前端脚本编辑器

| 步骤 | 内容 |
|------|------|
| 2.1 | 安装 `@codemirror/lang-shell` |
| 2.2 | 新建 `ShellEditor.vue` 组件 |
| 2.3 | `TaskMarketView.vue` 编辑抽屉集成脚本编辑区 |
| 2.4 | `pipeline.ts` 新增 `validateTemplate()` API |

### Phase 3：语法校验

| 步骤 | 内容 |
|------|------|
| 3.1 | `TaskKindController.java` 新增 `POST /{kind}/validate` |
| 3.2 | 前端 "验证语法" 按钮对接后端 |
| 3.3 | 保存时自动触发校验（`saveEdit()` 前置检查） |

---

## 9. 风险与应对

### 9.1 灰度策略

```
command_template 有值  →  使用模板替换 ${COMMAND}
command_template 为空  →  使用 DEFAULT_TEMPLATE（set -eu; cd; ${COMMAND}）
```

所有任务的行为变化：

| 任务 | 变化前 | 变化后 | 风险 |
|------|--------|--------|:----:|
| BUILD/SCAN/通用 | `commandScript()` | `DEFAULT_TEMPLATE` | 无（相同内容） |
| DEPENDENCY_ANALYSIS | `dependencyAnalysisScript()` | 数据库模板 | 低（内容相同） |
| FORMAT | `formatCommandScript()` | 数据库模板 | 低（内容相同） |
| LINT_SONAR | `lintSonarScript()` | 数据库模板 | 低（内容相同） |
| IMAGE | `imageBuildahScript()` + `imageCosignScript()` | 数据库模板 × 2 | 低（内容相同） |
| CLONE | 内联脚本 | 数据库模板 | 低（内容相同） |

### 9.2 验证清单

| 检查项 | 方法 |
|--------|------|
| 每个 `${COMMAND}` 被正确替换 | 单元测试验证替换结果 |
| CLONE 的 8 个 env 正确注入 | 集成测试验证 Tekton Task spec |
| IMAGE 两个 Step 互不影响 | 各 Step 独立替换对应模板 |
| 模板中的 `$GIT_REF` 不作为 `${COMMAND}` 替换 | Java `replace()` 精确匹配 `${COMMAND}` |
| `bash -n` 校验覆盖所有模板 | CI 中运行模板验证 |
| SQL 初始化可重复执行 | 使用 `INSERT OR IGNORE` |

### 9.3 回滚方案

- **SQL 级**：`UPDATE pipeline_task_kind SET command_template = ''` → 编译器 fallback 到 `DEFAULT_TEMPLATE`
- **应用级**：回退 `TektonCompiler` 到旧版本（保留 `*Script()` 方法的版本）
- **数据级**：`INSERT OR IGNORE` 保证幂等

---

## 10. 未来展望

### 10.1 多步骤模板

当前 IMAGE 的多步骤靠 Java 编排。未来可在模板中声明多个步骤：

```yaml
# 任务定义扩展（YAML 格式或 JSON 格式的模板元数据）
steps:
  - name: buildah
    image: quay.io/containers/buildah:v1.37.0
    script: |
      ... ${COMMAND} ...
  - name: cosign
    image: ghcr.io/sigstore/cosign:v2.4.3
    script: |
      ... ${COMMAND} ...
```

编译器解析步骤列表生成多个 `CompiledStep`，IMAGE 的特殊分支也可消除。

### 10.2 模板版本管理

用户修改 `command_template` 后，保留历史版本可回溯：

```sql
CREATE TABLE pipeline_task_kind_history (
    id              INTEGER NOT NULL PRIMARY KEY,
    kind_value      TEXT    NOT NULL,
    command_template TEXT   NOT NULL,
    updated_by      INTEGER,
    update_time     TEXT    NOT NULL DEFAULT (datetime('now')),
    reason          TEXT    NOT NULL DEFAULT ''
);
```

### 10.3 ShellCheck WASM 增强

未来可引入 ShellCheck WASM 在浏览器端提供更准确的语法检查和 lint 建议（检测未使用变量、常见陷阱等），减轻服务端压力。

---

## 11. 附录

### 11.1 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| 依赖分析任务设计 | `docs/pipeline/dependency-analysis-design.md` | DEPENDENCY_ANALYSIS 任务原始设计 |
| 任务市场设计 | `docs/pipeline/pipeline-task-marketplace-design.md` | 任务类型数据库设计与 API |
| Monorepo 多语言方案 | `docs/pipeline/monorepo-dependency-analysis-design.md` | 模块选择 + 自动检测方案 |
| Tekton 详解 | `docs/pipeline/tekton-intro.md` | Tekton 对象模型、数据传递 |

### 11.2 硬编码脚本对照

| 当前 Java 方法 | ~行数 | 去处 |
|---------------|:----:|------|
| `commandScript()` | 5 | `DEFAULT_TEMPLATE` 常量 |
| `dependencyAnalysisScript()` | 32 | `command_template` → DEPENDENCY_ANALYSIS |
| `formatCommandScript()` | 15 | `command_template` → FORMAT |
| `lintSonarScript()` | 15 | `command_template` → LINT_SONAR |
| `imageBuildahScript()` | 35 | `command_template` → IMAGE |
| `imageCosignScript()` | 15 | `command_template` → IMAGE_COSIGN |
| `evalPrefix()` | 5 | 内联到各模板 |
| CLONE 内联脚本 | 30+ | `command_template` → CLONE |