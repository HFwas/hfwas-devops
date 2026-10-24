# PM 前端偏差修复测试用例（第一批）

> 日期：2026-10-24
> 版本：v0.3
> 状态：已实施，独立校验第 1 轮不通过，文档项已修正待复验
> 关联设计：[design.md](./design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-24 | 初版：覆盖阻塞 2 项、高 6 项与顺带关闭的中低项 |
| v0.2 | 2026-10-24 | 按实际测试文件与测试名更新自动化映射；TC-07 改为手工验证并说明原因 |
| v0.3 | 2026-10-24 | 独立校验修正：TC-10/TC-20 映射改为 AppShell.test.tsx；TC-11 风险筛选预期改为暂不开放；TC-21 数量改为动态 N；TC-03 细化空值文案 |

---

## 1. 详情弹窗（B1 / H5 / H6 / M1 / M8 / M10 / M13 / M14）

### TC-01 操作记录页签保持详情骨架

- 前置：列表中存在 1 条工作项，打开 `?detail=<id>`。
- 步骤：点击「操作记录」页签。
- 预期：仍显示大标题、状态元信息、三个页签、右栏「属性」；左栏显示操作记录；可点击「详情」切回。
- 自动化：`frontend/src/modules/pm/components/WorkItemDetailDialog.test.tsx::切换操作记录/评论页签时保留标题与右栏`（含标题 30px 断言）

### TC-02 评论页签保持详情骨架

- 步骤：点击「评论」页签。
- 预期：标题、页签、右栏常驻，左栏显示评论输入与列表。
- 自动化：`WorkItemDetailDialog.test.tsx::评论页签保留标题与右栏`

### TC-03 右栏字段齐备

- 前置：工作项无迭代/故事点/风险等值。
- 预期：右栏依次出现 状态、优先级、类型、负责人、报告人、截止时间、所属迭代、故事点、风险等级、修复版本、所属模块、父事项、标签、关注者、环境；空值按字段语义显示「未设置」（日期/迭代/故事点/风险/修复版本/标签/关注者/环境）、「未分配」（负责人/报告人）、「未归属」（所属模块）、「无父事项」（父事项）；不出现第二张「人员与时间」卡片或重复「所属模块」。
- 自动化：`WorkItemDetailDialog.test.tsx::右栏渲染完整属性且空值显示未设置`

### TC-04 校验失败顶部汇总

- 步骤：完整编辑 → 清空标题 → 保存修改。
- 预期：顶部出现「有 1 个字段需要修正」，标题下方保留字段错误。
- 自动化：`WorkItemDetailDialog.test.tsx::标题为空时显示顶部汇总`

### TC-05 取消编辑二次确认

- 步骤：完整编辑 → 修改标题 → 点「取消」。
- 预期：出现「放弃未保存的修改？」确认；点「继续编辑」保留草稿，点「放弃修改」退出编辑。
- 自动化：`WorkItemDetailDialog.test.tsx::取消编辑需二次确认`

### TC-06 标题字号

- 预期：阅读态标题计算字号 ≥ 30px。
- 自动化：由 TC-01 中的 `text-3xl` 断言覆盖。

### TC-07 非法状态目标置灰

- 前置：状态内联菜单打开。
- 预期：列出全部状态；当前状态标「当前」，可流转项可点，不可流转项 disabled 且标「工作流不允许」。
- 自动化：未覆盖（Radix 下拉在 happy-dom 中无法稳定展开）；按本用例手工验证：详情 → 点状态值 → 观察「已完成」置灰且标「工作流不允许」。

### TC-08 权限/不存在提示

- 前置：请求返回 403 或 404。
- 预期：显示「事项不存在或无权访问」，不再显示「事项加载失败」。
- 自动化：`WorkItemDetailDialog.test.tsx::403 时显示不存在或无权访问`

## 2. 列表与筛选（B2 / H2 / H3 / H4 / M2 / M3 / M4 / M5 / M6 / M12）

### TC-09 应用筛选生效

- 前置：打开列表，筛选弹层为空。
- 步骤：点「我的工作项」→「应用筛选」。
- 预期：URL 增加 `f=`；请求体含 `assignee_id EQ <当前用户>`；工具栏出现筛后条数；弹层关闭；列表总数更新。
- 自动化：`frontend/src/modules/pm/pages/WorkItemListPage.test.tsx::应用筛选后写入 URL 并重新查询`

### TC-10 侧栏计数数值化

- 前置：类型数量为字符串 `"1"`、`"0"`。
- 预期：「全部工作项」显示 `1`，不出现 `010000`。
- 自动化：`frontend/src/components/console/AppShell.test.tsx::全部工作项数量按数值求和`（`AppSidebar` 通过 `AppShell` 挂载验证）

### TC-11 故事点与风险字段

- 预期：表头「故事点」；视图配置显示列含「故事点」且无「字段缺失」；**筛选字段暂不含「风险等级」**（项目无对应字段定义，见 `changes.md` §3.1；后端补齐定义后自动开放）。
- 自动化：`frontend/src/modules/pm/utils/columns.test.tsx::provides story points and risk metadata`
- 手工：`?overlay=view-config&tab=filter` 断言不出现「风险等级」，且不出现「字段缺失」。

### TC-12 保存视图入口

- 步骤：页签行点「＋ 保存视图」→ 输入名称 → 保存视图。
- 预期：调用 `pmViewApi.save`，成功后页签出现新视图并选中，URL `view=<id>`。
- 自动化：`frontend/src/modules/pm/components/WorkItemViewTabs.test.tsx::保存视图按钮打开命名弹层`、`WorkItemListPage.test.tsx::保存视图后切换到新视图`

### TC-13 重复页签去重

- 前置：存在与内置视图同名的已保存视图（如「研发待办」）。
- 预期：页签只出现一次，优先使用内置视图。
- 自动化：`WorkItemViewTabs.test.tsx::同名已保存视图不重复渲染`

### TC-14 空态主按钮

- 前置：当前类型 0 条数据。
- 预期：空态显示「新建工作项」主按钮；点击打开新建弹层；表头仍渲染。
- 自动化：`WorkItemListPage.test.tsx::空态显示新建按钮`

### TC-15 加载态骨架

- 前置：列表请求挂起。
- 预期：表头保留，骨架 8 行。
- 自动化：`WorkItemListPage.test.tsx::加载态保留表头与 8 行骨架`

### TC-16 失败态请求编号

- 前置：列表请求失败且返回 requestId。
- 预期：错误区显示请求编号。
- 自动化：`WorkItemListPage.test.tsx::加载失败展示请求编号`

### TC-17 优先级内联即选即存

- 步骤：详情右栏点「优先级」→ 选择「高」。
- 预期：立即调用单字段更新，不出现「保存」按钮。
- 自动化：`WorkItemDetailDialog.test.tsx::updatesPriorityInline`（选中后直接调用 `updateField`，无「保存」按钮）

### TC-18 批量栏内联

- 步骤：选中 1 行。
- 预期：批量条出现在工具栏下方（非 fixed 底部），整宽，文案「已选当前页 1 项」，含批量编辑/状态流转；选中行背景高亮。
- 自动化：`frontend/src/components/console/BulkActionBar.test.tsx::inline 形态不固定定位`、`WorkItemListPage.test.tsx::选中后内联批量栏`

## 3. 其他（H1 / M7 / M9 / M11）

### TC-19 视图配置页脚可见

- 预期：抽屉内容区独立滚动，页脚「恢复默认 / 保存配置」常驻可见。
- 自动化：`frontend/src/modules/pm/components/ViewConfigDrawer.test.tsx::页脚与内容区分层`
- 手工：1440×900 打开 `?overlay=view-config&tab=columns`，确认保存按钮完整在视口内。

### TC-20 面包屑

- 前置：项目名「云原生交付平台」，打开工作项详情。
- 预期：面包屑为 `云原生交付平台 / 工作项 / DEVOPS-1`（无数据时省略事项编号）。
- 自动化：
  - 端到端：`frontend/src/components/console/AppShell.test.tsx::PM 工作项面包屑包含项目名与事项编号`
  - 纯函数：`frontend/src/shared/console/navigation.test.ts::follows the current route`（含带 `projectName`/`itemKey` 上下文的三级面包屑断言）

### TC-21 文案

- 预期：搜索字段页签显示「搜索框会在这 N 个字段中匹配」，N 等于当前搜索字段数（默认配置为 4）；全局搜索占位「搜索项目、事项、成员」。
- 自动化：`ViewConfigDrawer.test.tsx::搜索字段展示实际数量`、`frontend/src/shared/console/shellSearch.test.ts::PM 作用域使用项目、事项、成员搜索文案`
