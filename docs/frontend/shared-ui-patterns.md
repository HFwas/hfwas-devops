# 共享界面模式

> 日期：2026-10-07
> 版本：v0.3
> 关联：[kite-frontend-style-guide.md](./kite-frontend-style-guide.md)、[frontend-style-unification-plan.md](./frontend-style-unification-plan.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：DataTable、StatusIcon、PageHeader、DetailShell、LogPanel 的用法与试点范围 |
| v0.2 | 2026-10-07 | 资源概览用 ResourceOverview；StatusIcon 增加同色圆点 |
| v0.3 | 2026-10-07 | DetailShell 增加 metrics 槽；圆点变体里 Running 为绿点 |

---

## 1. 放在哪里

全产品共用，不绑容器。源码在 `frontend/src/components/console/`。

| 组件 | 文件 | 做什么 |
|------|------|--------|
| `DataTable` | `DataTable.tsx` | 紧凑表、toolbar 槽、空态、加载。表头 `h-10 bg-muted px-2`，单元格 `p-2`，外框 `rounded-lg border`。已有行时加载用 `opacity-75` |
| `StatusIcon` + 字典 | `StatusIcon.tsx`、`status.ts` | Ready / Failed / Pending / Warning 等映射到同一套色和图标 |
| `PageHeader` | `PageHeader.tsx` | 标题、描述、右侧操作 |
| `DetailShell` | `DetailShell.tsx` | 贴在顶栏下的标题行、操作、Tab；正文由调用方按当前 Tab 填 |
| `OverviewMetricCards` | `ResourceOverview.tsx` | Tab 下方的指标卡。由 `DetailShell` 的 `metrics` 挂上 |
| `ResourceOverview` | `ResourceOverview.tsx` | 概览左主栏 + 右侧栏；指标卡也可直接放在这里 |
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

## 4. Phase 5 铺开

| 产品 | 页面 | 用到 |
|------|------|------|
| 流水线 | `/pipeline/credentials` 列表与详情 | `PageHeader`、`DataTable`、`DetailShell` |
| 流水线 | `/pipeline/task-kinds` 列表与详情 | `PageHeader`、`DataTable`、`DetailShell`、`StatusIcon`；命令模板用等宽块 |
| 容器 | 节点、Deployment、StatefulSet、Service、ConfigMap、Secret、PVC、StorageClass | `PageHeader`、`DataTable`、`StatusBadge` |
| 容器 | 节点、Pod、Service、ConfigMap 详情 | `DetailShell`；Pod 日志 / 终端仍是 `LogPanel` / `TerminalChrome` |
| 容器 | 集群概览、仓库、仓库项目、制品、镜像搜索 | `PageHeader` 或 `DetailShell`，列表用 `DataTable` |
| API 测试 | 集合、环境 | `PageHeader`、`DataTable`、`DetailShell`、`StatusIcon` |
| API 测试 | 接口详情「调试」 | 紧凑表单（`h-8`）+ `LogPanel` 响应 + 历史 `DataTable` |
| PM | `/pm/projects/:id/board/:type` | `PageHeader`；列本身是卡片，不进 `DataTable` |
| PM | 工作流状态与流转 | `PageHeader`、`DataTable`、`StatusIcon`。画布不在此页 |
| PM | 项目监控 | 项目 `DataTable`，链到看板 |
| 文件解析 / 文档生成 / 图片 | 工具页 | `PageHeader`、`StatusIcon`；图片记录与解析分页、生成结果用 `DataTable` |

环境变量和挂载卷仍是详情里的行内表单。图片裁剪、文档批量规格、Argo 画布、流水线 DAG、监控趋势图留到 Phase 6。

## 5. 下一步

- **Phase 6**：按 `frontend-style-unification-plan.md` 的延期项收尾，并做视觉回归。新列表和详情继续用本章组件。
