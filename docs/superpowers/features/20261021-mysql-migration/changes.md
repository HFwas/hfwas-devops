# MySQL 迁移代码变更

> 日期：2026-10-21
> 版本：v0.2
> 状态：代码实施完成，真实 MySQL 验证待 CI/环境
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 骨架：计划新增/修改/删除文件与对应设计章节 |
| v0.2 | 2026-10-21 | 补全实际改动、偏差、构建/测试/静态校验结果与未验证项 |

---

## 1. 计划变更

### 1.1 新增

| 文件 | 说明 | 设计章节 |
|------|------|----------|
| `backend/db-migration/pom.xml` | 迁移文件共享模块 | §4 |
| `backend/db-migration/src/main/resources/db/migration/V1__initial_schema.sql` | 56 张表 MySQL DDL | §3、§4 |
| `backend/db-migration/src/main/resources/db/migration/V2__seed_system_data.sql` | 系统种子 | §4 |
| `backend/server/src/test/java/com/hfwas/devops/db/FlywayMigrationTest.java` | 迁移集成测试 | §6 |
| `scripts/check-flyway-migrations.sh` | 迁移文件 lint | §6 |
| `deploy/docker/db-migrate/Dockerfile` | Flyway 迁移镜像 | §6 |
| `deploy/charts/backend/templates/job-migrate.yaml` | pre-upgrade 迁移 Job | §6 |

### 1.2 修改

| 文件 | 说明 | 设计章节 |
|------|------|----------|
| `backend/pom.xml` | 增加 db-migration 模块 | §5 |
| `backend/server/pom.xml` | 删 SQLite，加 Flyway/MySQL/Testcontainers | §5 |
| `backend/api-test-core/pom.xml` | 删 SQLite test，加 Testcontainers | §5 |
| `backend/server/src/main/resources/application*.yml` | MySQL datasource + Flyway | §5 |
| `backend/server/.../config/MybatisPlusConfig.java` | `DbType.MYSQL` | §5 |
| `backend/pm-core/.../query/engine/JsonSqlDialect.java` | MySQL JSON 函数 | §3.5 |
| `backend/api-test-core/.../ApiDefinitionService.java` | `JSON_CONTAINS` | §3.5 |
| `backend/pipeline-core/.../PipelineRunEntity.java` | `trigger_type` 映射 | §3.4 |
| `backend/pm-core/.../WorkItemSequenceService.java` | 并发编号原子化 | §7 |
| `backend/api-test-core/src/test/.../BaseApiTest.java` | Testcontainers MySQL | §6 |
| `deploy/docker-compose.yml`、`deploy/charts/backend/**` | MySQL 服务/Secret/Job | §6 |
| `.github/workflows/{maven,pr-backend}.yml` | MySQL/Testcontainers | §6 |

### 1.3 删除

| 文件 | 原因 | 设计章节 |
|------|------|----------|
| 7 份模块 SQLite schema | 由 V1 取代 | §4 |
| `deploy/charts/backend/files/db/**` | 由 Flyway 取代 | §4 |
| `scripts/{check-migrations.sh,migrations.lock,tests/test_migrate.py}` | SQLite 专用迁移器 | §4 |
| `SqliteSchemaInitializer`、`UserSchemaMigration`、`Pm*Migration`、`CloneConfigBackfill` | 重建库不需要旧兼容迁移 | §5 |

## 2. 实施记录

| 日期 | 文件 | 实际改动 | 偏差原因 |
|------|------|----------|----------|
| 2026-10-21 | `backend/db-migration/**` | 新增共享迁移模块；V1 56 张表 MySQL DDL、V2 系统种子 | — |
| 2026-10-21 | `backend/server/pom.xml`、`backend/api-test-core/pom.xml` | 删除 SQLite 驱动；增加 Flyway、MySQL、Testcontainers | — |
| 2026-10-21 | `application.yml` | MySQL datasource、Hikari 20、Flyway 配置；删除 `devops.schema.init` | — |
| 2026-10-21 | `MybatisPlusConfig` | `DbType.SQLITE` → `DbType.MYSQL` | — |
| 2026-10-21 | 9 个 Java 迁移/配置类 | 删除 embedded 建表与旧兼容迁移 | 重建库不需要 |
| 2026-10-21 | `JsonSqlDialect` | `JSON_EXTRACT/JSON_UNQUOTE`，加 `JSON_VALID` 保护 | — |
| 2026-10-21 | `ApiDefinitionService` | `json_each` → `JSON_CONTAINS` | — |
| 2026-10-21 | `PipelineRunEntity` | `trigger` 映射 `trigger_type` | MySQL 保留字 |
| 2026-10-21 | `WorkItemSequenceService`/Mapper | `SELECT ... FOR UPDATE`，插入冲突重取 | SQLite 单写掩盖并发 |
| 2026-10-21 | `BaseApiTest`、`application-test.yml` | Testcontainers MySQL + Flyway，支持外部库 | — |
| 2026-10-21 | `FlywayMigrationTest` | 空库、幂等、56 表、种子、索引、`trigger_type` | — |
| 2026-10-21 | `docker-compose.yml`、Helm backend | MySQL 服务/Secret/迁移 Job/RollingUpdate/readiness | — |
| 2026-10-21 | CI workflows | 移除 Python 迁移测试，增加 Flyway lint | — |
| 2026-10-21 | `scripts/check-flyway-migrations.sh` | 迁移命名与禁用语法 lint | 取代 `check-migrations.sh` |
| 2026-10-21 | 文档 | AGENTS/README/数据库/运维/测试/PM 设计同步 | — |

### 计划外偏差

- `db-migration` 作为 server 与 api-test-core 的依赖 jar，未做 resources 复制；依赖 Spring Boot/Flyway 对嵌套 jar 的 classpath 扫描。
- `BaseApiTest` 使用 `@Testcontainers(disabledWithoutDocker = true)`，本地无 Docker 时跳过 DB 集成测试；CI 执行。
- 当前环境无 Docker/MySQL，V1/V2 仅通过 sqlglot MySQL 解析与静态索引审计，未在真实 MySQL 执行。

## 3. 校验结果

| 项 | 结论 | 说明 |
|----|------|------|
| 构建 | 通过 | `mvn -B -f backend/pom.xml -DskipTests package` |
| 本地测试 | 通过（DB 集成跳过） | `mvn -B -f backend/pom.xml test`：32 通过，3 跳过（Flyway 容器测试） |
| DDL 解析 | 通过 | sqlglot MySQL 解析 V1 125 条语句 |
| 迁移 lint | 通过 | `scripts/check-flyway-migrations.sh` |
| 静态审计 | 通过 | backend/deploy 无 `jdbc:sqlite`、`SqliteSchemaInitializer`、`migrate.py` |
| 真实 MySQL 迁移 | 未执行 | 当前无 Docker/MySQL；需 CI 或本地 Docker |
| 部署验证 | 未执行 | 需 Docker/Helm 环境 |
| 10M 基准 | 未执行 | 需 MySQL 基准环境 |
| 独立 agent 校验 | 待执行 | 实施完成后进行 |
