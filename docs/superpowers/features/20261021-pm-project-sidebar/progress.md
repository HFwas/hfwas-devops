# PM 项目内左侧菜单 — AI 协作进度日志

> 日期：2026-10-21
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-21 | 建立日志，定义每轮记录格式 |

---

## 第 1 轮 · 2026-10-21 20:45 · 移除项目监控并落地项目内左侧菜单

- **状态**：待评审
- **触发**：用户要求移除项目监控，项目内菜单展示在左侧
- **目标**：侧栏去掉项目监控；进入项目后左侧切换为项目内菜单（全部项目、启用类型、看板、工作流）
- **读取**：`frontend/src/shared/console/navigation.ts`、`AppSidebar.tsx`、`router.tsx`、PM 页面、`docs/pm/pm-design.md`、`docs/product/06-prototype/pm/README.md`、`20261019-pm-list-config-bulk/design.md`、`prototype-first-standard.md`

### 做了什么

1. 复用容器模块的上下文侧栏模式：`/pm/projects/:projectId/**` 下侧栏渲染 `ProjectMenu`。
2. 类型菜单来自 `POST /pm/projects/issue-types/list`，内置类型映射图标，自定义类型用兜底图标。
3. 看板、工作流链接沿用当前路由 `typeCode`，无类型时回退首个启用类型或 `task`。
4. 移除「项目监控」菜单、路由、页面、面包屑文案与相关断言。
5. 下线列表、看板、工作流页内的类型按钮行与重复互跳按钮。
6. 补 `nav.ts` 单测与 AppShell 侧栏切换测试；同步 PM 与产品文档。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/modules/pm/nav.ts` / `nav.test.ts` | 新增 | 项目上下文解析、路径与图标工具 |
| `frontend/src/components/console/AppSidebar.tsx` | 修改 | 项目上下文菜单 |
| `frontend/src/shared/console/navigation.ts`、`breadcrumbs.ts`、`router.tsx` | 修改 | 移除项目监控 |
| `frontend/src/modules/pm/pages/ProjectMonitorPage.tsx` | 删除 | 项目监控页下线 |
| `frontend/src/modules/pm/pages/{WorkItemListPage,BoardPage,WorkflowPage}.tsx` | 修改 | 移除页内重复导航 |
| `frontend/src/components/console/AppShell.test.tsx`、`shared/console/navigation.test.ts` | 修改 | 更新侧栏断言 |
| `docs/superpowers/features/20261021-pm-project-sidebar/*` | 新增 | 设计、测试用例、变更、契约与进度 |
| `docs/pm/pm-design.md`、`docs/product/02-features/*`、`docs/frontend/shared-ui-patterns.md` | 修改 | 移除项目监控 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 单测 | `npm test` | 67 files / 260 passed, 1 skipped |
| 构建 | `npm run build` | 通过 |
| 手工浏览器验证 | 未执行 | 本地登录环境未启动，改由 AppShell 渲染测试覆盖 |

### 决策与取舍

- **决策**：进入项目后替换产品菜单，而不是在内容区新增第二列。**理由**：与容器模块一致，复用 `Sidebar` 共享组件，移动端折叠行为直接继承。
- **决策**：不实现类型数量角标。**理由**：后端无聚合字段，侧栏额外拉全量分页不划算。**未选方案**：每类型并发分页计数。
- **决策**：复用 `docs/product/06-prototype/pm/` 的模块原型。**理由**：避免同一页面维护两份基线。

### 偏差与假设

- **ASSUMPTION**：用户接受先落地左侧导航，不改看板/工作流页面信息结构。
- 项目原型冻结提交号仍未确认，未冒充冻结。

### 未完成 / 风险

- 未在真实登录环境做截图对比；侧栏交互由 AppShell 测试覆盖。
- 类型数量角标待后端聚合接口。

### 下一步

- **唯一动作**：独立 agent 按 `test-cases.md` 与当前 diff 校验，结论回填 `changes.md`。

---

## 第 2 轮 · 2026-10-21 20:50 · 独立校验后修复并补强用例

- **状态**：待评审
- **触发**：独立校验 agent 首轮结论为失败
- **目标**：关闭路由断言、登记遗漏、契约过度声明与用例缺口，然后复验
- **读取**：`changes.md`、`test-cases.md`、`prototype-contract.md`、独立校验报告、`router.helm.test.ts`

### 做了什么

1. 新增 `router.pm.test.ts`：`/pm/monitor` 解析到 `RouteScreen`，项目页面解析到真实页面。
2. AppShell 测试补停用类型过滤（`enabled: 0` 不展示）、看板/工作流 href 携带当前 `typeCode`、当前项 `data-active` 高亮断言。
3. `nav.test.ts` 覆盖范围修正为 TC-04 – TC-09，兜底图标断言改为 `CircleDot`。
4. `test-cases.md` 升 v0.2：TC-02 改指真实路由测试，新增 TC-10 / TC-11，标注手工用例未执行。
5. `changes.md` 补登 `README.md` 与 `router.pm.test.ts`，更新验证计数与首轮校验结论。
6. `prototype-contract.md` 升 v0.2：原型版本对齐 v1.11；未做真实截图对比的必须一致项取消勾选。
7. `design.md` 升 v0.2：原型版本引用同步。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/router.pm.test.ts` | 新增 | PM 路由与项目监控下线断言 |
| `frontend/src/components/console/AppShell.test.tsx` | 修改 | 停用类型、href、active 高亮 |
| `frontend/src/modules/pm/nav.test.ts` | 修改 | 兜底图标精确断言 |
| `docs/superpowers/features/20261021-pm-project-sidebar/{design,test-cases,changes,prototype-contract}.md` | 修改 | 修正与补登 |
| `README.md` | 修改 | 补登到变更清单（代码在第 1 轮已改） |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 定向单测 | `npx vitest run router.pm.test.ts nav.test.ts AppShell.test.tsx navigation.test.ts` | 4 files / 20 passed |
| 全量单测 | `npm test` | 68 files / 262 passed, 1 skipped |
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 复验 | 待独立 agent 重新执行 | 待回填 |

### 决策与取舍

- **决策**：用 `matchRoutes` 直接断言路由叶子，而不是只在侧栏断言文案。**理由**：删除路由必须由真实路由解析证明。
- **决策**：原型截图对比不勾选为已完成。**理由**：没有登录环境，不能把推断写成事实。

### 偏差与假设

- PM 模块总原型冻结提交号仍未确认，`prototype-contract.md` 保持未冻结状态。

### 未完成 / 风险

- 真实登录环境的截图对比与手工用例仍未执行。
- 类型数量角标未实现（后端无聚合字段）。

### 下一步

- **唯一动作**：独立 agent 按更新后的 `test-cases.md` 复验，结论回填 `changes.md` §4。

---

## 第 3 轮 · 2026-10-21 20:58 · 复验通过并回填结论

- **状态**：已完成
- **触发**：独立 agent 复验报告通过，提出 3 项 P3 文档小修
- **目标**：关闭剩余文档问题并回填校验结论
- **读取**：复验报告、`changes.md`、`test-cases.md`

### 做了什么

1. `changes.md` 升 v0.3：回填复验「通过」，记录关闭的 3 项文档问题。
2. `navigation.test.ts` 的用例映射只保留 TC-01。
3. 计划外偏差的原型版本统一为 v1.11（视觉基线 v1.9）。
4. `test-cases.md` 中 TC-11 的自动化位置展开为完整 `文件::测试名`，并补 MV-09 加载态手工用例。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261021-pm-project-sidebar/changes.md` | 修改 | 状态完成，回填复验结论 |
| `docs/superpowers/features/20261021-pm-project-sidebar/test-cases.md` | 修改 | TC-11 位置格式、MV-09 |
| `docs/superpowers/features/20261021-pm-project-sidebar/progress.md` | 修改 | 追加本轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 独立复验 | 独立 agent | 通过；`tsc`、68 files / 262 passed、`npm run build` 均通过 |
| 文档核对 | 复验报告逐项 | 3 项 P3 已关闭 |

### 决策与取舍

- **决策**：不再为加载态补自动化 mock。**理由**：复验列为可选，AppShell 渲染已覆盖主路径；加载态留在 MV-09。

### 偏差与假设

- 原型冻结提交与真实登录环境截图仍未完成，保持未勾选与风险登记。

### 未完成 / 风险

- PM 模块总原型冻结提交待用户确认。
- 手工用例 MV-01 – MV-09 未执行。

### 下一步

- **唯一动作**：用户在可登录环境按 MV-01 – MV-09 走一遍，并确认 PM 原型冻结提交号。
