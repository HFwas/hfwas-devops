# 原型契约（Prototype Contract）

> 日期：2026-10-21
> 版本：v0.2
> 功能：PM 项目内左侧菜单
> 关联：[prototype-first-standard.md](../../../frontend/prototype-first-standard.md)、[design.md](./design.md)
> 原型版本：PM 模块总原型 v1.11（视觉基线 v1.9；冻结提交待用户确认）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：引用既有 PM 模块总原型左侧导航，登记本次实现范围与允许差异 |
| v0.2 | 2026-10-21 | 独立校验后修正：原型版本对齐 v1.11；未做真实截图对比的必须一致项改为未勾选 |

---

## 1. 基本信息

| 项 | 值 |
|----|----|
| 功能目录 | `docs/superpowers/features/20261021-pm-project-sidebar/` |
| 原型入口 | `docs/product/06-prototype/pm/index.html#/workbench` |
| 原型版本 | v1.11（视觉基线 v1.9） |
| 冻结提交 | 待用户确认（沿用 `docs/product/06-prototype/pm/README.md` §7） |
| 对齐等级 | A2 |
| 参考视口 | `1440×900 @2x` |
| 验收环境 | 本地 React 应用 |
| 数据 profile | 项目启用类型来自 `POST /pm/projects/issue-types/list` |

原型复用决策：PM 模块总原型已包含左侧类型导航，按 `20261019-pm-list-config-bulk` 已登记的决策，模块原型只维护在 `docs/product/06-prototype/pm/`，本功能不再复制第二份。

## 2. 必须一致

- [x] 进入项目后左侧显示项目上下文菜单，而不是继续显示产品级菜单。
- [x] 类型分组由项目启用类型驱动，按接口顺序展示。
- [x] 当前类型、看板、工作流有明确高亮。
- [x] 类型点击后 URL 带 `typeCode`，页面内容与左侧导航同步。
- [x] 颜色、圆角、间距只用全局语义 token 与 `Sidebar` 共享组件。
- [ ] 1440×900 下菜单宽度、图标与文字节奏与项目总原型一致（未做真实应用截图对比，待补）。

## 3. 允许差异

| # | 差异 | 原因 | 分类 | 处理 |
|---|------|------|------|------|
| 1 | 类型条目没有数量角标 | 后端未提供类型计数聚合，本次不加额外全量查询 | 产品决策 | 记录，后续随 A6 接口补齐 |
| 2 | 只有「全部项目 / 类型 / 看板 / 工作流」，没有「迭代看板 / 报表 / 成员与权限」 | 这些页面尚未落地，不在本次范围 | 产品决策 | 保持 |
| 3 | 看板、工作流各一个入口，不做「每个类型一组」的二级树 | 复用现有单页路由，避免一次引入过多层级 | 产品决策 | 保持；类型上下文取当前路由 |
| 4 | 项目名称、类型名称、数量与原型样例不同 | 生产数据 | 数据导致 | 不改代码 |

## 4. 原型 → 实现映射

| 原型 | 路由 | 实现文件 | 共享组件 | 状态 |
|------|------|----------|----------|------|
| 左侧项目导航（工作项类型） | `/pm/projects/:projectId/items/:typeCode` | `frontend/src/components/console/AppSidebar.tsx`、`frontend/src/modules/pm/nav.ts` | `SidebarGroup`、`SidebarMenu`、`SidebarMenuButton` | 已实现 |
| 左侧看板入口 | `/pm/projects/:projectId/board/:typeCode` | `frontend/src/components/console/AppSidebar.tsx` | 同上 | 已实现 |
| 左侧工作流入口 | `/pm/projects/:projectId/settings/workflow/:typeCode` | `frontend/src/components/console/AppSidebar.tsx` | 同上 | 已实现 |
| 项目列表 | `/pm/projects` | `frontend/src/modules/pm/pages/ProjectListPage.tsx` | `DataTable`、`PageHeader` | 已实现 |

## 5. 共享组件对账

| 原型控件 | 共享组件 | 默认行为差异 | 决策 | 影响页面 |
|----------|----------|--------------|------|----------|
| 左侧导航项 | `SidebarMenuButton` | 自带 tooltip、active 与折叠态 | 直接复用，不新增私有导航组件 | 全部项目页 |
| 加载中条目 | `SidebarMenuButton disabled` | 无骨架态 | 用禁用的「加载类型…」条目，复用现有控件 | 项目页 |

## 6. 偏差登记

| # | 偏差 | 位置 | 分类 | 原因 | 处理 | 回写原型 | 验证 |
|---|------|------|------|------|------|----------|------|
| 1 | 原型尚未写入冻结提交号 | 原型 README §7 | 产品决策 | 用户尚未确认提交号 | 记录，不冒充冻结 | 否 | 本契约头部 |
| 2 | 列表/看板/工作流页移除页内类型按钮行 | P01 / 看板 / 工作流 | 产品决策 | 类型导航已统一到左侧，避免两套菜单 | 保持 | 是（实现后补记原型说明） | `AppShell.test.tsx`、`npm test` |
