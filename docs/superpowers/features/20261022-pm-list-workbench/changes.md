# PM 列表工作台代码变更

> 日期：2026-10-22
> 版本：v0.5
> 状态：已交付（v1.10 已冻结，布局修订复验通过）
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 开工骨架：计划文件与设计章节映射 |
| v0.2 | 2026-10-22 | 回填实际改动、偏差与真实验证结果 |
| v0.3 | 2026-10-22 | 第 2 次校验修复：恢复默认预览回写、补字段元数据/恢复默认用例；补文档轮次与计数 |
| v0.4 | 2026-10-22 | 第 3 次校验通过；预览排序驱动查询；统一文档措辞与计数 |
| v0.5 | 2026-10-22 | 布局修订：删除页头、主操作移到视图页签行、筛选入口收敛；补原型 v1.10 映射与前端验证 |

---

## 1. 变更清单（计划）

### 1.1 后端

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `backend/pm-core/.../query/model/QueryOperator.java` | 修改 | §5.2 | 新增 `NOT_LIKE` |
| `backend/pm-core/.../query/engine/JsonSqlDialect.java` | 修改 | §5.2 | `notLike` |
| `backend/pm-core/.../query/engine/FieldResolver.java` | 修改 | §5.2 | 系统字段 `NOT_LIKE` |
| `backend/pm-core/.../field/spi/handler/AbstractFieldTypeHandler.java` | 修改 | §5.2 | 自定义字段 `NOT_LIKE` |
| `backend/pm-core/.../workitem/service/TransitionConditionEvaluator.java` | 修改 | §5.2 | 流转条件 `NOT_LIKE` |
| `backend/pm-core/src/test/.../FieldResolverTest.java` | 修改 | TC-40/41 | NOT LIKE 用例 |
| `backend/pm-core/src/test/.../TransitionConditionEvaluatorTest.java` | 新增 | TC-42/时间比较 | end_time 时间比较 |
| `docs/pm/pm-api.md`、Postman | 修改 | §5.2 | 运算符与示例 |

### 1.2 前端契约与入口

| 文件 | 动作 | 说明 |
|---|---|---|
| `frontend/src/modules/pm/types/index.ts` | 修改 | 补字段、批量/筛选/视图类型 |
| `frontend/src/modules/pm/api/index.ts` | 修改 | 批量更新/流转、批量配置、IO 列 |
| `frontend/src/modules/pm/nav.ts` | 修改 | `all` 路径与 iteration 图标 |
| `frontend/src/modules/pm/utils/format.ts` | 修改 | `formatDate` |
| `frontend/src/router.tsx` | 不改 | 复用 `:typeCode`，`all` 为哨兵 |

### 1.3 纯函数

| 文件 | 说明 |
|---|---|
| `frontend/src/modules/pm/utils/listUrlState.ts` + test | URL 解析/序列化/裁剪 |
| `frontend/src/modules/pm/utils/viewConfig.ts` + test | 视图定义、保存形状、脏状态 |
| `frontend/src/modules/pm/utils/filterTree.ts` + test | 条件树、校验、表达式、快捷筛选 |
| `frontend/src/modules/pm/utils/querySpec.ts` + test | QuerySpec 构建 |
| `frontend/src/modules/pm/utils/columns.tsx` + test | 列目录与单元格 |

### 1.4 组件与页面

| 文件 | 说明 |
|---|---|
| `frontend/src/modules/pm/hooks/usePmListUrlState.ts` | URL 状态 Hook |
| `frontend/src/modules/pm/components/WorkItemViewTabs.tsx` | 视图页签与脏点 |
| `frontend/src/modules/pm/components/ViewConfigDrawer.tsx` | 视图配置抽屉 |
| `frontend/src/modules/pm/components/FilterBuilder.tsx` | 筛选构建器 |
| `frontend/src/modules/pm/components/BatchResultList.tsx` | 批量结果 |
| `frontend/src/modules/pm/components/BulkEditDialog.tsx` | 批量编辑 |
| `frontend/src/modules/pm/components/TransitionDialog.tsx` | 批量流转 |
| `frontend/src/modules/pm/components/BulkDeleteDialog.tsx` | 批量删除 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 页面重写 |
| `frontend/src/components/console/AppSidebar.tsx` | 全部工作项与计数 |
| `frontend/src/modules/pm/pages/ProjectListPage.tsx` | 入口改 `items/all` |

### 1.5 测试

| 文件 | 覆盖 |
|---|---|
| `listUrlState.test.ts` | TC-01–05 |
| `viewConfig.test.ts` | TC-06–09 |
| `filterTree.test.ts` | TC-10–14 |
| `querySpec.test.ts` | TC-15–20 |
| `columns.test.tsx` | TC-21–24 |
| `WorkItemListPage.test.tsx` | TC-25–36 |
| `nav.test.ts` / `AppShell.test.tsx` | TC-37–39 |
| `FieldResolverTest.java` | TC-40–41 |

## 2. 原型 → 路由 → 实现文件 → 共享组件

| 原型 | 路由 | 实现 | 共享组件 |
|---|---|---|---|
| `#/workbench` | `/pm/projects/:projectId/items/all` | `WorkItemListPage` | `ListPageShell`、`SearchInput`、`FilterBar`、`BulkActionBar`、`DataTable`、`Pagination` |
| `#/workbench?type=bug` | `/pm/projects/:projectId/items/bug` | 同上 | 类型导航（AppSidebar） |
| 视图配置 O01 | `?overlay=view-config` | `ViewConfigDrawer` | `Sheet`、`Tabs`、`Checkbox` |
| 筛选 O07 | `?overlay=filter` | `FilterBuilder` | `Dialog`、`Select`、`Input` |
| 批量编辑 O02 | `?overlay=bulk-edit` | `BulkEditDialog` | `Dialog`、`BatchResultList` |
| 批量流转 O10 | `?overlay=transition` | `TransitionDialog` | `Dialog`、`BatchResultList` |
| 批量删除 O08 | `?overlay=delete` | `BulkDeleteDialog` | `ConfirmDialog`/`Dialog` |

## 3. 计划外偏差

| # | 偏差 | 原因 |
|---|------|------|
| 1 | `ListPageShell` 新增 `tabs`、`emptyTitle` 两个可选 props | 视图页签需位于页头与工具栏之间、空态标题需可配置；均向后兼容 |
| 2 | 视图配置保存为 `columns: [单对象]`，兼容旧 `[{fieldKey}]` | 后端 `PmSavedView.columns` 为 `List<Map>`，无需迁移 |
| 3 | 内置视图保存按「同名 + 同 typeCode」创建/更新已保存视图 | 内置视图无数据库行；现有接口无个人/共享隔离 |
| 4 | 无项目角色接口，只读态由列表 403 驱动 | 项目成员模型未落地 |
| 5 | 批量删除逐条调用删除接口，默认关闭 | 后端无 batch-delete；设置页在后续阶段 |
| 6 | 故事点/风险/迭代按动态列处理，缺失时显示 `—` | 用户确认不新增迁移 |
| 7 | 批量编辑字段受默认白名单限制（priority/assignee_id/module_id/end_time） | 后端默认配置；自定义字段需 `batch_editable=1` |
| 8 | 搜索的 itemKey 仅解析编号、负责人仅精确匹配姓名 | 后端无 item_key 模糊查询，assignee 为 ID |
| 9 | 批量更新不接受 `status`，状态统一走批量流转 | 后端 `save/batch-update` 禁止直接改状态 |
| 10 | 未实现原型的 `state=empty/loading/...` 演示参数 | 以真实加载/空/错态、density 与 sel 覆盖等价状态 |
| 11 | 无真实登录环境截图对比 | 本机未启动后端；以自动化测试与构建为准 |
| 12 | 「管理事项类型」侧栏入口未加入 | 类型管理页在阶段 3 |
| 13 | 视图配置改为本地即时预览，保存才落库并写 URL | 对齐 AC-04；关闭抽屉丢弃未保存预览，避免预览与 URL 回写 |
| 14 | 「全部工作项」限定项目启用类型 | 保证侧栏类型计数合计等于全部工作项 |
| 15 | 批量 versions 兼容后端 Long→String 序列化 | 避免版本号永远是字符串导致乐观锁失效 |
| 16 | 流转条件 `end_time` 改为时间比较 | 支持日期 GT/GTE/LT/LTE/BETWEEN，原实现按数字解析会异常 |
| 17 | 未知/跨类型保存视图 id 自动回退 `dev` | 避免无激活页签与错误配置 |
| 18 | 「恢复默认」不再清空筛选树 | 按钮语义只恢复列/搜索/密度/排序 |

## 4. 验证结果

| 检查 | 命令 | 结果 |
|------|------|------|
| 迁移规范 | `bash scripts/check-flyway-migrations.sh` | 通过：3 个迁移，最新 V3 |
| 后端 pm-core | `mvn -B -f backend/pom.xml -pl pm-core -am test` | 通过：57 tests（NOT_LIKE 3 条、流转时间比较 2 条） |
| 后端 server | `mvn -B -f backend/pom.xml -pl server -am test` | 通过：34 tests，5 skipped（Docker 未启动） |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过：80 files，327 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| 手工浏览器验证 | — | 未执行：本机后端/登录环境未启动 |

新增/修改测试：

- `frontend/src/modules/pm/utils/{listUrlState,viewConfig,filterTree,querySpec}.test.ts`、`columns.test.tsx`
- `frontend/src/modules/pm/pages/WorkItemListPage.test.tsx`（11 条）
- `frontend/src/modules/pm/nav.test.ts`、`frontend/src/router.pm.test.ts`、`frontend/src/components/console/AppShell.test.tsx`
- `backend/pm-core/.../FieldResolverTest.java`（2 条 NOT_LIKE）

## 5. 校验结果

### 第 1 次独立校验（2026-10-22）

结论：**失败**。命令全部通过，但发现以下问题，已修复：

| # | 问题 | 处理 |
|---|------|------|
| 1 | 日期筛选「为空/不为空」被静默丢弃 | `querySpec` 日期分支先映射 `IS_NULL/IS_NOT_NULL`；补 `mapsDateEmptyOperators` |
| 2 | `pm-api.md` 未升版、无 NOT_LIKE 失败示例；Postman 失败示例请求/响应不自洽 | 升 v1.6；补 403 失败示例；Postman 改为真实可复现的 403 |
| 3 | 测试用例引用 5 个不存在的测试名 | 改为真实测试名，并补 TC-28/29/32/33/42/43/44 |
| 4 | 批量 `version` 因 Long→String 永远不发送 | 类型放宽为 `number \| string`，转换 `Number` 后发送；补字符串版本用例 |
| 5 | 流转条件 `end_time` 按数字解析异常 | 改为时间比较并补 `TransitionConditionEvaluatorTest` |
| 6 | URL 自定义筛选/排序在字段元数据到达前丢失 | 增加 `fieldsReady`，未就绪时保留原始树/排序 |
| 7 | 阻塞风险视图把 risk 与 status 混为 OR；本周上界非下周一 | 改为 risk 优先、否则 status；上界改为下周一 |
| 8 | 搜索防抖 300ms、列配置非即时 | 页面传 350ms；视图配置变更即时写 URL |
| 9 | 未知保存视图 id 无回退；恢复默认清筛选 | 回退 `dev`；恢复默认保留筛选 |
| 10 | 「全部工作项」包含停用类型事项，计数不一致 | 全部视图限定项目启用类型 |

### 第 2 次独立校验（2026-10-22）

结论：**失败**，仅剩 1 个功能回归与文档轮次记录：

| # | 问题 | 处理 |
|---|------|------|
| 1 | 「恢复默认」被即时预览回写，实际失效 | 改为本地 `previewConfig`（不写 URL），保存才落库；补 `restoreViewConfigResetsWithoutPreviewEcho` |
| 2 | `progress.md` 未记录修复轮次、`test-cases.md`/`changes.md` 未升版 | 本轮补齐版本与变更记录 |
| 3 | TC-33 引用不精确、TC-43/44 表格断裂 | 修正引用并合并表格 |

### 第 3 次独立校验（2026-10-22）

结论：**通过**。B1 专项复核确认恢复默认不再回写；后端 57、server 34（5 skipped）、前端 327、`tsc` 与构建全部通过；TC-01–TC-46 引用与断言核对通过；文档版本与轮次记录补齐。

剩余非阻断项：MV-01~05 与真实截图未执行（无登录环境）；快捷筛选「阻塞风险」与内置视图语义后续可统一；关闭抽屉未保存预览直接丢弃（有意行为）。

## 6. 布局修订（2026-10-22，原型 v1.10）

### 6.1 变更文件

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `frontend/src/components/console/ListPageShell.tsx` | 修改 | §10.1/§10.2 | 新增 `showHeader`、`rowActions`；页签与主操作同一行（`data-slot=list-page-tab-row`），隐藏页头时渲染 `sr-only` 标题 |
| `frontend/src/modules/pm/components/WorkItemViewTabs.tsx` | 修改 | §10.2 | 去掉自身 `border-b`，由页签行统一提供分隔线 |
| `frontend/src/components/console/FilterBar.tsx` | 修改 | §10.3 | 新增 `showLabel`；空条件且隐藏标签时不渲染占位文案 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 修改 | §10 | `showHeader=false`、`rowActions=新建`、移除描述与 `latestUpdate`；条件行仅在 `chips.length > 0` 时渲染，去掉「+ 添加筛选」与「清除全部」 |
| `frontend/src/modules/pm/pages/WorkItemListPage.test.tsx` | 修改 | TC-47/48 | 新增 `keepsCreateInTabRowAndDropsInlineFilterAdd` |
| `frontend/src/components/console/ListPageShell.test.tsx` | 修改 | TC-47/48 | 新增 `putsRowActionsInTabRowAndHidesHeader` |
| `frontend/src/components/console/FilterBar.test.tsx` | 修改 | TC-49 | 新增 `rendersBareChipsWithoutLabel` |
| `docs/product/06-prototype/pm/index.html` | 修改 | §10 | 原型 v1.10：删除 `page-head`、`.views-row` + `.views-actions`、条件行仅在存在条件时输出 |
| `docs/product/06-prototype/pm/{prototype-spec.md,prototype-spec.json,README.md,prototype-contract.md}` | 修改 | — | 版本、变更记录、信息层级与必须一致项同步 |
| `docs/product/06-prototype/pm/shots/{02..27,49,50,52}-*.png` | 重出 | — | 1440×900 @2x（52 号 1280×800 @2x） |

### 6.2 原型 → 实现映射（新增部分）

| 原型元素 | 实现 |
|---|---|
| 视图页签行右侧 [导入] [导出] [新建] | `ListPageShell.rowActions`：本轮仅「新建」；导入/导出见 §3 偏差 9 |
| 页签行整体分隔线 | `[data-slot=list-page-tab-row]` 的 `border-b` |
| 页头删除 | `ListPageShell showHeader={false}` → `h1.sr-only` |
| 工具栏「筛选」唯一入口 | 现有按钮保持不变 |
| 条件行（仅有条件时） | `FilterBar`，`showLabel={false}`、`resetLabel="清空全部"`、无边框样式 |

### 6.3 偏差

| # | 偏差 | 原因 |
|---|------|------|
| 19 | 实现侧页签行只有「新建」，没有「导入 / 导出」 | 导入/导出属后续阶段（§3 偏差 9），本次只调整位置，不新增功能 |
| 20 | 条件行保留「清空全部」与「筛后 N 条」 | 与原型一致：无筛选时整行不渲染，有筛选时可一键清空并看到筛后条数 |
| 21 | 隐藏页头后仍保留 `sr-only h1` | 无障碍与页面标题识别，视觉上不可见，符合「移除这一行」 |
| 22 | 原型页签行按钮 28px，实现用 `size="sm"`（32px） | 与 34px 页签同排时避免行高被撑到 40px；4px 差异属允许差异项（`spacing` 内） |

### 6.4 验证结果

| 检查 | 命令 | 结果 |
|---|---|---|
| 前端测试（受影响） | `npx vitest run src/components/console/ListPageShell.test.tsx src/components/console/FilterBar.test.tsx src/modules/pm/pages/WorkItemListPage.test.tsx` | 通过：3 files / 19 tests |
| 前端全量测试 | `npm test` | 通过：80 files，330 passed / 1 skipped |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| 原型视觉 | 本地 HTTP + headless Chrome，1440×900@2x 与 1280×800@2x | 默认、筛选、只读、暗色、1280 均无空条件行，页签行右侧操作对齐；52 号画板连拍存在左侧栏底部渲染时机抖动（3 次中 2 次哈希一致），仓库文件为内容完整变体 |

### 6.5 第 1 次独立校验后的修复（2026-10-22）

| # | 校验问题 | 处理 |
|---|---|---|
| 1 | P1：条件行丢了「清空全部」，与设计/契约/原型冲突 | `WorkItemListPage` 去掉 `showReset={false}`，传 `resetLabel="清空全部"`；`FilterBar` 移除未使用的 `showReset` |
| 2 | P2：design 变更记录写 AC-14/AC-15 | 改为 AC-16/AC-17，AC-17 补「清空全部」 |
| 3 | P2：契约写 README v1.12 | 改为 v1.13，并注明导入/导出在实现侧属后续阶段 |
| 4 | P2：changes/design 状态与 progress 不一致 | changes 改为「v0.5 布局修订待复验」 |
| 5 | P2：偏差 20 措辞矛盾 | 改为「条件行保留清空全部与筛后 N 条，无筛选时整行不渲染」 |
| 6 | P2：TC-47/49 断言不足 | `keepsCreateInTabRowAndDropsInlineFilterAdd` 补「项目名不出现」；`appliesFilter` 补 filter-bar 存在、chip、清空全部、无「添加筛选」 |
| 7 | P3：新建按钮尺寸与设计不符 | 改为 `size="sm"`，偏差 22 登记 4px 差异 |
| 8 | P3：`showHeader=false` 时 `actions` 被静默丢弃 | `actions` 在隐藏页头时并入页签行，注释同步 |
| 9 | P3：原型遗留注释「页头总数」 | 改为「工具栏总数」 |
| 10 | P3：52 号画板重拍不一致 | 用统一命令重拍，取左侧栏内容完整的变体；连拍仍有渲染时机抖动，已如实记录 |

修复后复跑：受影响 19 tests 通过；全量 330 passed / 1 skipped；`tsc --noEmit` 通过。

### 6.6 第 2 次独立校验（2026-10-22）

结论：**通过**。P1 已关闭，3 项 P2 文档项已修正，测试与类型检查通过。

复验后补充处理：
- TC-49 自动化引用改为 `FilterBar.test.tsx::rendersBareChipsWithoutLabel`，预期补「清空全部」（test-cases v0.5）。
- `changes.md` 与 `design.md` 中残留的 `showReset` 描述已删除。
- 新增 `ListPageShell.test.tsx::fallsBackToActionsWhenHeaderHidden`，覆盖 `showHeader=false` 且只传 `actions` 的兜底分支。
- 复跑：受影响 3 files / 20 tests 通过；全量 80 files / 331 passed / 1 skipped；`npx tsc --noEmit` 通过。
