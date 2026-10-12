# 视觉回归清单

> 日期：2026-10-12
> 版本：v0.1
> 关联：[frontend-style-unification-plan.md](./frontend-style-unification-plan.md) v0.10、[kite-frontend-style-guide.md](./kite-frontend-style-guide.md) v0.8

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-12 | 初版：Phase 6 关键屏清单。手测留给可登录环境，本阶段以类型检查、单测和构建为准 |

---

## 1. 怎么用

Phase 6 不在无登录环境里代点这些屏。可登录之后，按下面每组至少看亮色一次；抽一组再切暗色。对照规范看三件事：壳层不错位、表格密度一致、数字不因位数跳动。

默认正文仍是系统无衬线。不要为了回归去打开 Maple Mono 或 95% display-scale。

每条的「看什么」只列本阶段动过的共享层：指标卡标签 `text-xs`、数字 `tabular-nums`、用量条高度与颜色。画布、行内编辑、趋势图保持原样，不作为本清单的失败条件。

## 2. 登录

登录走 Keycloak，页面不在本仓库的业务壳里。

| 屏 | 路径 / 入口 | 看什么 |
|----|-------------|--------|
| 跳转中 | 未登录打开任意业务路由 | 文案「正在跳转登录…」，`text-sm text-muted-foreground`，没有第二套顶栏 |
| 连接中 | 应用刚加载、`main.tsx` 尚未完成登录 | 文案「正在连接登录…」，同样只用 muted 正文 |
| Keycloak 登录页 | 跳转后的外部登录表单 | 本仓库不画这页。确认跳走后能回到原来的路径，回来后进入下面的壳 |

## 3. 工作台

| 屏 | 路径 | 看什么 |
|----|------|--------|
| 工作台 | `/workbench` | `PageHeader`，产品卡片 `p-4`，侧栏是当前产品菜单 |
| 用户列表 | `/user/accounts` | `DataTable` 表头 `h-10`、单元格 `p-2`，状态走 `StatusIcon` |
| 账号设置 | `/user/settings` | `DetailShell`，字段名偏小、muted，没有私有表格 |

## 4. PM

| 屏 | 路径 | 看什么 |
|----|------|--------|
| 项目列表 | `/pm/projects` | 列表密度与工作台用户列表一致；编码列等宽 |
| 事项列表 | `/pm/projects/:projectId/items/:typeCode` | 编号、优先级等数字列位数变化时列宽不抖 |
| 事项详情 | `/pm/projects/:projectId/items/:typeCode/:itemId` | `DetailShell` 标题贴在顶栏下；状态用共享色，不另写红底 Badge |

看板 `/pm/projects/:projectId/board/:typeCode` 和工作流画布不进本清单的表格验收。

## 5. 流水线

| 屏 | 路径 | 看什么 |
|----|------|--------|
| 流水线列表 | `/pipeline/pipelines` | 列表 + 状态色；壳层与 PM 相同 |
| 运行记录 | `/pipeline/pipelines/:pipelineId` | 耗时列数字对齐 |
| 运行详情 | `/pipeline/pipelines/:pipelineId/runs/:runId` | `DetailShell` + `LogPanel`。日志仍是等宽小字，协议未改 |
| 任务概览（用量相关，建议加看） | `/pipeline/task-kinds/:kind` | CPU / 内存字段为 `text-xs` 标签 + `tabular-nums`。这里仍是字段，不是趋势图 |

流水线 DAG 和编辑画布不改，不作为失败条件。

## 6. 容器

| 屏 | 路径 | 看什么 |
|----|------|--------|
| 集群列表 | `/container/clusters` | `DataTable` + `StatusBadge` |
| 集群概览 | `/container/clusters/:clusterId` | 统计是 `OverviewMetricCards`：标签 `text-xs`，数字 `text-2xl` 且 `tabular-nums`。下面的趋势图保持 ECharts，不要求变成用量条 |
| Deployment 详情 | `/container/clusters/:clusterId/deployments/:namespace/:name` | 概览指标卡同上。容器 Tab 的 CPU / 内存仍显示「请求 / 上限」；能解析时有一条高 `h-2` 的 Requests 条（低用量为冷蓝，超过约 60% 黄、超过约 90% 红） |

节点详情的容量 / 可分配是字段，标签 `text-xs`，数值 `tabular-nums`。环境变量和挂载卷仍是行内表单。

## 7. API 测试

| 屏 | 路径 | 看什么 |
|----|------|--------|
| 接口列表 | `/api-test` | `PageHeader` + `DataTable` + 状态 |
| 接口详情 | `/api-test/definitions/:definitionId` | `DetailShell`；调试响应在 `LogPanel` |
| 环境列表 | `/api-test/environments` | 与接口列表同一套表密度 |
| 集合详情（建议加看） | `/api-test/collections/:collectionId` | 耗时列走 `DataTable` 的 `tabular-nums` |

## 8. 不在本次手测里判失败

- 工作流 / 流水线 / Argo 画布的内部节点样式
- 环境变量、挂载卷的行内编辑器
- 图片裁剪、文档批量规格、文件解析历史
- 监控趋势图的序列颜色（图表仍用 ECharts，不改成 `UsageBar`）
