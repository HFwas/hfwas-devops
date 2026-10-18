# 流水线接口文档

> 日期：2026-10-18
> 版本：v0.2
> 状态：已完成（路径、契约、错误码、示例与 Postman）
> 数据来源：`backend/pipeline-core/.../controller/`、`backend/server/.../ws/`、`docs/pipeline/pipeline-core-api.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：11 个 Controller 的方法与路径清单（含依赖分析与 Argo） |
| v0.2 | 2026-10-18 | 补齐请求/响应、状态枚举、错误码与 Postman；与 canonical API 文档对齐 |

---

## 1. 文档入口

| 形态 | 位置 | 说明 |
|------|------|------|
| Markdown（canonical） | [docs/pipeline/pipeline-core-api.md](../../../pipeline/pipeline-core-api.md) | 全部接口、字段、示例、WebSocket 协议 |
| Postman v2.1 | [docs/pipeline/pipeline-core-api.postman_collection.json](../../../pipeline/pipeline-core-api.postman_collection.json) | 43 个请求，含正常/失败 examples |
| 本文 | 产品视角索引 | 路径、鉴权、关键规则与已知问题 |

## 2. 通用约定

| 项目 | 说明 |
|------|------|
| Base URL | `http://localhost:8089`（后端直连）；前端经 `/api` 代理 |
| 鉴权 | `Authorization: Bearer <Keycloak JWT>` |
| 租户 | `X-Tenant-Id`；缺省取 JWT/默认租户 |
| 响应 | `BaseResult<T>{code,msg,data,requestId}` |
| 分页 | 默认 `pageNo=1`、`pageSize=10`，上限 100 |
| 业务失败 | HTTP 200 + `code != 0`；认证/权限 401/403 |
| 错误码 | 10001 内部错误、10002 参数/业务校验、10003 不存在、10004 重复、10005 操作失败、11010 租户上下文缺失 |
| 白名单 | `/pipeline/runs/{runId}/artifacts` POST（Tekton 回传）、`/ws/exec/**` |

## 3. 状态与枚举

**运行/任务状态：** `QUEUED`、`RUNNING`、`WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`CANCELLED`。

**凭证类型：** `PASSWORD`、`TOKEN`、`KUBECONFIG`。

**参数类型：** `input`、`select`、`api_select`；**值来源：** `fixed`、`runtime`；**可见性：** `user`、`hidden`。

**任务类型：** CLONE、LINT_SEMGREP、LINT_SONAR、BUILD、TEST、SCAN、PACKAGE、CUSTOM、IMAGE、PUBLISH、UPLOAD、DEPLOY、APPROVAL、NOTIFY、FORMAT、DEPENDENCY_ANALYSIS、KUBECTL、DEPENDENCY_TRACK。

## 4. 接口清单

### 4.1 流水线

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/pipeline/pipelines/page` | 分页（pageNo/pageSize/keyword） |
| POST | `/pipeline/pipelines` | 创建，返回 id |
| GET | `/pipeline/pipelines/{id}` | 详情（含 stages/jobs/lastRun） |
| PUT | `/pipeline/pipelines/{id}` | 更新（删除旧 stage/job 后重建） |
| DELETE | `/pipeline/pipelines/{id}` | 逻辑删除 |
| GET | `/pipeline/pipelines/{id}/runs` | 运行分页 |
| POST | `/pipeline/pipelines/{id}/runs` | 触发运行，body `{params:{}}` |
| GET | `/pipeline/pipelines/{id}/runs/default-params` | 运行弹框参数 |
| GET | `/pipeline/pipelines/{id}/runs/{runId}` | 运行详情（含 jobs） |
| POST | `/pipeline/pipelines/{id}/runs/{runId}/cancel` | 取消 |
| POST | `/pipeline/pipelines/{id}/runs/{runId}/approve` | 审批通过（无 body） |

创建校验：名称必填；至少 1 阶段且每阶段至少 1 任务；全局至多 1 CLONE；APPROVAL 独占阶段；同阶段至多 1 IMAGE；除 CLONE/APPROVAL 外命令必填；含 CLONE 时 repoUrl 必须为 http(s)；gitRef 缺省 `main`。

运行初始状态：无集群 `FAILED`；首阶段审批 `WAITING_APPROVAL`；否则 `QUEUED`。审批续跑/并发规则见 canonical 文档。

### 4.2 凭证

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/credentials` | 全部凭证 |
| GET | `/pipeline/credentials/{id}` | 详情 |
| POST | `/pipeline/credentials/page` | 分页 |
| POST | `/pipeline/credentials` | 创建/更新（id 为空创建） |
| DELETE | `/pipeline/credentials/{id}` | 删除；被引用时 10002 |

PASSWORD 必须有用户名；TOKEN 缺省 `x-access-token`；secret AES-GCM 存储且不在响应中返回；更新时 secret 留空不修改。

### 4.3 任务类型（任务市场）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/task-kinds` | 全部类型（含禁用） |
| GET | `/pipeline/task-kinds/{kind}` | 详情 |
| PUT | `/pipeline/task-kinds/{kind}` | 更新概览/参数；不升版本 |
| PATCH | `/pipeline/task-kinds/{kind}/toggle` | 启停 |
| GET | `/pipeline/task-kinds/{kind}/versions` | 版本列表 |
| POST | `/pipeline/task-kinds/{kind}/versions` | 发布版本（semver，缺省 patch+1） |
| PUT | `/pipeline/task-kinds/{kind}/versions/{version}/activate` | 激活版本 |
| POST | `/pipeline/task-kinds/{kind}/validate` | `bash -n` 校验脚本 |

### 4.4 流水线级参数

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/job-params?pipelineId={id}` | 列表 |
| GET | `/pipeline/job-params/{id}` | 详情 |
| POST | `/pipeline/job-params` | 创建 |
| PUT | `/pipeline/job-params/{id}` | 更新 |
| DELETE | `/pipeline/job-params/{id}` | 删除 |
| POST | `/pipeline/job-params/preview-api` | 预览 api_select 选项 |

### 4.5 工具链与产物

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/toolchains` | 工具链模板 |
| POST | `/pipeline/runs/{runId}/artifacts` | 上传产物（multipart：file/jobId/type） |
| GET | `/pipeline/runs/{runId}/artifacts` | 产物列表 |
| GET | `/pipeline/runs/{runId}/artifacts/{artifactId}/download` | 下载 |
| GET | `/pipeline/runs/{runId}/artifacts/sbom` | 最新 SBOM |

### 4.6 依赖扫描与组件

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/dependency-scan/submit` | 提交扫描（自动建流水线并运行） |
| POST | `/dependency-scan/batch-submit` | 批量提交 |
| GET | `/dependency-scan/tasks?limit=20` | 任务列表（当前恒空，PIPE-909） |
| GET | `/dependency/components/page` | 组件分页 |
| GET | `/dependency/components/aggregated` | 组件聚合 |

### 4.7 Pod 容器与终端

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/pipeline/pipelines/{pipelineId}/runs/{runId}/jobs/{jobId}/containers` | 容器列表与推荐模式 |
| WS | `/ws/exec/{pipelineId}/{runId}/{jobId}` | 终端；JWT 通过 `Sec-WebSocket-Protocol` |

终端模式：`exec`（运行中）、`ephemeral`（已退出/等待）、`debug_pod`（Pod 删除但 PVC 存在）、`unavailable`。最大 20 会话；30s ping、90s 无 pong 断开、30 分钟空闲断开。

### 4.8 Argo（后端可用、前端未接入）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/argo/workflows/page` | 工作流分页 |
| GET/POST/DELETE | `/argo/workflows/*` | 详情/创建/删除/提交/运行/日志/事件 |
| POST/GET | `/argo/templates/*` | 模板分页/详情/保存 |

## 5. 已知问题

| 编号 | 问题 |
|------|------|
| PIPE-908 | 批量扫描 `scans=null` 触发 NPE |
| PIPE-909 | `/dependency-scan/tasks` 恒返回空列表 |
| PIPE-910 | 前端 api_select 未渲染下拉 |
| PIPE-911 | 凭证引用统计未加租户条件 |
| PIPE-915 | fixed 参数可被运行时覆盖 |
| PIPE-916 | 产物接口未校验租户/run 归属 |
| PIPE-917 | 产物文件名未清洗 |

## 6. 变更维护

接口字段、错误码或行为变化时，同步更新：

1. [docs/pipeline/pipeline-core-api.md](../../../pipeline/pipeline-core-api.md)（Markdown）
2. [docs/pipeline/pipeline-core-api.postman_collection.json](../../../pipeline/pipeline-core-api.postman_collection.json)（Postman）
3. 本文与 `docs/product/02-features/modules/pipeline.md`、`03-database/tables/pipeline.md`
