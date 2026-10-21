# 数据库总览与数据字典

> 日期：2026-10-21
> 版本：v0.4
> 状态：已完成（表级；MySQL 类型与迁移基线已更新）
> 数据来源：`backend/db-migration/src/main/resources/db/migration/*.sql`、Flyway、`application.yml`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：56 张表总览、初始化机制、公共约定、模块索引与已知问题 |
| v0.2 | 2026-10-17 | 补充 `pipeline_run.status` 实际枚举 |
| v0.3 | 2026-10-18 | 新增数据库文档规范入口；明确本目录文档必须遵循 10 列数据字典与四节结构 |
| v0.4 | 2026-10-21 | 数据库切换 MySQL 8 + Flyway；更新连接串、迁移账本、类型映射与表来源 |

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
| 数据库 | MySQL 8（InnoDB，utf8mb4） |
| 连接串 | `jdbc:mysql://localhost:3306/hfwas_devops?...`（环境变量 `DB_URL`） |
| 本地数据 | MySQL `mysql-data` 卷；不再生成 `data/hfwas-devops.db` |
| 表数量 | 56 张业务表 + `flyway_schema_history` |
| 迁移记录表 | `flyway_schema_history`（V1 基线、V2 种子；后续从 V3 递增） |
| 事务隔离 | MySQL 默认 REPEATABLE READ |
| 连接池 | Hikari，默认 `maximum-pool-size=20` |

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

数据库为 MySQL 8，统一由 **Flyway** 管理：

| 组件 | 说明 |
|------|------|
| 迁移目录 | `backend/db-migration/src/main/resources/db/migration/` |
| 基线 | `V1__initial_schema.sql`（56 张表）、`V2__seed_system_data.sql`（系统种子） |
| 账本 | `flyway_schema_history`，checksum 校验，禁止修改已执行文件 |
| 本地/CI | 应用启动执行 Flyway；测试用 Testcontainers `mysql:8.0.40` |
| 生产 | Helm pre-install/pre-upgrade Job 执行 Flyway；应用 `SPRING_FLYWAY_ENABLED=false` |
| 重建 | `DROP DATABASE` 后重跑 Flyway；不迁移 SQLite 数据 |

> 新增 DDL/DML 时遵守仓库「数据库变更」规范：新增 `V3__*.sql` 起的迁移文件，禁止 SQLite 语法，同步更新本目录与模块数据库文档。

## 4. 公共字段约定

| 约定 | user | pm | api-test | pipeline | image | container | helm |
|------|------|----|----------|----------|-------|-----------|------|
| 主键 | `id BIGINT AUTO_INCREMENT` | `id INTEGER PK`（雪花） | 同左 | 同左 | 同左 | `AUTO_INCREMENT` | `AUTO_INCREMENT` |
| 创建时间 | `create_time` | `create_time` | `create_time` | `create_time` | `create_time` | `created_at` | `created_at` |
| 更新时间 | `update_time` | `update_time` | `update_time` | `update_time` | 无 | `updated_at` | 无 |
| 创建人 | 无（用 `user_id`/`sender_id`） | 部分 `create_by`/`update_by` | 全量 `create_by`/`update_by` | 部分 | `create_by` | 无 | `uploaded_by` |
| 逻辑删除 | `del_flag` | `del_flag` | `deleted` | `deleted` | `deleted` | 无 | 无 |
| 租户隔离 | `tenant_id` | `tenant_id` + `project_id` | `project_id`（无 tenant） | `tenant_id` | `tenant_id` + `user_id` | `tenant_id` | `tenant_id` |
| 时间类型 | DATETIME | DATETIME | DATETIME | DATETIME | DATETIME | DATETIME | DATETIME |
| 布尔 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 | 0/1 |

MySQL 类型映射：`id/外键 → BIGINT`、短文本 → `VARCHAR(n)`、长文本/JSON → `TEXT/MEDIUMTEXT`、时间 → `DATETIME`、布尔 → `TINYINT`、浮点 → `DOUBLE`。JSON 字段以文本存储，SQL 使用 `JSON_EXTRACT` / `JSON_UNQUOTE` / `JSON_CONTAINS`。

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
| ~~`migrate.sh` 与 `migrate.py` 迁移表名不一致~~ | 已统一为 `_schema_migrations`；`migrate.sh` 仅包装 `migrate.py`，旧 `schema_migrations` 自动合并 | 已解决（2026-10-18） |
| `deploy/.../05-pipeline-schema.sql` 与 classpath 版本存在差异 | external/embedded 建表结果可能漂移 | 以代码侧为权威，发布前做 schema diff |
| 未启用 WAL | 并发读写可能 `database is locked` | 评估启用 `journal_mode=WAL` 与 `busy_timeout` |
| 多个 DB 文件落点 | 本地调试可能读到不同库 | 统一启动脚本的 `DB_PATH` |
| api-test 无 `tenant_id` | 跨租户隔离依赖项目归属 | 在项目层强制租户校验 |

## 8. 维护规则

1. 新增表/字段：先在对应 schema SQL 与 Java 侧同步，再更新本目录字典。
2. 字典中的“写入方”以 Controller/Service 归属为准；跨模块写入需显式标注。
3. 不修改已执行的迁移 SQL；新增编号文件。
4. 每次发布前执行一次 `CREATE TABLE` 清单与生产库对比，确认无漂移。
