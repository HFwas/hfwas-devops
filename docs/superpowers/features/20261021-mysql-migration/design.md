# 后端 SQLite → MySQL 8 迁移设计

> 日期：2026-10-21
> 版本：v0.1
> 状态：设计完成，待实施
> 关联：[AGENTS.md](../../../../AGENTS.md)、[容量评估](../../../pm/pm-capacity-assessment.md)、[PM 设计](../../../pm/pm-design.md)、[数据库字典](../../../product/03-database/data-dictionary.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：全平台后端切换 MySQL 8 + Flyway，重建库不迁旧数据；定义 DDL 规范、迁移组织、代码改造、测试与部署 |

---

## 1. 背景与目标

### 1.1 背景

- 当前后端为单实例 Spring Boot + 单文件 SQLite，未启用 WAL，连接池未显式配置。
- 10M 事项基准显示：热项目列表 0.4–3.3 秒、深分页 6 秒、LIKE/JSON 无命中 4 秒以上；SQLite 单写与缺索引无法支撑多人并发。
- 用户确认数据库路线：**全平台后端统一 MySQL 8、本地/CI/生产全环境、重建库不迁旧数据**。

### 1.2 目标

| 编号 | 目标 | 可验证结果 |
|------|------|-----------|
| G1 | 全模块统一 MySQL 8 | 空库执行 Flyway 建出约 56 张表，应用启动无 SQLite 依赖 |
| G2 | 迁移可版本化 | Flyway `flyway_schema_history` 记录 V1/V2，重复执行幂等 |
| G3 | API 语义不变 | 现有 118 个测试中可运行部分通过；PM/API 测试/流水线/容器/图片冒烟通过 |
| G4 | 容量基线 | 10M `pm_work_item` 下项目列表 p95 < 200ms（索引命中） |
| G5 | 部署可运维 | Compose/Helm 有 MySQL、Secret、迁移 Job、备份恢复流程 |

### 1.3 范围与非目标

**范围**：`backend/` 下 user、pm、api-test、image、pipeline、container/helm、server 七个模块的数据库层。

**非目标**：

- 不迁移 `delivery-platform/`（独立 Go 产品，单独决策）。
- 不迁移旧 SQLite 数据，不提供 SQLite → MySQL 数据导入脚本。
- 不改变现有 HTTP API 契约与前端调用方式。
- 不引入读写分离、分库分表；10M 阶段先靠索引与库能力。

---

## 2. 架构决策（ADR）

| 编号 | 决策 | 理由 |
|------|------|------|
| ADR-01 | 统一 MySQL 8.0（最低 8.0.13，推荐 8.0.36+），删除 SQLite 双模式 | 用户确认；避免双 schema 漂移 |
| ADR-02 | 迁移执行器使用 Flyway（`flyway-core` + `flyway-mysql`），删除 `migrate.py` | 标准账本、checksum、版本排序、Spring Boot 集成、并发锁 |
| ADR-03 | 迁移文件集中在新模块 `backend/db-migration` | server 与 api-test-core 共用，避免 Maven 环 |
| ADR-04 | 重建库，不迁旧数据 | 绿地项目约定；首次切换成本最低 |
| ADR-05 | JSON 字段继续 `TEXT/MEDIUMTEXT`，SQL 使用 MySQL JSON 函数 | 避免原生 JSON 规范化、空串与 TypeHandler 兼容问题 |
| ADR-06 | `pipeline_run.trigger` 改列名 `trigger_type` | `TRIGGER` 是 MySQL 保留字 |
| ADR-07 | 生产由 Helm pre-upgrade Job 执行 Flyway，应用账号无 DDL 权限 | 最小权限、迁移先于新 Pod |
| ADR-08 | 测试使用 Testcontainers MySQL，支持外部库兜底 | CI 与本地一致；无 Docker 时可用 Homebrew MySQL |

---

## 3. MySQL DDL 规范

### 3.1 类型映射

| SQLite | MySQL 8 | 规则 |
|--------|---------|------|
| `INTEGER` 主键 / `*_id` / `tenant_id` / `create_by` / `update_by` | `BIGINT` | 雪花 ID 为 64 位，禁止 `INT` |
| `INTEGER` 计数 / `sort_order` / `layout` | `INT` | — |
| 0/1 标志 | `TINYINT NOT NULL DEFAULT 0/1` | `del_flag`、`enabled`、`is_*` |
| `TEXT` 索引/唯一列 | `VARCHAR(n)` | 见 §3.3 |
| `TEXT` 普通文本 | `TEXT` / `MEDIUMTEXT` | 请求体、响应体、日志、README、values |
| `TEXT` JSON | `TEXT` / `MEDIUMTEXT` | 不建原生 `JSON` |
| `REAL` | `DOUBLE` | 工作流画布坐标 |
| `datetime('now')` | `CURRENT_TIMESTAMP` | — |
| `AUTOINCREMENT` | `AUTO_INCREMENT` | 仅 `IdType.AUTO` 表 |
| `INSERT OR IGNORE` | 普通 `INSERT`（种子） | 不用 `INSERT IGNORE` |

### 3.2 主键

- 雪花 ID 表：`BIGINT NOT NULL PRIMARY KEY`。
- 自增表：`BIGINT NOT NULL AUTO_INCREMENT`，仅 user 8 表、container/helm 4 表、PM 3 表。
- `pipeline_task_kind` 实体为 `ASSIGN_ID`，种子必须显式 `id`。

### 3.3 索引与唯一键

- 索引列不得为 `TEXT`，必须 `VARCHAR`；组合索引总长度 ≤ 3072 bytes。
- 唯一标识列（username/code/external_id/kc_event_id/channel/kind_value 等）使用 `utf8mb4_bin`。
- 普通检索文本使用 `utf8mb4_0900_ai_ci`。
- 新增 PM 容量索引：
  - `pm_work_item(project_id, del_flag, update_time)`
  - `pm_work_item(project_id, type_code, del_flag, create_time)`
  - `pm_work_item(assignee_id)`
  - `pm_work_item(sprint_id)`、`pm_work_item(parent_id)`

### 3.4 默认值、字符集、时区

- `ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci ROW_FORMAT=DYNAMIC`。
- 时间统一 `DATETIME`，默认 `CURRENT_TIMESTAMP`；JDBC、MySQL、JVM 统一 UTC。
- 保留字：`trigger` → `trigger_type`，实体用 `@TableField("trigger_type")`。
- `TEXT DEFAULT ('')` 仅在 MySQL ≥ 8.0.13 使用；短字段优先 `VARCHAR DEFAULT ''`。

### 3.5 JSON 语义

| SQLite | MySQL |
|--------|-------|
| `json_extract(c,'$.k')` | `JSON_UNQUOTE(JSON_EXTRACT(c,'$.k'))` |
| `json_valid(tags) AND EXISTS (SELECT 1 FROM json_each(tags) WHERE value=?)` | `CASE WHEN JSON_VALID(tags) THEN JSON_CONTAINS(tags, JSON_QUOTE(?)) ELSE 0 END` |
| `json_set/json_object` | `JSON_SET/JSON_OBJECT` |

---

## 4. 迁移组织

```text
backend/db-migration/src/main/resources/db/migration/
├── V1__initial_schema.sql     # 56 张表 + 索引
└── V2__seed_system_data.sql   # 系统种子
```

- `V1` 只建表与索引，不使用 `IF NOT EXISTS`。
- `V2` 只写种子，显式主键，普通 `INSERT`。
- 后续变更从 `V3` 起，禁止修改已执行文件。
- Flyway 配置：`placeholder-replacement=false`（种子含 `${COMMAND}`）、`clean-disabled=true`、`validate-on-migrate=true`、`baseline-on-migrate=false`。
- DDL 隐式提交；V1 失败需 `DROP DATABASE` 重跑。生产迁移前必须备份。

---

## 5. 代码改造清单

| 类型 | 文件 |
|------|------|
| 依赖 | `backend/pom.xml`、`backend/server/pom.xml`、`backend/api-test-core/pom.xml`、`backend/db-migration/pom.xml` |
| 配置 | `application.yml`、`application-dev.yml`、`application-prod.yml`、`MybatisPlusConfig` |
| 删除 | `SqliteSchemaInitializer`、`UserSchemaMigration`、`PmItemNoMigration`、`PmModuleMigration`、`PmTenantMigration`、`CloneConfigBackfill` 及测试 |
| 方言 | `JsonSqlDialect`、`ApiDefinitionService`、`PipelineRunEntity` |
| 并发 | `WorkItemSequenceService` 增加行锁/原子更新 |
| 测试 | `BaseApiTest`、`application-test.yml`、新增 `FlywayMigrationTest` |
| 迁移脚本 | 删除 7 份 SQLite schema、deploy `files/db/`、`migrate.py`、`migrate.sh`、`test_migrate.py`、`migrations.lock`、`check-migrations.sh` |
| 部署 | `docker-compose.yml`、Helm values/templates、Secret、迁移 Job |
| 文档 | `AGENTS.md`、`README.md`、`docs/product/03-database/*`、`docs/backend/db_design.md`、运维/测试文档 |

---

## 6. 测试与部署

### 6.1 测试

- 本地/CI 使用 Testcontainers `mysql:8.0.40`；无 Docker 时用 `-Dtest.mysql.url` 指向外部库。
- 新增 `FlywayMigrationTest`：空库、幂等、56 表、种子计数、索引审计、`trigger_type`、JSON、并发编号。
- `BaseApiTest` 的 20 个子类不改方法，只换基类数据源。

### 6.2 部署

- Compose 增加 `mysql:8.0.40`、healthcheck、`mysql-data` 卷；backend 依赖 `service_healthy`。
- Helm 使用外部 MySQL + Secret + pre-upgrade Flyway Job；`strategy: RollingUpdate`；删除 SQLite PVC 与 `DB_PATH`。
- 新增 `/health/ready` 执行 `SELECT 1`。
- 备份：`mysqldump --single-transaction`；恢复演练纳入上线检查。

---

## 7. 风险

| 风险 | 影响 | 应对 |
|------|------|------|
| `INT` 存雪花 ID | 溢出/写入失败 | 全部 ID 用 `BIGINT`，DDL 审计 |
| TEXT 索引/默认值 | 建表失败 | 索引列改 `VARCHAR`，默认值用 `DEFAULT ('')` |
| JSON 语义差异 | 查询结果错误 | 统一 `JSON_UNQUOTE`，补集成测试 |
| `trigger` 保留字 | SQL 语法错误 | 改 `trigger_type` |
| 时区不一致 | 时间偏移 | MySQL/JDBC/JVM 统一 UTC |
| V1 部分失败 | 半成品 schema | `DROP DATABASE` 重跑；迁移前备份 |
| 工单编号并发 | 唯一键冲突 | `SELECT ... FOR UPDATE` 或原子更新 |
| 本地无 Docker | 无法真实验证 | CI Testcontainers；本地静态审计 |

---

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-01 | `mvn -B -f backend/pom.xml clean verify` 在 MySQL 环境通过 |
| AC-02 | 空库 Flyway 执行 V1/V2 成功，账本 2 行 |
| AC-03 | 业务表 56 张，无重复 `sys_user` |
| AC-04 | 种子计数：tenant 1 / 类型 4 / 状态 16 / 任务类型 19 / 参数 23 |
| AC-05 | 无 SQLite 运行时依赖与 SQLite 语法残留 |
| AC-06 | 索引审计无 TEXT 列，PM 列表 EXPLAIN 命中组合索引 |
| AC-07 | JSON 查询、`trigger_type`、并发工单编号测试通过 |
| AC-08 | Compose 健康、Helm 迁移 Job 成功、RollingUpdate 双副本 Ready |
| AC-09 | `mysqldump` 备份恢复演练通过 |
| AC-10 | 10M 事项列表 p95 < 200ms |
| AC-11 | 数据库文档与 AGENTS 同步为 MySQL |

---

## 9. 实施顺序

1. 功能文档（本目录四件套）。
2. 依赖与 `db-migration` 模块。
3. V1/V2 DDL 与种子。
4. 配置切换、删除 embedded 初始化。
5. 方言与并发修复。
6. 测试与 CI。
7. 部署。
8. 文档同步。
9. 10M 基准与验收。
