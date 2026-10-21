# 原型契约：PM 工作台改版

> 日期：2026-10-19
> 版本：v0.1
> 功能：`20261019-pm-workbench-redesign`
> 关联：[design.md](./design.md)、[prototype-first-standard.md](../../../frontend/prototype-first-standard.md) v0.2
> 原型版本：`v1.0`（**未冻结**，等待评审）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-19 | 初版：对齐等级 A2、必须一致项、允许差异、数据 profile 与共享组件对账（实现前） |

---

## 1. 基本信息

| 项 | 值 |
|----|----|
| 功能目录 | `docs/superpowers/features/20261019-pm-workbench-redesign/` |
| 原型入口 | `prototype/index.html` |
| 原型版本 | `v1.0` |
| 冻结提交 | 待评审后填写 |
| 对齐等级 | `A2`（布局对齐） |
| 参考视口 | `1440×900 @2x` |
| 验收环境 | 本地 Chrome / Playwright |
| 数据 profile | `pm-workbench-fixtures-v1` |
| 设计验收人 | 产品 / 前端 / 测试 |

## 2. 权责确认

- [x] 页面清单、信息架构、页面层级以冻结原型为准。
- [x] 颜色、字体、圆角、阴影、状态色以设计系统 token 为准。
- [x] 布局骨架与间距以原型 1440×900 为基准，响应式由前端定义。
- [x] 交互路径、页签、弹层、返回路径以冻结原型为准。
- [x] 业务规则、校验、权限以 `design.md`、后续 `test-cases.md`、接口文档为准。
- [x] 真实数据、数量、状态分布以 API 与后端为准。
- [x] 可访问性、性能、安全、浏览器兼容由前端负责。
- [x] 组件内部 DOM 与实现以 `src/components/ui`、`src/components/console` 为准。

## 3. 必须一致（A2）

- [x] 页面组成顺序：页头 → 视图页签 → 工具栏 → 条件行 → 批量栏 → 表格 → 分页。
- [x] 侧栏 256px、顶栏 56px、内容区 24px 边距。
- [x] 表格列顺序与含义：选择、编号、类型、标题、状态、优先级、负责人、截止、更新、操作。
- [x] 列宽比例：编号 104、类型 72、状态 96、优先级 72、负责人 128、截止 92、更新 120、操作 44，标题弹性。
- [x] 行高：标准 44px、紧凑 36px。
- [x] 按钮层级：新建 = 主按钮；导入/导出 = 次按钮；删除 = 危险按钮。
- [x] 状态语义：待办 / 进行中 / 已阻塞 / 待验证 / 已完成，均带圆点与文案。
- [x] 优先级：最高 / 高 / 中 / 低，带方向图标与文案。
- [x] 共享组件选型：`DataTable`、`Pagination`、`PageHeader`、`Sheet`、`Dialog`。
- [x] 1440×900 下无横向滚动，长标题省略。

## 4. 允许差异

| # | 差异 | 原因 | 分类 | 处理 |
|---|------|------|------|------|
| 1 | 真实数据值、条数、状态分布不同 | 后端数据 | 数据导致 | 不改代码 |
| 2 | 无数据时隐藏分页 | 共享 `Pagination` 单页行为 + 空态语义 | 产品决策 | 保持并记录 |
| 3 | 无权限时不渲染工具栏与分页 | 权限规则 | 产品决策 | 保持并记录 |
| 4 | 头像使用首字占位而非真实图片 | 原型无外部资源 | 环境差异 | 实现用真实头像，降级为首字 |
| 5 | 字体抗锯齿与系统字体回退 | 渲染环境 | 环境差异 | 不作为失败条件 |
| 6 | 分页翻页使用轮转样例数据 | 原型无后端 | 数据导致 | 实现接真实分页 |
| 7 | 分组展示未在原型落地 | 首版范围 | 产品决策 | 实现阶段不开发 |
| 8 | 筛选面板首版仅演示两个条件 | 原型简化 | 产品决策 | 实现按 `design.md` §5.2 支持多条件 |

## 5. 数据 profile

| 状态 | Fixture | 动态区域 mask | 对应用例 |
|------|---------|---------------|----------|
| 默认 | 24 条样例，total=128 | 更新时间 | 待 `test-cases.md` |
| 筛选 | 状态=进行中 + 优先级=高 | 结果数 | 待补 |
| 选中 | 第 2 / 5 / 8 行 | — | 待补 |
| 空态 | 无记录 | — | 待补 |
| 加载 | 骨架 8 行 | 骨架 | 待补 |
| 失败 | HTTP 503 | 请求编号 | 待补 |
| 无权限 | 只读访客 | — | 待补 |
| 大数据量 | total=2486 | 页码 | 待补 |
| 长文本 | 超长标题 + 长负责人名 | — | 待补 |
| 校验失败 | 标题为空、截止早于今天 | — | 待补 |

要求：每个状态均可通过固定 URL 直达；生产数据不得作为视觉基线。

## 6. 原型 → 实现映射（计划）

| 原型 | 路由 | 实现文件 | 共享组件 | 状态 |
|------|------|----------|----------|------|
| P01 工作项列表 | `/pm/projects/:projectId/items/:typeCode` | `modules/pm/pages/WorkItemListPage.tsx` | `ListPageShell`、`DataTable`、`Pagination` | 待实现 |
| P02–P07 状态 | 同上 | 同上 | `EmptyState`、`ErrorState`、`SkeletonRows`、`NoPermissionState` | 待实现 |
| P08 项目列表 | `/pm/projects` | `modules/pm/pages/ProjectListPage.tsx` | 同上 | 待实现 |
| O01 筛选 | 查询参数 | `modules/pm/components/FilterBuilder.tsx` | `Dialog`、`Select` | 待实现 |
| O02 展示设置 | 查询参数 | `modules/pm/components/DisplayMenu.tsx` | `Popover`、`Checkbox` | 待实现 |
| O03 删除确认 | 查询参数 | 复用 `ConfirmDialog` | `AlertDialog` | 待实现 |
| O04 快速预览 | 查询参数 | `components/console/QuickViewDrawer.tsx` | `Sheet` | 待实现 |
| O05 新建 | 查询参数 | `modules/pm/components/CreateWorkItemDialog.tsx` | `Dialog`、`Form` | 待实现 |

## 7. 共享组件对账

| 原型控件 | 共享组件 | 默认行为差异 | 决策 | 影响页面 |
|----------|----------|--------------|------|----------|
| 列表骨架 | 无（现由各页面拼装） | 无统一壳 | 新增 `ListPageShell`，内部复用 `PageHeader` | PM 全部列表 |
| 表格选择列 | `DataTable` | 无选择、排序、密度 | 新增可选 props：`selectable` `selectedIds` `onSelect` `sort` `density` `stickyHeader` | 所有列表 |
| 分页 | `Pagination` | 只有页码与上下页 | 新增可选 props：`total` `pageSize` `onPageSizeChange`；不传时保持现状 | 所有列表 |
| 搜索框 | 各页 `Input` | 无防抖、清除、快捷键 | 新增共享 `SearchInput` | 所有列表 |
| 条件 chip | 无 | — | 新增 `FilterBar` | 所有列表 |
| 状态展示 | `StatusIcon` | 面向运行状态图标 | 新增 `StatusPill`，颜色映射复用 `console/status.ts` 的 tone | PM 列表 / 抽屉 |
| 抽屉 | `DetailShell`（页面级） | 非浮层 | 新增 `QuickViewDrawer`，基于 shadcn `Sheet` | PM、流水线 |
| 空态 / 失败 / 无权限 | 各页文本 | 无统一插图与行动 | 新增 `EmptyState` / `ErrorState` / `NoPermissionState` | 所有列表 |
| 头像 | 无 | — | 新增 `UserAvatar`，降级首字 | 全站 |

冲突处理顺序：复用现有组件 → 增加可选 props → 页面级覆盖 → 新增共享组件。禁止在业务页复制私有表格皮肤。

## 8. 偏差登记

实现阶段填写；当前原型阶段无实现偏差。

| # | 偏差 | 位置 | 分类 | 原因 | 处理 | 回写原型 | 验证 |
|---|------|------|------|------|------|----------|------|
| — | — | — | — | — | — | — | — |

## 9. 验收与冻结

- [ ] 冻结原型版本与提交已记录。
- [ ] 对齐等级已确认。
- [ ] 共享组件对账完成（本文 §7，实现前复核）。
- [ ] 数据 profile 与 fixture / seed 已就绪。
- [ ] 真实应用截图与冻结原型逐页逐状态对比。
- [ ] 偏差按分类登记并关闭。
- [ ] Playwright 视觉回归基线建立并审批。
- [ ] 独立 agent 校验结论写回 `changes.md`。

| 角色 | 姓名 | 结论 | 日期 |
|------|------|------|------|
| 产品 | 待填 | 待评审 | — |
| 前端 | 待填 | 待评审 | — |
| 测试 | 待填 | 待评审 | — |
