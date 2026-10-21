# PM 阶段 1 进度日志

> 日期：2026-10-21
> 版本：v0.1（记录格式版本）
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)、[changes.md](./changes.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 建立进度日志格式 |

---

## 第 1 轮 · 2026-10-21 · 冻结原型并建立阶段 1 文档骨架

- **状态**：进行中
- **触发**：用户确认冻结 PM 模块总原型，并选择「阶段 1 精简：仅后端 + 共享组件」、批量接口采用真实实现
- **目标**：先完成文档先行门禁与冻结记录，再实施后端迁移/接口与前端共享组件
- **读取**：`pm-design.md`、PM 模块总原型 README/spec/contract、三个 PM 功能设计、前后端 PM 代码、迁移与测试

### 做了什么

1. 梳理当前实现与原型差距：PM 页面仍是旧版列表/只读详情；后端缺 end_time/version/批量/活动分页/类型计数等。
2. 与用户确认三项决策：冻结提交 6741712；阶段 1 只做后端 + 共享组件；批量接口真实实现。
3. 新建功能目录与 `design.md`、`test-cases.md`、`changes.md`、`progress.md`。
4. 确定实施顺序：V3 迁移 → 模型/错误码 → end_time 与查询修复 → 单字段/批量/活动/配置/类型接口 → 前端共享组件 → 测试与文档同步。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261021-pm-backend-shared-foundation/design.md` | 新增 | 阶段 1 设计与接口契约 |
| `.../test-cases.md` | 新增 | 46 条自动化用例 + 5 条手工用例 |
| `.../changes.md` | 新增 | 计划文件清单与映射 |
| `.../progress.md` | 新增 | 本日志 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|---|---|---|
| 文档结构 | 人工核对 | design/test-cases/changes/progress 均已建立 |
| 代码改动 | — | 本轮尚未改代码 |

### 决策与取舍

- **决策**：本阶段不新建 `prototype/`。**理由**：不修改任何页面，只交付共享组件，复用冻结的模块总原型作为后续基线。
- **决策**：乐观锁用手工条件更新，不启用 MP `OptimisticLockerInnerInterceptor`。**理由**：避免改变既有 `save`/`transition` 的全局语义。
- **决策**：活动分页新增 `/activities/page`，保留旧全量接口。**理由**：不破坏现有详情页。

### 偏差与假设

- **A1**：项目级成员/只读角色未建模，批量角色暂按 admin/user。
- **A2**：Docker 当前未启动，迁移测试可能跳过。

### 未完成 / 风险

- 冻结记录尚未写入原型 README/contract。
- 代码、测试与文档同步尚未开始。

### 下一步

- **唯一动作**：把冻结版本与提交写入 `docs/product/06-prototype/pm/README.md` §7 与 `prototype-contract.md` §9。

---

## 第 2 轮 · 2026-10-21 · 阶段 1 后端与共享组件实施

- **状态**：已实现，待独立校验
- **触发**：冻结记录写入后，按批准计划实施后端增量与前端共享组件
- **目标**：完成 V3 迁移、增量接口、查询修复、共享列表组件及文档同步
- **读取**：PM 现有实现、迁移、测试、`pm-design.md`、PM 原型与契约

### 做了什么

1. 冻结记录写入 `README.md` v1.12 与 `prototype-contract.md` v0.13（提交 6741712）。
2. 新增 `V3__pm_backend_shared_foundation.sql`：`end_time`/`version`、类型元数据、字段批量属性、活动批次号修正、关注者表、批量动作配置表、`iteration` 种子与索引。
3. 后端：单字段 PATCH、批量更新、批量流转、活动分页、批量动作配置、类型模板与类型计数；`end_time` 贯通查询/排序/IO/活动；修复顶层 OR、自定义字段 key 校验与数值/日期比较。
4. 前端共享组件：扩展 `DataTable`（选择/排序/密度/吸顶）与 `Pagination`（总数/页大小/跳页），新增 `ListPageShell`、`SearchInput`、`FilterBar`、`BulkActionBar`、`EmptyState`、`ErrorState`、`NoPermissionState`；用 shadcn CLI 添加 7 个 UI 原语。
5. 同步 `pm-api.md` v1.5、Postman、PM 数据字典 v0.2、数据库总览 v0.5、`pm-design.md` v2.6。

### 变更文件

见 `changes.md` §1。核心新增：

| 文件 | 说明 |
|------|------|
| `backend/db-migration/.../V3__pm_backend_shared_foundation.sql` | V3 迁移 |
| `backend/pm-core/.../WorkItemFieldUpdateService.java` | 单字段与批量字段更新 |
| `backend/pm-core/.../WorkItemBatchService.java` | 批量更新/流转 |
| `backend/pm-core/.../BatchActionConfigService.java` | 批量动作配置 |
| `frontend/src/components/console/DataTable.tsx`、`Pagination.tsx` | 向后兼容扩展 |
| `frontend/src/components/console/{ListPageShell,SearchInput,FilterBar,BulkActionBar,EmptyState,ErrorState,NoPermissionState}.tsx` | 共享列表基础组件 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 迁移规范 | `bash scripts/check-flyway-migrations.sh` | 通过，3 个迁移，最新 V3 |
| 后端 pm-core 测试 | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 通过，43 tests |
| 后端 server 测试 | `mvn -B -f backend/pom.xml -pl server -am test` | 通过，34 tests / 5 skipped（Docker 未启动） |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过，74 files / 280 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| Flyway 实跑 | Testcontainers | 未执行：Docker 未启动 |

### 决策与取舍

- **决策**：单字段更新使用显式 `UpdateWrapper` 条件更新，保留 null 清空语义。**理由**：MP 默认策略会忽略 null。
- **决策**：批量逐条调用独立 `@Transactional` 服务方法。**理由**：单条失败不回滚已成功项。
- **决策**：活动分页新增 `/activities/page`。**理由**：不破坏现有全量接口调用方。
- **决策**：内置类型名称/图标/颜色只读，只允许改描述与排序。**理由**：对齐设计 D-02/A-10。

### 偏差与假设

- 见 `changes.md` §3：shadcn 多生成 `toggle.tsx`、关注者仅建表、批量角色仅 admin/user、密度两档等。
- Docker 未启动，迁移与 Flyway 测试未实跑。

### 未完成 / 风险

- 独立 agent 校验未执行。
- PM 列表/详情/类型管理页面未改造（按计划留到阶段 2/3）。
- V3 尚未在任何数据库实跑，启动 Docker 或 CI 后需补验。

### 下一步

- **唯一动作**：新开独立 agent，按 `design.md` / `test-cases.md` / `changes.md` 与当前 diff 做一致性校验和用例复核，结论回填 `changes.md` §5。

---

## 第 3 轮 · 2026-10-21 · 独立复验通过

- **状态**：已完成（阶段 1）
- **触发**：首轮独立校验发现 3 个阻断项，修复后请求复验
- **目标**：关闭阻断项并取得最终校验结论

### 做了什么

1. 修复 `updateStatus` 的 Long 引用比较（version ≥ 128 误报 21033），补 version=200 用例。
2. 恢复旧 `transition(...)` 的 `@Transactional`，并在 `transitionWithVersion` 增加 `WHERE version=?` 的原子递增；补 guard 0 行用例。
3. `PmMetaService` 模板不存在/停用时抛 21039，补用例。
4. `FieldResolver` 非法/未知自定义字段统一返回 22018。
5. 批量配置空白名单回落默认、null 白名单显式清空，新增 `BatchActionConfigServiceTest`（6 条）。
6. 修正 `pm-api.md`、Postman、`design.md` 与 `changes.md` 的文档差异。

### 验证

| 检查 | 命令 | 结果 |
|------|------|------|
| pm-core 测试 | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 通过：52 tests |
| server 测试 | `mvn -B -f backend/pom.xml -pl server -am test` | 通过：34 tests / 5 skipped（Docker 未启动） |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过：74 files / 280 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| 迁移规范 | `bash scripts/check-flyway-migrations.sh` | 通过，最新 V3 |
| 独立复验 | 独立 agent | **通过**：3 个阻断项关闭，文档与实现一致 |

### 决策与取舍

- **决策**：排序字段非法沿用 400，条件过滤非法字段返回 22018。**理由**：保持既有排序契约，同时让过滤错误码与文档一致；差异已在 design §6 写明。

### 偏差与假设

- Docker 未启动，V3 与 `FlywayMigrationTest` 未在本地实跑；需 CI 或启动 Docker 后补验。
- 项目级成员/只读角色未建模，批量角色仍按 admin/user。

### 未完成 / 风险

- 阶段 2：列表工作台页面改造、批量/导入导出/视图配置 UI。
- 阶段 3：可编辑详情与类型管理页。
- 非阻断遗留：`selectBatchIds` 返回顺序、`WorkItemBatchTransitionDto.reason` 未使用、watcher 仅建表、TC-29/30 断言偏弱。

### 下一步

- **唯一动作**：启动阶段 2，先按冻结原型改造列表工作台，并复用本阶段共享组件。
