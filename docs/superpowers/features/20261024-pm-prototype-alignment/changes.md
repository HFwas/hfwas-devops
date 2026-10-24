# 代码变更：PM 前端与冻结原型偏差修复（第一批）

> 日期：2026-10-24
> 版本：v0.5
> 状态：第一批已实施；独立校验第 2 轮通过、第 3 轮确认通过（阻塞 0 项）
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-24 | 开工骨架：计划改动文件与对应用例 |
| v0.2 | 2026-10-24 | 补全实际改动、计划外偏差与本地验证结果 |
| v0.3 | 2026-10-24 | 回填独立校验第 1 轮结论；按校验修正 test-cases/design/契约文档 |
| v0.4 | 2026-10-24 | 回填第 2 轮通过结论；补 §2 两处代码增量说明 |
| v0.5 | 2026-10-24 | 第 3 轮确认通过；§2 #1 空值文案与 design 口径对齐 |

---

## 1. 原型 → 路由 → 实现文件 → 共享组件映射

| 原型画板 | 路由 / 状态 | 实现文件 | 共享组件 |
|----------|-------------|----------|----------|
| 02/03 列表 | `/pm/projects/:id/items/:typeCode` | `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | `ListPageShell`、`DataTable`、`BulkActionBar`、`WorkItemViewTabs` |
| 10 选中态 | 同上 + `sel=` | 同上 | `BulkActionBar`（inline）、`DataTable`（行高亮） |
| 11 视图配置 | `?overlay=view-config` | `frontend/src/modules/pm/components/ViewConfigDrawer.tsx` | `Sheet` |
| 19/26 筛选 | `?overlay=filter` | `frontend/src/modules/pm/components/FilterBuilder.tsx` | `Dialog` |
| 49 保存视图 | `?overlay=save-view` | `WorkItemViewTabs.tsx` + `SaveViewDialog.tsx` | `Dialog`、`Input` |
| 28–41 详情 | `?detail=<id>&dtab=` | `frontend/src/modules/pm/components/WorkItemDetailDialog.tsx` | `Dialog` |
| 全局 header/侧栏 | 所有 PM 页面 | `AppSidebar.tsx`、`AppShell.tsx`、`breadcrumbs.ts`、`shellSearch.ts` | — |
| 列/表头 | 列表 | `frontend/src/modules/pm/utils/columns.tsx` | — |

## 2. 实际改动清单

| # | 文件 | 改动 | 用例 |
|---|------|------|------|
| 1 | `frontend/src/modules/pm/components/WorkItemDetailDialog.tsx` | 标题/元信息/页签/右栏抽为常驻结构，页签只切换左栏；右栏补齐 所属迭代/故事点/风险等级/修复版本/父事项/标签/关注者/环境，空值按字段语义显示（未设置/未分配/未归属/无父事项）；移除重复「所属模块」的独立卡片，人员与时间并入属性卡；标题 30px；校验失败顶部汇总；取消编辑二次确认；状态内联列出全部状态、非法项置灰；403/404 文案；优先级选中即保存 | TC-01～TC-08、TC-17 |
| 2 | `frontend/src/modules/pm/components/FilterBuilder.tsx` | 应用时只调用一次 `onApply`，避免旧 URL 状态覆盖；筛选字段只展示可筛选元数据 | TC-09 |
| 3 | `frontend/src/modules/pm/components/ViewConfigDrawer.tsx` | 固定头 + 独立滚动内容 + 固定页脚；页脚左右布局；搜索字段数量替换 `N`；列行补字段键与「固定」标记 | TC-19、TC-21 |
| 4 | `frontend/src/modules/pm/utils/columns.tsx` | 新增 `storyPoints`、`risk` 伪字段元数据与单元格渲染；`buildColumnCatalog` 在同名自定义字段定义存在时合并 `filterable/sortable/queryField` | TC-11 |
| 5 | `frontend/src/components/console/AppSidebar.tsx` | 计数 `Number()` 化后求和 | TC-10 |
| 6 | `frontend/src/modules/pm/components/WorkItemViewTabs.tsx` | 按 id/名称与内置视图去重；新增「＋ 保存视图」入口；保存按钮移出 `role="tablist"` 容器（a11y） | TC-12、TC-13 |
| 7 | `frontend/src/modules/pm/components/SaveViewDialog.tsx`（新增） | 视图命名、共享范围（仅自己可见）、配置摘要 | TC-12 |
| 8 | `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 保存视图接线；空态改为表内空行（保留表头）并含「新建工作项」；表格始终渲染（骨架保留表头）；错误 requestId；内联批量栏；错误/空态文案 | TC-09、TC-12、TC-14～TC-16、TC-18 |
| 9 | `frontend/src/components/console/ListPageShell.tsx` | 新增 `batchBar` 插槽（工具栏与筛选条之间） | TC-18 |
| 10 | `frontend/src/components/console/BulkActionBar.tsx` | 新增 `inline` 形态与 `countPrefix` | TC-18 |
| 11 | `frontend/src/components/console/DataTable.tsx` | 新增 `skeletonRows`（默认 4，PM 传 8，避免影响其他页面）；选中行 `bg-primary/10` | TC-15、TC-18 |
| 12 | `frontend/src/components/console/breadcrumbs.ts`、`AppShell.tsx` | 面包屑补项目名与事项编号，标签「事项」→「工作项」；Shell 查询项目/事项数据 | TC-20 |
| 13 | `frontend/src/shared/console/shellSearch.ts` | PM 作用域占位「搜索项目、事项、成员」 | TC-21 |
| 14 | `frontend/src/shared/types/common.ts`、`errors/apiError.ts`、`api/request.ts` | `BaseResult`/`ApiError` 透传 `requestId`，供失败态展示 | TC-16 |
| 15 | `frontend/src/modules/pm/utils/listUrlState.ts` | overlay 白名单新增 `save-view` | TC-12 |
| 16 | 测试 | 修改 7 个、新增 2 个测试文件（共 9 个），覆盖 21 条用例 | 全部 |

## 3. 计划外偏差

1. **故事点/风险筛选与排序未开放。** 伪字段已补中文名与单元格渲染，但项目 `pm_field_definition` 无对应定义，走 `custom.*` 过滤器/排序会被后端拒绝。为避免 400，本轮将二者默认标为不可筛选/不可排序；`buildColumnCatalog` 会在同名自定义字段定义存在时自动合并 `filterable/sortable/queryField`，因此后端补默认字段定义后无需再改前端。
2. **空态不再使用 `ListPageShell` 的 `EmptyState`**，改为 `DataTable` 内空行，以保留原型要求的表头。`ListPageShell` 的 `empty` 分支保持原样，未删除。
3. **优先级「选中即保存」保留铅笔触发**：点击铅笔后选择即提交，去掉了「保存/取消」按钮；未改成点击字段文本直接弹菜单。
4. **面包屑项目名/事项编号由 `AppShell` 查询**，与详情弹窗共用 query key，不产生额外请求。
5. **构建产物同步方式**：本地 nginx 挂载 `artifacts/frontend`，使用 `cp -a frontend/dist/. artifacts/frontend/` 同步；未修改 compose 或 Dockerfile。
6. **`DataTable` 选中行高亮从 `primary/5` 提到 `primary/10` 是共享组件级改动**，会影响其他使用选择能力的表格；选择态更明显属于统一改进，未单独升版共享组件文档。
7. **编辑态会强制切回「详情」页签**：从操作记录/评论点「完整编辑」时调用 `onTabChange('detail')`，避免左栏仍显示旧页签内容。

## 4. 本地验证结果

- `npx tsc --noEmit`：通过。
- `npm test`：84 个测试文件、368 通过、1 跳过，0 失败。
- `npm run build`：通过（仅既有 chunk 体积告警）。
- 真实浏览器（1440×900，admin）复验：
  - 应用筛选后 URL 写入 `f=`，列表变为「共 0 条」并出现条件行「负责人 是 1」「筛后 0 条」。
  - 详情切换到「操作记录」后标题、3 个页签、右栏属性仍在，`aside` 存在。
  - 详情标题计算字号 30px；右栏 15 项字段齐备；无重复「所属模块」。
  - 侧栏「全部工作项」显示 1；表头显示「故事点」。
  - 视图配置页脚「恢复默认 / 保存配置」完整可见（bottom 888 < 900），左右分列；配置中无「字段缺失」。
  - 选中行后批量条为 `data-variant="inline"`、`position: static`、整宽 1128px，文案「已选当前页 1 项」，行背景 `primary/10`。
  - 需求类型页签仅 4 个内置视图，重复「研发待办」已消失。
  - 面包屑为「kokokoda / 工作项」。
  - 保存视图弹层默认名「研发待办 · 我的配置」，配置摘要 8/4/5。

## 5. 校验结果

### 第 1 轮（2026-10-24，独立 agent）

**结论：不通过（阻塞 3 项，非阻塞 5 项）。**

- `npx tsc --noEmit` 通过；`npm test` 84 文件 / 368 通过 / 1 跳过 / 0 失败。
- 真实浏览器复验 B1、B2、H1–H6、M1–M14 的主要验收点均成立（截图 `/tmp/pm-alignment-verify-20261024/`）。
- 实现未发现与 `changes.md` §2 不一致的漏做/多做。

**阻塞项（均为测试文档问题，非代码问题）：**

1. TC-10 映射的 `AppSidebar.test.tsx` 不存在，实际测试在 `AppShell.test.tsx`。
2. TC-20 映射的 `navigation.test.ts::PM 工作项面包屑包含项目名与事项编号` 不存在，实际在 `AppShell.test.tsx`；纯函数用例在 `navigation.test.ts::follows the current route`。
3. TC-11 要求「筛选字段含风险等级」，但 `risk` 当前 `filterable:false`（无字段定义），视图配置筛选页签实测无该字段；该行为已登记在 §3.1，用例未同步。

**非阻塞项：** `design.md` §5.3/§7 #5 仍写「可排序可筛选 / 表头含风险等级」；契约 §3 批量栏仍列「导出」、视图页签行仍写「只落新建」；TC-21 写死「6 个字段」；§2 #16 测试文件数写成 7；TC-03 空值文案未区分「未分配/未归属/无父事项」。

**本轮修正：**

- `test-cases.md` v0.3：修正 TC-10/TC-20 映射；TC-11 预期改为「风险筛选暂不开放」并补手工断言；TC-21 改为动态 N；TC-03 细化空值文案。
- `design.md` v0.3：修正 H3 与 §5.3/§7，明确故事点/风险当前只做显示，筛选排序待后端字段定义。
- `prototype-contract.md` v0.23：批量栏「导出」、视图页签行「导入/导出」补后续阶段注记，并注明已落「保存视图」。
- `changes.md` §2 #16 测试文件数改为 9（修改 7 + 新增 2）。

修正仅涉及文档，不改代码；`npx tsc --noEmit` 与 `npm test` 重新执行通过。待独立 agent 复验后更新本节结论。

### 第 2 轮（2026-10-24，独立 agent 复验）

**结论：通过（阻塞 0 项）。** 第 1 轮 3 项阻塞与 5 组非阻塞文档问题已逐项关闭并经证据核对。

- `npx tsc --noEmit` 通过；`npm test -- --run` 84 文件 / 368 通过 / 1 跳过 / 0 失败。
- 浏览器在 16:09 构建产物（`index-Cl0HV3EH.js`）上复验：需求类型页签 4 个且无重复「研发待办」、侧栏「全部工作项 1」、表头含「故事点」、全局搜索占位「搜索项目、事项、成员」、「保存视图」入口可用；`?overlay=view-config&tab=filter` 无「风险等级」且无「字段缺失」。

**剩余风险（不阻塞）：**

1. 写操作链路（评论、单字段更新、批量提交、删除）与无权限/只读态未真实执行；前者属只读审计限制，后者按用户决策暂缓。
2. 第 2 轮建议的两条文档精度问题已在本轮同步：§2 #4 补 `buildColumnCatalog` 合并逻辑、§2 #6 补保存按钮移出 tablist；`design.md` v0.4 将 H5/§7 #7 空值文案细化为「未设置 / 未分配 / 未归属 / 无父事项」。
3. 基线漂移：16:06–16:08 的两处代码增量（同名字段能力合并、tablist 结构）已在第 2 轮按当前 diff 重新核对并通过。

### 第 3 轮（2026-10-24，独立 agent 确认）

**结论：通过（阻塞 0 项）。** 本轮无代码变化（`git diff` 与第 2 轮一致），两条文档建议已准确落实，未引入新的阻塞性不一致。

- 剩余仅两处可选清理的笼统空值措辞（`design.md` §5.3 第 5 点、`changes.md` §2 #1），已在第 3 轮统一为「未设置 / 未分配 / 未归属 / 无父事项」。
- `progress.md` 第 1 轮历史日志按「只追加不改历史」保持原样；`test-cases.md` 中的「未设置」为测试函数名，不做修改。
