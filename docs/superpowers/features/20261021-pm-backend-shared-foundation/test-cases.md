# PM 阶段 1 测试用例

> 日期：2026-10-21
> 版本：v0.2
> 状态：已执行（独立校验通过；TC-01~04 待 Docker/CI 实跑）
> 关联：[design.md](./design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：迁移、查询、单字段/批量、活动、类型、共享组件用例 |
| v0.2 | 2026-10-21 | 执行结果：本地 52 后端 + 280 前端通过；TC-01~04 因 Docker 未启动跳过；批量配置用例补指向 `BatchActionConfigServiceTest` |

---

## 1. 后端 · 迁移

| 编号 | 前置 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|---|
| TC-01 | 空 MySQL 8 库、Docker 可用 | Flyway 执行全部迁移 | — | 3 条成功迁移；58 张业务表 | `backend/server/src/test/java/com/hfwas/devops/db/FlywayMigrationTest.java::schemaAndSeedAreComplete` |
| TC-02 | 同 TC-01 | 查询 information_schema | — | `pm_work_item.end_time` 为 datetime、`version` 为 bigint 非空；`pm_work_item_type.built_in/description/template_code` 存在；`pm_field_definition.batch_editable/batch_order` 存在 | `FlywayMigrationTest.java::pmFoundationColumnsExist` |
| TC-03 | 同 TC-01 | 查询活动与索引 | — | `pm_work_item_activity.batch_id` 为 varchar；watcher/批量配置表存在；`(project_id,end_time)` 与 `batch_id` 索引存在 | `FlywayMigrationTest.java::pmFoundationSchemaObjectsExist` |
| TC-04 | 同 TC-01 | 查询类型与状态 | — | 5 个类型（含 iteration，排序 4）；20 条状态（iteration 4 条） | `FlywayMigrationTest.java::schemaAndSeedAreComplete` |
| TC-05 | 无 Docker | 运行 `mvn test` | — | 迁移测试跳过，其余测试通过；`changes.md` 如实记录未实跑 | 命令输出 |

## 2. 后端 · 查询

| 编号 | 前置 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|---|
| TC-06 | 构造 QueryEngine | 顶层 OR | `logic=OR`，两个条件 | SQL 为 `(cond1 OR cond2)`，项目/类型过滤仍 AND | `backend/pm-core/src/test/java/com/hfwas/devops/pm/query/engine/QueryEngineTest.java::topLevelOrCombinesConditions` |
| TC-07 | 同上 | 顶层 AND | `logic=AND` | 条件顺序 AND | `QueryEngineTest.java::topLevelAndKeepsConditions` |
| TC-08 | 同上 | 自定义字段 key 注入 | `custom.a'); DROP` | 抛异常，不生成 SQL | `FieldResolverTest.java::rejectsInvalidCustomFieldKey` |
| TC-09 | 同上 | 数值比较 | `custom.score GT 10` | SQL 含 `CAST(... AS DECIMAL)` | `FieldResolverTest.java::numberCustomFieldSupportsComparisons` |
| TC-10 | 同上 | 日期区间 | `custom.due BETWEEN a,b` | SQL 含 CAST DATE 与 BETWEEN | `FieldResolverTest.java::dateCustomFieldSupportsBetween` |
| TC-11 | 同上 | 文本比较 | `custom.text GT x` | 抛 `OPERATOR_UNSUPPORTED` | `FieldResolverTest.java::textCustomFieldRejectsComparison` |
| TC-12 | 同上 | 按 end_time 排序 | `sort=end_time:ASC` | 排序被接受 | `backend/pm-core/src/test/java/com/hfwas/devops/pm/query/engine/QueryEngineTest.java::endTimeSortAccepted` |

## 3. 后端 · 单字段更新

| 编号 | 前置 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|---|
| TC-13 | 事项 version=3 | PATCH 优先级 | `{field:"priority",value:"high",version:3}` | 成功，version=4，返回 old/new label | `backend/pm-core/src/test/java/com/hfwas/devops/pm/workitem/service/WorkItemFieldUpdateServiceTest.java::updatesSystemFieldAndBumpsVersion` |
| TC-14 | 同上 | PATCH 过期版本 | `version:2` | 抛 `WORK_ITEM_VERSION_CONFLICT`，不写库 | `WorkItemFieldUpdateServiceTest.java::rejectsStaleVersion` |
| TC-15 | 负责人已设置 | PATCH 清空 | `{field:"assignee_id",value:null}` | 更新语句显式写 NULL | `WorkItemFieldUpdateServiceTest.java::clearsFieldWithNull` |
| TC-16 | 有多个自定义字段 | PATCH 一个自定义字段 | `custom.score=8` | 其他自定义字段保留 | `WorkItemFieldUpdateServiceTest.java::mergesCustomFieldWithoutLosingOthers` |
| TC-17 | — | PATCH 未知自定义字段 | `custom.unknown=1` | 抛 `FIELD_UNKNOWN` | `WorkItemFieldUpdateServiceTest.java::rejectsUnknownCustomField` |
| TC-18 | 状态 in_progress，唯一 done 流转 | PATCH 状态 | `{field:"status",value:"done"}` | 走 transition，状态 done，记活动 | `WorkItemFieldUpdateServiceTest.java::statusUpdateUsesWorkflowTransition` |
| TC-19 | 多个流转到同一状态 | PATCH 状态不带 transitionId | `value:"closed"` | 抛 `STATUS_TRANSITION_FORBIDDEN` 并提示 transitionId | `WorkItemFieldUpdateServiceTest.java::ambiguousStatusRequiresTransitionId` |
| TC-20 | — | PATCH 标题为空 | `title:""` | 抛 `TITLE_REQUIRED` | `WorkItemFieldUpdateServiceTest.java::rejectsBlankTitle` |

## 4. 后端 · 批量

| 编号 | 前置 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|---|
| TC-21 | 3 条事项，1 条版本过期 | 批量更新 | `ids=[1,2,3]`，versions 含过期项 | success=2、failed=1；成功项已落库 | `backend/pm-core/src/test/java/com/hfwas/devops/pm/workitem/service/WorkItemBatchServiceTest.java::partialFailureDoesNotRollbackSuccesses` |
| TC-22 | 同上 | dry-run | `dryRun:true` | 无 update/activity 调用，outcome=SUCCESS | `WorkItemBatchServiceTest.java::dryRunPerformsNoWrites` |
| TC-23 | 两个项目事项 | 批量更新 | ids 跨项目 | 整批拒绝 `BATCH_PROJECT_MISMATCH` | `WorkItemBatchServiceTest.java::rejectsCrossProjectBatch` |
| TC-24 | 动作禁用 | 批量更新 | 配置 `enabled=0` | 拒绝 `BATCH_ACTION_DISABLED` | `WorkItemBatchServiceTest.java::rejectsDisabledAction`、`BatchActionConfigServiceTest.java::assertActionAllowedRejectsDisabledAndOverLimit` |
| TC-25 | 上限 2 | 批量 3 条 | ids=3 | 拒绝 `BATCH_SIZE_EXCEEDED` | `WorkItemBatchServiceTest.java::rejectsOverLimit`、`BatchActionConfigServiceTest.java::assertActionAllowedRejectsDisabledAndOverLimit` |
| TC-26 | 字段不在白名单 | 批量更新 | patch 含 `risk` | 单条 SKIPPED + `BATCH_FIELD_NOT_ALLOWED` | `WorkItemBatchServiceTest.java::skipsFieldOutsideWhitelist` |
| TC-27 | 状态不允许流转 | 批量流转 | toStatus 不可达 | 单条 SKIPPED + `STATUS_TRANSITION_FORBIDDEN` | `WorkItemBatchServiceTest.java::skipsIllegalTransition` |
| TC-28 | 校验器失败 | 批量流转 | 必填字段缺失 | 单条 FAILED，其余继续 | `WorkItemBatchServiceTest.java::validatorFailureIsFailedOutcome` |

## 5. 后端 · 活动、类型、导入导出

| 编号 | 前置 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|---|
| TC-29 | 12 条活动 | 分页查询 | pageNo=2,pageSize=5 | 返回 5 条、total=12、最新在前 | `backend/pm-core/src/test/java/com/hfwas/devops/pm/workitem/service/WorkItemActivityServiceTest.java::pagesActivitiesNewestFirst` |
| TC-30 | 批量更新 2 条 | 查活动 | — | 两条活动共享同一 batchId | `WorkItemActivityServiceTest.java::sharesBatchIdAcrossItems` |
| TC-31 | 内置类型 | 删除类型 | `builtIn=1` | 抛 `BUILT_IN_TYPE_PROTECTED` | `backend/pm-core/src/test/java/com/hfwas/devops/pm/meta/PmMetaServiceTest.java::rejectsDeletingBuiltInType` |
| TC-32 | 模板 iteration | 新建自定义类型 | `templateCode=iteration` | 复制 iteration 工作流 | `PmMetaServiceTest.java::clonesWorkflowFromTemplate` |
| TC-33 | 项目有各类型事项 | 类型列表 | — | workItemCount 正确，零计数类型也返回 | `backend/pm-core/src/test/java/com/hfwas/devops/pm/meta/ProjectIssueTypeServiceTest.java::enrichesTypeCounts` |
| TC-34 | — | 导入/导出 end_time | `2026-11-01 18:00:00` | 可写入与导出；非法日期 `21032` | `backend/pm-core/src/test/java/com/hfwas/devops/pm/workitem/io/WorkItemIoValueCodecTest.java::roundTripsEndTime` |

## 6. 前端 · 共享组件

| 编号 | 前置 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|---|
| TC-35 | 默认 DataTable | 渲染 | 无新 props | 表头 `h-10 bg-muted px-2`、单元格 `p-2 tabular-nums` 不变 | `frontend/src/components/console/DataTable.test.tsx::keepsDefaultCompactMarkup` |
| TC-36 | selectable | 点击表头/行复选框 | 3 行 | 全选、半选、区间选择正确；不触发 onRowClick | `DataTable.test.tsx::selectionAndIndeterminate` |
| TC-37 | sortable | 点击表头，Shift 点击另一列 | — | 单列循环 ASC/DESC；Shift 追加多列 | `DataTable.test.tsx::cyclesAndAppendsSorts` |
| TC-38 | density=standard | 渲染 | — | 使用标准密度 class，不破坏现有 compact | `DataTable.test.tsx::appliesStandardDensity` |
| TC-39 | Pagination 旧用法 | 仅 page/pageCount | — | 摘要仍为 `第 x / y 页` | `frontend/src/components/console/Pagination.test.tsx::shows the default summary and reports page changes` |
| TC-40 | Pagination 新用法 | 传 total/pageSize | total=128,size=10 | 摘要 `共 128 条 · 第 x / 13 页`；页大小选项 10/20/50/100 | `Pagination.test.tsx::derivesPageCountAndSizeOptions` |
| TC-41 | 跳页 | 输入 999/0/abc | — | 纠正到合法范围，不产生非法页码 | `Pagination.test.tsx::clampsJumpInput` |
| TC-42 | SearchInput | 输入后等待 300ms / 按 Enter | `abc` | 防抖只触发一次；Enter 立即触发；清空触发空串 | `frontend/src/components/console/SearchInput.test.tsx::debouncesAndSubmitsImmediately` |
| TC-43 | FilterBar | 点击移除/重置 | 2 个条件 | 单条移除与全部重置回调正确 | `frontend/src/components/console/FilterBar.test.tsx::removesAndResetsChips` |
| TC-44 | BulkActionBar | selectedCount=0/2 | — | 0 不渲染；2 显示已选与动作，清空回调正确 | `frontend/src/components/console/BulkActionBar.test.tsx::rendersOnlyWhenSelected` |
| TC-45 | ListPageShell | forbidden/error/empty/content | — | 状态优先级正确，错误态可重试 | `frontend/src/components/console/ListPageShell.test.tsx::rendersStatePrecedence` |
| TC-46 | 状态组件 | 渲染 Empty/Error/NoPermission | — | 文案、role、操作按钮正确 | `frontend/src/components/console/StateViews.test.tsx::rendersEmptyVariants`、`::rendersErrorWithRetry`、`::rendersNoPermission` |

## 7. 手工验证

| 编号 | 步骤 | 预期 |
|---|---|---|
| MV-01 | 启动 MySQL 8，执行 Flyway | V3 成功，`SHOW COLUMNS` 与文档一致 |
| MV-02 | 启动后端，调用顶层 OR 查询 | 结果为并集且仍限定项目 |
| MV-03 | 对同一条事项并发 PATCH（旧版本） | 后到请求返回 21033 |
| MV-04 | 批量 3 条含 1 条非法流转 | 返回明细，成功项可在列表查到 |
| MV-05 | 前端 `npm run build` | 构建通过，无类型错误 |
