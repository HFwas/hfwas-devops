# Argo Workflows 前端产品设计

> 版本：v0.1
> 日期：2026-10-01

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-01 | 初版：路由/页面/组件/API 设计 |

---

## 1. 产品定位

Argo Workflows 作为通用工作流引擎，前端提供：

- **工作流模板管理**：浏览、创建、编辑 WorkflowTemplate
- **工作流实例管理**：列表、提交、查看运行状态与日志
- **可视化编排**：拖拽式 DAG 编辑器，节点配置

与现有流水线（Pipeline）的区别：

| | 流水线 (Pipeline) | 工作流 (Workflow) |
|---|---|---|
| 执行引擎 | Tekton | Argo Workflows |
| 编排方式 | 列式 DAG（阶段→任务） | 自由 DAG（任意连线） |
| 节点类型 | CI 步骤（clone/build/test） | 通用（脚本/HTTP/K8s/条件） |
| 适用场景 | CI/CD | ETL/巡检/数据处理/自动化 |

## 2. 路由设计

```
/argo                          ← 产品根（重定向到 /argo/workflows）
/argo/workflows                ← 工作流列表
/argo/workflows/new            ← 新建工作流（编辑器）
/argo/workflows/:name          ← 工作流详情/运行历史
/argo/workflows/:name/edit     ← 编辑工作流
/argo/workflows/:name/runs/:runId  ← 运行详情

/argo/templates                ← 工作流模板列表
/argo/templates/new            ← 新建模板
/argo/templates/:name          ← 模板详情
/argo/templates/:name/edit     ← 编辑模板（编辑器）

/argo/cron                     ← CronWorkflow 列表
/argo/cron/new                 ← 新建定时工作流
```

## 3. 页面设计

### 3.1 工作流列表页 (`/argo/workflows`)

布局：控制台标准头 + 内容区

```
┌─────────────────────────────────────────────────┐
│  [←] 工作流                         [+ 新建]    │
│  搜索框 [________]  筛选 [全部 ▼]               │
├─────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐             │
│  │ health-check  │  │ data-export  │             │
│  │ 🔵 Running    │  │ ✅ Succeeded │             │
│  │ 耗时: 3m20s   │  │ 耗时: 12m05s │             │
│  │ 触发: 手动     │  │ 触发: Cron   │             │
│  └──────────────┘  └──────────────┘             │
│  ┌──────────────┐  ┌──────────────┐             │
│  │ java-invest   │  │ nightly-build│             │
│  │ ❌ Failed     │  │ ⏳ Pending   │             │
│  │ 耗时: 8m02s   │  │ 耗时: -      │             │
│  └──────────────┘  └──────────────┘             │
│                                    [加载更多 ▼]  │
└─────────────────────────────────────────────────┘
```

- 卡片式布局，每张卡片显示：名称、状态（带色图标）、耗时、触发方式
- 搜索按名称过滤，筛选按状态分组
- 卡片操作：点击进入详情，hover 显示「运行」「编辑」「删除」

### 3.2 工作流编辑器 (`/argo/workflows/new` 或 `edit`)

核心页面，三栏布局：

```
┌──────────────────────────────────────────────────────────────┐
│  [← 返回]  工作流名称 [________]  [💾 保存] [▶ 运行] [⋮]   │
├──────┬───────────────────────────────────────┬───────────────┤
│      │                                       │               │
│ 节点  │         画布 (Vue Flow)               │  配置面板     │
│ 面板  │                                       │               │
│      │  ┌──────┐                              │  ─────────── │
│      │  │ 开始  │                              │  节点名称     │
│      │  └──┬───┘                              │  [________]  │
│      │     │                                  │               │
│  ───  │  ┌──────┐  ┌─────────┐              │  镜像         │
│  脚本  │  │ 克隆  │  │  构建   │              │  [________]  │
│  HTTP │  └──┬───┘  └────┬────┘              │               │
│  DAG  │     │           │                    │  命令         │
│  条件  │     └──────┬────┘                    │  [________]  │
│  循环  │        ┌───────┐                     │               │
│  人工  │        │  部署  │                     │  高级设置     │
│       │        └───┬───┘                     │  ├ 重试: 3次  │
│  ───  │            │                         │  ├ 超时: 30m  │
│       │        ┌──────────┐                  │  └ 资源: 1C   │
│       │        │  钉钉通知  │                 │               │
│       │        └──────────┘                  │               │
│       │                                       │               │
│       │                    [工具条]            │               │
│       │  缩放手柄 布局 小地图 触屏模式          │               │
├──────┴───────────────────────────────────────┴───────────────┤
│  Mini Map 区域（右下角浮动）                                  │
└──────────────────────────────────────────────────────────────┘
```

**左栏 — 节点面板**：
- 分组展示可拖拽节点类型
- 分页：基础（脚本、HTTP、DAG）、高级（条件、循环、人工审批）、Argo 原语（WorkflowTemplate、Cron）
- 拖拽到画布即创建新节点

**中栏 — 画布**：
- 基于 `@vue-flow/core` 的自由 DAG
- 节点连线：拖拽手柄连线，支持条件分支
- 节点状态：运行中/成功/失败/跳过（彩色图标）
- 背景网格 + 缩放手势

**右栏 — 配置面板**：
- 选中节点后显示配置表单
- 节点名称、镜像、命令、环境变量、重试策略、超时、资源
- 选中连线后显示条件/参数映射

### 3.3 运行详情页 (`/argo/workflows/:name/runs/:runId`)

```
┌──────────────────────────────────────────────────────────────┐
│  [←]  工作流: health-check  # v0.1.0                        │
│  状态: 🔴 Failed     触发: 手动     耗时: 3m20s              │
│  [⏸ 暂停] [⏹ 停止] [↻ 重跑]                                 │
├──────────────────────────────────────────────────────────────┤
│  ┌──────────────────────────────────────────────────┐        │
│  │  画布（只读，显示执行状态）                         │        │
│  │  ┌──────┐     ┌──────────┐     ┌─────────────┐  │        │
│  │  │ ✅   │     │   ✅     │     │    ❌       │  │        │
│  │  │ 开始├────►│  克隆   ├────►│  镜像构建  │  │        │
│  │  └──────┘     └──────────┘     └─────────────┘  │        │
│  │                                    │            │        │
│  │                               ┌──────────┐     │        │
│  │                               │   ⏳     │     │        │
│  │                               │  钉钉通知  │     │        │
│  │                               └──────────┘     │        │
│  └──────────────────────────────────────────────────┘        │
├──────────────────────────────────────────────────────────────┤
│  [日志] [事件] [YAML]  ← 底部 Tab                              │
│  ┌──────────────────────────────────────────────────┐        │
│  │ time="..." level=error msg="Build failed: ..."    │        │
│  │ time="..." level=info msg="Step 2/5 completed"    │        │
│  └──────────────────────────────────────────────────┘        │
└──────────────────────────────────────────────────────────────┘
```

- 画布只读，节点按执行结果着色
- 点击节点可查看该步骤日志
- 底部面板切换日志/事件/YAML

## 4. 组件树

```
WorkflowModule/
├── layout/
│   └── ArgoShell.vue              ← 产品壳（二级 Tab 导航）
│
├── views/
│   ├── WorkflowListView.vue       ← 工作流列表
│   ├── WorkflowEditorView.vue     ← 编辑器（三栏）
│   ├── WorkflowRunView.vue        ← 运行详情
│   ├── WorkflowHomeView.vue       ← 工作流详情
│   ├── TemplateListView.vue       ← 模板列表
│   └── TemplateEditorView.vue     ← 模板编辑器
│
├── components/
│   ├── flow/
│   │   ├── FlowCanvas.vue         ← 画布容器（Vue Flow 实例）
│   │   ├── FlowToolbar.vue        ← 底部工具条
│   │   ├── FlowMinimap.vue        ← 小地图
│   │   ├── NodePalette.vue        ← 左栏节点面板（可拖拽）
│   │   ├── NodeConfigPanel.vue    ← 右栏配置面板
│   │   ├── EdgeConfigPanel.vue    ← 连线配置面板
│   │   ├── EdgeContextMenu.vue    ← 右键菜单
│   │   └── LayoutEngine.vue       ← 自动布局
│   │
│   ├── nodes/
│   │   ├── BaseNode.vue           ← 基础节点（通用）
│   │   ├── StartNode.vue          ← 开始节点
│   │   ├── EndNode.vue            ← 结束节点
│   │   ├── ScriptNode.vue         ← 脚本节点
│   │   ├── HttpNode.vue           ← HTTP 请求节点
│   │   ├── DagNode.vue            ← DAG 子工作流节点
│   │   ├── ConditionNode.vue      ← 条件分支节点
│   │   ├── ParallelNode.vue       ← 并行节点
│   │   ├── ApprovalNode.vue       ← 人工审批节点
│   │   └── SuspendNode.vue        ← 暂停/等待节点
│   │
│   ├── run/
│   │   ├── RunStatusBadge.vue     ← 状态徽标
│   │   ├── RunLogViewer.vue       ← 日志查看器
│   │   ├── RunEventList.vue       ← 事件列表
│   │   ├── RunYamlViewer.vue      ← YAML 查看器
│   │   └── RunTimeline.vue        ← 步骤时间线
│   │
│   └── common/
│       ├── YamlEditor.vue         ← YAML 编辑器（CodeMirror）
│       ├── ImageSelector.vue      ← 镜像选择器
│       └── ResourceSlider.vue     ← 资源滑块
│
├── stores/
│   ├── workflowStore.ts           ← 工作流状态
│   ├── workflowRunStore.ts        ← 运行状态
│   └── templateStore.ts           ← 模板状态
│
├── api/
│   └── workflowApi.ts             ← 后端 API
│
├── types/
│   └── workflow.ts                ← 类型定义
│
├── graph/
│   ├── nodeRegistry.ts            ← 节点类型注册表
│   ├── nodeIcons.ts               ← 节点图标映射
│   └── layoutEngine.ts            ← 自动布局算法（dagre）
│
└── router/
    └── argoRoutes.ts              ← 路由定义
```

## 5. 节点类型注册表

```typescript
interface WorkflowNodeMeta {
  type: string
  label: string
  group: string            // 'trigger' | 'task' | 'flow-control' | 'notification'
  description: string
  icon: Component
  color: string            // 节点头部色条
  defaultImage: string     // 默认容器镜像
  defaultCommand: string
  inputs: NodePort[]       // 输入端口
  outputs: NodePort[]      // 输出端口
}
```

| type | label | group | 默认镜像 | 说明 |
|------|-------|-------|---------|------|
| `start` | 开始 | trigger | — | DAG 入口 |
| `end` | 结束 | trigger | — | DAG 出口 |
| `script` | 脚本 | task | `alpine:3.21` | Shell/Bash 命令 |
| `http` | HTTP 请求 | task | `curlimages/curl:8.11.1` | HTTP API 调用 |
| `dag` | 子工作流 | task | — | 嵌套 WorkflowTemplate |
| `condition` | 条件判断 | flow-control | — | if/else 分支 |
| `parallel` | 并行 | flow-control | — | fan-out/fan-in |
| `approval` | 人工审批 | flow-control | — | 钉钉/站内审批 |
| `suspend` | 等待 | flow-control | — | 定时等待 |
| `notification` | 通知 | notification | `curlimages/curl:8.11.1` | 钉钉/邮件 |

## 6. 数据模型

```typescript
interface WorkflowTemplate {
  name: string
  description?: string
  entrypoint: string
  arguments?: Parameter[]
  templates: WorkflowTemplateSpec[]
  volumes?: Volume[]
}

interface WorkflowTemplateSpec {
  name: string
  inputs?: { parameters?: Parameter[] }
  outputs?: { parameters?: OutputParameter[]; artifacts?: Artifact[] }
  steps?: StepGroup[]       // 顺序 steps
  dag?: { tasks: DagTask[] }  // DAG 模式
  container?: Container
  script?: Script
  retryStrategy?: RetryStrategy
  activeDeadlineSeconds?: number
}

interface DagTask {
  name: string
  template: string       // 引用 templates[].name
  arguments?: { parameters?: ParameterRef[] }
  dependencies?: string[]  // DAG 依赖
  when?: string           // 条件表达式
  withParam?: string      // 动态 fan-out
}

interface StepGroup {
  - name: string         // 阶段名
  - template: string     // 当前如果是单步
  - name: string         // 或者嵌套并行
    - name: string
      template: string
      arguments: ...
}
```

## 7. 视觉风格

对齐现有 Pipeline 产品的视觉体系:

| 元素 | 规范 |
|------|------|
| 页面背景 | 渐变 `radial-gradient(1200px 320px..., #f5f5f5)` |
| 卡片 | 白底 `border-radius:10px`，hover 上浮 |
| 节点 | 白底 + 顶部色条 + 圆角 `12px` |
| 节点状态 | 色点: 🔵运行中 🟢成功 🔴失败 ⏸暂停 ⏹停止 |
| 连线 | 箭头曲线，运行时着色 |
| 图标 | @lucide/vue 统一图标 |
| 字体 | 系统 UI 字体 |

节点配色（按 group）：

| group | 色条颜色 | 背景色 |
|-------|---------|--------|
| trigger | `#3370ff` | `#eff6ff` |
| task | `#059669` | `#ecfdf5` |
| flow-control | `#d97706` | `#fffbeb` |
| notification | `#7c3aed` | `#f5f3ff` |

## 8. API 前缀

```
POST   /api/argo/workflows/page         ← 工作流列表（分页）
POST   /api/argo/workflows              ← 创建
GET    /api/argo/workflows/{name}       ← 详情
PUT    /api/argo/workflows/{name}       ← 更新
DELETE /api/argo/workflows/{name}       ← 删除
POST   /api/argo/workflows/{name}/submit  ← 提交运行
GET    /api/argo/workflows/{name}/runs   ← 运行历史
GET    /api/argo/workflows/runs/{runId}  ← 运行详情
GET    /api/argo/workflows/runs/{runId}/logs  ← 步骤日志
GET    /api/argo/workflows/runs/{runId}/events ← 步骤事件

POST   /api/argo/templates/page         ← 模板列表
POST   /api/argo/templates              ← 创建模板
GET    /api/argo/templates/{name}       ← 模板详情
PUT    /api/argo/templates/{name}       ← 更新模板
DELETE /api/argo/templates/{name}       ← 删除模板

POST   /api/argo/cron/page              ← 定时工作流列表
POST   /api/argo/cron                   ← 创建
....
```

## 9. 实施切片

| 切片 | 内容 | 工作量 |
|------|------|--------|
| A | 路由 + 壳 + 产品注册 + 列表页 | ~2d |
| B | 编辑器三栏布局 + 节点面板 + 配置面板骨架 | ~2d |
| C | 画布集成 @vue-flow + 自定义节点 + 连线 | ~3d |
| D | 节点配置表单 + YAML 编辑器 | ~2d |
| E | 运行详情页 + 状态实时刷新 + 日志 | ~2d |
| F | 模板管理 + Cron 管理 | ~2d |
| G | 拖拽体验优化 + 撤销/重做 + 自动布局 | ~2d |

**合计：约 15 个工作日**