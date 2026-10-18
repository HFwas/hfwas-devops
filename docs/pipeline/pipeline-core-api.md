# pipeline-core API 接口文档

> 日期：2026-10-18
> 版本：v0.4

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-08 | 初版：现有接口与 VO 说明 |
| v0.2 | 2026-09-13 | 标明运行 job 的 startedAt/finishedAt 取自 Tekton step 真实起止时间，完成后不再被 watch 轮询刷新 |
| v0.3 | 2026-09-13 | TaskKindVO / 更新接口增加预置环境变量 `params` |
| v0.4 | 2026-10-18 | 补齐任务类型版本/激活/校验、流水线级参数、运行产物、依赖扫描/组件；补充租户头、失败响应、运行参数与取消/审批语义；同步 Postman collection |

---

## 概述

所有接口统一返回 `BaseResult<T>` 结构：

```json
{
  "code": 0,
  "msg": null,
  "data": "<T>",
  "requestId": "req-xxx"     // 请求追踪 ID（由 ResponseBodyAdvice 注入）
}
```

### 通用错误码

| code | msg | 说明 |
|------|-----|------|
| 0 | "成功" | OK |
| 10001 | "服务器内部错误" | INTERNAL_ERROR |
| 10002 | "请求参数错误" | BAD_REQUEST；业务校验失败多复用此码并覆盖 msg |
| 10003 | "资源不存在" | NOT_FOUND |
| 10004 | "数据已存在" | DUPLICATE（如参数键/版本重复） |
| 10005 | "操作失败" | OPERATION_FAILED |
| 11001 | "未登录或登录已过期" | UNAUTHORIZED（HTTP 401） |
| 11002 | "无权访问" | FORBIDDEN（HTTP 403） |
| 11010 | "未登录或租户上下文缺失" | TENANT_CONTEXT_MISSING |

### 请求头

| Header | 说明 |
|--------|------|
| `Authorization` | `Bearer <keycloak-jwt-token>` |
| `X-Tenant-Id` | 当前租户 ID；缺省时取 JWT 或用户默认租户 |
| `X-Request-Id` | 可选；响应 `requestId` 与日志追踪使用 |
| `Content-Type` | JSON 接口 `application/json`；产物上传 `multipart/form-data` |

### 认证方式

所有接口（除 `/health/check`、产物回传白名单、WebSocket）通过 Spring Security OAuth2 Resource Server 鉴权。业务异常返回 HTTP 200 + `code != 0`；认证/权限失败返回 401/403。

### 分页约定

`/page` 接口默认 `pageNo=1`、`pageSize=10`，`pageSize` 上限 100；流水线与凭证按 `updateTime` 倒序，运行记录按 id 倒序。

### 统一失败响应

参数/业务校验失败（HTTP 200）：

```json
{
  "code": 10002,
  "msg": "名称不能为空",
  "data": null,
  "requestId": "req-xxx"
}
```

资源不存在（HTTP 200）：

```json
{
  "code": 10003,
  "msg": "流水线不存在",
  "data": null,
  "requestId": "req-xxx"
}
```

数据重复（HTTP 200）：

```json
{
  "code": 10004,
  "msg": "参数键重复: PROFILE",
  "data": null,
  "requestId": "req-xxx"
}
```

未认证（HTTP 401）：

```json
{
  "code": 11001,
  "msg": "未登录或登录已过期",
  "data": null,
  "requestId": "req-xxx"
}
```

无权限（HTTP 403）：

```json
{
  "code": 11002,
  "msg": "无权访问",
  "data": null,
  "requestId": "req-xxx"
}
```

租户上下文缺失（HTTP 200）：

```json
{
  "code": 11010,
  "msg": "未登录或租户上下文缺失",
  "data": null,
  "requestId": "req-xxx"
}
```

各接口的具体失败场景见对应章节与 Postman examples。

---

## 1. Pipeline 定义与运行管理

**Base URL:** `/pipeline/pipelines`

### 1.1 分页查询流水线

```
POST /pipeline/pipelines/page
```

**Request Body:**

```json
{
  "pageNo": 1,
  "pageSize": 10,
  "keyword": "流水线名称"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNo` | int | 否 | 默认 1，小于 1 按 1 |
| `pageSize` | int | 否 | 默认 10，上限 100 |
| `keyword` | string | 否 | 按名称模糊搜索 |

**Response `data`:** `IPage<PipelineVO>`

```json
{
  "records": [
    {
      "id": 1,
      "name": "构建部署流水线",
      "repoUrl": "https://github.com/org/repo.git",
      "gitRef": "refs/heads/main",
      "credentialId": 1,
      "updateTime": "2026-09-08T10:00:00",
      "lastRunId": 100,
      "lastRunStatus": "SUCCEEDED",
      "lastRunTime": "2026-09-08T10:30:00",
      "stages": [
        {
          "id": 10,
          "name": "代码检查",
          "sortOrder": 1,
          "jobs": [
            {
              "id": 100,
              "name": "Clone",
              "kind": "CLONE",
              "command": "git clone ...",
              "stack": null,
              "runtimeVersion": null,
              "toolVersion": null,
              "sortOrder": 1
            }
          ]
        }
      ]
    }
  ],
  "total": 5,
  "size": 10,
  "current": 1
}
```

### 1.2 创建流水线

```
POST /pipeline/pipelines
```

**Request Body:**

```json
{
  "name": "构建部署流水线",
  "repoUrl": "https://github.com/org/repo.git",
  "gitRef": "refs/heads/main",
  "credentialId": 1,
  "stages": [
    {
      "name": "代码检查",
      "sortOrder": 1,
      "jobs": [
        {
          "name": "Clone",
          "kind": "CLONE",
          "command": "git clone ...",
          "sortOrder": 1
        }
      ]
    }
  ]
}
```

**Response `data`:** `Long` — 流水线 ID

**校验规则：**

| 规则 | 失败响应 |
|------|----------|
| `name` 非空（trim） | 10002「名称不能为空」 |
| 至少 1 个阶段，每阶段至少 1 个任务 | 10002「流水线至少需要一个阶段」/「阶段「x」至少需要一个任务」 |
| 全局至多 1 个 CLONE | 10002「流水线至多一个 clone 任务」 |
| APPROVAL 必须独占阶段 | 10002「审批任务必须独占一列」 |
| 同阶段至多 1 个 IMAGE | 10002「镜像构建不能与其它镜像构建并行」 |
| 未知 kind | 10002「未知任务类型」 |
| 除 CLONE/APPROVAL 外 command 非空 | 10002「任务「x」需要命令」 |
| 含 CLONE 时 repoUrl 合法 | 10002「仓库地址…」 |

失败示例：

```json
{
  "code": 10002,
  "msg": "名称不能为空",
  "data": null,
  "requestId": "req-xxx"
}
```

`gitRef` 为空时落库为 `main`；创建时忽略请求体 `id`。

### 1.3 获取流水线详情

```
GET /pipeline/pipelines/{id}
```

**Response `data`:** `PipelineVO`（结构同上 1.1）

### 1.4 更新流水线

```
PUT /pipeline/pipelines/{id}
```

**Request Body:** 同 1.2（字段 `id` 可选，path 中的 id 优先）

**Response `data`:** `Long` — 流水线 ID

### 1.5 删除流水线

```
DELETE /pipeline/pipelines/{id}
```

**Response `data`:** `null`

### 1.6 分页查询运行记录

```
GET /pipeline/pipelines/{id}/runs?pageNo=1&pageSize=10
```

**Response `data`:** `IPage<PipelineRunVO>`

```json
{
  "records": [
    {
      "id": 100,
      "pipelineId": 1,
      "pipelineName": "构建部署流水线",
      "status": "SUCCEEDED",
      "trigger": "MANUAL",
      "gitRef": "refs/heads/main",
      "commitSha": "abc123def",
      "triggeredByName": "张三",
      "stack": "JAVA_MAVEN",
      "runtimeVersion": "17",
      "toolVersion": "3.9.6",
      "image": "maven:3.9-eclipse-temurin-17",
      "errorMessage": null,
      "startedAt": "2026-09-08T10:00:00",
      "finishedAt": "2026-09-08T10:30:00",
      "jobs": [...]    // 仅详情页传 true 时包含
    }
  ],
  "total": 20,
  "size": 10,
  "current": 1
}
```

#### PipelineRunJobVO

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Long | 运行 job ID |
| jobId | Long | 定义 job ID |
| stageName | String | 阶段名 |
| jobName | String | 任务名 |
| kind | String | CLONE / BUILD / ... |
| command | String | 命令 |
| status | String | QUEUED / RUNNING / WAITING_APPROVAL / SUCCEEDED / FAILED / CANCELLED |
| logText | String | 日志文本 |
| podName | String | K8s Pod 名 |
| namespace | String | K8s 命名空间 |
| containers | String[] | 容器列表 JSON |
| startedAt | LocalDateTime | 任务实际开始（Tekton step `startedAt`，UTC 墙钟）；排队中为空 |
| finishedAt | LocalDateTime | 任务实际结束（Tekton step `finishedAt`）；完成后不再被后续同步刷新 |

#### 运行状态枚举

| status | 含义 |
|--------|------|
| QUEUED | 等待调度 |
| RUNNING | 执行中 |
| WAITING_APPROVAL | 人工审批 |
| SUCCEEDED | 成功 |
| FAILED | 失败 |
| CANCELLED | 已取消 |

### 1.7 触发运行

```
POST /pipeline/pipelines/{id}/runs
```

**Request Body（可选）：**

```json
{
  "params": {
    "GIT_REF": "feature/x",
    "PROFILE": "prod",
    "CMD_VERSION": "1.2.3"
  }
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `params` | Map<String,String> | 否 | 运行时参数；`CMD_` 前缀用于替换命令占位符 |

**初始状态：**

| 条件 | run 状态 |
|------|----------|
| 未配置执行集群 | `FAILED`（error 含「未配置执行集群」，所有 job=FAILED） |
| 首个阶段为 APPROVAL | `WAITING_APPROVAL` |
| 其他 | `QUEUED`（未超并发则提交执行） |

**Response `data`:** `PipelineRunVO`

失败示例：

```json
{
  "code": 10002,
  "msg": "运行时参数 [分支] 为必填项",
  "data": null,
  "requestId": "req-xxx"
}
```

### 1.8 获取运行详情

```
GET /pipeline/pipelines/{id}/runs/{runId}
```

**Response `data`:** `PipelineRunVO`（含 jobs 数组）

### 1.9 取消运行

```
POST /pipeline/pipelines/{id}/runs/{runId}/cancel
```

- 终态（SUCCEEDED/FAILED/CANCELLED）原样返回，不报错。
- 非终态：run 置 `CANCELLED` + `finishedAt`，并 best-effort 取消 Tekton 执行。
- 当前不更新 `pipeline_run_job`（PIPE-903）。

**Response `data`:** `PipelineRunVO`

### 1.10 审批通过

```
POST /pipeline/pipelines/{id}/runs/{runId}/approve
```

- 无请求体；仅 `WAITING_APPROVAL` 可调用。
- 通过后按 ApprovalPlan：还有审批则保持 `WAITING_APPROVAL`；无下一段则 `SUCCEEDED`；有下一段则 `QUEUED` 并提交。
- 非待审批状态返回 10002。

**Response `data`:** `PipelineRunVO`

失败示例：

```json
{
  "code": 10002,
  "msg": "当前运行不在待审批",
  "data": null,
  "requestId": "req-xxx"
}
```

### 1.11 默认运行参数

```
GET /pipeline/pipelines/{id}/runs/default-params
```

**Response `data`:** `List<RunParamDefinitionVO>`

```json
[
  {
    "paramKey": "PROFILE",
    "paramLabel": "环境",
    "paramType": "select",
    "defaultValue": "dev",
    "required": true,
    "options": ["dev", "prod"],
    "placeholder": "请选择"
  }
]
```

仅返回 `runtime` 绑定且非 hidden 的参数；`api_select` 会预取 options（前端当前仍按 Input 渲染，PIPE-910）。

---

## 2. 凭证管理

**Base URL:** `/pipeline/credentials`

### 2.1 列表

```
GET /pipeline/credentials
```

**Response `data`:** `List<CredentialVO>`

```json
[
  {
    "id": 1,
    "name": "GitHub Token",
    "kind": "TOKEN",
    "username": "bot-user",
    "updateTime": "2026-09-01T12:00:00"
  }
]
```

### 2.2 详情

```
GET /pipeline/credentials/{id}
```

### 2.3 保存

```
POST /pipeline/credentials
```

**Request Body:**

```json
{
  "id": null,
  "name": "GitHub Token",
  "kind": "TOKEN",
  "username": "bot-user",
  "secret": "ghp_xxx"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `id` | Long | 否 | 为空创建；非空更新 |
| `name` | string | 是 | 名称 |
| `kind` | string | 是 | `PASSWORD` / `TOKEN` / `KUBECONFIG`（大小写不敏感） |
| `username` | string | 条件 | PASSWORD 必填；TOKEN 缺省 `x-access-token` |
| `secret` | string | 条件 | 创建或更新传值时必填；更新留空保留原密文 |

**Response `data`:** `Long` — 凭证 ID

失败示例：

```json
{
  "code": 10002,
  "msg": "账号密码凭证需要用户名",
  "data": null,
  "requestId": "req-xxx"
}
```

响应 VO 不包含 `secret` 字段。

### 2.4 删除

```
DELETE /pipeline/credentials/{id}
```

被流水线引用时返回 10002「凭证仍被流水线引用，无法删除」；否则逻辑删除。

```json
{
  "code": 10002,
  "msg": "凭证仍被流水线引用，无法删除",
  "data": null,
  "requestId": "req-xxx"
}
```

---

## 3. 任务类型管理

**Base URL:** `/pipeline/task-kinds`

### 3.1 列表

```
GET /pipeline/task-kinds
```

**Response `data`:** `List<TaskKindVO>`

```json
[
  {
    "kindValue": "CLONE",
    "label": "克隆代码",
    "taskGroup": "SCM",
    "description": "拉取源代码",
    "hint": "需配置代码仓库地址",
    "defaultCommand": "git clone ...",
    "requiresCommand": false,
    "enabled": true,
    "sortOrder": 1,
    "toolImage": "",
    "defaultImage": "alpine/git:latest",
    "commandTemplate": "",
    "params": [
      {
        "paramKey": "GIT_REF",
        "paramLabel": "代码分支",
        "paramType": "input",
        "defaultValue": "main",
        "required": true,
        "sortOrder": 0,
        "placeholder": "main / develop / commit SHA"
      }
    ]
  }
]
```

### 3.2 详情

```
GET /pipeline/task-kinds/{kind}
```

**Path Variable:** `kind` — 任务类型标识（如 `CLONE`、`BUILD`）

### 3.3 更新

```
PUT /pipeline/task-kinds/{kind}
```

**Request Body:**

```json
{
  "label": "克隆代码",
  "description": "拉取源代码仓库",
  "hint": "需配置代码仓库地址",
  "defaultCommand": "git clone {{repoUrl}} .",
  "toolImage": "",
  "commandTemplate": "",
  "sortOrder": 1,
  "params": [
    {
      "paramKey": "GIT_REF",
      "paramLabel": "代码分支",
      "paramType": "input",
      "defaultValue": "main",
      "required": true,
      "placeholder": "main / develop / commit SHA"
    }
  ]
}
```

**Validation:**

- `label` 不可为空（`@NotBlank`）。
- `taskGroup` 传空串返回 10002「分组不能为空」。
- `toolImage` 为空时不覆盖原值。
- 资源字段必须是合法 K8s Quantity，且不超过平台上限。
- `params` 传入时为全量替换：先软删旧参数再插入；参数键需匹配 `[A-Za-z_][A-Za-z0-9_]*`，重复返回 10004；`paramType` 仅 `input`/`select`/`api_select`。
- 保存概览/参数**不会**改变 `version`。

### 3.4 启用/禁用

```
PATCH /pipeline/task-kinds/{kind}/toggle
```

切换任务类型的启用状态。

### 3.5 版本列表

```
GET /pipeline/task-kinds/{kind}/versions
```

**Response `data`:** `List<TaskKindVersionVO>`

```json
[
  { "version": "1.0.1", "note": "调整模板", "createdAt": "2026-10-18T10:00:00", "isCurrent": true },
  { "version": "1.0.0", "note": "初版", "createdAt": "2026-10-17T10:00:00", "isCurrent": false }
]
```

### 3.6 发布版本

```
POST /pipeline/task-kinds/{kind}/versions
```

**Request Body（可选）：**

```json
{ "version": "2.0.0", "note": "发布说明" }
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `version` | string | 否 | 显式版本必须 semver；为空则 patch+1，首次 `1.0.0` |
| `note` | string | 否 | ≤500 字 |

重复版本返回 10004；备注超长返回 10002。

### 3.7 激活版本

```
PUT /pipeline/task-kinds/{kind}/versions/{version}/activate
```

用快照覆盖工作副本与参数，并标记为当前版本。版本不存在返回 10003；版本为空返回 10002。

### 3.8 校验命令模板

```
POST /pipeline/task-kinds/{kind}/validate
```

**Request Body：**

```json
{ "script": "echo hello\n" }
```

**Response `data`：**

```json
{ "valid": true, "errors": [] }
```

脚本为空直接返回 valid=true；否则通过 `bash -n` 校验语法。

---

## 4. 工具链选项

**Base URL:** `/pipeline/toolchains`

### 4.1 列表

```
GET /pipeline/toolchains
```

**Response `data`:** `List<ToolchainOptionVO>`

```json
[
  {
    "stack": "JAVA_MAVEN",
    "runtimeVersion": "17",
    "toolVersion": "3.9.6",
    "image": "maven:3.9-eclipse-temurin-17",
    "buildCommand": "mvn clean package",
    "testCommand": "mvn test"
  },
  {
    "stack": "NODE",
    "runtimeVersion": "20",
    "toolVersion": null,
    "image": "node:20-alpine",
    "buildCommand": "npm run build",
    "testCommand": "npm test"
  }
]
```

---

## 5. Pod 容器查询（终端调试）

**Base URL:** `/pipeline/pipelines/{pipelineId}/runs/{runId}/jobs/{jobId}`

### 5.1 查询容器列表

```
GET /pipeline/pipelines/{pipelineId}/runs/{runId}/jobs/{jobId}/containers
```

**路径参数:**

| 参数 | 类型 | 说明 |
|------|------|------|
| pipelineId | Long | 流水线 ID |
| runId | Long | 运行记录 ID |
| jobId | Long | 运行任务 ID |

**权限校验:**
1. 流水线归属当前用户/租户
2. run 属于 pipeline
3. job 属于 run

**Response `data`:** `PodContainersVO`

```json
{
  "namespace": "hfwas-pipeline",
  "podName": "task-build-abcde-pod",
  "podExists": "true",
  "workspaceKind": "pvc",
  "workspacePath": "/workspace/source/src",
  "defaultContainer": "step-build",
  "containers": [
    {
      "name": "step-build",
      "state": "running",
      "exitCode": null,
      "hasShell": true,
      "recommendedMode": "exec",
      "unavailableReason": null
    },
    {
      "name": "step-test",
      "state": "terminated",
      "exitCode": 0,
      "hasShell": true,
      "recommendedMode": "ephemeral",
      "unavailableReason": null
    }
  ]
}
```

#### PodContainersVO 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| namespace | String | K8s 命名空间 |
| podName | String | Pod 名称 |
| podExists | String | `"true"` / `"false"` / `"unknown"`（无集群时为 unknown） |
| workspaceKind | String | `"pvc"` / `"emptydir"` / `"unknown"` |
| workspacePath | String | 工作区挂载路径，固定 `/workspace/source/src` |
| containers | ContainerInfo[] | 容器列表 |
| defaultContainer | String | 推荐的默认容器名 |

#### ContainerInfo 字段

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 容器名 |
| state | String | `"running"` / `"terminated"` / `"waiting"` / `"unknown"` |
| exitCode | Integer | 退出码（terminated 时） |
| hasShell | boolean | 是否有 shell |
| recommendedMode | String | 推荐连接模式: `"exec"` / `"ephemeral"` / `"debug_pod"` / `"unavailable"` |
| unavailableReason | String | 不可用时的中文说明 |

#### Pod 状态 → 连接模式对照

| Pod 状态 | 容器状态 | 推荐模式 | 说明 |
|----------|---------|----------|------|
| Pod 存在 | Running | exec | fabric8 exec 直连 |
| Pod 存在 | Terminated/Waiting | ephemeral | 注入 netshoot 调试容器 |
| Pod 已删除 | PVC 存在 | debug_pod | 创建调试 Pod 挂载 PVC |
| Pod 已删除 | emptyDir | unavailable | 现场已丢失 |
| 无集群 | — | unavailable | 未配置执行集群 |

---

## 6. WebSocket 终端（exec）

### 连接地址

```
ws://<host>/api/ws/exec/{pipelineId}/{runId}/{jobId}?container={containerName}
```

### 认证

JWT 通过 `Sec-WebSocket-Protocol` 头部传递（非 query param）：

```javascript
// 浏览器 JavaScript
const token = await getToken()
const ws = new WebSocket(url, [token, 'pipeline-exec'])
```

服务端在握手阶段用 `JwtDecoder` 验证 token，通过后建立连接。

### 协议格式

| 方向 | 帧类型 | 格式 | 说明 |
|------|--------|------|------|
| Server → Client | Binary | PTY 字节流 | 终端输出 |
| Server → Client | Text (JSON) | `{"type":"connected", "mode":"exec", "container":"step-build"}` | 连接成功 |
| Server → Client | Text (JSON) | `{"type":"error", "message":"..."}` | 错误 |
| Server → Client | Text (JSON) | `{"type":"progress", "message":"..."}` | 进度（注入/等待） |
| Server → Client | Text (JSON) | `{"type":"ping"}` | 心跳探测 |
| Client → Server | Text (JSON) | `{"type":"pong"}` | 心跳响应 |
| Client → Server | Text (JSON) | `{"type":"input", "data":"ls -la\n"}` | 键盘输入 |
| Client → Server | Text (JSON) | `{"type":"resize", "cols":80, "rows":24}` | 终端尺寸调整 |

### 连接生命周期

```
closed → selecting（前端选择容器）→ connecting → connected → disconnected
                                                          ↓
                                                       error → closed
```

- 服务端 30s 发送一次 ping
- 连续 3 次未收到 pong（90s）自动断开
- 30 分钟 idle 超时自动断开
- 最大并发连接数 20

### 重连

前端断开后显示错误/断开提示，用户点击「重新连接」即可重新发起握手。
---

## 7. 流水线级参数

**Base URL:** `/pipeline/job-params`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/job-params?pipelineId={id}` | 按流水线列出参数 |
| GET | `/pipeline/job-params/{id}` | 参数详情 |
| POST | `/pipeline/job-params` | 创建参数 |
| PUT | `/pipeline/job-params/{id}` | 更新参数 |
| DELETE | `/pipeline/job-params/{id}` | 删除参数 |
| POST | `/pipeline/job-params/preview-api` | 预览远程 api_select 选项 |

### 7.1 创建/更新参数

```json
{
  "pipelineId": 1,
  "jobId": 10,
  "paramKey": "PROFILE",
  "paramLabel": "环境",
  "paramType": "select",
  "valueMode": "runtime",
  "defaultValue": "dev",
  "required": true,
  "sortOrder": 0,
  "options": ["dev", "prod"],
  "placeholder": "请选择环境"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pipelineId` / `jobId` | Long | 是 | 归属流水线/任务 |
| `paramKey` | string | 是 | 建议匹配 `[A-Za-z_][A-Za-z0-9_]*` |
| `paramType` | string | 否 | `input`（默认）/`select`/`api_select` |
| `valueMode` | string | 否 | `fixed`/`runtime`（缺省 runtime） |
| `defaultValue` / `required` / `sortOrder` | — | 否 | 默认值、必填、排序 |
| `options` | string[] | select 时 | 固定选项 |
| `apiUrl` / `apiMethod` / `apiHeaders` / `apiResponsePath` | — | api_select 时 | 远程选项配置 |

更新/删除不存在参数返回 10003「参数定义不存在」。

### 7.2 预览远程选项

```json
POST /pipeline/job-params/preview-api
{
  "apiUrl": "https://api.example.com/regions",
  "apiMethod": "GET",
  "apiHeaders": { "Authorization": "Bearer xxx" },
  "apiResponsePath": "$.data[].name"
}
```

```json
{
  "code": 0,
  "msg": "成功",
  "data": { "success": true, "options": ["cn-hangzhou", "cn-beijing"], "errorMessage": null },
  "requestId": "req-xxx"
}
```

非 2xx 或调用异常时 `success=false`，`errorMessage` 返回原因。

---

## 8. 运行产物

**Base URL:** `/pipeline/runs/{runId}/artifacts`

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/pipeline/runs/{runId}/artifacts` | 白名单（Tekton 回传） | 上传产物 |
| GET | `/pipeline/runs/{runId}/artifacts` | 已认证 | 产物列表 |
| GET | `/pipeline/runs/{runId}/artifacts/{artifactId}/download` | 已认证 | 下载产物 |
| GET | `/pipeline/runs/{runId}/artifacts/sbom` | 已认证 | 最新 SBOM |

### 8.1 上传

`multipart/form-data`：

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `file` | file | 是 | 产物文件 |
| `jobId` | Long | 否 | 关联运行任务 |
| `type` | string | 否 | 默认 `sbom` |

响应：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "id": 5,
    "runId": 100,
    "jobId": 10,
    "artifactType": "sbom",
    "fileName": "bom.json",
    "fileSize": 1024,
    "contentType": "application/json",
    "createTime": "2026-10-18T10:00:00"
  },
  "requestId": "req-xxx"
}
```

失败：

| 场景 | code | msg |
|------|------|-----|
| run 不存在 | 10003 | 运行记录不存在 |
| 文件保存失败 | 10001 | 保存文件失败: … |

> 当前未校验产物所属租户/run 归属，文件名未清洗（PIPE-916/917）。

---

## 9. 依赖扫描与组件

### 9.1 提交扫描

```
POST /dependency-scan/submit
```

```json
{
  "repoUrl": "https://github.com/org/app.git",
  "gitRef": "main",
  "credentialId": 1,
  "stack": "JAVA_MAVEN",
  "runtimeVersion": "21",
  "modulePath": "backend/"
}
```

`stack` 可为空（自动检测）；`modulePath` 去首尾空白与尾部斜杠。接口会创建「[依赖扫描] {repo}」流水线并立即运行。

响应 `data`：`DependencyScanVO`

```json
{
  "pipelineId": 42,
  "pipelineName": "[依赖扫描] app",
  "runId": 100,
  "repoUrl": "https://github.com/org/app.git",
  "gitRef": "main",
  "status": "QUEUED",
  "triggeredByName": "ada",
  "startedAt": "2026-10-18T10:00:00",
  "finishedAt": null,
  "errorMessage": null
}
```

### 9.2 批量提交

```
POST /dependency-scan/batch-submit
{ "scans": [ { "repoUrl": "..." }, { "repoUrl": "..." } ] }
```

顺序创建并启动，返回 `List<DependencyScanVO>`。当前 `scans=null` 会触发 NPE（PIPE-908）。

### 9.3 扫描任务列表

```
GET /dependency-scan/tasks?limit=20
```

当前实现恒返回空列表（PIPE-909）。

### 9.4 组件查询与聚合

| 方法 | 路径 | 参数 |
|------|------|------|
| GET | `/dependency/components/page` | `pageNo`、`pageSize`、`search`、`language` |
| GET | `/dependency/components/aggregated` | 同上 |

聚合按 `purl|name|version` 分组，返回 `occurrenceCount` 与最多 3 个 `usedInRepos`，按出现次数倒序。

---

## 10. Argo 工作流（后端可用、前端未接入）

**Base URL:** `/argo`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/argo/workflows/page` | 工作流分页 |
| GET | `/argo/workflows/{name}` | 详情 |
| POST | `/argo/workflows` | 创建 |
| DELETE | `/argo/workflows/{name}` | 删除 |
| POST | `/argo/workflows/{name}/submit` | 提交运行 |
| GET | `/argo/workflows/{name}/runs` | 运行分页 |
| GET | `/argo/workflows/runs/{runId}` | 运行详情 |
| GET | `/argo/workflows/runs/{runId}/logs` | 日志 |
| GET | `/argo/workflows/runs/{runId}/events` | 事件 |
| POST | `/argo/templates/page` | 模板分页 |
| GET | `/argo/templates/{name}` | 模板详情 |
| POST | `/argo/templates` | 保存模板 |

依赖 `ARGO_KUBECONFIG` 指向可达集群；文件不存在时回退到 pipeline KubernetesClient。

---

## 11. Postman Collection

同目录提供 [pipeline-core-api.postman_collection.json](./pipeline-core-api.postman_collection.json)，使用变量：

| 变量 | 说明 |
|------|------|
| `baseUrl` | 后端地址，如 `http://localhost:8089` |
| `token` | Keycloak JWT |
| `tenantId` | 当前租户 |
| `pipelineId` / `runId` / `credentialId` / `kind` / `artifactId` | 示例资源 ID |

每个接口至少包含 1 个正常响应 example 和 1 个失败响应 example。
