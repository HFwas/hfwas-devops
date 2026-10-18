# 数据库总览与数据字典

> 日期：2026-10-18
> 版本：v0.3
> 状态：已完成（表级；字段枚举与默认值明细后续补充）
> 数据来源：`backend/*/src/main/resources/db/*.sql`、`deploy/charts/backend/files/db/*.sql`、`SqliteSchemaInitializer.java`、`UserSchemaMigration.java`、`migrate.py`、`migrate.sh`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：56 张表总览、初始化机制、公共约定、模块索引与已知问题 |
| v0.2 | 2026-10-17 | 补充 `pipeline_run.status` 实际枚举 |
| v0.3 | 2026-10-18 | 新增数据库文档规范入口；明确本目录文档必须遵循 10 列数据字典与四节结构 |

---

## 0. 文档规范

本目录及所有模块数据库文档必须遵循 [database-documentation-standard.md](database-documentation-standard.md)：

- 每模块固定四节：ER 图、表清单、数据字典、迁移与 DDL；
- 每表先写表级元数据，再写 10 列字段字典；
- 字段必须有类型、长度/精度、空否、默认值与中文说明；
- 枚举、JSON、敏感字段单独成节；
- 新增/修改表结构时同步更新文档，否则视为未完成。

## 1. 数据库概况

| 项目 | 说明 |
|------|------|
| 数据库 | SQLite 3 |
| 连接串 | `jdbc:sqlite:./data/hfwas-devops.db`（相对进程工作目录，`application.yml`） |
| 本地文件 | `backend/server/data/hfwas-devops.db`、`backend/data/hfwas-devops.db`、根目录 `data/hfwas-devops.db`（不同启动方式落点不同） |
| 表数量 | 56 张唯一表（schema 文件合计 57 个 `CREATE TABLE`，其中 `sys_user` 在 user 与 pm schema 各定义一次） |
| 迁移记录表 | `_schema_migrations`（`migrate.sh`）与 `schema_migrations`（`migrate.py`）——两套命名，见第 7 节 |
| WAL | 未启用（默认 `journal_mode=delete`） |
| 连接池 | 未显式配置 |

## 2. 表归属与数量

| 模块 | 表数 | 权威 schema 文件 | 部署副本 | 字典 |
|------|------|------------------|----------|------|
| 用户 / 租户 | 8 | `backend/user-core/src/main/resources/db/user-schema.sql` | `deploy/.../01-user-schema.sql` | [tables/user.md](tables/user.md) |
| 项目管理 | 15（+ 重复 `sys_user`） | `backend/server/src/main/resources/db/pm-schema.sql` | `deploy/.../02-pm-schema.sql` | [tables/pm.md](tables/pm.md) |
| API 测试 | 16 | `backend/server/src/main/resources/db/api-test-schema.sql` | `deploy/.../03-api-test-schema.sql` | [tables/api-test.md](tables/api-test.md) |
| 图片处理 | 1 | `backend/server/src/main/resources/db/image-schema.sql` | `deploy/.../04-image-schema.sql` | [tables/image.md](tables/image.md) |
| 流水线 | 12 | `backend/server/src/main/resources/db/pipeline-schema.sql` | `deploy/.../05-pipeline-schema.sql` | [tables/pipeline.md](tables/pipeline.md) |
| 容器 | 2 | `backend/container-core/src/main/resources/db/container-schema.sql` | `deploy/.../06-container-schema.sql` | [tables/container.md](tables/container.md) |
| Helm | 2 | `backend/container-core/src/main/resources/db/helm-chart-schema.sql` | `deploy/.../13-helm-chart-schema.sql` | [tables/helm.md](tables/helm.md) |

增量迁移（仅 external 模式，位于 `deploy/charts/backend/files/db/`）：

| 文件 | 内容 |
|------|------|
| `10-add-runtime-params.sql` | 流水线运行时参数 |
| `11-add-task-kind-resource-limits.sql` | 任务类型 CPU/Memory 限制 |
| `12-add-pipeline-job-param-bindings.sql` | 任务参数绑定 |
| `13-helm-chart-schema.sql` | Helm 仓库与制品表 |
| `14-helm-chart-content.sql` | Helm chart 内容缓存字段 |
| `15-task-kind-version-visibility.sql` | 任务类型版本可见性 |

## 3. 初始化机制

`devops.schema.init`（环境变量 `DEVOPS_SCHEMA_INIT`）控制：

### 3.1 embedded（默认）

| 执行者 | 文件 | 内容 |
|--------|------|------|
| `UserSchemaMigration`（`@Order(0)`） | `backend/user-core/.../config/UserSchemaMigration.java` | 用 JdbcTemplate 建 8 张 `sys_*` 表，执行加列/重建 `sys_user`/seed 默认租户与管理员；**不读取** `user-schema.sql` |
| `SqliteSchemaInitializer`（`@Order(1)`） | `backend/server/.../config/SqliteSchemaInitializer.java` | 创建 `./data`，执行兼容性 ALTER，再按顺序执行 `pm`、`api-test`、`image`、`pipeline`、`container`、`helm` 六个 classpath SQL（**不含 user**） |

### 3.2 external（Helm）

| 组件 | 说明 |
|------|------|
| `configmap-schema.yaml` | 将 `deploy/charts/backend/files/db/*` 挂载到 `/sql` |
| initContainer | 执行 `python3 /sql/migrate.py`，`DB_PATH=/app/data/hfwas-devops.db` |
| `migrate.py` | 按文件名顺序执行 SQL，记录 `schema_migrations`，对已执行文件做 checksum 校验 |
| `migrate.sh` | bash + sqlite3 版本，记录 `_schema_migrations`；与 `migrate.py` 并存 |
| 应用配置 | `DEVOPS_SCHEMA_INIT=external`，关闭进程内建表 |

> 新增 DDL/DML 时遵守仓库「数据库变更」规范：新增编号 SQL（下一个编号从当前最大值递增）、语句幂等、同步更新 Java 侧建表逻辑与 `schemaVersion`。

## 4. 公共字段约定

| 约定 | user | pm | api-test | pipeline | image | container | helm |
|------|------|----|----------|----------|-------|-----------|------|
| 主键 | `id INTEGER PK AUTOINCREMENT` | `id INTEGER PK`（雪花） | 同左 | 同左 | 同左 | `AUTOINCREMENT` | `AUTOINCREMENT` |
| 创建时间 | `create_time` | `create_time` | `create_time` | `create_time` | `create_time` | `created_at` | `created_at` |
| 更新时间 | `update_time` | `update_time` | `update_time` | `update_time` | 无 | `updated_at` | 无 |
| 创建人 | 无（用 `user_id`/`sender_id`） | 部分 `create_by`/`update_by` | 全量 `create_by`/`update_by` | 部分 | `create_by` | 无 | `uploaded_by` |
| 逻辑删除 | `del_flag` | `del_flag` | `deleted` | `deleted` | `deleted` | 无 | 无 |
| 租户隔离 | `tenant_id` | `tenant_id` + `project_id` | `project_id`（无 tenant） | `tenant_id` | `tenant_id` + `user_id` | `tenant_id` | `tenant_id` |
| 时间类型 | TEXT | TEXT | TEXT | TEXT | TEXT | TEXT | TEXT |
| 布尔 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 |

SQLite 类型映射：`BIGINT → INTEGER`、`VARCHAR(n) → TEXT`、`JSON → TEXT`、`DATETIME → TEXT`。

## 5. 模块 ER 概览

```text
sys_tenant ─┬─ sys_tenant_member ─ sys_user
            ├─ pm_project ─┬─ pm_work_item ─┬─ pm_work_item_comment
            │              │                ├─ pm_work_item_activity
            │              │                └─ pm_work_item_link
            │              ├─ pm_project_module
            │              ├─ pm_field_definition ─ pm_field_option
            │              ├─ pm_status_definition
            │              ├─ pm_saved_view
            │              └─ pm_type_field_layout
            ├─ pipeline ─┬─ pipeline_stage ─ pipeline_job
            │            ├─ pipeline_run ─┬─ pipeline_run_job
            │            │                └─ pipeline_run_artifact
            │            └─ pipeline_job_param
            ├─ pipeline_credential
            ├─ pipeline_task_kind ─┬─ pipeline_task_kind_param
            │                      └─ pipeline_task_kind_version
            ├─ cluster_info
            ├─ registry_info
            └─ helm_chart_repository ─ helm_chart_artifact

api_group ─ api_definition ─┬─ api_definition_param
                            ├─ api_definition_response
                            ├─ api_definition_version
                            ├─ api_definition_script
                            ├─ api_definition_assertion
                            └─ api_definition_extract
api_environment ─ api_environment_variable
api_collection ─┬─ api_collection_folder
                ├─ api_collection_item
                └─ api_collection_run ─ api_collection_run_item
api_debug_history（独立，关联 definition/environment）

pipeline_run_artifact ─ dependency_component
image_convert_history（独立）
```

## 6. 状态与枚举（已知）

| 表 / 字段 | 取值 |
|-----------|------|
| `sys_user.role` | `admin`、`user` |
| `sys_tenant_member.tenant_role` | `tenant_admin`、`member` |
| `api_definition.status` | `draft`、`published`、`deprecated` |
| `api_debug_history.status` | 成功/失败/错误（具体枚举待确认） |
| `api_collection_run.status` | 运行中/成功/失败等（待确认完整枚举） |
| `pipeline_run.status` | `QUEUED`、`RUNNING`、`WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `image_convert_history.status` | success/failed（待确认） |
| `cluster_info.status` | 连接状态（待确认枚举） |

## 7. 已知问题与风险

| 问题 | 影响 | 建议 |
|------|------|------|
| `pm-schema.sql` 重复定义 `sys_user` | embedded 模式下可能覆盖 user 模块表结构（缺少 `auth_source` 等列） | 移除 pm schema 中的 `sys_user` 定义，统一以 user 模块为准 |
| `migrate.sh` 与 `migrate.py` 迁移表名不一致 | 两套执行器可能重复执行已迁移文件 | 统一为 `schema_migrations`，或只保留一个执行器 |
| `deploy/.../05-pipeline-schema.sql` 与 classpath 版本存在差异 | external/embedded 建表结果可能漂移 | 以代码侧为权威，发布前做 schema diff |
| 未启用 WAL | 并发读写可能 `database is locked` | 评估启用 `journal_mode=WAL` 与 `busy_timeout` |
| 多个 DB 文件落点 | 本地调试可能读到不同库 | 统一启动脚本的 `DB_PATH` |
| api-test 无 `tenant_id` | 跨租户隔离依赖项目归属 | 在项目层强制租户校验 |

## 8. 维护规则

1. 新增表/字段：先在对应 schema SQL 与 Java 侧同步，再更新本目录字典。
2. 字典中的“写入方”以 Controller/Service 归属为准；跨模块写入需显式标注。
3. 不修改已执行的迁移 SQL；新增编号文件。
4. 每次发布前执行一次 `CREATE TABLE` 清单与生产库对比，确认无漂移。
