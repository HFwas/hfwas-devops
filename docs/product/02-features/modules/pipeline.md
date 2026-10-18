# 流水线 / CI 功能规格

> 日期：2026-10-18
> 版本：v0.3
> 状态：已完成（核心功能与规则）
> 数据来源：`backend/pipeline-core/`、`backend/server/.../ws/`、`frontend/src/modules/pipeline/`、`docs/pipeline/`、`deploy/charts/backend/files/db/05-pipeline-schema.sql`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：流水线编排、运行、任务市场、凭证、依赖分析 |
| v0.2 | 2026-10-17 | 补充实际运行状态枚举、审批/取消/并发规则、参数合并语义；标注前端未接入 Pod 终端与产物下载 |
| v0.3 | 2026-10-18 | 补全任务类型枚举、凭证/版本/产物/依赖扫描/终端细节、前端现状与 PIPE-9xx 已知问题；更新验收标准 |

---

## 1. 概述

流水线模块把用户定义的阶段/任务编译为 Tekton PipelineRun，在 Kubernetes 集群执行，并把运行状态、日志、产物、SBOM 回写到平台。数据按 `tenant_id` 隔离。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 研发工程师 | 创建流水线、选择任务与参数、触发运行、查看日志、审批 |
| 平台管理员 | 维护任务类型/版本、工具链、凭证、依赖扫描 |
| SRE | 通过 Helm/KUBECTL 任务完成部署，查看运行产物与 SBOM |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 流水线列表 | 分页、关键词搜索、运行、删除 | `/pipeline/pipelines` | 可用 |
| 流水线编辑 | 阶段、任务、参数、工具链、Git 绑定、保存并运行 | `/pipeline/pipelines/new`、`/:id/edit` | 可用 |
| 流水线详情 | 只读画布、运行历史（状态/时间/关键词筛选） | `/pipeline/pipelines/:id`、`/history` | 可用 |
| 运行详情 | 任务状态、阶段/全程日志、审批、取消 | `/:pipelineId/runs/:runId` | 可用（日志 3 秒轮询） |
| 任务市场 | 任务类型列表/详情、启停、参数、版本发布/激活 | `/pipeline/task-kinds` | 可用 |
| 凭证管理 | Git/镜像仓库/kubeconfig 凭证 | `/pipeline/credentials` | 可用（无连接测试） |
| 依赖分析 | 提交扫描、组件查询/聚合 | 运行详情/接口 | 部分可用 |
| Pod 终端 | WebSocket exec/ephemeral/debug Pod | 无前端入口 | 后端可用、前端未接入 |
| 运行产物/SBOM | 上传、列表、下载、最新 SBOM | 无前端入口 | 后端可用、前端未接入 |
| Argo 工作流 | 工作流/模板/运行/日志 | 前端无路由 | 后端可用、前端未接入 |

## 4. 核心流程

### 4.1 流水线定义

```text
新建/编辑
  → 名称必填；至少 1 个阶段，每阶段至少 1 个任务
  → 全局至多 1 个 CLONE；APPROVAL 必须独占阶段；同阶段至多 1 个 IMAGE
  → 除 CLONE/APPROVAL 外，任务命令必填
  → 含 CLONE 时 repoUrl 必须为合法 http(s) 地址；gitRef 为空默认 main
  → 保存：创建时插入 pipeline/stage/job；更新时删除旧 stage/job 后重建
```

### 4.2 运行与状态机

```text
触发运行
  → 合并运行时参数并校验（必填、select 选项范围）
  → 无执行集群：run=FAILED，所有 job=FAILED，error 写入「未配置执行集群」
  → 首个阶段是审批：run=WAITING_APPROVAL，审批 job=WAITING_APPROVAL
  → 否则：run=QUEUED；未超并发则提交执行器
  → 执行器每 2 秒轮询 Tekton：
       job 成功/失败/运行中 → 回写状态
       run 结束 → 按 ApprovalPlan 进入下一段 / WAITING_APPROVAL / SUCCEEDED
       结束一个 run 后按 FIFO 取出下一个 QUEUED
```

状态枚举：`QUEUED`、`RUNNING`、`WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`CANCELLED`。

### 4.3 审批

```text
仅 run=WAITING_APPROVAL 可 approve（无请求体、无驳回）
  → 将第一个 WAITING_APPROVAL 的 APPROVAL job 置 SUCCEEDED
  → 还有审批：保持 WAITING_APPROVAL，标记下一个审批 job
  → 无下一段：run=SUCCEEDED
  → 有下一段：run=QUEUED，segmentIndex=下一段并提交（当前路径不检查并发上限）
```

### 4.4 取消

```text
run 已终态（SUCCEEDED/FAILED/CANCELLED）→ 原样返回
否则 → run=CANCELLED + finishedAt；best-effort 取消 Tekton 执行
      → 当前不更新 pipeline_run_job（PIPE-903）
```

### 4.5 任务类型版本

```text
编辑概览/参数 → 保存工作副本，不升版本
发布版本 → 快照当前配置；版本号显式时必须 semver，否则 patch+1（首次 1.0.0）
激活版本 → 用快照覆盖工作副本与参数，并标记为当前版本
```

### 4.6 运行产物与 SBOM

```text
任务回传产物（multipart：jobId 可选、type 默认 sbom、file 必传）
  → 落盘 {pipeline.artifact.storage}/{runId}/{uuid}/{fileName}
  → type=sbom 或文件名含 sbom：解析 CycloneDX components
       → 按 identityKey（purl 或 gav:group|name|version）在本次 SBOM 内去重
       → 按 (pipeline_id, identity_key) upsert dependency_component
  → 查询：列表、下载、最新 SBOM
```

### 4.7 依赖扫描

```text
POST /dependency-scan/submit
  → 新建流水线「[依赖扫描] {repo名}」，固定 3 阶段：
       CLONE → BUILD → DEPENDENCY_ANALYSIS
  → gitRef 默认 main；按 stack 生成 BUILD/分析命令；modulePath 作为 cd 前缀
  → 立即触发运行，返回 pipelineId/runId/status
```

## 5. 任务类型与命令

### 5.1 任务类型枚举

`CLONE`、`LINT_SEMGREP`、`LINT_SONAR`、`BUILD`、`TEST`、`SCAN`、`PACKAGE`、`CUSTOM`、`IMAGE`、`PUBLISH`、`UPLOAD`、`DEPLOY`、`APPROVAL`、`NOTIFY`、`FORMAT`、`DEPENDENCY_ANALYSIS`、`KUBECTL`、`DEPENDENCY_TRACK`。

| 规则 | 说明 |
|------|------|
| 命令必填 | 除 `CLONE`、`APPROVAL` 外，`command` 不能为空 |
| 种子任务类型 | 17 个：CLONE、BUILD、IMAGE、LINT_SEMGREP、FORMAT、LINT_SONAR、DEPENDENCY_ANALYSIS、SCAN、PACKAGE、PUBLISH、UPLOAD、DEPLOY、TEST、CUSTOM、APPROVAL、NOTIFY、DEPENDENCY_TRACK；`KUBECTL` 仅枚举无种子 |
| 参数类型 | `input`、`select`、`api_select`；空默认 `input`，非法值拒绝 |
| 参数可见性 | `user`（运行弹框展示）、`hidden`（不展示，最终使用市场默认值） |
| 参数键 | 正则 `[A-Za-z_][A-Za-z0-9_]*`；同一任务类型内不得重复 |
| 版本 | 发布显式版本必须 semver；重复版本 10004；备注 ≤500 字 |
| 资源限制 | CPU/Memory request/limit 需为 K8s Quantity，并受平台上限约束 |
| 启停 | 禁用后不出现在流水线编辑器的任务列表 |

## 6. 业务规则

| 规则 | 说明 |
|------|------|
| 租户隔离 | `pipeline`、`pipeline_credential`、`pipeline_task_kind` 带 `tenant_id`；查询/写入强制当前租户 |
| 分页 | 默认 `pageNo=1`、`pageSize=10`，上限 100；按 `updateTime` 倒序 |
| 凭证类型 | `PASSWORD`、`TOKEN`、`KUBECONFIG` |
| 凭证校验 | `PASSWORD` 必须有用户名；`TOKEN` 无用户名默认 `x-access-token`；创建或更新传密钥时必须非空 |
| 凭证加密 | AES/GCM/NoPadding，256 位密钥由 `pipeline.credential-key` 派生；`secret_enc` 密文存储 |
| 凭证更新 | 密钥留空保留原密文；接口 VO 不返回密钥 |
| 凭证删除 | 被流水线引用时返回 10002；当前引用统计未加租户条件（PIPE-911） |
| 参数合并 | fixed 绑定取绑定值/默认值；用户参数覆盖默认值；hidden 最终覆盖用户值（PIPE-915）；`GIT_REF` 优先取运行时值 |
| 命令替换 | 仅 `CMD_` 前缀参数替换命令中的 `${CMD_KEY}`，其他占位不替换 |
| 运行并发 | `pipeline.max-concurrent-runs` 默认 10（按租户统计 RUNNING）；超出保持 QUEUED，按 id FIFO |
| 运行日志 | 执行器 2 秒轮询；逐容器汇聚；掩码凭证与 `SONAR_TOKEN`/`COSIGN_PRIVATE_KEY`/`S3_SECRET_KEY`；单容器截断 512KB |
| commitSha | 从 CLONE 日志中的 commit 标记回写 |
| 产物存储 | `pipeline.artifact.storage`，默认 `data/artifacts`；路径 `{runId}/{uuid}/{fileName}` |
| 依赖组件 | `(pipeline_id, identity_key)` 唯一；聚合按 `purl|name|version` 分组，`occurrenceCount` 为出现次数 |
| 逻辑删除 | pipeline/credential/task kind/job param/artifact 使用 `deleted` 0/1 |

## 7. 凭证与运行参数

### 7.1 凭证

```json
{
  "name": "git-prod",
  "kind": "PASSWORD",
  "username": "ada",
  "secret": "******"
}
```

- 创建时 `id` 为空；更新时传 `id`，`secret` 留空表示不修改。
- 密钥不会出现在列表/详情响应中。
- 当前不校验重名（PIPE-905）。

### 7.2 运行时参数

```json
{
  "params": {
    "GIT_REF": "feature/x",
    "PROFILE": "prod",
    "CMD_VERSION": "1.2.3"
  }
}
```

- 参数来源：`input` 手填、`select` 固定选项、`api_select` 远程 API（后端 `getDefaultParams` 预取选项，前端当前仍按 Input 渲染，PIPE-910）。
- 仅 `runtime` 绑定且非 hidden 的参数出现在运行弹框。
- 必填为空或 select 值不在选项内 → 10002。

## 8. 异常与边界

| 场景 | 系统行为 |
|------|----------|
| 名称为空 | 10002「名称不能为空」 |
| 阶段/任务为空 | 10002「至少需要一个阶段/任务」 |
| 多个 CLONE / 审批共享阶段 / 并行 IMAGE | 10002，提示具体约束 |
| 含 CLONE 但 repoUrl 非法 | 10002（GitRemote 解析失败） |
| 未知任务类型 | 10002「未知任务类型」 |
| 运行参数必填缺失 | 10002「运行时参数 [label] 为必填项」 |
| select 值不在范围 | 10002「值不在可选范围内」 |
| 无执行集群 | run/job 均 FAILED，error 含「未配置执行集群」 |
| 非待审批状态 approve | 10002「当前运行不在待审批」 |
| 取消终态运行 | 原样返回 |
| 凭证被引用时删除 | 10002「凭证仍被流水线引用，无法删除」 |
| 产物 run 不存在 | 10003「运行记录不存在」 |
| 产物文件缺失 | 10003「产物文件不存在」 |
| SBOM 解析失败 | 仅记录日志，不影响上传 |
| 批量扫描 `scans=null` | 当前 NPE（PIPE-908） |
| 扫描任务列表 | 当前恒返回空列表（PIPE-909） |
| 产物跨租户访问 | 当前未校验归属（PIPE-916） |
| 产物文件名含 `../` | 当前未清洗，存在路径穿越风险（PIPE-917） |

## 9. 前端现状

| 项目 | 现状 |
|------|------|
| 运行日志 | 3 秒轮询，非 WebSocket |
| Pod 终端 | 无前端入口；后端 `/ws/exec` 可用 |
| 运行产物/SBOM | 无前端入口 |
| 审批 | 仅「通过」，无驳回 |
| api_select | 后端已返回 options，前端仍渲染普通 Input（PIPE-910） |
| 任务类型编辑 | 仅平台 admin 可编辑概览/参数/版本；普通用户只读 |
| 运行历史 | 单次最多拉取 200 条 |
| 凭证 | 新建/编辑/删除；无连接测试；编辑时密钥留空不修改 |
| 删除/取消 | 使用统一 ConfirmDialog 二次确认 |

## 10. 接口与数据

- 接口：`/pipeline/pipelines/*`、`/pipeline/credentials/*`、`/pipeline/task-kinds/*`、`/pipeline/job-params/*`、`/pipeline/toolchains`、`/pipeline/runs/{runId}/artifacts/*`、`/dependency-scan/*`、`/dependency/components/*`、`/argo/*`
- WebSocket：`/ws/exec/{pipelineId}/{runId}/{jobId}`
- 详见 [04-api/modules/pipeline.md](../../04-api/modules/pipeline.md)
- 数据表：`pipeline`、`pipeline_stage`、`pipeline_job`、`pipeline_run`、`pipeline_run_job`、`pipeline_job_param`、`pipeline_credential`、`pipeline_run_artifact`、`pipeline_task_kind`、`pipeline_task_kind_param`、`pipeline_task_kind_version`、`dependency_component`
- 详见 [03-database/tables/pipeline.md](../../03-database/tables/pipeline.md)

## 11. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-PIPE-01 | 多阶段流水线按阶段顺序执行，阶段内任务可并行 |
| AC-PIPE-02 | input/select/api_select 三种参数来源正确传递；必填与选项范围校验生效 |
| AC-PIPE-03 | 运行日志可回看；敏感信息被掩码 |
| AC-PIPE-04 | 取消运行能终止 Tekton 执行 |
| AC-PIPE-05 | 凭证不以明文返回，删除受引用保护 |
| AC-PIPE-06 | 运行产物可上传/下载，SBOM 可解析与查询 |
| AC-PIPE-07 | 跨租户无法看到其他租户流水线/运行/凭证 |
| AC-PIPE-08 | 审批按 ApprovalPlan 正确分段；连续审批保持等待 |
| AC-PIPE-09 | 任务类型版本发布/激活可恢复快照 |
| AC-PIPE-10 | 依赖扫描可自动创建并运行扫描流水线 |

## 12. 已知问题（PIPE-9xx）

| 编号 | 问题 |
|------|------|
| PIPE-901 | 更新流水线时旧 `pipeline_job_param` 不清理 |
| PIPE-902 | 删除流水线不级联 run/artifact |
| PIPE-903 | 取消运行不更新 `pipeline_run_job` |
| PIPE-904 | 审批续跑绕过并发上限 |
| PIPE-905 | 凭证允许重名 |
| PIPE-906 | 后端未校验 select 参数至少一个选项 |
| PIPE-907 | `KUBECTL` 有枚举无种子类型 |
| PIPE-908 | 批量扫描 `scans=null` 触发 NPE |
| PIPE-909 | `/dependency-scan/tasks` 恒返回空列表 |
| PIPE-910 | 前端 api_select 未渲染下拉 |
| PIPE-911 | 凭证引用统计未加租户条件 |
| PIPE-912 | 任务类型无租户隔离 |
| PIPE-913 | 前端无 Pod 终端/产物入口 |
| PIPE-914 | 运行日志为轮询而非 WebSocket |
| PIPE-915 | fixed 参数可被运行时覆盖 |
| PIPE-916 | 产物接口未校验租户/run 归属 |
| PIPE-917 | 产物文件名未清洗 |

## 13. 待确认

1. Dependency-Track 集成的实际开关与配置项。
2. Argo 前端接入计划。
3. 前端 Pod 终端、运行产物/SBOM 下载入口的排期。
4. 取消运行是否同步更新 job 状态；审批续跑是否受并发限制。
5. 依赖扫描任务列表接口是补实现还是下线。
6. 任务类型是否需要按租户隔离。
