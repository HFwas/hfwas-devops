# MySQL 迁移测试用例

> 日期：2026-10-21
> 版本：v0.1
> 状态：待实施
> 关联：[design.md](./design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：迁移、DDL、JSON、并发、部署、备份与 10M 基准用例 |

---

## 1. 环境与前置

| 项 | 值 |
|----|----|
| 数据库 | MySQL 8.0.40（最低 8.0.13） |
| 字符集/排序 | `utf8mb4` / `utf8mb4_0900_ai_ci` |
| 时区 | UTC |
| 自动化 | Testcontainers `mysql:8.0.40`；无 Docker 用 `-Dtest.mysql.url` |
| 迁移 | Flyway V1/V2 |

## 2. 迁移与 DDL

| 编号 | 场景 | 前置 | 步骤 | 预期 | 自动化 |
|------|------|------|------|------|--------|
| TC-DB-001 | 空库迁移 | 空 MySQL 库 | 执行 Flyway migrate | V1/V2 成功，`flyway_schema_history` 2 行 | `FlywayMigrationTest::emptyDatabaseMigrates` |
| TC-DB-002 | 重复迁移 | 已迁移库 | 再次 migrate | 无 pending，无错误 | `FlywayMigrationTest::migrateIsIdempotent` |
| TC-DB-003 | 表清单 | 已迁移 | 查询 information_schema | 业务表 56 张，无重复 `sys_user` | `FlywayMigrationTest::schemaShape` |
| TC-DB-004 | 种子数据 | 已迁移 | 统计关键表 | tenant 1、类型 4、状态 16、任务类型 19、参数 23 | `FlywayMigrationTest::seedCounts` |
| TC-DB-005 | 显式种子 ID | 已迁移 | 查 `pipeline_task_kind.id` | 全部非空且唯一 | 同上 |
| TC-DB-006 | 索引审计 | 已迁移 | information_schema.statistics join columns | 无 TEXT/BLOB 索引列 | `FlywayMigrationTest::noTextIndexes` |
| TC-DB-007 | 保留字 | 已迁移 | 查 `pipeline_run` 列 | 有 `trigger_type`，无 `trigger` | `FlywayMigrationTest::triggerColumn` |
| TC-DB-008 | 修改已执行迁移 | 已迁移 | 改 V1 后 validate | 校验失败 | 脚本/集成 |
| TC-DB-009 | 重建 | 已迁移 | DROP DATABASE 后重跑 | 成功，无 SQLite 文件 | 部署/手工 |

## 3. SQL 方言

| 编号 | 场景 | 步骤 | 预期 | 自动化 |
|------|------|------|------|--------|
| TC-SQL-001 | 自定义字段 EQ/LIKE/IN/IS NULL | 写入 `custom_fields` 后查询 | 结果与 SQLite 语义一致 | `JsonSqlDialectTest` / PM 集成 |
| TC-SQL-002 | 标签交集 | `JSON_CONTAINS` 查询 tags | 命中包含标签的记录 | api-test 集成 |
| TC-SQL-003 | 流水线运行创建/列表/详情 | 调 API | `trigger` JSON 字段不变，DB 写 `trigger_type` | pipeline 集成 |
| TC-SQL-004 | 迁移 lint | 运行 `check-flyway-migrations.sh` | 无 SQLite 语法、命名/版本合法 | 脚本 |

## 4. 并发与事务

| 编号 | 场景 | 步骤 | 预期 | 自动化 |
|------|------|------|------|--------|
| TC-DB-010 | 并发建单 | 20 线程同时创建 | `item_no` 唯一，无 DuplicateKey | `WorkItemSequenceConcurrencyTest` |
| TC-DB-011 | 事务回滚 | 测试事务失败 | 数据回滚，无脏写 | 现有 `@Transactional` 测试 |
| TC-DB-012 | 批量更新 | 100 条批量编辑 | 全部成功或按明细跳过 | PM 集成 |

## 5. API 与部署

| 编号 | 场景 | 步骤 | 预期 |
|------|------|------|------|
| TC-API-001 | 登录与租户 | Keycloak 登录后调 PM 列表 | 200，租户隔离 |
| TC-API-002 | PM CRUD | 项目/事项/评论/活动 | 全部 2xx，数据一致 |
| TC-API-003 | API 测试标签 | 分组/定义/标签过滤 | 结果正确 |
| TC-API-004 | 流水线任务市场 | 列表/详情/参数 | 正常 |
| TC-API-005 | 容器/镜像/Helm | 集群/仓库/Chart | 正常 |
| TC-DEP-001 | Compose | `docker compose up -d mysql backend` | MySQL 健康，`/health/ready` 200 |
| TC-DEP-002 | Helm | 安装/升级 | 迁移 Job 成功后双副本 Ready |
| TC-DEP-003 | Secret | 查看 Deployment | 无明文数据库密码 |
| TC-OPS-001 | 备份恢复 | mysqldump → 空库恢复 → validate | 行数一致，冒烟通过 |

## 6. 性能

| 编号 | 场景 | 数据 | 阈值 | 自动化 |
|------|------|------|------|--------|
| TC-PERF-001 | 项目事项列表 | 10M `pm_work_item` | p95 < 200ms，EXPLAIN 命中 `(project_id, del_flag, update_time)` | 基准脚本 |
| TC-PERF-002 | 类型筛选列表 | 10M，按类型 | p95 < 300ms | 基准脚本 |
| TC-PERF-003 | 深分页 | keyset 分页 | p95 < 200ms | 基准脚本 |
| TC-PERF-004 | 写入 | 并发创建/更新 | p95 < 100ms，无锁超时 | 基准脚本 |

## 7. 退出标准

- TC-DB-001–010、TC-SQL-001–004、TC-DEP-001–003、TC-OPS-001 全部通过。
- `mvn clean verify` 在 MySQL 环境通过。
- 10M 基线达到阈值，或记录偏差与后续优化项。
