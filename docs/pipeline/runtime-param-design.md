# 流水线运行时参数选择设计方案

> 日期：2026-09-13
> 版本：v0.4

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-13 | 初版：运行时参数选择方案设计 |
| v0.2 | 2026-09-13 | 实施完成：后端数据模型/API/编译器 + 前端编辑器/运行对话框 |
| v0.3 | 2026-09-13 | 补齐入口：并行任务虚线占位、编辑器右侧常驻检查器、参数「写死 / 变量」、保存后回读 job.id、所有运行入口弹框 |
| v0.4 | 2026-09-13 | 定义迁到任务市场：Task 预置环境变量（静态值/枚举/远程接口）；流水线只绑定写死或设为变量；运行时选项来自任务市场 |

---

## 1. 背景与目标

### 1.1 现状问题

当前流水线所有参数（`gitRef`、job `command`、`stack`、`runtimeVersion` 等）都在编辑阶段写死保存在 `pipeline_job` 表中：

| 参数 | 存储位置 | 运行时可否修改 |
|------|----------|:------------:|
| `gitRef`（分支） | `pipeline` 表 | ❌ 需重新编辑流水线 |
| `command`（命令） | `pipeline_job` 表 | ❌ 需重新编辑流水线 |
| `stack` / `runtimeVersion` / `toolVersion` | `pipeline_job` 表 | ❌ 需重新编辑流水线 |

用户每次运行想改分支、传参、选镜像 tag 时，必须重新编辑流水线 → 保存 → 再运行，流程冗长。

### 1.2 业界参考

| 平台 | 运行时参数机制 | 参数类型 |
|------|--------------|----------|
| **GitLab CI** | `input` 关键字定义变量，手动触发时展示 UI | string / choice / boolean / file |
| **GitHub Actions** | `workflow_dispatch.inputs` | string / choice / boolean / environment |
| **Jenkins** | "Build with Parameters" — 参数化构建 | String / Choice / Boolean / Credentials |
| **Argo Workflows** | `parameters` 字段，支持 `default` / `enum` | string / array |

### 1.3 设计目标

- **任务市场定义变量**：编辑 Task 时预置环境变量，取值来源为静态值 / 枚举 / 远程接口
- **流水线只做绑定**：编辑流水线时对预置变量选择「写死」或「设为变量」，不能在流水线里新增定义
- **运行对话框**：存在「设为变量」时弹出选择界面，选项范围来自任务市场
- **参数注入**：写死值与运行时选择的值通过 env 或模板替换注入 Tekton
- **无变量不弹框**：没有任何 runtime 绑定的流水线直接运行

---

## 2. 总体设计

### 2.1 概念模型

```
任务市场 Task Kind
  └─ pipeline_task_kind_param   预置环境变量
        param_type: input | select | api_select

流水线 Job
  └─ pipeline_job.param_bindings JSON
        { "GIT_REF": { "mode": "runtime" }, "DEST": { "mode": "fixed", "value": "..." } }

运行弹框
  └─ 仅 mode=runtime 的 key
  └─ 选项来自任务市场（枚举 / 远程接口 / 静态值）
```

```
┌─ 任务市场 ──────────────┐     ┌─ 流水线编辑 ──────────┐     ┌─ 运行 ──────────────┐
│ CLONE                    │     │ 分支/SHA  写死|变量    │     │ RunParamDialog       │
│  GIT_REF  静态值/枚举/API │ --> │ IMAGE_TAG 写死|变量    │ --> │ 选项=任务市场定义     │
│ IMAGE                    │     │ 保存到 param_bindings  │     │ POST /runs {params}  │
│  IMAGE_TAG …             │     └──────────────────────┘     └─────────────────────┘
└─────────────────────────┘
```

- **GIT_REF 写死** → 使用流水线 `gitRef`（分支/SHA 输入框）
- **GIT_REF 变量** → 运行弹框选择；默认值仍可用 `gitRef`
- 其它 key 注入为 Tekton Step 环境变量

### 2.2 参数生命周期

```
定义期（任务市场）           绑定期（流水线编辑）         运行期              执行期
     │                           │                      │                   │
     │ PUT /task-kinds/{kind}    │ 写死 / 设为变量       │ GET default-params │
     │   params: [{              │ 保存 param_bindings   │ 仅 runtime 绑定    │
     │     paramKey, paramType,  │                      │ 选项=任务市场      │
     │     options | apiUrl }]   │                      │ POST /runs        │
     │                           │                      │                   │ env 注入
```

---

## 3. 数据模型

### 3.1 任务市场预置表 `pipeline_task_kind_param`

定义挂在 Task Kind 上，不在流水线 Job 上新增。旧表 `pipeline_job_param` 不再作为定义源。

```sql
CREATE TABLE IF NOT EXISTS pipeline_task_kind_param (
    id                  INTEGER      NOT NULL PRIMARY KEY,
    kind_value          TEXT         NOT NULL,
    param_key           TEXT         NOT NULL,
    param_label         TEXT         NOT NULL,
    param_type          TEXT         NOT NULL DEFAULT 'input', -- input | select | api_select
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
```

默认种子：CLONE / `GIT_REF` / 代码分支 / `input` / 默认 `main`。

#### 字段设计说明

- **`param_key`**：环境变量名，注入 Tekton Step。`GIT_REF` 特殊：写死时用流水线 `gitRef`，变量时进入运行弹框
- **`param_type = 'input'`**：静态值（文本）
- **`param_type = 'select'`**：枚举，选项在 `options_json`
- **`param_type = 'api_select'`**：远程接口，运行前由后端拉取选项
- **`required`**：仅对「设为变量」做运行时必填校验

### 3.1.1 流水线绑定 `pipeline_job.param_bindings`

```sql
ALTER TABLE pipeline_job ADD COLUMN param_bindings TEXT NOT NULL DEFAULT '{}';
```

JSON 形状：

```json
{
  "GIT_REF": { "mode": "runtime" },
  "IMAGE_TAG": { "mode": "fixed", "value": "latest" }
}
```

- **`mode = fixed`（写死）**：保存后按 `value` 注入（GIT_REF 除外，用流水线 gitRef），运行时不询问
- **`mode = runtime`（设为变量）**：点击运行时弹出 `RunParamDialog`，选项来自任务市场该 key 的类型配置
- 未出现在 bindings 中的预置变量视为写死，取值用任务市场 `default_value`

### 3.2 `pipeline_run` 表增加字段

```sql
ALTER TABLE pipeline_run ADD COLUMN runtime_params TEXT NOT NULL DEFAULT '';
```

存储运行时用户选择的参数值 JSON，格式：

```json
{
  "GIT_REF": "develop",
  "IMAGE_TAG": "v1.2.3",
  "DEPLOY_ENV": "staging"
}
```

---

## 4. 后端 API

### 4.1 运行接口改造

**`POST /pipeline/pipelines/{id}/runs`**

改前：无请求体，直接启动。

改后：接受可选请求体。

**Request:**
```json
{
  "params": {
    "GIT_REF": "develop",
    "IMAGE_TAG": "v1.2.3",
    "DEPLOY_ENV": "staging"
  }
}
```

**Response:** 不变，但 `gitRef` 值使用运行时传入的覆盖值。

### 4.2 预置变量与预览

定义随任务市场保存：

```
PUT /pipeline/task-kinds/{kind}
```

请求体在原有 Task 字段上增加 `params` 数组（全量替换该 Kind 的预置变量）。远程接口可在保存前预览：

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/pipeline/job-params/preview-api` | 测试远程 API 并返回选项预览 |

**DTO：**

```java
// TaskKindParamSaveDTO.java
@Data
public class TaskKindParamSaveDTO {
    private String paramKey;
    private String paramLabel;
    private String paramType;         // input | select | api_select
    private String defaultValue;
    private Boolean required;
    private Integer sortOrder;
    private List<String> options;     // select
    private String apiUrl;            // api_select
    private String apiMethod;
    private Map<String, String> apiHeaders;
    private String apiResponsePath;
    private String placeholder;       // input
}
```

流水线保存时 `PipelineJobDTO.paramBindings` 写入 `pipeline_job.param_bindings`。

### 4.3 运行时参数查询

**`GET /pipeline/pipelines/{id}/runs/default-params`**

返回流水线所有 **mode = runtime** 的绑定（写死不出现在弹框里）。选项来自任务市场：`select` 用枚举，`api_select` 由后端预取，`input` 为文本。

```json
{
  "params": [
    {
      "paramKey": "GIT_REF",
      "paramLabel": "代码分支",
      "paramType": "api_select",
      "defaultValue": "main",
      "required": true,
      "options": ["main", "develop", "feature/*"],
      "loading": false
    },
    {
      "paramKey": "IMAGE_TAG",
      "paramLabel": "镜像标签",
      "paramType": "input",
      "defaultValue": "latest",
      "placeholder": "输入镜像标签，如 v1.0.0"
    },
    {
      "paramKey": "DEPLOY_ENV",
      "paramLabel": "部署环境",
      "paramType": "select",
      "defaultValue": "staging",
      "options": ["staging", "production", "canary"]
    }
  ]
}
```

`loading` 标记用于 `api_select` 类型——前端需异步从 `api_url` 拉取选项，此时 `loading` 为 `true`，前端显示加载状态。

### 4.4 参数预览 API

**`POST /pipeline/job-params/preview-api`**

用于在任务市场配置远程接口后测试连通性：

**Request:**
```json
{
  "apiUrl": "https://gitlab.example.com/api/v4/projects/1/repository/branches",
  "apiMethod": "GET",
  "apiHeaders": {
    "PRIVATE-TOKEN": "glpat-xxx"
  },
  "apiResponsePath": "$[].name"
}
```

**Response:**
```json
{
  "success": true,
  "options": ["main", "develop", "release/1.0"],
  "errorMessage": null
}
```

---

## 5. 编译执行层改造

### 5.1 `CompileRequest` 扩展

```java
public record CompileRequest(
    // ... 现有字段 ...
    Map<String, String> runtimeParams    // ← 新增：运行时参数覆盖（key → value）
) {}
```

### 5.2 `TektonCompiler` 改造

核心逻辑：遍历每个 job 关联的运行时参数定义，将用户选定的值注入 Tekton Step 的环境变量。

```
toSteps() 中每个 Step 的 env 构建逻辑：
  for each (param in job.params) {
      if (runtimeParams contains param.paramKey) {
          env.put(param.paramKey, runtimeParams.get(param.paramKey))
      }
  }
```

**特殊参数 `GIT_REF`**：不在 env 中注入，而是直接替换 `request.gitRef()` 的值，从而影响 CLONE step 的 `$GIT_REF` 环境变量注入。

**命令模板替换**：如果参数 key 以 `CMD_` 为前缀，则对 job 的 `command` 字段做 `${}` 占位符替换。例如：
- 参数 `CMD_DEPLOY_ENV`，值 `staging`
- job 的 command 为 `deploy ${DEPLOY_ENV}`
- 替换后为 `deploy staging`

### 5.3 `TektonPipelineExecutor` 改造

```java
// 在编译时传递 runtimeParams
CompiledTekton compiled = TektonCompiler.compile(new CompileRequest(
    // ... 现有参数 ...
    runtimeParams          // ← 新增：从 PipelineRunEntity.runtimeParams 解析
));
```

### 5.4 `PipelineRunService.start()` 改造

```java
@Transactional
public PipelineRunVO start(Long pipelineId, Map<String, String> runtimeParams) {
    PipelineEntity pipeline = definitionService.requireOwned(pipelineId);
    
    // 1. 合并运行时参数 — GIT_REF 特殊处理
    String effectiveGitRef = runtimeParams != null && runtimeParams.containsKey("GIT_REF")
        ? runtimeParams.get("GIT_REF")
        : pipeline.getGitRef();
    
    // 2. 验证 runtimeParams 合法性
    //    - required 参数必须提供
    //    - select 类型的值必须在选项内
    validateRuntimeParams(pipelineId, runtimeParams);
    
    // 3. 创建 run，保存 runtimeParams 到 run 记录
    PipelineRunEntity run = new PipelineRunEntity();
    run.setGitRef(effectiveGitRef);
    run.setRuntimeParams(toJson(runtimeParams));
    // ...
}
```

### 5.5 参数注入汇总

| 参数 key | 注入方式 | 作用范围 |
|----------|----------|----------|
| `GIT_REF` | 替换 `run.gitRef` → 影响 CLONE step 的 `$GIT_REF` env | 流水线全局 |
| 非 `GIT_REF` 且非 `CMD_` 前缀 | 注入对应 job 的 Tekton Step env | 本 job 内 |
| 以 `CMD_` 为前缀 | 替换 job 的 `command` 字段中的 `${PARAM_KEY}` 占位符 | 本 job 内 |

---

## 6. 前端设计

### 6.1 新增前端类型

```typescript
// types/pipeline.ts

export type JobParamType = 'input' | 'select' | 'api_select'
export type JobParamValueMode = 'fixed' | 'runtime'

export interface JobParamDefinition {
  id?: EntityId
  pipelineId: EntityId
  jobId: EntityId
  paramKey: string
  paramLabel: string
  paramType: JobParamType
  valueMode: JobParamValueMode
  defaultValue: string
  required: boolean
  sortOrder: number
  options?: string[]
  apiUrl?: string
  apiMethod?: string
  apiHeaders?: Record<string, string>
  apiResponsePath?: string
  placeholder?: string
}

export interface RunParamValue {
  paramKey: string
  paramLabel: string
  paramType: JobParamType
  defaultValue: string
  required: boolean
  options?: string[]
  loading?: boolean
  placeholder?: string
}
```

### 6.2 任务市场定义 + 流水线绑定

**任务市场**编辑 Task 时配置预置环境变量（`JobParamEditor.vue`）：静态值 / 枚举 / 远程接口，保存进 `PUT /pipeline/task-kinds/{kind}` 的 `params`。

**流水线编辑器**右侧检查器不再「添加参数」，只对任务市场已预置的变量做绑定（`JobParamBinder.vue`）：

- **写死**：填写固定值（CLONE 的 `GIT_REF` 用「分支 / SHA」输入框）
- **设为变量**：运行时按任务市场的枚举 / 远程接口 / 静态值选择

CLONE 的 `GIT_REF` 在「分支 / SHA」旁提供「写死 | 设为变量」。

```
┌── 任务「代码克隆」─────────────────────────┐
│  名称: [代码克隆                    ]      │
│                                          │
│  ── 运行参数（来自任务市场）──             │
│  GIT_REF  在下方「分支 / SHA」设置         │
│                                          │
│  [删除任务]                               │
│  仓库 HTTPS  [...]                        │
│  分支 / SHA   (写死 | 设为变量)  [dev]     │
└──────────────────────────────────────────┘
```

未在任务市场配置预置变量时，检查器提示去任务市场编辑对应 Task。

### 6.3 运行参数选择对话框

#### 新增组件：`RunParamDialog.vue`

改造「运行」按钮逻辑（编辑器「保存并运行」、运行页「运行」、列表「运行」、首页「运行」均走同一套）：

```
整体流程：
1. 用户点击「运行」
2. 调用 GET /pipeline/pipelines/{id}/runs/default-params
3. 如果返回 params 为空 → 直接调 start()，不弹框
4. 如果 params 非空 → 弹出 RunParamDialog
5. 用户修改参数值 → 点击「确认运行」
6. 调用 POST /pipeline/pipelines/{id}/runs, body: { params: {...} }
```

对话框 UI：

```
┌── 运行流水线「构建部署」 ────────────────────┐
│                                          │
│  以下参数可在本次运行中调整：                  │
│                                          │
│  代码分支  *  [main        ▼]            │
│              ├ main                      │
│              ├ develop                   │
│              ├ release/1.0               │
│              └ feature/*                 │
│                                          │
│  镜像标签    [latest               ]     │
│                                          │
│  部署环境    ● staging                    │
│             ○ production                 │
│             ○ canary                     │
│                                          │
│  [取消]                        [确认运行]  │
└──────────────────────────────────────────┘
```

**`api_select` 类型处理**：前端从 `param.apiUrl` 拉取选项，显示加载态 `⏳ 正在加载分支列表...`，失败时回退到 `defaultValue` 并显示错误提示。

### 6.4 前端 API 层

```typescript
// api/pipeline.ts — 新增

export const pipelineJobParamApi = {
  list: (pipelineId: EntityId) =>
    get<JobParamDefinition[]>(`/pipeline/job-params`, { pipelineId }),
  get: (id: EntityId) =>
    get<JobParamDefinition>(`/pipeline/job-params/${id}`),
  create: (data: JobParamSavePayload) =>
    post<EntityId>('/pipeline/job-params', data),
  update: (id: EntityId, data: JobParamSavePayload) =>
    put<void>(`/pipeline/job-params/${id}`, data),
  delete: (id: EntityId) =>
    del<void>(`/pipeline/job-params/${id}`),
  previewApi: (data: { apiUrl: string; apiMethod?: string; apiHeaders?: Record<string, string>; apiResponsePath?: string }) =>
    post<{ success: boolean; options: string[]; errorMessage?: string }>('/pipeline/job-params/preview-api', data),
}

// pipelineApi 新增方法
export const pipelineApi = {
  // ... 现有方法 ...
  startWithParams: (id: EntityId, params: Record<string, string>) =>
    post<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs`, { params }),
  getDefaultParams: (id: EntityId) =>
    get<{ params: RunParamValue[] }>(`/pipeline/pipelines/${asId(id)}/runs/default-params`),
}
```

---

## 7. 涉及修改的文件清单

| 文件 | 改动 | 说明 |
|------|------|------|
| **后端（新增）** | | |
| `entity/PipelineJobParamEntity.java` | **新增** | 参数定义 Entity |
| `mapper/PipelineJobParamMapper.java` | **新增** | MyBatis Mapper |
| `service/PipelineJobParamService.java` | **新增** | CRUD + 预览 API |
| `controller/PipelineJobParamController.java` | **新增** | REST Controller |
| `dto/JobParamDefinitionVO.java` | **新增** | 响应 DTO |
| `dto/JobParamSaveDTO.java` | **新增** | 保存请求 DTO |
| `dto/JobParamPreviewResultVO.java` | **新增** | 预览结果 DTO |
| **后端（修改）** | | |
| `entity/PipelineRunEntity.java` | 修改 | 新增 `runtimeParams` 字段 |
| `dto/PipelineRunVO.java` | 修改 | 新增 `runtimeParams` 字段 |
| `service/PipelineRunService.java` | 修改 | `start()` 重载 + `getDefaultParams()` + `validateRuntimeParams()` |
| `controller/PipelineController.java` | 修改 | `start()` 接受请求体 |
| `tekton/CompileRequest.java` | 修改 | 新增 `runtimeParams` |
| `tekton/TektonCompiler.java` | 修改 | 运行时参数注入 env / 命令替换 |
| `executor/TektonPipelineExecutor.java` | 修改 | 传递 `runtimeParams` |
| SQL 初始化脚本 | 修改 | `pipeline_task_kind_param` + `pipeline_job.param_bindings` |
| **前端（新增）** | | |
| `components/JobParamEditor.vue` | **新建** | 任务市场：预置环境变量编辑 |
| `components/JobParamBinder.vue` | **新建** | 流水线：写死 / 设为变量 |
| `components/RunParamDialog.vue` | **新建** | 运行参数选择对话框 |
| **前端（修改）** | | |
| `types/pipeline.ts` | 修改 | `TaskKindParam` / `JobParamBinding` |
| `views/TaskMarketView.vue` | 修改 | 编辑 Task 时配置预置变量 |
| `components/JobInspector.vue` | 修改 | 绑定预置变量；GIT_REF 设为变量 |
| `views/PipelineEditorView.vue` | 修改 | 加载 task kinds，保存 bindings |

---

## 8. 实施步骤

### Phase 1：数据模型（~0.5d）

| 步骤 | 内容 |
|------|------|
| 1.1 | SQL DDL — 创建 `pipeline_job_param` 表 + 索引 |
| 1.2 | 后端 `PipelineJobParamEntity` + `PipelineJobParamMapper` |
| 1.3 | `pipeline_run` 表加 `runtime_params` 列 + Entity 映射 |

### Phase 2：后端 API（~1.5d）

| 步骤 | 内容 |
|------|------|
| 2.1 | 参数 CRUD: Service + Controller + DTO |
| 2.2 | 改造 `PipelineRunService.start()` 支持 `Map<String, String> runtimeParams` |
| 2.3 | `GET /default-params` 端点 |
| 2.4 | `POST /preview-api` 端点（HTTP 调用 + JSONPath 解析） |
| 2.5 | 参数合法性校验（required、select 选项范围） |

### Phase 3：编译执行（~1d）

| 步骤 | 内容 |
|------|------|
| 3.1 | `CompileRequest` 新增 `runtimeParams` |
| 3.2 | `TektonCompiler` 注入运行时参数到 Step env |
| 3.3 | `GIT_REF` 特殊处理（替换 `request.gitRef()`） |
| 3.4 | `CMD_` 前缀的命令模板替换 |
| 3.5 | `TektonPipelineExecutor` 传递 runtimeParams |

### Phase 4：前端编辑器（~1d）

| 步骤 | 内容 |
|------|------|
| 4.1 | 前端类型 + API 层 |
| 4.2 | `JobParamEditor.vue` 参数编辑表单 |
| 4.3 | `JobParamList.vue` 参数列表 |
| 4.4 | 嵌入 `JobInspector.vue` |

### Phase 5：前端运行对话框（~1d）

| 步骤 | 内容 |
|------|------|
| 5.1 | `RunParamDialog.vue` 组件 |
| 5.2 | 集成到 `PipelineRunView.vue` |
| 5.3 | 集成到 `PipelineEditorView.vue`（保存并运行） |
| 5.4 | `api_select` 异步加载处理 |

### Phase 6：联调测试（~0.5d）

| 步骤 | 内容 |
|------|------|
| 6.1 | 全流程 E2E 验证 |
| 6.2 | 边界条件（无参数、空值校验、API 超时） |

---

## 9. 参数注入方式

| 参数 key 命名约定 | 注入方式 | 示例 |
|-------------------|----------|------|
| `GIT_REF` | 替换 `request.gitRef()` → 影响 CLONE step 的 `$GIT_REF` env | `develop` |
| 其他名称（如 `IMAGE_TAG`） | 在对应 job 的 Tekton Step 中追加 env 变量 | `IMAGE_TAG=v1.0.0` 注入到 IMAGE-BUILD step |
| `CMD_` 前缀（如 `CMD_DEPLOY_ENV`） | 替换 job 的 `command` 字段中的 `${KEY}` 占位符 | `command="deploy ${DEPLOY_ENV}"` → `deploy staging` |

---

## 10. 验证方案

| 检查项 | 方法 |
|--------|------|
| 编辑器创建参数定义 | 新建/编辑/删除参数，确认保存后 reload 正确 |
| `select` 类型展示选项列表 | 定义 3-5 个静态选项，运行弹框确认可下拉 |
| `input` 类型展示输入框 | 定义文本参数，运行弹框确认可输入 |
| `api_select` 从远程 API 拉取 | mock / 真实 GitLab API 返回分支列表 |
| 运行时参数注入 Tekton env | 运行后查看 Tekton TaskRun spec 确认 env |
| GIT_REF 覆盖生效 | 运行不同分支，确认 clone 的是对应分支代码 |
| 无参数的流水线保持旧行为 | 不弹框，直接运行 |
| required 参数未填阻止运行 | 前端校验 + 后端校验 |
| `POST /preview-api` 正确解析 | 输入 URL 和 JSONPath，确认返回正确选项 |

---

## 11. 风险与应对

| 风险 | 影响 | 应对 |
|------|------|------|
| 远程 API 不可用或超时 | 运行弹框 `api_select` 加载失败 | 显示错误提示 + 回退到默认值；允许用户手动输入文本 |
| 参数 key 冲突（跨 job 同名但含义不同） | 运行时注入冲突 | 参数作用域仅限于本 job；同名不同含义在前端 label 区分 |
| 存量流水线无参数定义 | 运行弹框不必要出现 | 无参数时跳过弹框，保持旧行为 |
| API 返回非预期数据格式 | 选项无法解析 | 用户可配置 JSONPath 提取；预览功能可提前验证 |
| 并发运行时参数覆盖 | 不同 run 参数互相影响 | 每个 run 独立保存 `runtime_params` 到 `pipeline_run` 表，互不干扰 |

---

## 12. 相关文档

| 文档 | 路径 | 说明 |
|------|------|------|
| Tekton 任务脚本模板化 | `docs/pipeline/task-script-template-design.md` | command_template 模板机制 |
| 任务市场设计 | `docs/pipeline/pipeline-task-marketplace-design.md` | pipeline_task_kind 表设计 |
| Pipeline Core API | `docs/pipeline/pipeline-core-api.md` | 现有运行 API 定义 |
| Tekton 详解 | `docs/pipeline/tekton-intro.md` | Tekton 对象模型与数据传递 |