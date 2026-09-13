# pipeline-core API 接口文档

> 日期：2026-09-13
> 版本：v0.3

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-09-08 | 初版：现有接口与 VO 说明 |
| v0.2 | 2026-09-13 | 标明运行 job 的 startedAt/finishedAt 取自 Tekton step 真实起止时间，完成后不再被 watch 轮询刷新 |
| v0.3 | 2026-09-13 | TaskKindVO / 更新接口增加预置环境变量 `params` |

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
| 10002 | "请求参数错误" | BAD_REQUEST |
| 10003 | "资源不存在" | NOT_FOUND |
| 11001 | "未登录或登录已过期" | UNAUTHORIZED |
| 11002 | "无权访问" | FORBIDDEN |

### 认证方式

所有接口（除 health check）通过 Spring Security OAuth2 Resource Server 鉴权，请求头携带：

```
Authorization: Bearer <keycloak-jwt-token>
```

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
  "keyword": "流水线名称"   // 可选，模糊搜索
}
```

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

**Response `data`:** `PipelineRunVO`

### 1.8 获取运行详情

```
GET /pipeline/pipelines/{id}/runs/{runId}
```

**Response `data`:** `PipelineRunVO`（含 jobs 数组）

### 1.9 取消运行

```
POST /pipeline/pipelines/{id}/runs/{runId}/cancel
```

**Response `data`:** `PipelineRunVO`

### 1.10 审批通过

```
POST /pipeline/pipelines/{id}/runs/{runId}/approve
```

**Response `data`:** `PipelineRunVO`

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
  "name": "GitHub Token",
  "kind": "TOKEN",
  "username": "bot-user",
  "secret": "ghp_xxx"
}
```

**Response `data`:** `Long` — 凭证 ID

### 2.4 删除

```
DELETE /pipeline/credentials/{id}
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

**Validation:** `label` 字段不可为空（`@NotBlank`）。`params` 若传入则为该 Kind 预置环境变量的全量替换（静态值 / 枚举 / 远程接口）。

### 3.4 启用/禁用

```
PATCH /pipeline/task-kinds/{kind}/toggle
```

切换任务类型的启用状态。

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