# PM 阶段 1 代码变更

> 日期：2026-10-21
> 版本：v0.2
> 状态：已实现，待独立校验
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 开工骨架：计划新增/修改文件与设计章节映射 |
| v0.2 | 2026-10-21 | 回填实际改动、计划外偏差与真实测试结果；状态改为待独立校验 |

---

## 1. 变更清单（计划）

### 1.1 文档与冻结

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `docs/product/06-prototype/pm/README.md` | 修改 | §2 | §7 写入冻结版本 v1.11、视觉 v1.9、提交 6741712 |
| `docs/product/06-prototype/pm/prototype-contract.md` | 修改 | §2 | §9 勾选冻结记录 |
| 本功能目录 `design.md` / `test-cases.md` / `changes.md` / `progress.md` | 新增 | — | 文档先行 |

### 1.2 数据库

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `backend/db-migration/src/main/resources/db/migration/V3__pm_backend_shared_foundation.sql` | 新增 | §3 | 新列/新表/索引/迭代种子 |
| `backend/server/src/test/java/com/hfwas/devops/db/FlywayMigrationTest.java` | 修改 | §3 / TC-01–04 | 更新精确计数与结构断言 |
| `docs/product/03-database/tables/pm.md` | 修改 | §3 | 新列/新表/索引/枚举 |
| `docs/product/03-database/data-dictionary.md` | 修改 | §3 | 迁移编号与 ER |

### 1.3 后端模型与接口

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `backend/pm-core/.../workitem/entity/PmWorkItem.java` | 修改 | §3 | `endTime`、`version` |
| `backend/pm-core/.../meta/PmWorkItemType.java` | 修改 | §4.6 | `builtIn/description/templateCode/workItemCount/fieldCount` |
| `backend/pm-core/.../field/model/FieldDefinition.java` | 修改 | §3 | `batchEditable/batchOrder` |
| `backend/pm-core/.../workitem/entity/PmBatchActionConfig.java` | 新增 | §3 | 批量动作配置实体 |
| `backend/pm-core/.../workitem/mapper/PmBatchActionConfigMapper.java` | 新增 | §3 | Mapper |
| `backend/pm-core/.../workitem/model/WorkItemFieldUpdateVO.java` | 新增 | §4.1 | 单字段更新结果 |
| `backend/pm-core/.../workitem/model/BatchOperationResultVO.java` / `BatchOperationItemVO.java` | 新增 | §4.2–4.3 | 批量结果 |
| `backend/pm-core/.../workitem/model/BatchFieldChangeVO.java` / `WorkItemPatchResult.java` | 新增 | §4.1–4.3 | 字段变更与补丁结果 |
| `backend/pm-core/.../workitem/model/BatchActionConfigVO.java` | 新增 | §4.5 | 配置视图 |
| `backend/pm-core/.../meta/PmWorkItemTypeTemplateVO.java` | 新增 | §4.6 | 类型模板 |
| `backend/pm-core/.../common/PmDateTimeUtils.java` | 新增 | §4.7 | UTC naive 日期解析与格式化 |
| `backend/pm-core/.../workitem/service/WorkItemFieldUpdateService.java` | 新增 | §4.1 | 单字段更新与版本校验 |
| `backend/pm-core/.../workitem/mapper/PmWorkItemMapper.java` | 修改 | §4.6 | 按类型聚合事项数 |
| `backend/pm-core/.../workitem/service/TransitionValidatorExecutor.java` | 修改 | §4.7 | 校验器读取 `end_time` |
| `backend/pm-core/.../field/spi/handler/{Date,DateTime,Number}FieldTypeHandler.java` | 修改 | §4.7 | 自定义字段比较 CAST 类型 |
| `backend/pm-core/.../workitem/service/WorkItemBatchService.java` | 新增 | §4.2–4.3 | 批量逐条执行 |
| `backend/pm-core/.../workitem/service/BatchActionConfigService.java` | 新增 | §4.5 | 配置默认值/校验 |
| `backend/pm-core/.../workitem/service/WorkItemService.java` | 修改 | §4.1–4.3 | 流转版本、批量复用、end_time |
| `backend/pm-core/.../workitem/service/WorkItemActivityService.java` | 修改 | §4.4 | 分页与共享 batchId |
| `backend/pm-core/.../workitem/service/WorkItemFieldApplicator.java` | 修改 | §4.7 | `end_time` |
| `backend/pm-core/.../workitem/service/WorkItemChangeDetector.java` | 修改 | §4.7 | `end_time` |
| `backend/pm-core/.../workitem/service/WorkItemFieldDisplayResolver.java` | 修改 | §4.7 | 日期展示 |
| `backend/pm-core/.../workitem/service/StatusDefinitionService.java` | 修改 | §4.7 | 条件字段 `end_time` |
| `backend/pm-core/.../workitem/io/WorkItemIoValueCodec.java` | 修改 | §4.7 | 导入导出 `end_time` |
| `backend/pm-core/.../field/service/FieldDefinitionService.java` | 修改 | §4.7 | 系统字段 `end_time`、批量属性 |
| `backend/pm-core/.../query/engine/QueryEngine.java` | 修改 | §4.7 | 顶层 OR、排序白名单 |
| `backend/pm-core/.../query/engine/FieldResolver.java` | 修改 | §4.7 | `end_time`、key 校验 |
| `backend/pm-core/.../query/engine/JsonSqlDialect.java` | 修改 | §4.7 | 比较操作符、key 校验 |
| `backend/pm-core/.../field/spi/handler/AbstractFieldTypeHandler.java` | 修改 | §4.7 | 自定义字段比较 |
| `backend/pm-core/.../meta/PmMetaService.java` | 修改 | §4.6 | 内置保护、模板 |
| `backend/pm-core/.../meta/ProjectIssueTypeService.java` | 修改 | §4.6 | 计数与字段数 |
| `backend/server/.../pm/api/controller/PmWorkItemController.java` | 修改 | §4.1 | PATCH |
| `backend/server/.../pm/api/controller/PmWorkItemBatchController.java` | 新增 | §4.2–4.3 | 批量接口 |
| `backend/server/.../pm/api/controller/PmWorkItemActivityController.java` | 修改 | §4.4 | 分页接口 |
| `backend/server/.../pm/api/controller/PmBatchActionConfigController.java` | 新增 | §4.5 | 配置接口 |
| `backend/server/.../pm/api/controller/PmMetaController.java` | 修改 | §4.6 | 模板接口 |
| `backend/server/.../pm/api/dto/WorkItemFieldUpdateDto.java` 等 | 新增 | §4 | 请求 DTO |
| `backend/user-api/.../error/ResultCode.java` | 修改 | §6 | 21033–21039 |
| `docs/backend/error-code-design.md` | 修改 | §6 | 错误码范围 |

### 1.4 前端共享组件

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `frontend/src/components/console/DataTable.tsx` | 修改 | §5.1 | 选择/排序/密度/吸顶 |
| `frontend/src/components/console/Pagination.tsx` | 修改 | §5.2 | 总数/页大小/跳页 |
| `frontend/src/components/console/ListPageShell.tsx` | 新增 | §5.3 | 列表外壳 |
| `frontend/src/components/console/SearchInput.tsx` | 新增 | §5.3 | 防抖搜索 |
| `frontend/src/components/console/FilterBar.tsx` | 新增 | §5.3 | 条件 chip |
| `frontend/src/components/console/BulkActionBar.tsx` | 新增 | §5.3 | 批量栏 |
| `frontend/src/components/console/EmptyState.tsx` | 新增 | §5.3 | 空态 |
| `frontend/src/components/console/ErrorState.tsx` | 新增 | §5.3 | 错误态 |
| `frontend/src/components/console/NoPermissionState.tsx` | 新增 | §5.3 | 无权限态 |
| `frontend/src/components/ui/{checkbox,switch,tabs,radio-group,toggle-group,scroll-area}.tsx` | 新增 | §5.4 | shadcn CLI 生成 |

### 1.5 测试

| 文件 | 动作 | 覆盖用例 |
|---|---|---|
| `backend/pm-core/src/test/java/.../query/engine/FieldResolverTest.java` | 新增 | TC-06–11 |
| `.../query/engine/QueryEngineTest.java` | 修改 | TC-12 |
| `.../workitem/service/WorkItemFieldUpdateServiceTest.java` | 新增 | TC-13–20 |
| `.../workitem/service/WorkItemBatchServiceTest.java` | 新增 | TC-21–28 |
| `.../workitem/service/WorkItemActivityServiceTest.java` | 新增 | TC-29–30 |
| `.../meta/PmMetaServiceTest.java` | 新增 | TC-31–32 |
| `.../meta/ProjectIssueTypeServiceTest.java` | 新增 | TC-33 |
| `.../workitem/io/WorkItemIoValueCodecTest.java` | 新增 | TC-34 |
| `frontend/src/components/console/DataTable.test.tsx` | 新增 | TC-35–38 |
| `frontend/src/components/console/Pagination.test.tsx` | 修改 | TC-39–41 |
| `frontend/src/components/console/SearchInput.test.tsx` | 新增 | TC-42 |
| `frontend/src/components/console/FilterBar.test.tsx` | 新增 | TC-43 |
| `frontend/src/components/console/BulkActionBar.test.tsx` | 新增 | TC-44 |
| `frontend/src/components/console/ListPageShell.test.tsx` | 新增 | TC-45 |
| `frontend/src/components/console/StateViews.test.tsx` | 新增 | TC-46 |
| `backend/pm-core/src/test/java/.../workitem/service/BatchActionConfigServiceTest.java` | 新增 | AC-08：默认值、上限、管理员、白名单 |

### 1.6 接口文档

| 文件 | 动作 | 说明 |
|---|---|---|
| `docs/pm/pm-api.md` | 修改 | 升 v1.5，新增接口正常/失败示例 |
| `docs/pm/pm-api.postman_collection.json` | 修改 | 新文件夹与 examples |
| `docs/pm/pm-design.md` | 修改 | 升 v2.6，更新缺口状态 |

## 2. 原型 → 路由 → 实现文件 → 共享组件映射

本阶段不改 PM 页面，路由映射为空；后续阶段补：

| 原型页面 | 路由 | 实现文件 | 共享组件 |
|---|---|---|---|
| 工作项列表 `#/workbench` | `/pm/projects/:projectId/items/:typeCode` | 待阶段 2 | `ListPageShell`、`SearchInput`、`FilterBar`、`BulkActionBar`、`DataTable`、`Pagination` |
| 事项详情 `#/items/:id` | `/pm/projects/:projectId/items/:typeCode/:itemId` | 待阶段 2 | `EmptyState`、`ErrorState`、`NoPermissionState` |
| 类型管理 `#/types` | `/pm/projects/:projectId/settings/types` | 待阶段 3 | `ListPageShell`、`DataTable` |
| 视图/批量配置 `#/settings/*` | `/pm/projects/:projectId/settings/*` | 待阶段 2 | `DataTable`、`Switch`、`Tabs` |

## 3. 计划外偏差

| # | 偏差 | 原因 / 影响 |
|---|------|-------------|
| 1 | shadcn 实际生成 7 个文件（多出 `toggle.tsx`） | `toggle-group` 依赖 `toggle`；未改动 `package.json`，沿用已安装的 `radix-ui` 与 `cn` |
| 2 | 活动分页新增 `/activities/page`，未改旧 `/activities` | 避免破坏现有详情页；旧接口保留兼容，见 design §4.4 |
| 3 | 共享组件密度只实现 `compact` / `standard` | 对齐原型 URL 的 `density=standard\|compact`，未引入第三档 |
| 4 | `pm_work_item_watcher` 仅建表，无读写接口 | 关注者属于详情阶段能力；本阶段只补存储（design A/G-05 标记为部分） |
| 5 | 批量角色仅支持 `admin` / `user` | 项目成员/只读角色尚未建模（design 假设 A1） |
| 6 | `fieldCount` = 8 个系统字段 + 该类型适用自定义字段 | 与 `FieldDefinitionService.SYSTEM_FIELD_COUNT` 保持一致 |
| 7 | 未新建 `prototype/` | 本阶段不改页面，只交付共享组件；复用冻结的模块总原型 v1.11 作为后续基线 |
| 8 | `pm-api.md` 单字段更新使用 body `{field,value,version}` | 按 `pm-design.md` A-01 契约，而非路径参数形式 |

## 4. 验证结果

| 检查 | 命令 | 结果 |
|------|------|------|
| 迁移规范 | `bash scripts/check-flyway-migrations.sh` | 通过：3 个迁移，最新 V3 |
| 后端编译 | `mvn -B -f backend/pom.xml -pl pm-core,server -am -DskipTests compile` | 通过 |
| pm-core 测试 | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 通过：52 tests，0 failures |
| server 测试 | `mvn -B -f backend/pom.xml -pl server -am test` | 通过：34 tests，0 failures；5 skipped（Flyway/Testcontainers，Docker 未启动） |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过：74 files，280 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（仅既有 chunk 体积警告） |
| 迁移实跑 | — | **未执行**：本机 Docker 未启动，`FlywayMigrationTest` 被跳过；待 CI 或启动 Docker 后补验 |

## 5. 校验结果

### 第 1 次独立校验（2026-10-21）

结论：**失败**。命令全部通过，但发现 3 个阻断项：

| # | 问题 | 处理 |
|---|------|------|
| 1 | `updateStatus` 用 `Long` 引用比较，version ≥ 128 时误报 21033 | 改为 `expectedVersion.equals(item.getVersion())`；补 version=200 用例 |
| 2 | 旧 `transition(...)` 方法丢失 `@Transactional`（自调用绕过代理） | 外层方法恢复 `@Transactional`；流转增加带 version 条件的原子递增 |
| 3 | `TYPE_TEMPLATE_INVALID(21039)` 未被使用，文档承诺与实现不符 | `PmMetaService` 改抛 `BizException(21039)`，并校验模板 `enabled=1` |

同时修复：

- 自定义字段 key 非法/未知现在返回 `22018`（与文档一致）。
- 批量配置 `fieldWhitelist` 置空时显式清空旧值；空数组按默认白名单处理。
- `pm-api.md` 批量流转多可达结果改为 `SKIPPED`；`UPDATE_FIELDS.requireConfirm` 示例改为 false；补类型模板与批量配置失败示例。
- Postman 示例同步修正。
- 新增 `BatchActionConfigServiceTest`（6 条）。

第 1 次校验结论不作为最终通过；待第 2 次独立校验。

### 第 2 次独立校验（2026-10-21）

结论：**通过**。3 个阻断项全部关闭；命令全绿（pm-core 51 → 复验时 51、server 34 + 5 skipped、前端 74 files / 280 passed / 1 skipped、`tsc` 通过）。

复验后补充的非阻断修正：

| # | 问题 | 处理 |
|---|------|------|
| 1 | 原子流转 `WHERE version` 缺单测 | 新增 `WorkItemServiceTest::transitionRejectsStaleVersionAtomically`，断言 guard 0 行时抛 21033 且不写回 |
| 2 | `pm-api.md` §4.11 `SKIPPED` 说明残留「状态不可流转」 | 删除该短语（批量更新入口已拒绝 `status`） |
| 3 | 排序路径非法字段沿用 400 | 在 `design.md` §6 明确：条件过滤返回 22018，排序沿用 400 |

最终验证：pm-core **52 tests** 通过。剩余非阻断遗留（`selectBatchIds` 顺序、`reason` 未使用、watcher 无接口、TC-29/30 断言偏弱、Docker 未启动导致迁移未实跑）登记在 `progress.md`。
