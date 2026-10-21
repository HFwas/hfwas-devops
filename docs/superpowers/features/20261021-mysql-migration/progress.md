# MySQL 迁移 — AI 协作进度日志

> 日期：2026-10-21
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-21 | 建立日志，定义阶段检查表与每轮记录 |

---

## 阶段检查表

| 阶段 | 内容 | 状态 | 验证 |
|------|------|------|------|
| 0 | 功能文档四件套 | 已完成 | 文档齐全 |
| 1 | 依赖与 db-migration 模块 | 已完成 | `mvn package` 通过 |
| 2 | V1/V2 MySQL DDL 与种子 | 已完成（静态） | sqlglot 解析 V1；真实 MySQL 待 CI |
| 3 | 配置切换、删除 embedded | 已完成 | 无 `jdbc:sqlite` |
| 4 | 方言与并发修复 | 已完成 | 编译通过；集成测试待 CI |
| 5 | 测试与 CI | 已完成（代码） | 本地 32 通过/3 跳过；CI 待跑 |
| 6 | 部署 | 已完成（代码） | 需 Docker/Helm 验证 |
| 7 | 文档同步 | 进行中 | 核心文档已更新 |
| 8 | 10M 基准与验收 | 未开始 | 需 MySQL 环境 |

## 第 1 轮 · 2026-10-21 · 建立迁移设计与文档骨架

- **状态**：进行中
- **触发**：用户确认全平台统一 MySQL、全环境统一、重建库不迁旧数据
- **目标**：完成设计、测试用例、变更骨架与进度日志，开始依赖改造
- **读取**：`AGENTS.md`、`backend/pom.xml`、`backend/server/pom.xml`、`application.yml`、7 份 schema、`SqliteSchemaInitializer`、`migrate.py`、`BaseApiTest`、`deploy/docker-compose.yml`、Helm backend chart、CI workflows

### 做了什么

1. 盘点 SQLite 改造面：22 个 SQL、约 56 张唯一表、9 个 Java 迁移/配置类、21 个 DB 集成测试、Compose/Helm/CI 均无 MySQL。
2. 确定 Flyway + `db-migration` 模块、重建库、JSON 继续 TEXT、`trigger_type`、Testcontainers 测试。
3. 创建 `design.md`、`test-cases.md`、`changes.md`、`progress.md`。
4. 评估当前环境无 Docker/MySQL，真实迁移验证需 CI 或具备 Docker 的环境。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261021-mysql-migration/design.md` | 新增 | 迁移设计 |
| `.../test-cases.md` | 新增 | 测试用例 |
| `.../changes.md` | 新增 | 变更骨架 |
| `.../progress.md` | 新增 | 进度日志 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 改造面盘点 | 全仓 grep + 文件统计 | 完成，见 design §5 |
| Docker | `docker info` | 失败，无 daemon |
| MySQL | `which mysql/mysqld` | 未安装 |
| 构建 | 未执行 | 本轮仅文档 |

### 决策与取舍

- **决策**：Flyway 取代自研 `migrate.py`。**理由**：标准账本/checksum/并发锁，减少自定义维护。
- **决策**：JSON 列继续 TEXT。**理由**：避免原生 JSON 规范化与空串兼容问题。
- **决策**：生产迁移 Job + 应用最小权限。**理由**：迁移先于新 Pod，应用账号无需 DDL。

### 偏差与假设

- **ASSUMPTION**：生产 MySQL 最低 8.0.13，推荐 8.0.36+。
- 当前环境无法执行真实 MySQL 验证。

### 未完成 / 风险

- 阶段 1–8 未开始。
- 需要 Docker/MySQL 或 CI 才能验证 Flyway 与集成测试。

### 下一步

- **唯一动作**：完成阶段 1（依赖与 `db-migration` 模块）并开始 V1 DDL。
- 需要用户确认：是否允许在 CI 中启用 Testcontainers/MySQL service。

---

## 第 2 轮 · 2026-10-21 · 完成 MySQL 改造代码与部署配置

- **状态**：待评审
- **触发**：用户确认全平台 MySQL、全环境统一、重建库不迁数据；计划批准
- **目标**：完成依赖、DDL、配置、方言、测试、部署与文档改造
- **读取**：7 份 SQLite schema、pom、application、Helm/Compose、CI、BaseApiTest、迁移器

### 做了什么

1. 新增 `backend/db-migration` 模块：V1（56 张表 MySQL DDL）、V2（系统种子）。
2. 删除 SQLite 驱动与 9 个 embedded 迁移类；`DbType.MYSQL`；Hikari 20 + Flyway。
3. 重写 JSON 方言与标签查询；`trigger_type`；工单编号 `FOR UPDATE`。
4. 测试改为 Testcontainers MySQL；新增 `FlywayMigrationTest`；CI 改为 Flyway lint。
5. Compose/Helm 增加 MySQL、Secret、迁移 Job、RollingUpdate、`/health/ready`。
6. 更新 AGENTS、README、数据库/运维/测试/PM 设计与容量评估。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `backend/db-migration/**` | 新增 | Flyway 模块与 V1/V2 |
| `backend/server/pom.xml`、`api-test-core/pom.xml` | 修改 | 驱动/Flyway/Testcontainers |
| `application.yml`、`MybatisPlusConfig` | 修改 | MySQL + Flyway |
| 9 个 Java 迁移类 | 删除 | 重建库不需要 |
| `JsonSqlDialect`、`ApiDefinitionService`、`PipelineRunEntity`、`WorkItemSequenceService` | 修改 | MySQL 方言/保留字/并发 |
| `BaseApiTest`、`FlywayMigrationTest`、`application-test.yml` | 修改/新增 | MySQL 测试 |
| `docker-compose.yml`、Helm backend、CI | 修改 | MySQL 运行与迁移 |
| `scripts/check-flyway-migrations.sh` | 新增 | 迁移 lint |
| 文档 | 修改/新增 | AGENTS、README、数据库、运维、PM 容量 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 编译打包 | `mvn -B -f backend/pom.xml -DskipTests package` | 通过 |
| 本地测试 | `mvn -B -f backend/pom.xml test` | 32 通过，3 跳过（容器） |
| DDL 语法 | sqlglot MySQL 解析 V1 | 125 条语句通过 |
| 迁移 lint | `bash scripts/check-flyway-migrations.sh` | 通过 |
| SQLite 残留 | `grep jdbc:sqlite/SqliteSchemaInitializer` | backend/deploy 为 0 |
| 真实 MySQL | 未执行 | 本机无 Docker/MySQL |
| Compose/Helm | 未执行 | 需 Docker/集群 |
| 10M 基准 | 未执行 | 需 MySQL 环境 |

### 决策与取舍

- **决策**：Flyway + 独立 `db-migration` 模块。**理由**：标准账本、共享给 server 与测试。
- **决策**：JSON 继续 TEXT，SQL 用 MySQL JSON 函数并加 `JSON_VALID`。**理由**：兼容空串与 TypeHandler。
- **决策**：生产迁移由 Helm Job 执行，应用关闭 Flyway。**理由**：最小权限、迁移先于 Pod。

### 偏差与假设

- 真实 MySQL 未验证，V1/V2 可能仍有语法/语义问题，需 CI 首跑修复。
- `db-migration` 作为依赖 jar，依赖 Flyway 嵌套 jar 扫描。
- 10M 索引已加入 V1，但未做 EXPLAIN 验证。

### 未完成 / 风险

- 需在 Docker/CI 环境执行 Flyway 与 21 个 DB 集成测试。
- 部署未实测；Helm Job 镜像 `hfwas/devops-db-migrate` 需构建推送。
- 文档尚有历史 SQLite 描述未全部清理。

### 下一步

- **唯一动作**：在有 Docker 的环境（或 CI）运行 `mvn clean verify` 与 Compose 冒烟，修复 V1/V2 实际错误。
