# 术语表与领域模型

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成
> 数据来源：8 个模块 schema SQL、`backend/*/controller`、`README.md`、模块设计文档

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：统一术语、核心实体与关系 |

---

## 1. 平台级术语

| 术语 | 英文 / 代码 | 定义 |
|------|-------------|------|
| 租户 | Tenant / `sys_tenant` | 逻辑隔离单元，拥有独立的用户成员、项目、流水线、集群与镜像仓库。默认租户 ID 由 `UserSchemaMigration` 初始化 |
| 用户 | User / `sys_user` | 平台账号。可由 Keycloak 登录自动落库，也可由管理员创建 |
| 平台角色 | Platform Role / `sys_user.role` | `admin`（平台管理员）或 `user`（普通用户） |
| 租户角色 | Tenant Role / `sys_tenant_member.tenant_role` | `tenant_admin`（租户管理员）或 `member`（租户成员） |
| 租户上下文 | Tenant Context | 通过请求头 `X-Tenant-Id`、JWT 或用户默认租户解析出的当前租户；由 `TenantContextFilter` 建立 |
| 产品 | Product | 控制台顶部产品切换器中的一项，如 PM、流水线、容器管理，定义于 `frontend/src/shared/console/products.ts` |
| 工作台 | Workbench | 登录后的聚合首页 `/workbench`，提供快捷入口、最近访问与我的事项 |
| 请求追踪号 | Request ID | 请求头/响应头 `X-Request-Id`，同时注入 `BaseResult.requestId` 与日志 MDC |

## 2. 项目管理（PM）

| 术语 | 代码 / 表 | 定义 |
|------|-----------|------|
| 项目 | `pm_project` | 事项的顶层容器，按租户隔离，项目编码在租户内唯一 |
| 项目成员 | `pm_project_member` | 项目与用户的关联及项目内角色 |
| 事项 | Work Item / `pm_work_item` | 需求、任务、缺陷等统一模型，使用 `item_key`（如 `DEMO-1`）对外标识 |
| 事项类型 | Work Item Type / `pm_work_item_type` | 由 `type_code` 标识的类型定义，如需求、任务、缺陷 |
| 项目事项类型 | `pm_project_issue_type` | 项目启用了哪些事项类型及其排序 |
| 模块 | `pm_project_module` | 项目内的树形模块，用于归类事项 |
| 字段定义 | Field Definition / `pm_field_definition` | 项目级/系统级动态字段，含字段类型、作用域、是否必填 |
| 字段选项 | `pm_field_option` | 下拉/单选等字段的可选值 |
| 类型字段布局 | `pm_type_field_layout` | 某事项类型在详情/创建页的字段布局 |
| 状态定义 | `pm_status_definition` | 某项目某类型的状态、初始/终态、画布坐标与可用流转 |
| 状态工作流 | Status Workflow | 状态 + 流转规则 + 校验器（validator）+ 后置动作（post-function） |
| 保存视图 | Saved View / `pm_saved_view` | 用户保存的查询条件、列配置与默认视图 |
| 活动 | Activity / `pm_work_item_activity` | 事项字段变更、流转、评论等审计流水 |
| 事项关联 | `pm_work_item_link` | 事项之间的关联关系（如阻塞、关联、重复） |
| 事项键 | `itemKey` | 项目编码 + 序号，如 `DEMO-12`，由 `pm_project_sequence` 分配 |

## 3. 流水线（Pipeline）

| 术语 | 代码 / 表 | 定义 |
|------|-----------|------|
| 流水线 | Pipeline / `pipeline` | 一条 CI/CD 定义，绑定 Git 仓库、分支与凭证 |
| 阶段 | Stage / `pipeline_stage` | 流水线内的顺序分组，阶段内任务可并行 |
| 任务 | Job / `pipeline_job` | 最小执行单元，引用任务类型、命令、工具链与资源限制 |
| 任务类型 | Task Kind / `pipeline_task_kind` | 任务市场中的可复用类型模板，如 `GIT_CLONE`、`MAVEN_BUILD`、`KUBECTL` |
| 任务类型参数 | `pipeline_task_kind_param` | 任务类型的参数定义（input/select/api_select） |
| 任务类型版本 | `pipeline_task_kind_version` | 任务类型脚本/配置的快照版本，可激活 |
| 运行 | Run / `pipeline_run` | 一次流水线执行实例，含触发方式、Git 提交、Tekton 名称、状态 |
| 运行任务 | Run Job / `pipeline_run_job` | 某次运行中单个任务的执行结果与日志 |
| 运行产物 | Artifact / `pipeline_run_artifact` | 运行产生的文件/SBOM 等产物 |
| 运行时参数 | Runtime Params / `pipeline_job_param` | 运行前填写的参数，支持输入、下拉、远程 API 选项 |
| 凭证 | Credential / `pipeline_credential` | Git、镜像仓库、kubeconfig 等敏感凭证，密文存储 |
| 工具链 | Toolchain | 任务可选的镜像/语言版本组合，由 `PipelineToolchainController` 提供 |
| 依赖组件 | `dependency_component` | SBOM 解析出的组件（purl、版本、许可证、语言） |
| Tekton | Tekton | 流水线的实际执行引擎，后端把 Pipeline 编译为 Tekton PipelineRun |

## 4. 容器平台

| 术语 | 代码 / 表 | 定义 |
|------|-----------|------|
| 集群 | Cluster / `cluster_info` | 纳管的 Kubernetes 集群，保存 kubeconfig 与连接状态 |
| 镜像仓库 | Registry / `registry_info` | Harbor 等镜像仓库的连接信息与凭证 |
| 命名空间 | Namespace | K8s 逻辑隔离单元，通过集群 API 实时读取，不落库 |
| 工作负载 | Workload | Deployment、StatefulSet、Pod、Job、CronJob 等 |
| Helm 仓库 | `helm_chart_repository` | Helm chart 仓库地址与凭证 |
| Helm 制品 | `helm_chart_artifact` | 已上传/缓存的 chart 版本、values、README |
| Helm 发布 | Helm Release | 集群中的 Helm release，通过 Helm API 实时操作，不落库 |
| 监控 | Monitor | 基于 Prometheus 的集群/节点/Pod/JVM 指标查询 |

## 5. API 测试平台

| 术语 | 代码 / 表 | 定义 |
|------|-----------|------|
| 接口分组 | API Group / `api_group` | 接口定义的树形分组 |
| 接口定义 | API Definition / `api_definition` | 一个 HTTP 接口的路径、方法、状态、版本与元数据 |
| 参数定义 | `api_definition_param` | Path/Query/Header/Body 参数 |
| 响应定义 | `api_definition_response` | 状态码、Content-Type、响应体 schema/示例 |
| 接口版本 | `api_definition_version` | 发布时的快照（路径、方法、参数、响应） |
| 前置/后置脚本 | `api_definition_script` | 调试前后的 JavaScript 脚本 |
| 断言 | `api_definition_assertion` | 响应断言规则 |
| 变量提取 | `api_definition_extract` | 从响应中提取变量供后续请求使用 |
| 环境 | Environment / `api_environment` | 一套变量集合（如 dev/test/prod） |
| 环境变量 | `api_environment_variable` | 环境中的键值对，支持 secret 标记 |
| 调试历史 | Debug History / `api_debug_history` | 单次调试的请求、响应、耗时、断言与日志 |
| 集合 | Collection / `api_collection` | 可批量执行的接口集合 |
| 集合文件夹 | `api_collection_folder` | 集合内的树形文件夹 |
| 集合项 | `api_collection_item` | 集合中引用的一条接口定义及启用状态/顺序 |
| 集合运行 | Collection Run / `api_collection_run` | 一次批量执行及其汇总结果 |
| 集合运行项 | `api_collection_run_item` | 批量执行中单条接口的结果 |

## 6. 效率工具

| 术语 | 代码 / 表 | 定义 |
|------|-----------|------|
| Docgen 生成 | Docgen Generate | 按格式、大小、数量生成样例文档；单个下载或批量落盘 |
| 文件解析 | File Parse | 上传文件后做格式识别、文本提取、OCR、压缩 |
| 图片会话 | Image Session | 一次图片处理的会话，含预览、元数据、转换任务 |
| 图片转换历史 | `image_convert_history` | 图片格式转换的结果记录 |
| OCR | Optical Character Recognition | 图片文字识别（Tesseract / PP-OCR 引擎） |

## 7. 核心实体关系

```text
sys_tenant 1 ── N sys_tenant_member N ── 1 sys_user
    │
    ├── 1 ── N pm_project 1 ── N pm_work_item
    │                      ├── N pm_project_module
    │                      ├── N pm_field_definition
    │                      └── N pm_status_definition
    │
    ├── 1 ── N pipeline 1 ── N pipeline_stage 1 ── N pipeline_job
    │                 └── 1 ── N pipeline_run 1 ── N pipeline_run_job
    │
    ├── 1 ── N pipeline_credential
    ├── 1 ── N cluster_info   （K8s 资源不落库）
    ├── 1 ── N registry_info
    └── 1 ── N helm_chart_repository 1 ── N helm_chart_artifact

pm_work_item 1 ── N pm_work_item_comment
             1 ── N pm_work_item_activity
             1 ── N pm_work_item_link

api_group 1 ── N api_definition 1 ── N api_definition_param
                                ├── N api_definition_response
                                ├── N api_definition_version
                                ├── N api_definition_script / assertion / extract

api_collection 1 ── N api_collection_folder
               1 ── N api_collection_item ── 1 api_definition
               1 ── N api_collection_run 1 ── N api_collection_run_item

api_environment 1 ── N api_environment_variable
```

## 8. 命名与约定

| 约定 | 说明 |
|------|------|
| 表前缀 | `sys_`（用户/租户）、`pm_`、`api_`、`pipeline_`、`cluster_`、`registry_`、`helm_`、`image_`、`dependency_` |
| 主键 | 统一 `INTEGER PRIMARY KEY`；user/container/helm 使用自增，其余由 MyBatis-Plus 雪花 ID 生成 |
| 时间字段 | 多数表用 `create_time` / `update_time`（TEXT，`datetime('now')`）；container/helm 用 `created_at` / `updated_at` |
| 逻辑删除 | PM/user 用 `del_flag`（0/1）；api-test/pipeline/image 用 `deleted`（0/1）；container/helm 无逻辑删除 |
| 租户字段 | `tenant_id`；api-test 使用 `project_id` 而**没有** `tenant_id` |
| 状态值 | 统一使用小写英文/下划线，如 `draft`、`published`、`deprecated`、`succeeded`、`failed` |

详细字段见 [data-dictionary.md](../03-database/data-dictionary.md)。
