# 流水线管理前端页面原型

> 日期：2026-10-17
> 版本：v0.1

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：基于 pipeline-core 后端、frontend/src/modules/pipeline、pipeline schema 与 Tekton 配置还原 6 个管理后台界面原型 |

---

## 0. 原型说明

### 0.1 依据

- 后端接口与 VO：`backend/pipeline-core/`、`docs/pipeline/pipeline-core-api.md`
- 前端页面与组件：`frontend/src/modules/pipeline/`、`frontend/src/router.tsx`
- 库表：`deploy/charts/backend/files/db/05-pipeline-schema.sql`、`10-add-runtime-params.sql`、`11-add-task-kind-resource-limits.sql`、`12-add-pipeline-job-param-bindings.sql`、`15-task-kind-version-visibility.sql`
- 执行侧配置：`deploy/tekton/`（Tekton Pipeline / Task 由后端生成，界面上只暴露任务定义，不直接编辑 YAML）

### 0.2 图例

| 标记 | 含义 |
|------|------|
| 【只读】 | 仅展示，用户不能修改 |
| 【可编辑】 | 用户可输入、选择、拖拽 |
| 【动作】 | 点击后触发接口调用、跳转或弹层 |
| 虚线 | 非主流程的条件分支或弹层触发关系 |

> 说明：以下全部是 `flowchart` 布局原型，模拟管理后台的界面区域，不是 CI/CD 执行时序图。

### 0.3 页面清单与实际路由

| 需求页面 | 实际实现 | 路由 | 主要代码 |
|----------|----------|------|----------|
| 流水线列表页 | `PipelineListPage` | `/pipeline/pipelines` | `frontend/src/modules/pipeline/pages/PipelineListPage.tsx` |
| 流水线详情页 | `PipelineDetailPage` 的「详情」页签 | `/pipeline/pipelines/:pipelineId` | `PipelineDetailPage.tsx`、`PipelineDefinitionCanvas.tsx` |
| 新建 / 编辑流水线页 | `PipelineEditorPage` | `/pipeline/pipelines/new`、`/pipeline/pipelines/:pipelineId/edit` | `PipelineEditorPage.tsx` |
| 流水线运行记录页 | `PipelineDetailPage` 的「运行历史」页签 | `/pipeline/pipelines/:pipelineId/history` | `PipelineDetailPage.tsx`、`runHistory.ts` |
| 运行详情页 | `PipelineRunDetailPage` | `/pipeline/pipelines/:pipelineId/runs/:runId` | `PipelineRunDetailPage.tsx`、`RunSwimlane.tsx` |
| 流水线日志弹窗 | 右侧 `Sheet` 抽屉 + `LogPanel` | 叠加在运行详情页上 | `PipelineRunDetailPage.tsx`、`components/console/LogPanel.tsx` |

> 注意：运行记录不是独立顶级页面，日志也不是居中 Modal，而是详情页页签和右侧抽屉。原型按现有实现还原，避免文档与代码脱节。

### 0.4 运行状态枚举

| 值 | 界面文案 | 说明 |
|----|----------|------|
| 空 | 未运行 | 流水线还没有任何运行记录 |
| `QUEUED` | 排队中 | 等待调度 |
| `RUNNING` | 运行中 | 执行中，界面轮询刷新 |
| `WAITING_APPROVAL` | 待审批 | 命中人工卡点，运行详情页出现「通过」按钮 |
| `SUCCEEDED` | 成功 | 终态 |
| `FAILED` | 失败 | 终态 |
| `CANCELLED` | 已取消 | 终态 |

### 0.5 触发方式枚举

| 值 | 界面文案 | 现状 |
|----|----------|------|
| `MANUAL` | 手动 | 当前后端唯一实际写入值 |
| `WEBHOOK` | Webhook | 前端已支持展示，后端暂未写入 |
| `SCHEDULE` / `SCHEDULED` / `CRON` / `TIMER` | 定时 | 前端已支持展示，后端暂未写入 |

---

## 1. 流水线列表页

### 1.1 布局原型

```mermaid
flowchart TB
  subgraph PAGE["流水线列表页 · /pipeline/pipelines"]
    direction TB
    HEADER["页头【只读 + 动作】<br/>标题：流水线<br/>副标题：共 N 条流水线<br/>[输入] 搜索流水线、仓库或分支<br/>[动作] + 新建流水线 → /pipeline/pipelines/new"]
    FILTER["状态快捷筛选【可编辑】<br/>分段按钮：全部 / 运行中 / 成功 / 失败<br/>提示文案：状态筛选仅作用于当前页"]
    TABLE["数据表格【只读 + 行内动作】"]
    subgraph COLS["表格列"]
      direction LR
      C1["① 流水线名称【只读 + 跳转】<br/>名称链接 → 详情页<br/>副标题：任务名列表，如 代码克隆、构建、部署"]
      C2["② 仓库 / 分支【只读】<br/>仓库短名<br/>分支名（去掉 refs/heads/）"]
      C3["③ 阶段预览【只读】<br/>最多 8 个阶段标签<br/>圆点颜色 = 最近运行状态<br/>运行中圆点脉冲"]
      C4["④ 最近运行【只读 + 跳转】<br/>状态图标 + 文案<br/>相对时间，如 3分钟前<br/>有 lastRunId 时链接 → 运行详情"]
      C5["⑤ 操作【动作】<br/>运行 / 编辑 / 历史 / 删除"]
    end
    PAGER["分页【动作】<br/>第 X / Y 页 · 上一页 / 下一页<br/>pageSize = 10"]
    EMPTY["空态【只读】<br/>还没有流水线<br/>或 没有匹配的流水线"]
    DELETE["删除确认弹窗【动作】<br/>标题：删除流水线「名称」？<br/>说明：删除后无法恢复，运行历史会一并移除。<br/>按钮：取消 / 删除"]
    RUNPARAM["运行参数弹窗【可编辑】<br/>见 5.4 节运行参数弹窗<br/>无默认参数时直接触发运行并跳转"]
  end

  HEADER --> FILTER --> TABLE --> PAGER
  TABLE -.-> COLS
  TABLE -.无数据.-> EMPTY
  C5 -.删除.-> DELETE
  C5 -.运行.-> RUNPARAM
```

### 1.2 数据与控件

| 区域 | 字段 / 控件 | 来源 | 说明 |
|------|-------------|------|------|
| 页头 | 标题、总数 | `total` | 只读 |
| 页头 | 搜索框 | `keyword` | 300ms 防抖后写入 URL `?q`，服务端过滤，重置页码 |
| 页头 | 新建流水线 | — | 跳转 `/pipeline/pipelines/new` |
| 筛选 | 全部 / 运行中 / 成功 / 失败 | `lastRunStatus` | 仅过滤当前页数据，不调后端 |
| 表格 | 流水线名称 | `name` | 链接到详情 |
| 表格 | 任务摘要 | `stages[].jobs[].name` | 无任务时显示「尚未配置阶段」 |
| 表格 | 仓库 / 分支 | `repoUrl`、`gitRef` | 仓库取短名 |
| 表格 | 阶段预览 | `stages[]`、`lastRunStatus` | 最多 8 个；无阶段但有运行状态时只显示一个状态点 |
| 表格 | 最近运行 | `lastRunStatus`、`lastRunTime`、`lastRunId` | 状态图标 + 相对时间 |
| 表格 | 操作 | — | 运行、编辑、历史、删除 |
| 分页 | 页码、上一页 / 下一页 | `total`、`pages` | 默认每页 10 条 |
| 弹窗 | 删除确认 | — | 危险操作，需二次确认 |

### 1.3 页面功能与核心交互

页面用于浏览全部流水线、快速发起运行和进入编辑/历史。搜索走后端分页接口，状态筛选只作用于当前页（界面已明确提示），所以翻页后筛选结果会变化。点击「运行」会先请求 `GET /pipeline/pipelines/{id}/runs/default-params`：有运行时参数就先弹出参数表单，没有则直接创建运行并跳转到运行详情。删除为不可恢复操作，必须经过确认弹窗。

---

## 2. 流水线详情页

### 2.1 布局原型

```mermaid
flowchart TB
  subgraph PAGE["流水线详情页 · /pipeline/pipelines/:pipelineId"]
    direction TB
    HEADER["页头【只读 + 动作】<br/>[动作] ← 返回列表<br/>标题：流水线名称<br/>[动作] 编辑 → /pipeline/pipelines/:id/edit<br/>[动作] 运行"]
    TABS["页签【可编辑】<br/>详情 | 运行历史<br/>切换页签同时切换 URL"]
    META["概览卡片【只读】<br/>仓库 | 分支 | 阶段名列表 | 最近运行状态 + 相对时间 | 凭证名"]
    subgraph CANVAS["定义画布 PipelineDefinitionCanvas【只读】"]
      direction LR
      START["开始"] --> S1["阶段 1<br/>阶段名<br/>任务卡：类型图标 + 任务名 + 类型文案"]
      S1 --> S2["阶段 2<br/>阶段名<br/>任务卡 …"]
      S2 --> END["结束"]
    end
    EMPTY["空态【只读】<br/>这条流水线还没有阶段<br/>点右上角「编辑」后，阶段会显示在这里"]
    ERROR["错误态【只读】<br/>流水线加载失败"]
  end

  HEADER --> TABS --> META --> CANVAS
  CANVAS -.无阶段.-> EMPTY
  META -.加载失败.-> ERROR
```

### 2.2 数据与控件

| 区域 | 字段 / 控件 | 来源 | 说明 |
|------|-------------|------|------|
| 页头 | 返回 / 编辑 / 运行 | — | 运行按钮复用运行参数弹窗 |
| 页签 | 详情、运行历史 | URL | `history` 路由对应运行历史 |
| 概览 | 仓库 | `repoUrl` | 短名显示 |
| 概览 | 分支 | `gitRef` | 去前缀 |
| 概览 | 阶段 | `stages[].name` | 按 `sortOrder` 排序后用 `·` 连接 |
| 概览 | 最近运行 | `lastRunStatus`、`lastRunTime` | 无运行显示「未运行」 |
| 概览 | 凭证 | `credentialId` | 从凭证列表解析名称；未配置显示「未配置」 |
| 画布 | 阶段、任务 | `stages[]`、`stages[].jobs[]` | 只读，按 `sortOrder` 排列 |

### 2.3 页面功能与核心交互

详情页是流水线定义的只读视图：顶部展示仓库、分支、阶段和最近运行，正文用横向泳道画布展示「开始 → 阶段 → 任务 → 结束」。所有修改都必须点「编辑」进入编排页，详情页不提供行内编辑。运行历史页签与详情页签共用一个页面壳，切换时只改 URL，不重新挂载整个页面。

---

## 3. 新建 / 编辑流水线页

### 3.1 布局原型

```mermaid
flowchart TB
  subgraph PAGE["新建 / 编辑流水线页 · /new 或 /:pipelineId/edit"]
    direction TB
    TOP["顶部操作栏【动作】<br/>标题：新建流水线 / 编辑流水线<br/>[动作] 取消（有未保存改动时确认放弃）<br/>[动作] 保存（Ctrl/Cmd + S）<br/>[动作] 保存并运行"]
    BASIC["基本信息【可编辑】<br/>名称 * | 仓库 * | 分支（默认 main） | 凭证（可搜索、可清空）"]
    ERROR["校验错误条【只读】<br/>名称不能为空 / 仓库地址无法解析 / 阶段至少需要一个任务 …"]
    subgraph MAIN["主工作区"]
      direction LR
      subgraph LANES["阶段画布【可编辑】"]
        direction LR
        ZOOM["缩放【可编辑】<br/>75% / 100% / 125% / 150%"]
        ST1["阶段 1 卡片【可编辑】<br/>拖拽手柄 · 阶段名输入<br/>左移 / 右移 / 删除<br/>任务卡：点击选中、拖拽、上移 / 下移 / 删除<br/>[动作] + 添加任务"]
        ST2["阶段 2 卡片 …"]
        ADDSTAGE["[动作] + 添加阶段"]
      end
      subgraph PANEL["右侧面板（22rem）【可编辑】"]
        direction TB
        ADDLIST["添加任务面板<br/>标题：添加到「阶段 N · 名称」<br/>按任务组折叠：代码 / 构建 / 质量控制 / 制品 / 部署 / 测试 / 命令 / 流程<br/>点击任务即添加"]
        INSPECTOR["任务配置面板<br/>任务名称 *<br/>任务类型<br/>命令（按任务类型决定是否必填）<br/>参数绑定：写死 / 变量<br/>高级设置：技术栈 / 运行时 / 工具版本<br/>默认命令提示"]
      end
    end
  end

  TOP --> BASIC --> ERROR --> MAIN
  LANES -.选中任务.-> INSPECTOR
  LANES -.添加任务.-> ADDLIST
```

### 3.2 表单输入项

| 区域 | 输入项 | 必填 | 说明 |
|------|--------|------|------|
| 基本信息 | 名称 | 是 | 提交前 trim |
| 基本信息 | 仓库 | 有 CLONE 任务时必填 | 仅支持 `http` / `https`，需有主机和路径 |
| 基本信息 | 分支 | 否 | 默认 `main`；保存时会同步到 CLONE 的 `GIT_REF` 固定绑定 |
| 基本信息 | 凭证 | 否 | 异步搜索凭证，可清空为「不使用凭证」 |
| 阶段卡片 | 阶段名称 | 是 | 行内输入 |
| 任务卡 | 任务名称 | 是 | 右侧检查器编辑 |
| 任务配置 | 任务类型 | 是 | 受约束过滤，见 3.4 |
| 任务配置 | 命令 | 按类型 | `CLONE`、`APPROVAL` 等不需要命令；其余需要 |
| 任务配置 | 参数绑定 | 否 | 每个可见参数一行：`写死` 或 `变量`（运行时填写） |
| 任务配置 | 技术栈 / 运行时 / 工具版本 | 否 | 级联选择，来自 `/pipeline/toolchains` |
| 添加任务 | 任务组、任务 | — | 数据来自 `/pipeline/task-kinds`，禁用任务不显示 |

### 3.3 交互与校验

- **保存**：校验通过后调用 `POST /pipeline/pipelines` 或 `PUT /pipeline/pipelines/{id}`；新建成功后跳到编辑路由。
- **保存并运行**：先保存，再走运行参数弹窗 / 直接运行。
- **脏检查**：草稿 JSON 与保存快照不一致时，取消会弹「放弃未保存的修改？」；关闭或刷新页面触发浏览器离开确认。
- **快捷键**：`Ctrl/Cmd + S` 保存。
- **拖拽**：阶段和任务都支持拖拽排序，也保留左移/右移、上移/下移按钮。
- **任务选择**：点击任务卡在右侧打开检查器；点击「添加任务」把右侧切到任务市场列表。

### 3.4 编排约束

| 约束 | 触发时机 | 表现 |
|------|----------|------|
| 流水线至多一个 CLONE 任务 | 添加 / 修改类型 / 保存 | 报错并阻止 |
| 审批任务必须独占一个阶段 | 添加 / 修改类型 / 保存 | 报错；从阶段添加时提示并自动放到新阶段 |
| 同一阶段不能并行多个 IMAGE 任务 | 添加 / 修改类型 / 保存 | 报错并阻止 |
| 阶段至少一个任务 | 保存 | 报错 |
| 阶段名称、任务名称不能为空 | 保存 | 报错 |
| 必填参数不能为空 | 保存 | 报错；绑定方式为「变量」时跳过 |

### 3.5 任务类型

添加面板按任务组展示，任务类型由后端 `pipeline_task_kind` 动态提供；前端 `jobCatalog.ts` 提供兜底名称和默认命令。

| 任务组 | 任务类型 |
|--------|----------|
| 代码 | `CLONE` 代码克隆 |
| 构建 | `BUILD` 构建、`IMAGE` 镜像构建 |
| 质量控制 | `LINT_SEMGREP`、`LINT_SONAR`、`FORMAT`、`DEPENDENCY_ANALYSIS`、`SCAN`、`DEPENDENCY_TRACK` |
| 制品 | `PACKAGE`、`PUBLISH`、`UPLOAD` |
| 部署 | `DEPLOY` 部署、`KUBECTL` K8s 命令 |
| 测试 | `TEST` |
| 命令 | `CUSTOM` |
| 流程 | `APPROVAL` 人工卡点、`NOTIFY` 通知 |

### 3.6 页面功能与核心交互

这是流水线的编排器，不是传统表单：上面一行是基础信息，左侧是可拖拽的阶段/任务画布，右侧是任务市场或任务检查器。所有编辑先落在前端草稿里，只有点保存才写后端；运行前同样要先通过校验并保存。人工卡点、镜像构建、克隆任务的约束在前端即时拦截，后端保存时也会做一致性校验。

---

## 4. 流水线运行记录页（运行历史页签）

### 4.1 布局原型

```mermaid
flowchart TB
  subgraph PAGE["运行记录 · /pipeline/pipelines/:pipelineId/history"]
    direction TB
    HEADER["页头【只读 + 动作】<br/>[动作] ← 返回列表<br/>标题：流水线名称<br/>[动作] 编辑 / 运行"]
    TABS["页签【可编辑】<br/>详情 | 运行历史（当前）"]
    TOOLBAR["筛选栏【可编辑】<br/>状态下拉：全部 / 运行中 / 成功 / 失败 / 排队中 / 待审批 / 已取消<br/>时间下拉：近 24 小时 / 近 7 天（默认）/ 近 30 天 / 全部时间<br/>搜索框：运行编号、分支<br/>右侧统计：最近 N 次中 M 条 · 近 7 天成功率 X%"]
    TABLE["数据表格【只读 + 行内动作】"]
    subgraph COLS["表格列"]
      direction LR
      C1["① 运行编号【只读 + 跳转】<br/>#运行 ID<br/>commit 短 SHA 或触发人"]
      C2["② 触发方式【只读】<br/>手动 / Webhook / 定时"]
      C3["③ 分支【只读】"]
      C4["④ 状态【只读】<br/>排队中 / 运行中 / 待审批 / 成功 / 失败 / 已取消"]
      C5["⑤ 开始时间【只读】"]
      C6["⑥ 耗时【只读】<br/>如 58s、1m 05s"]
      C7["⑦ 操作【动作】<br/>查看详情"]
    end
    PAGER["分页【动作】<br/>本地分页，每页 10 条"]
    EMPTY["空态【只读】<br/>还没有运行。点击「运行」后会打开这次运行的详情。<br/>或 没有符合筛选的运行"]
  end

  HEADER --> TABS --> TOOLBAR --> TABLE --> PAGER
  TABLE -.-> COLS
  TABLE -.无数据.-> EMPTY
```

### 4.2 筛选条件

| 筛选项 | 可选值 | 说明 |
|--------|--------|------|
| 状态 | 全部、运行中、成功、失败、排队中、待审批、已取消 | 客户端过滤 |
| 时间 | 近 24 小时、近 7 天（默认）、近 30 天、全部时间 | 按 `startedAt` 过滤 |
| 关键词 | 运行编号、分支 | 300ms 防抖，匹配 `id`、`gitRef` |

### 4.3 数据与交互

- 数据来源：`GET /pipeline/pipelines/{id}/runs?pageNo=1&pageSize=200`，一次最多拉 200 条历史。
- 有非终态运行时每 4 秒轮询；全部结束后停止。
- 状态、时间、关键词都写入 URL 查询参数，刷新后保持筛选条件。
- 页面内分页是本地分页，不是服务端分页。
- 成功率只统计时间窗口内 `SUCCEEDED` / `FAILED` / `CANCELLED` 的运行，没有已结束运行时显示 `—`。
- 点击行或「查看详情」进入运行详情页。

### 4.4 页面功能

运行历史用来回看某条流水线的执行结果、定位失败运行并进入日志。它复用详情页的页头和页签，是一个页面级页签而不是独立路由；筛选和分页都在前端完成，因为一次已经拉取了最近 200 条记录。

---

## 5. 运行详情页

### 5.1 布局原型

```mermaid
flowchart TB
  subgraph PAGE["运行详情页 · /pipeline/pipelines/:pipelineId/runs/:runId"]
    direction TB
    HEADER["页头【只读 + 动作】<br/>[动作] ← 返回（运行历史）<br/>标题：流水线名 #运行ID<br/>副标题：错误信息，或 分支 · commit<br/>状态徽标<br/>[动作] 通过（仅 WAITING_APPROVAL 且运行未结束）<br/>[动作] 取消（确认弹窗）"]
    FIELDS["基本信息【只读】<br/>触发 | 触发人 | 开始时间 | 耗时（运行中实时跳动）"]
    subgraph CANVAS["运行画布 RunSwimlane【只读 + 日志动作】"]
      direction LR
      START["开始"] --> S1["阶段卡片<br/>阶段名 · 阶段耗时<br/>[动作] 日志<br/>任务项：任务名 / 日志"]
      S1 --> S2["阶段 2 …"]
      S2 --> END["结束（颜色随运行状态）"]
    end
    OVERALL["[动作] 查看全程日志<br/>位于画布右上角"]
    CANCEL["取消确认弹窗【动作】<br/>标题：取消运行 #ID？<br/>说明：正在执行的任务会被中止。<br/>按钮：继续运行 / 取消运行"]
  end

  HEADER --> FIELDS --> CANVAS
  OVERALL -.打开日志抽屉.-> LOG["日志抽屉（见第 6 节）"]
  HEADER -.点击取消.-> CANCEL
```

### 5.2 数据与控件

| 区域 | 字段 / 控件 | 来源 | 说明 |
|------|-------------|------|------|
| 页头 | 标题 | `pipelineName`、`id` | 只读 |
| 页头 | 副标题 | `errorMessage` 或 `gitRef` + `commitSha` | 失败原因优先 |
| 页头 | 状态 | `status` | 状态徽标 |
| 页头 | 通过 | — | 仅 `WAITING_APPROVAL` 显示，调用 approve 接口 |
| 页头 | 取消 | — | 非终态显示，二次确认后调用 cancel 接口 |
| 信息 | 触发 / 触发人 / 开始 / 耗时 | `trigger`、`triggeredByName`、`startedAt`、`finishedAt` | 耗时运行中按当前时间计算 |
| 画布 | 阶段状态 | 阶段内任务聚合 | 边框颜色反映状态 |
| 画布 | 阶段耗时 | `startedAt` / `finishedAt` | 最早开始到最晚结束 |
| 画布 | 任务状态 | `jobs[].status` | 任务项展示 |
| 画布 | 日志入口 | — | 全程 / 阶段 / 任务三种粒度 |

### 5.3 核心交互

- 运行未结束时每 3 秒轮询一次；进入终态后停止。
- 点「查看全程日志」看全部任务日志；点阶段卡片的「日志」看该阶段聚合日志；点任务项看单任务日志。
- 三种入口都会在右侧打开同一个日志抽屉，标题随焦点变化。
- 运行中「耗时」每秒刷新；结束后固定为 `finishedAt - startedAt`。
- 审批和取消成功后同时刷新运行详情、运行历史列表和流水线列表。

### 5.4 运行参数弹窗（StartRunButton）

列表页、详情页、编辑页的「运行 / 保存并运行」共用同一个弹窗组件。

```mermaid
flowchart TB
  subgraph DIALOG["运行参数弹窗 · StartRunButton"]
    direction TB
    TRIGGER["触发【动作】<br/>列表 / 详情 / 编辑页点「运行」或「保存并运行」<br/>先请求 GET /runs/default-params"]
    CONTENT["表单内容【可编辑】<br/>按参数动态渲染：<br/>标签 + 必填星号<br/>input → 文本框<br/>select → 下拉，含「请选择」<br/>默认值取 defaultValue"]
    FOOTER["底部按钮【动作】<br/>取消 | 运行"]
    DIRECT["无参数时【动作】<br/>不弹窗，直接创建运行并跳转运行详情"]
  end

  TRIGGER --> CONTENT --> FOOTER
  TRIGGER -.参数为空.-> DIRECT
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `paramKey` | string | 参数键，提交时作为 `params` 的 key |
| `paramLabel` | string | 展示标签 |
| `paramType` | `input` / `select` / `api_select` | `select` 且有 options 时渲染下拉，其余渲染文本框 |
| `defaultValue` | string | 初始值 |
| `required` | boolean | 必填校验，空值阻止提交 |
| `options` | string[] | 下拉选项 |
| `placeholder` | string | 输入提示 |

提交调用 `POST /pipeline/pipelines/{id}/runs`，请求体为 `{ "params": { "KEY": "VALUE" } }`；成功后跳转运行详情。

---

## 6. 流水线日志弹窗（右侧抽屉）

### 6.1 布局原型

```mermaid
flowchart TB
  subgraph OVERLAY["运行详情页上层 · 日志抽屉"]
    direction TB
    TRIGGER["打开方式【动作】<br/>① 画布右上角「查看全程日志」<br/>② 阶段卡片「日志」<br/>③ 任务项「日志」"]
    subgraph SHEET["右侧 Sheet 抽屉 · 宽 min 720px / 92vw"]
      direction TB
      TITLE["标题【只读】<br/>全程日志<br/>阶段 · 阶段名<br/>阶段名 / 任务名"]
      STATE["连接状态【只读】<br/>连接中 · 已连接 · 未连接（运行已结束）"]
      BODY["终端内容【只读】<br/>运行中自动跟随滚动并隐藏行号<br/>结束后显示行号<br/>空态：这条任务还没有日志<br/>加载中：加载中…"]
      CLOSE["[动作] 关闭<br/>X / 点击遮罩 / Esc"]
    end
  end

  TRIGGER --> SHEET
```

### 6.2 内容说明

| 焦点 | 标题 | 内容来源 |
|------|------|----------|
| 全程 | 全程日志 | 按阶段顺序拼接全部任务 `logText` |
| 阶段 | 阶段 · 阶段名 | 该阶段下全部任务日志 |
| 任务 | 阶段名 / 任务名 | 单任务 `logText` |

- 抽屉使用终端配色（`--term-bg` / `--term-fg`），等宽字体、自动换行。
- 运行中隐藏行号（内容在变，行号会跳），结束后显示行号。
- 运行中自动跟随最新日志；用户上滚后仍可查看历史内容。
- 焦点对应的任务/阶段被删除或不存在时自动关闭抽屉。

### 6.3 页面功能

日志抽屉承担「看执行现场」的职责，不单独占一个路由。它以任务为最小粒度，同时支持阶段聚合和全程聚合，方便从失败任务一路向上排查；抽屉状态完全由运行详情页的焦点状态驱动，不额外发请求。

---

## 7. 页面跳转关系

```mermaid
flowchart LR
  LIST["流水线列表<br/>/pipeline/pipelines"] -->|新建| NEW["新建流水线<br/>/new"]
  LIST -->|编辑| EDIT["编辑流水线<br/>/:id/edit"]
  LIST -->|名称 / 最近运行| DETAIL["流水线详情<br/>/:id"]
  LIST -->|历史| HISTORY["运行历史页签<br/>/:id/history"]
  DETAIL -->|编辑| EDIT
  DETAIL -->|运行| RUN["运行详情<br/>/:id/runs/:runId"]
  HISTORY -->|查看详情 / 点击行| RUN
  RUN -->|返回| HISTORY
  RUN -->|日志| LOG["日志抽屉<br/>Sheet"]
  NEW -->|保存成功| EDIT
  EDIT -->|保存并运行| RUN
```

---

## 8. 与需求的对应关系与差异

| 需求项 | 还原结果 |
|--------|----------|
| 流水线列表页 | 已还原，含搜索、当前页状态筛选、阶段预览、行内运行/编辑/历史/删除 |
| 流水线详情页 | 已还原，定义为只读画布，运行历史为页签 |
| 新建/编辑流水线表单页 | 实际是画布式编排器：基础信息 + 阶段/任务拖拽 + 右侧检查器，不是传统纵向表单 |
| 流水线运行记录页 | 实际是详情页的「运行历史」页签（`/history`），不是独立顶级路由 |
| 流水线日志弹窗 | 实际是运行详情页上的右侧 Sheet 抽屉，支持全程/阶段/任务三种焦点 |
| 状态枚举 | 列表筛选只暴露 4 种；运行历史和运行详情展示完整 6 种状态 |
| 触发方式 | 前端支持手动 / Webhook / 定时三种展示，后端当前只写入 `MANUAL` |

---

## 9. 接口对照

| 界面动作 | 接口 |
|----------|------|
| 列表分页 / 搜索 | `POST /pipeline/pipelines/page` |
| 详情 | `GET /pipeline/pipelines/{id}` |
| 新建 | `POST /pipeline/pipelines` |
| 更新 | `PUT /pipeline/pipelines/{id}` |
| 删除 | `DELETE /pipeline/pipelines/{id}` |
| 触发运行 | `POST /pipeline/pipelines/{id}/runs` |
| 运行前默认参数 | `GET /pipeline/pipelines/{id}/runs/default-params` |
| 运行历史 | `GET /pipeline/pipelines/{id}/runs?pageNo=1&pageSize=200` |
| 运行详情 | `GET /pipeline/pipelines/{id}/runs/{runId}` |
| 取消运行 | `POST /pipeline/pipelines/{id}/runs/{runId}/cancel` |
| 审批通过 | `POST /pipeline/pipelines/{id}/runs/{runId}/approve` |
| 任务类型 / 任务组 | `GET /pipeline/task-kinds` |
| 工具链级联 | `GET /pipeline/toolchains` |
| 凭证选择 | `POST /pipeline/credentials/page` |
