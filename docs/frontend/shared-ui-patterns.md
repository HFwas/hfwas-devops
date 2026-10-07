# 共享界面模式

> 日期：2026-10-07
> 版本：v0.2
> 关联：[kite-frontend-style-guide.md](./kite-frontend-style-guide.md)、[frontend-style-unification-plan.md](./frontend-style-unification-plan.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：DataTable、StatusIcon、PageHeader、DetailShell、LogPanel 的用法与试点范围 |
| v0.2 | 2026-10-07 | Phase 4：各产品再接一处列表和详情；流水线运行日志与容器日志共用 LogPanel |

---

## 1. 放在哪里

全产品共用，不绑容器。源码在 `frontend/src/components/console/`。

| 组件 | 文件 | 做什么 |
|------|------|--------|
| `DataTable` | `DataTable.tsx` | 紧凑表、toolbar 槽、空态、加载。表头 `h-10 bg-muted px-2`，单元格 `p-2`，外框 `rounded-lg border`。已有行时加载用 `opacity-75` |
| `StatusIcon` + 字典 | `StatusIcon.tsx`、`status.ts` | Ready / Failed / Pending / Warning 等映射到同一套色和图标 |
| `PageHeader` | `PageHeader.tsx` | 标题、描述、右侧操作 |
| `DetailShell` | `DetailShell.tsx` | 贴在顶栏下的标题行、操作、Tab；正文由调用方按当前 Tab 填 |
| `LogPanel` / `TerminalChrome` | `LogPanel.tsx` | 约 40vh、可拖到最小 120px、顶栏 `h-10 bg-muted/50`、连接呼吸灯、等宽区 |

新的列表、详情、状态、日志页用这些组件。不要再写一套私有表格密度或状态色。

`LogPanel` 和 `TerminalChrome` 只包外观。WebSocket、xterm 协议留在原来的业务组件里。

## 2. 状态字典

`resolveStatusTone()` 把各产品的状态收成七种语义：

| 语义 | 色 | 举例 |
|------|----|------|
| success | green-500 / dark:green-400 | Ready、Connected、SUCCEEDED、已解决 |
| failed | red-500 | Failed、Disconnected、CrashLoopBackOff |
| warning | yellow-500 | Warning、Degraded、WAITING_APPROVAL |
| progress | blue-500 | Pending、Running、QUEUED |
| terminating | orange-500 | Terminating、取消中 |
| paused | purple-500 | Paused、Hold |
| neutral | muted | Unknown、CANCELLED、草稿；字典里没有的码 |

PM 自定义工作流码不在字典里时，给 `StatusIcon` 传 `tone`，不要在页面里另写颜色。

## 3. 试点

只证明组件能跨产品复用，不是全站换皮。Phase 3 先接了一处，Phase 4 每个主要产品再接一处列表和一处详情。

| 阶段 | 产品 | 页面 | 用到 |
|------|------|------|------|
| 3 | PM | 项目列表 | `PageHeader`、`DataTable` |
| 3 | 流水线 | `/pipeline/pipelines` | `PageHeader`、`DataTable`、`StatusIcon` |
| 3 | 容器 | 集群列表 | `PageHeader`、`DataTable`、`StatusBadge`（内部是 `StatusIcon`） |
| 3 | 容器 | Deployment 详情 | `DetailShell`、`StatusIcon` |
| 3 | 容器 | Pod 日志 / 终端 | `LogPanel`、`TerminalChrome`（协议未改） |
| 4 | PM | `/pm/projects/:id/items/:type` 事项列表 | `PageHeader`、`DataTable`、`StatusIcon` |
| 4 | PM | 事项详情 | `DetailShell`、`StatusIcon`（自定义工作流用 `tone`） |
| 4 | 流水线 | `/pipeline/pipelines/:id` 运行记录 | `PageHeader`、`DataTable`、`StatusIcon` |
| 4 | 流水线 | 运行详情 | `DetailShell`、`StatusIcon`、`LogPanel`（任务 `logText`，不改协议） |
| 4 | 容器 | Pod 列表 | `PageHeader`、`DataTable`、`StatusBadge` |
| 4 | 容器 | StatefulSet 详情 | `DetailShell` |
| 4 | API 测试 | `/api-test` 接口列表 | `PageHeader`、`DataTable`、`StatusIcon` |
| 4 | API 测试 | 接口详情 | `DetailShell`、`StatusIcon`、`DataTable` |
| 4 | 工作台 | `/workbench` | `PageHeader`，产品卡片 `p-4` |
| 4 | 用户中心 | 用户管理、账号设置 | `PageHeader` / `DataTable` / `StatusIcon`，设置页 `DetailShell` |

看板和 DAG 仍不进 `DataTable`。

## 4. 下一步

- **Phase 5**：按模块清单铺开其余页面，勾掉 `frontend-style-unification-plan.md` 里的列表 / 详情 / 状态 / 日志。包括凭证、任务市场、节点与其余工作负载、集合与环境、调试工作台、PM 看板。看板和 DAG 只统一外围间距和颜色。
