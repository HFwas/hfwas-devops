# PM 项目内左侧菜单测试用例

> 日期：2026-10-21
> 版本：v0.2
> 状态：自动化用例已执行通过；手工用例未执行（无本地登录环境）
> 关联：[design.md](./design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：项目监控移除、项目内左侧菜单、类型高亮与链接上下文 |
| v0.2 | 2026-10-21 | 独立校验后补真实路由断言、停用类型过滤与看板/工作流 href 用例；标注手工用例未执行 |

---

## 1. 自动化用例

| 编号 | 前置条件 | 步骤 | 预期结果 | 自动化位置 |
|------|----------|------|----------|-----------|
| TC-01 | 已登录，位于 `/pm/projects` | 查看左侧栏 | 只显示「项目」，不显示「项目监控」 | `frontend/src/shared/console/navigation.test.ts::shows only the current product menu`、`frontend/src/components/console/AppShell.test.tsx::keeps one shell and swaps the sidebar to the current product` |
| TC-02 | 任意路径 | 访问 `/pm/monitor` 并解析路由 | 落到 `RouteScreen`，不存在 `ProjectMonitorPage` | `frontend/src/router.pm.test.ts::no longer exposes the project monitor route` |
| TC-03 | 位于 `/pm/projects/12/items/task` | 查看左侧栏 | 显示「全部项目」、项目名称、需求/任务/缺陷、看板、工作流 | `frontend/src/components/console/AppShell.test.tsx::keeps one shell and swaps the sidebar to the current product` |
| TC-04 | 位于 `/pm/projects/12/items/task` | 解析项目上下文 | `{ projectId: '12', typeCode: 'task' }` | `frontend/src/modules/pm/nav.test.ts::reads the project and type from project routes` |
| TC-05 | 位于 `/pm/projects/12/items/task/99` | 判断类型高亮 | `task` 高亮，`bug` 不高亮 | `frontend/src/modules/pm/nav.test.ts::highlights the active item type, including detail routes` |
| TC-06 | 位于 `/pm/projects/12/board/bug` | 解析项目上下文 | `{ projectId: '12', typeCode: 'bug' }` | `frontend/src/modules/pm/nav.test.ts::reads the project and type from project routes` |
| TC-07 | 位于 `/pm/projects/12/settings/workflow/requirement` | 解析项目上下文 | `{ projectId: '12', typeCode: 'requirement' }` | `frontend/src/modules/pm/nav.test.ts::reads the project and type from project routes` |
| TC-08 | 任意自定义类型编码 | 调用图标映射 | 自定义类型返回 `CircleDot` 兜底图标 | `frontend/src/modules/pm/nav.test.ts::falls back to a generic icon for custom types` |
| TC-09 | 项目列表与非法项目 ID | 解析项目上下文 | 返回 `null` | `frontend/src/modules/pm/nav.test.ts::rejects the project list and non-numeric project ids` |
| TC-10 | 项目类型接口返回 `enabled: 0` 的「史诗」 | 进入 `/pm/projects/12/items/task` 查看侧栏 | 侧栏不显示停用类型 | `frontend/src/components/console/AppShell.test.tsx::keeps one shell and swaps the sidebar to the current product` |
| TC-11 | 位于 `/pm/projects/12/items/task`，随后进入 `/pm/projects/12/board/bug` | 检查侧栏链接与高亮 | 列表页看板/工作流指向 `task`；看板页看板高亮，工作流指向 `bug` | `frontend/src/components/console/AppShell.test.tsx::keeps one shell and swaps the sidebar to the current product` |

## 2. 手工验证

| 编号 | 步骤 | 预期结果 |
|------|------|----------|
| MV-01 | 登录后进入「项目管理」 | 左侧只有「项目」；浏览器访问 `/pm/monitor` 落到路由兜底页，不再出现项目监控页面 |
| MV-02 | 从项目列表进入任意项目 | 侧栏切换为项目菜单，项目名称显示在类型分组上方 |
| MV-03 | 依次点击需求、任务、缺陷 | URL 变为对应 `typeCode`，当前类型高亮，列表页头同步 |
| MV-04 | 在类型为缺陷时点击「看板」 | 进入 `/board/bug`，侧栏看板高亮 |
| MV-05 | 在类型为缺陷时点击「工作流」 | 进入 `/settings/workflow/bug`，侧栏工作流高亮 |
| MV-06 | 点击「全部项目」 | 返回项目列表，侧栏恢复产品菜单 |
| MV-07 | 断开类型接口后进入项目 | 类型分组显示为空或加载失败，看板/工作流仍可进入 `task` |
| MV-08 | 检查列表、看板、工作流页面 | 不再出现横向类型按钮行与重复的看板/工作流按钮 |
| MV-09 | 在类型接口返回前进入项目 | 类型分组显示禁用的「加载类型…」条目 |
