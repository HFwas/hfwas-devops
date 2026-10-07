# 共享界面模式

> 日期：2026-10-07
> 版本：v0.2
> 关联：[kite-frontend-style-guide.md](./kite-frontend-style-guide.md)、[frontend-style-unification-plan.md](./frontend-style-unification-plan.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：DataTable、StatusIcon、PageHeader、DetailShell、LogPanel 的用法与试点范围 |
| v0.2 | 2026-10-07 | 资源概览用 ResourceOverview；StatusIcon 增加同色圆点 |

---

## 1. 放在哪里

全产品共用，不绑容器。源码在 `frontend/src/components/console/`。

| 组件 | 文件 | 做什么 |
|------|------|--------|
| `DataTable` | `DataTable.tsx` | 紧凑表、toolbar 槽、空态、加载。表头 `h-10 bg-muted px-2`，单元格 `p-2`，外框 `rounded-lg border`。已有行时加载用 `opacity-75` |
| `StatusIcon` + 字典 | `StatusIcon.tsx`、`status.ts` | Ready / Failed / Pending / Warning 等映射到同一套色和图标 |
| `PageHeader` | `PageHeader.tsx` | 标题、描述、右侧操作 |
| `DetailShell` | `DetailShell.tsx` | 贴在顶栏下的标题行、操作、Tab；正文由调用方按当前 Tab 填 |
| `ResourceOverview` | `ResourceOverview.tsx` | 概览指标卡 + 左主栏 + 右侧栏 |
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

## 3. 本阶段试点

只证明组件能跨产品复用，不是全站换皮。

| 产品 | 页面 | 用到 |
|------|------|------|
| PM | 项目列表 | `PageHeader`、`DataTable` |
| 流水线 | `/pipeline/pipelines` | `PageHeader`、`DataTable`、`StatusIcon` |
| 容器 | 集群列表 | `PageHeader`、`DataTable`、`StatusBadge`（内部是 `StatusIcon`） |
| 容器 | Deployment / Pod / StatefulSet / Service / Node / ConfigMap 详情 | `DetailShell`、`ResourceOverview`、`StatusIcon`（概览用 `variant="dot"`） |
| 容器 | Pod 日志 / 终端 | `LogPanel`、`TerminalChrome`（协议未改） |

## 4. 下一步

- **Phase 4**：每个子系统再改 1 个列表和 1 个详情（事项详情、流水线运行详情、工作负载其余详情、API 测试列表）。流水线日志与容器日志继续共用 `LogPanel`。
- **Phase 5**：按模块清单铺开，勾掉 `frontend-style-unification-plan.md` 里的列表 / 详情 / 状态 / 日志。看板和 DAG 不进 `DataTable`，只统一外围间距和颜色。

## 5. 资源详情概览

工作负载和资源详情仍用 `DetailShell`。概览 Tab 的正文用 `ResourceOverview`：

- 标题是资源名，说明行写命名空间。操作按钮高度 `h-8`：刷新、描述、克隆、重启、删除。没有对应接口的动作用「即将支持」，不造数据。
- 工作负载 Tab：概览、Pods、容器、YAML、日志、终端、卷、关联、历史、事件、监控。缺接口的 Tab 是空态。
- 概览：一排指标卡，下面左栏是表和信息，右栏是事件、关联、标签、注解。
- 状态圆点用 `StatusIcon variant="dot"`，颜色仍来自第 2 节字典，不另写一套色。
