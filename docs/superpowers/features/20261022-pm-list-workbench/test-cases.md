# PM 列表工作台测试用例

> 日期：2026-10-22
> 版本：v0.3
> 状态：已执行（本地通过；无真实登录环境手工用例待补）
> 关联：[design.md](./design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 初版：URL/视图/筛选/查询/列/页面/批量/后端 NOT_LIKE 用例 |
| v0.2 | 2026-10-22 | 回填执行结果：后端 54、前端 319 通过；页面与纯函数用例已自动化 |
| v0.3 | 2026-10-22 | 修正测试引用；新增日期为空、字符串版本、恢复默认、元数据未就绪用例；计数更新为后端 57 / 前端 327 |

---

## 1. 纯函数

| 编号 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-01 | 解析空 URL | `{}` | view=dev、size=10、page=1、配置继承视图默认 | `listUrlState.test.ts::defaultsAndClamping` |
| TC-02 | 解析非法分页/排序/overlay | `page=0&size=37&sort=bad:x&overlay=nope` | page=1、size=10、sort 丢弃、overlay 空 | `listUrlState.test.ts::dropsInvalidValues` |
| TC-03 | 序列化与默认值 | 配置等于视图默认 | 不写入 `cols/search/ff/density/sort` | `listUrlState.test.ts::omitsDefaultsOnSerialize` |
| TC-04 | 往返 | 含 q/f/page/sel 的完整状态 | parse→serialize→parse 等值 | `listUrlState.test.ts::roundTrips` |
| TC-05 | 旧别名 | `cols=type,assignee,updated` | 归一为 `type_code,assignee_id,update_time` | `listUrlState.test.ts::normalizesAliases` |
| TC-06 | 内置视图定义 | 四个视图 | dev/mine/week/blocked 的列、排序、谓词正确 | `viewConfig.test.ts::definesBuiltInViews`、`querySpec.test.ts::buildsBlockedPredicates`、`::usesNextMondayAsWeekUpperBound`、`::buildsMinePredicate` |
| TC-07 | 旧保存视图 | `columns=[{fieldKey:'title'}]` | 保留旧列 key，其余配置取默认 | `viewConfig.test.ts::normalizesLegacySavedView` |
| TC-08 | 保存形状 | 配置 + 筛选树 | 序列化为单对象数组，可 JSON 往返 | `viewConfig.test.ts::buildsSavedColumns` |
| TC-09 | 脏状态 | 修改列/密度/filter | dirty=true；仅 q/page 变化 dirty=false | `viewConfig.test.ts::detectsDirtyConfig` |
| TC-10 | 缺值校验 | 文本“包含”无值、区间单值、IN 空数组 | 全部标记 invalid，给出摘要 | `filterTree.test.ts::validatesMissingValues` |
| TC-11 | 2 层限制 | 根 + 组 + 组内嵌套 | 组内嵌套 layers 被忽略（只保留两级） | `filterTree.test.ts::enforcesTwoLevels` |
| TC-12 | 表达式与 chip | 状态属于 [open,doing]、优先级等于 high | 文案可读，AND/OR 正确 | `filterTree.test.ts::buildsReadableExpression` |
| TC-13 | 快捷筛选 | mine/week/blocked/unassigned | 生成对应条件；无 risk 时 blocked 降级 | `filterTree.test.ts::buildsQuickFilters` |
| TC-14 | 未知字段/操作符 | 手工构造非法树 | 清洗丢弃，不进入查询 | `filterTree.test.ts::sanitizesUnknown` |
| TC-15 | 全部工作项 | typeCode=all | QuerySpec 不含 typeCode | `querySpec.test.ts::omitsTypeCodeForAll` |
| TC-16 | 搜索映射 | q=登录，字段 title/description/itemKey/assignee | title/description LIKE、item_no、assignee IN 正确 | `querySpec.test.ts::mapsSearchFields`、`::mapsTextSearchToLike` |
| TC-17 | 不包含 | 文本 NOT_CONTAINS | 输出 `NOT_LIKE` | `querySpec.test.ts::mapsNotContainsToNotLike` |
| TC-18 | 日期预设 | TODAY/THIS_WEEK/BEFORE/AFTER/BETWEEN | 输出正确区间 | `querySpec.test.ts::mapsDateOperators` |
| TC-19 | 组合 | 视图 + 搜索 + 筛选 | 三个独立 group，顶层 AND | `querySpec.test.ts::combinesGroupsWithAnd` |
| TC-20 | 我的事项 | 当前用户 id | `assignee_id EQ` 数字 id | `querySpec.test.ts::buildsMinePredicate` |

## 2. 列与页面

| 编号 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-21 | 构建列目录 | 系统字段 + 自定义字段 | 系统/伪列/自定义/缺失列齐全 | `columns.test.tsx::buildsCatalog` |
| TC-22 | 缺失故事点 | 项目无 storyPoints | 列存在、单元格渲染 `—` | `columns.test.tsx::rendersMissingFieldDash` |
| TC-23 | 单元格 | 状态/优先级/负责人/日期/自定义选择 | 标签、显示名、日期、选项名正确 | `columns.test.tsx::rendersCells` |
| TC-24 | 全部工作项链接 | item.typeCode=bug | 详情链接包含 bug 而非 all | `columns.test.tsx::linksWithItemType` |
| TC-25 | 默认列表 | 类型路由 | 请求带 typeCode；8 列渲染；总数一致 | `WorkItemListPage.test.tsx::rendersDefaultList` |
| TC-26 | 全部工作项 | `/items/all` | 请求不带 typeCode | `WorkItemListPage.test.tsx::allItemsOmitsTypeCode` |
| TC-27 | 搜索 | 输入“登录”等待 350ms/Enter | URL q 更新，查询含 LIKE 组 | `WorkItemListPage.test.tsx::searchWritesUrl` |
| TC-28 | 应用筛选 | 状态属于 open/doing | URL f 更新，查询含 IN 条件，页面显示「筛后 N 条」 | `WorkItemListPage.test.tsx::appliesFilter` |
| TC-29 | 列配置 | 下移标题 | URL `cols` 即时更新（表格重绘），脏点出现；保存后落库 | `WorkItemListPage.test.tsx::columnConfigUpdates` |
| TC-30 | 选择与批量栏 | 勾选当前页 | 批量栏出现，已选数量正确 | `WorkItemListPage.test.tsx::selectionShowsBulkBarAndBulkEdit` |
| TC-31 | 批量编辑 | dry-run + 执行 | 两次调用 batchUpdate，结果明细渲染 | `WorkItemListPage.test.tsx::selectionShowsBulkBarAndBulkEdit` |
| TC-32 | 批量流转 | 目标状态不可达 | 调用 batchTransition，展示 SKIPPED 与原因 | `WorkItemListPage.test.tsx::bulkTransition` |
| TC-33 | 批量删除 | 动作启用，一条失败 | 逐条调用 delete；失败项 ID 保留在 `sel` | `WorkItemListPage.test.tsx::bulkDeleteSequential`、`::bulkDeleteFailedKeepsSelection` |
| TC-34 | 无权限 | 列表 403 | 显示只读态，隐藏新建/选择/批量 | `WorkItemListPage.test.tsx::forbiddenState` |
| TC-35 | 空/错误态 | 空结果 / 500 | 空态入口；错误态可重试 | `WorkItemListPage.test.tsx::emptyAndErrorStates` |
| TC-36 | 页码收敛 | 返回 2 页但 URL page=5 | page 收敛为 2 | `WorkItemListPage.test.tsx::clampsPage` |

## 3. 导航

| 编号 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-37 | 全部工作项路径 | `projectItemsPath(12, 'all')` | `/pm/projects/12/items/all` | `nav.test.ts::builds project scoped hrefs` |
| TC-38 | 路径解析/高亮 | `/pm/projects/12/items/all` | typeCode=all；类型导航高亮“全部工作项” | `nav.test.ts::reads the project and type from project routes`、`::highlights the active item type, including detail routes` |
| TC-39 | 侧栏入口与计数 | 项目类型含计数 | 显示“全部工作项”与合计；类型行显示计数 | `AppShell.test.tsx::keeps one shell and swaps the sidebar to the current product` |

## 4. 后端

| 编号 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-40 | 系统字段不包含 | title NOT_LIKE | SQL 含 `NOT LIKE` | `FieldResolverTest.java::systemFieldSupportsNotLike` |
| TC-41 | 自定义字段不包含 | custom.note NOT_LIKE | SQL 含 `NOT LIKE` | `FieldResolverTest.java::customFieldSupportsNotLike` |
| TC-42 | OR 组合 | NOT_LIKE + 其他条件 | 顶层逻辑正确，项目过滤仍 AND | `QueryEngineTest.java::topLevelOrSupportsNotLike` |
| TC-43 | 日期为空 | end_time EMPTY / NOT_EMPTY | 输出 IS_NULL / IS_NOT_NULL | `querySpec.test.ts::mapsDateEmptyOperators` |
| TC-44 | 字符串版本号 | 后端 Long 序列化为字符串 | 批量请求 versions 仍带 `{id:number}` | `WorkItemListPage.test.tsx::selectionShowsBulkBarAndBulkEdit` |
| TC-45 | 恢复默认 | 改列后点恢复默认 | 表格列回到默认且不被即时预览写回 | `WorkItemListPage.test.tsx::restoreViewConfigResetsWithoutPreviewEcho` |
| TC-46 | 元数据未就绪 | URL 含自定义 `f`/`sort` | 字段元数据到达前保留，不丢配置 | `listUrlState.test.ts::preservesCustomFilterBeforeMetadata` |

## 5. 手工验证

| 编号 | 步骤 | 预期 |
|---|---|---|
| MV-01 | 列表页刷新/分享 URL | 视图、搜索、筛选、排序、分页、选择保持 |
| MV-02 | 1440×900 / 1280×800 | 默认 8 列无横向滚动，长标题省略 |
| MV-03 | 空/加载/失败/403 | 头部保留，状态文案完整 |
| MV-04 | 批量编辑 dry-run 后取消 | 无数据变化、无活动写入 |
| MV-05 | 视图配置保存后刷新 | 配置从保存视图恢复，脏点消失 |
