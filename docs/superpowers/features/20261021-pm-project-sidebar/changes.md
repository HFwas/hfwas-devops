# PM 项目内左侧菜单代码变更

> 日期：2026-10-21
> 版本：v0.3
> 状态：已实现，独立校验与复验通过
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：移除项目监控；项目上下文切换左侧菜单；页内重复导航下线；测试与产品文档同步 |
| v0.2 | 2026-10-21 | 独立校验后修复：补真实路由断言、停用类型与 href 用例；补登 README；修正原型契约过度声明 |
| v0.3 | 2026-10-21 | 复验通过；回填校验结论，修正 TC 映射、原型版本措辞与用例位置格式 |

---

## 1. 变更清单

| 文件 | 动作 | 对应设计 | 说明 |
|------|------|----------|------|
| `frontend/src/modules/pm/nav.ts` | 新增 | §2 / §3 | 项目路由解析、项目内路径构造、当前类型高亮、类型图标映射 |
| `frontend/src/modules/pm/nav.test.ts` | 新增 | TC-04 – TC-09 | 项目上下文与路径工具单测 |
| `frontend/src/router.pm.test.ts` | 新增 | TC-02 | 校验 `/pm/monitor` 落到兜底路由，项目页面解析到真实页面 |
| `frontend/src/components/console/AppSidebar.tsx` | 修改 | §2 / §3 | 新增 `ProjectMenu`；进入项目后替换产品菜单；类型加载态；看板/工作流上下文 |
| `frontend/src/shared/console/navigation.ts` | 修改 | §1.2 G1 | 移除「项目监控」菜单项与 `Activity` 图标 |
| `frontend/src/components/console/breadcrumbs.ts` | 修改 | §1.2 G1 | 移除 `monitor` 面包屑文案 |
| `frontend/src/router.tsx` | 修改 | §2 | 移除 `/pm/monitor` 路由与页面导入 |
| `frontend/src/modules/pm/pages/ProjectMonitorPage.tsx` | 删除 | §1.2 G1 | 项目监控页面下线 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 修改 | §3.6 | 移除页内类型按钮行与重复的看板/工作流按钮 |
| `frontend/src/modules/pm/pages/BoardPage.tsx` | 修改 | §3.6 | 移除页内类型按钮行与「列表」互跳按钮 |
| `frontend/src/modules/pm/pages/WorkflowPage.tsx` | 修改 | §3.6 | 移除页内类型按钮行与互跳按钮 |
| `frontend/src/shared/console/navigation.test.ts` | 修改 | TC-01 | 更新 PM 菜单与高亮断言 |
| `frontend/src/components/console/AppShell.test.tsx` | 修改 | TC-01 / TC-03 / TC-10 / TC-11 | 新增 QueryClient 包装与 PM API mock；验证项目菜单、停用类型过滤与链接上下文 |
| `README.md` | 修改 | §1.2 G1 | 移除 `/pm/monitor` 路由行，升版 v0.11 |
| `docs/pm/pm-design.md` | 修改 | §1.2 G1 | 信息架构与现状描述移除项目监控 |
| `docs/product/02-features/feature-catalog.md` | 修改 | §1.2 G1 | 移除项目监控功能项与路由 |
| `docs/product/02-features/modules/pm.md` | 修改 | §1.2 G1 | 移除项目监控功能项 |
| `docs/frontend/shared-ui-patterns.md` | 修改 | §1.2 G1 | 移除项目监控页面登记与趋势图延期项 |

## 2. 计划外偏差

| # | 偏差 | 原因 |
|---|------|------|
| 1 | 未新建 `prototype/index.html` | 复用 PM 模块总原型 v1.11（视觉基线 v1.9）的左侧导航，避免复制第二份基线；已记录在 `prototype-contract.md` |
| 2 | 类型数量角标未实现 | 后端无类型计数聚合，避免为侧栏额外发起全量分页查询 |

## 3. 验证结果

| 检查 | 命令 | 结果 |
|------|------|------|
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 单元测试 | `npm test` | 68 个文件通过，262 通过 / 1 跳过 |
| 生产构建 | `npm run build` | 通过（仅有既有 chunk 体积警告） |
| 文档一致性 | 独立 agent 比对 `design.md` / `test-cases.md` / diff | 首轮发现 6 项差异，已修复 5 项；原型冻结项仍待用户确认 |
| 独立 agent 校验 | 首轮 | 失败：TC-02 名不副实、README 未登记、契约过度声明、缺停用类型与 href 用例 |
| 独立 agent 复验 | 第二轮 | 通过：TC-01 – TC-11 全通过；无阻断项；剩余 P3 文档措辞已关闭 |

## 4. 校验结果

首轮独立校验结论为**失败**，已修复：

| # | 问题 | 处理 |
|---|------|------|
| 1 | TC-02 引用的测试未校验 `/pm/monitor` 路由 | 新增 `router.pm.test.ts`，断言落到 `RouteScreen` |
| 2 | `README.md` 变更未登记 | 补入 §1 与 `progress.md` 第 2 轮 |
| 3 | `prototype-contract.md` 把未做截图对比的项勾为已满足 | 取消勾选，登记为未闭环；原型版本引用对齐 v1.11 |
| 4 | 缺「停用类型不展示」与「看板/工作流 href 携带 typeCode」用例 | 新增 TC-10 / TC-11 并自动化 |
| 5 | `nav.test.ts` 覆盖范围与 TC-08 断言偏弱 | 改为 TC-04 – TC-09，兜底图标断言 `CircleDot` |
| 6 | 原型冻结提交未确认 | 无法由实现方闭合，保留为待用户确认项 |

复验（独立 agent）结论：**通过**。无阻断项；TC-01 – TC-11 全部通过，MV-01 – MV-12 因无本地登录环境标注未执行。复验发现并已关闭的文档问题：

| # | 问题 | 处理 |
|---|------|------|
| 1 | `navigation.test.ts` 仍标注 TC-01 / TC-02 | 改为仅 TC-01 |
| 2 | 计划外偏差仍写原型 v1.9 | 统一为 v1.11（视觉基线 v1.9） |
| 3 | TC-11 自动化位置写「同上」 | 展开为完整 `文件::测试名` |

保留风险：PM 模块总原型冻结提交待用户确认；真实登录环境截图与手工用例未执行。
