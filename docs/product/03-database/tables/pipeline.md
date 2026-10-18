# 流水线数据字典

> 日期：2026-10-18
> 版本：v0.3
> 状态：已完成（表级 + 关键 JSON 结构）
> 数据来源：`backend/server/src/main/resources/db/pipeline-schema.sql`、`deploy/charts/backend/files/db/05-pipeline-schema.sql`、`10~15-*.sql`、`backend/pipeline-core/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：12 张表字段、索引与写入方 |
| v0.2 | 2026-10-17 | 补充运行状态枚举与取消不更新 job 的现状 |
| v0.3 | 2026-10-18 | 补充 JSON 字段结构、枚举、种子类型、写入方与 external/embedded 差异 |

---

共 12 张表。SQLite 类型映射：`BIGINT → INTEGER`、`VARCHAR/TEXT → TEXT`、`JSON → TEXT`、`DATETIME → TEXT`、布尔用 0/1。主键为 `INTEGER PRIMARY KEY`，由 MyBatis-Plus 雪花 ID 生成。

## 1. 表关系

```text
pipeline 1 ── N pipeline_stage 1 ── N pipeline_job 1 ── N pipeline_job_param
    │                                   │
    └── 1 ── N pipeline_run 1 ── N pipeline_run_job
                    │
                    └── 1 ── N pipeline_run_artifact 1 ── N dependency_component

pipeline 1 ── N pipeline_credential（credential_id 引用）
pipeline_task_kind 1 ── N pipeline_task_kind_param
pipeline_task_kind 1 ── N pipeline_task_kind_version
```

## 2. pipeline

流水线定义。

| 字段 | 说明 |
|------|------|
| `id` | 主键（雪花） |
| `tenant_id` | 租户 |
| `name` | 名称，非空 |
| `repo_url` | Git 仓库；含 CLONE 时必填且为 http(s) |
| `git_ref` | 分支；保存时为空默认 `main` |
| `credential_id` | Git 凭证 ID |
| `deleted` / `create_by` / `update_by` / `create_time` / `update_time` | 逻辑删除与审计 |

索引：`idx_pipeline_tenant`。

## 3. pipeline_stage

阶段。

| 字段 | 说明 |
|------|------|
| `id` / `pipeline_id` | 主键与流水线 |
| `name` | 阶段名，非空 |
| `sort_order` | 顺序，缺省按数组下标 |

## 4. pipeline_job

任务定义。

| 字段 | 说明 |
|------|------|
| `id` / `pipeline_id` / `stage_id` | 主键、流水线、阶段 |
| `name` | 任务名，非空 |
| `kind` | 任务类型，见枚举 |
| `command` | 执行命令；除 CLONE/APPROVAL 外必填 |
| `stack` / `runtime_version` / `tool_version` | 工具链（JAVA_MAVEN/NODE/GO/PYTHON） |
| `sort_order` | 顺序 |
| `param_bindings` | 参数绑定 JSON，见下 |

`param_bindings` 示例：

```json
{
  "GIT_REF": { "mode": "runtime" },
  "PROFILE": { "mode": "fixed", "value": "prod" },
  "VERSION": { "mode": "runtime" }
}
```

- `mode=fixed`：值来自 `value` 或任务类型参数默认值。
- `mode=runtime`：运行弹框填写。
- `GIT_REF` 特殊：运行时值优先，否则取 `pipeline.git_ref`。

## 5. pipeline_run

运行实例。

| 字段 | 说明 |
|------|------|
| `id` / `pipeline_id` / `tenant_id` | 主键、流水线、租户 |
| `status` | 运行状态，见枚举 |
| `trigger` | 触发方式；当前手动触发为 `MANUAL` |
| `git_ref` / `commit_sha` | Git 信息；commit 从 CLONE 日志回写 |
| `triggered_by_name` | 触发人显示名 |
| `stack` / `runtime_version` / `tool_version` / `image` | 运行镜像与工具链 |
| `tekton_name` | Tekton PipelineRun/TaskRun 名称 |
| `segment_index` | 审批分段索引 |
| `error_message` | 失败原因 |
| `runtime_params` | 最终生效参数 JSON |
| `started_at` / `finished_at` | 起止时间 |
| `create_by` / `create_time` | 创建信息 |

索引：`idx_pipeline_run_pipeline`。

状态枚举：

| 值 | 含义 |
|----|------|
| `QUEUED` | 等待调度 |
| `RUNNING` | 执行中 |
| `WAITING_APPROVAL` | 等待人工审批 |
| `SUCCEEDED` | 成功 |
| `FAILED` | 失败（含未配置执行集群） |
| `CANCELLED` | 已取消 |

`runtime_params` 示例：

```json
{
  "GIT_REF": "feature/x",
  "PROFILE": "prod",
  "TOKEN": "******"
}
```

## 6. pipeline_run_job

运行中的任务结果。

| 字段 | 说明 |
|------|------|
| `id` / `run_id` / `job_id` | 主键、运行、任务定义 |
| `stage_name` / `job_name` / `kind` | 运行时的快照信息 |
| `command` | 实际命令（已做 `CMD_` 替换） |
| `status` | 与 run 相同的状态枚举 |
| `log_text` | 日志文本，可能很大 |
| `started_at` / `finished_at` | Tekton step 真实起止时间 |
| `pod_name` / `namespace` | 执行 Pod |
| `containers` | 容器名 JSON 数组，如 `["step-build"]` |

## 7. pipeline_job_param

流水线级运行参数定义。

| 字段 | 说明 |
|------|------|
| `id` / `pipeline_id` / `job_id` | 主键、流水线、任务 |
| `param_key` / `param_label` | 参数键/显示名；键匹配 `[A-Za-z_][A-Za-z0-9_]*` |
| `param_type` | `input` / `select` / `api_select` |
| `value_mode` | `fixed` / `runtime` |
| `default_value` / `required` / `sort_order` | 默认值、必填、排序 |
| `options_json` | select 选项数组，如 `["dev","prod"]` |
| `api_url` / `api_method` / `api_headers_json` / `api_response_path` | api_select 远程配置 |
| `placeholder` | 输入占位提示 |
| `deleted` / 审计字段 | 逻辑删除与审计 |

索引：`idx_job_param_job`、`idx_job_param_pipeline`。

## 8. pipeline_task_kind

任务类型（任务市场）。

| 字段 | 说明 |
|------|------|
| `id` / `tenant_id` | 主键、租户 |
| `kind_value` / `label` / `task_group` | 类型值、名称、分组 |
| `description` / `hint` | 描述与提示 |
| `default_command` / `requires_command` | 默认命令、是否必须填命令 |
| `enabled` / `sort_order` | 启用与排序 |
| `tool_image` / `default_image` / `command_template` | 工具镜像与命令模板 |
| `cpu_request` / `cpu_limit` / `memory_request` / `memory_limit` | K8s Quantity 资源限制 |
| `version` | 当前激活版本；保存概览不改变 |
| `deleted` / 审计字段 | 逻辑删除与审计 |

索引：`idx_task_kind_tenant`、`idx_task_kind_group`。

种子任务类型 19 行（18 个 enabled）：CLONE、BUILD、IMAGE、LINT_SEMGREP、FORMAT、LINT_SONAR、DEPENDENCY_ANALYSIS、SCAN、PACKAGE、PUBLISH、UPLOAD、DEPLOY、TEST、CUSTOM、APPROVAL、NOTIFY、KUBECTL、DEPENDENCY_TRACK，以及 `enabled=0` 的 IMAGE_COSIGN（仅作为 IMAGE 的编译器子步骤，不进入任务市场）。

## 9. pipeline_task_kind_param

任务类型参数定义。

| 字段 | 说明 |
|------|------|
| `id` / `kind_value` | 主键、任务类型 |
| `param_key` / `param_label` / `param_type` | 参数标识、名称、类型 |
| `default_value` / `required` / `sort_order` | 默认值、必填、顺序 |
| `options_json` | select 选项 |
| `api_url` / `api_method` / `api_headers_json` / `api_response_path` | api_select 远程配置 |
| `placeholder` | 占位提示 |
| `visibility` | `user` 展示 / `hidden` 不展示且最终使用默认值 |
| `deleted` / 审计字段 | 逻辑删除与审计 |

索引：`idx_task_kind_param`。

## 10. pipeline_task_kind_version

任务类型版本快照。

| 字段 | 说明 |
|------|------|
| `id` / `kind_value` / `version` | 主键、类型、semver |
| `note` | 版本说明，≤500 字 |
| `snapshot_json` | 快照 JSON |
| `create_time` | 创建时间 |

索引：`idx_task_kind_version_kind`。

`snapshot_json` 包含：

```json
{
  "label": "构建",
  "taskGroup": "构建",
  "description": "",
  "hint": "",
  "defaultCommand": "",
  "toolImage": "maven:3.9",
  "commandTemplate": "mvn package",
  "sortOrder": 10,
  "cpuRequest": "500m",
  "cpuLimit": "1",
  "memoryRequest": "512Mi",
  "memoryLimit": "1Gi",
  "params": [
    { "paramKey": "PROFILE", "paramLabel": "Profile", "paramType": "input",
      "defaultValue": "dev", "required": false, "visibility": "user" }
  ]
}
```

激活版本会用该快照覆盖工作副本，但**不包含** `enabled`、`requiresCommand`、`defaultImage`。

## 11. pipeline_credential

凭证。

| 字段 | 说明 |
|------|------|
| `id` / `tenant_id` | 主键、租户 |
| `name` | 名称 |
| `kind` | `PASSWORD` / `TOKEN` / `KUBECONFIG` |
| `username` | 用户名；TOKEN 缺省 `x-access-token` |
| `secret_enc` | AES/GCM 密文（Base64，前 12 字节 IV） |
| `deleted` / 审计字段 | 逻辑删除与审计 |

加密密钥由 `pipeline.credential-key` 经 SHA-256 派生；密钥明文不落库，接口 VO 不返回。

## 12. pipeline_run_artifact

运行产物。

| 字段 | 说明 |
|------|------|
| `id` / `run_id` / `job_id` | 主键、运行、任务（jobId 可空） |
| `artifact_type` | 类型，默认 `sbom` |
| `file_name` / `file_size` / `content_type` | 文件信息 |
| `storage_path` | 相对路径 `{runId}/{uuid}/{fileName}` |
| `create_time` | 时间 |

索引：`idx_artifact_run`。存储根目录由 `pipeline.artifact.storage` 配置，默认 `data/artifacts`。

## 13. dependency_component

依赖组件（SBOM）。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `artifact_id` / `run_id` / `pipeline_id` | 关联产物/运行/流水线 |
| `identity_key` | 唯一标识：优先 purl，否则 `gav:{group}|{name}|{version}` |
| `purl` / `group_name` / `name` / `version` | 组件信息 |
| `license` / `scope` / `language` | 许可证、范围、语言 |
| `create_time` | 时间 |

索引/约束：`uk_dep_comp_pipeline_identity`（`pipeline_id + identity_key` 唯一）、`idx_dep_comp_purl`、`idx_dep_comp_run`、`idx_dep_comp_name`。

## 14. Schema 与迁移

| 模式 | 文件 | 说明 |
|------|------|------|
| embedded | `backend/server/src/main/resources/db/pipeline-schema.sql` | 应用启动执行 |
| external | `deploy/charts/backend/files/db/05-pipeline-schema.sql` | Helm initContainer 执行 |
| 增量 | `10-add-runtime-params.sql` | `pipeline_run.runtime_params` |
| 增量 | `11-add-task-kind-resource-limits.sql` | 任务类型资源限制 |
| 增量 | `12-add-pipeline-job-param-bindings.sql` | `pipeline_job.param_bindings` |
| 增量 | `15-task-kind-version-visibility.sql` | 参数 `visibility`；external 创建 `pipeline_task_kind_version` |
| 增量 | `16-upgrade-task-template-seeds.sql` | 历史默认命令/DT_FAIL_ON 选项；唯一允许 UPDATE 的迁移 |

> 05 为零 UPDATE 基线：种子模板/选项直接写入 `INSERT OR IGNORE`；历史库通过 `16-*` 精确匹配旧默认值升级。embedded 与 external 的任务类型、DT 参数、模板、IMAGE_COSIGN 已对齐。

## 15. 写入方

| 表 | 归属 |
|----|------|
| pipeline/stage/job | `PipelineDefinitionService` |
| 运行/运行任务 | `PipelineRunService`、`TektonPipelineExecutor` |
| 任务类型/版本 | `PipelineTaskKindService` |
| 凭证 | `PipelineCredentialService` |
| 流水线级参数 | `PipelineJobParamService` |
| 产物 | `PipelineRunArtifactService` |
| 依赖组件 | `SbomParserService`、`DependencyScanService` |
| 依赖查询 | `DependencyComponentService` |

## 16. 注意

1. `pipeline_run_job.log_text` 可能很大，需关注 SQLite 单行/文件体积；日志在内存中按单容器 512KB 截断后写入。
2. 取消运行当前只更新 `pipeline_run`，不更新 `pipeline_run_job`（PIPE-903）。
3. 删除流水线不级联删除 run/artifact（PIPE-902）。
4. 更新流水线时旧 `pipeline_job_param` 不清理，可能残留参数定义（PIPE-901）。
5. 任务类型当前无租户过滤，所有租户共享种子与自定义类型（PIPE-912）。
6. 产物接口当前未校验租户/run 归属，文件名未清洗（PIPE-916/917）。
