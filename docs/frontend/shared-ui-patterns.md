# 共享界面模式

> 日期：2026-10-13
> 版本：v0.11
> 关联：[kite-frontend-style-guide.md](./kite-frontend-style-guide.md) v0.8、[frontend-style-unification-plan.md](./frontend-style-unification-plan.md) v0.10

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：DataTable、StatusIcon、PageHeader、DetailShell、LogPanel 的用法与试点范围 |
| v0.2 | 2026-10-07 | 资源概览用 ResourceOverview；StatusIcon 增加同色圆点 |
| v0.3 | 2026-10-07 | DetailShell 增加 metrics 槽；圆点变体里 Running 为绿点 |
| v0.4 | 2026-10-11 | LogPanel / TerminalChrome 指向 Xshell 风格会话表面设计 |
| v0.5 | 2026-10-11 | LogPanel 顶栏改为至少一行高，工具栏放不下时在顶栏内换行 |
| v0.6 | 2026-10-11 | LogPanel 增加可选跟随：内容变长时贴底，上滚暂停，回到底部或点「跟随最新」再跟上 |
| v0.7 | 2026-10-11 | LogPanel 增加 terminal 变体，描边状态色与圆点共用同一套语义色 |
| v0.8 | 2026-10-12 | 对齐规范 v0.7：字体默认仍是系统 sans；数字列与指标值应加 tabular-nums，指标卡标签目标是 text-xs，组件尚未改 |
| v0.9 | 2026-10-12 | Phase 6：指标卡与字段名改为 text-xs，数值与 DataTable 补 tabular-nums；新增 UsageBar。画布、行内编辑和趋势图仍延期 |
| v0.10 | 2026-10-13 | 新增 `Pagination` 分页组件与 `pageCountOf` 工具；容器、PM、用户、接口测试、流水线的列表页统一改用它做服务端分页 |
| v0.11 | 2026-10-13 | 新增 `AsyncSelect`（可检索、下拉到底加载下一页）；实体下拉（项目、命名空间、集群、调试环境、Helm 仓库、流水线凭证）统一改用它；节点/StorageClass/命名空间列表与集群/Registry/凭证/Helm 仓库接口补服务端分页与检索；分页默认每页 10 条 |

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
| `LogPanel` / `TerminalChrome` | `LogPanel.tsx` | 约 40vh、可拖到最小 120px、顶栏至少 `min-h-10` 且工具栏 `flex-wrap`、连接呼吸灯、等宽区。可选 `follow`：内容变长时贴底；上滚后暂停，滚回底部或点「跟随最新」再跟上。`variant="terminal"` 用深色会话底，抽屉里可关底边拖拽 |
| `UsageBar` | `UsageBar.tsx` | Requests / Limits 细条，高 `h-2`。低于等于 60% 时 Requests 为 `bg-primary`、Limits 为 `bg-orange-500`；再高转黄、红 |
| `Pagination` | `Pagination.tsx` | 统一分页：左侧说明（缺省「第 x / y 页」）+ 右侧上一页 / 页码 / 下一页；页码多时省略号收拢，只有一页时不显示翻页。受控组件，页码状态由调用方（URL 或 state）持有 |
| `ConfirmDialog` | `ConfirmDialog.tsx` | 统一二次确认，替代 `window.confirm`。基于 `AlertDialog`，跟随主题 token，可禁用确认按钮等待异步 |
| `AsyncSelect` | `AsyncSelect.tsx` | 实体下拉换皮：Popover + 检索（250ms 防抖）+ 下拉到底自动加载下一页。受控 `value/onChange`，数据由 `fetchPage({page,pageSize,keyword})` 提供；可选 `clearLabel`（置顶清空项）、`selectedLabel`（选中值不在已加载页时的回显兜底） |

新的列表、详情、状态、日志页用这些组件。不要再写一套私有表格密度或状态色。

列表页统一用 `Pagination`：只负责展示与回调，服务端分页时用 `pageCountOf(pageResult, pageSize)`（`shared/types/pagination.ts`）从后端返回的 `pages` / `total` 推导总页数；筛选条件变化时应把页码重置回第 1 页。

字体默认是系统无衬线，标识用 `font-mono text-xs`，见规范 v0.8。`DataTable` 单元格已经带 `tabular-nums`，计数、容量、副本数、百分比不用在每个页面再写。`OverviewMetricCards` 的标签是 `text-xs text-muted-foreground`，数值带 `tabular-nums`（强调态 `font-bold`，其余 `font-semibold`）。`InfoGrid` / `InfoPairs` 的字段名同样是 `text-xs`。这些字号在共享组件里改，不要在每个详情页各写一套。

`UsageBar` 只画已经有比例的用量。Deployment 容器的 CPU / 内存列用 `RequestLimitCell`：原文仍是「请求 / 上限」，能解析时以上限为分母画 Requests 条。没有节点容量时不画 Limits 条。监控趋势图不改成这条。

`LogPanel` 和 `TerminalChrome` 只包外观。WebSocket、xterm 协议留在原来的业务组件里。Xshell 风格的统一会话表面方案见 [终端 / 日志界面设计方案](../container-platform/xshell-terminal-log-design.md)。

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

PM 自定义工作流码不在字典里时，给 `StatusIcon` 传 `tone`，不要在页面里另写颜色。卡片描边用 `STATUS_BORDER_CLASS`，和圆点、图标同一套色。

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

环境变量和挂载卷仍是详情里的行内表单。图片裁剪、文档批量规格、Argo 画布、流水线 DAG、监控趋势图在 Phase 6 **没有改业务**，仍按原页面保留。

## 5. Phase 6 之后

Phase 6 已把指标卡、数字列和用量条收进本章组件，并写了视觉回归清单（`visual-regression-checklist.md`）。清单上的屏留给可登录环境手测。

新列表和详情继续用本章组件。下面这些不进 `DataTable`，也不要为了对齐样式重写：

- PM 工作流画布、流水线 DAG、Argo 工作流画布
- 环境变量与挂载卷的行内编辑
- 图片裁剪、文档批量规格、文件解析历史
- 项目监控与集群监控的趋势图
