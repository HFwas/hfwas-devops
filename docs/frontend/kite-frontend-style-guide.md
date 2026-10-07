# 前端样式规范（参照 Kite，全产品共用）

> 日期：2026-10-07
> 版本：v0.6
> 来源参照：[kite-org/kite](https://github.com/kite-org/kite)（默认分支 `main`，许可证 Apache-2.0）
> 适用范围：**hfwas-devops 全部前端产品面**（工作台、用户中心、PM、流水线/CI、容器平台、API 测试、文档生成、文件解析、图片处理等），不是容器模块专用规范
> 原则：**学规范与模式，不整仓拷贝**；一套 token / 壳层 / 列表 / 详情 / 状态语义，各业务模块只换内容和数据

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：从 kite `ui/` 提炼 token、壳层、表格、状态、详情 Tab、日志/终端规范 |
| v0.2 | 2026-10-07 | 明确全产品共用；落地顺序改为全局壳与 token → 各业务模块；去掉「仅容器」表述 |
| v0.3 | 2026-10-07 | 主色锁定为 kite 冷蓝 hue 235；全站侧栏化；Cloud Agent 分阶段落地 |
| v0.4 | 2026-10-07 | 共享模式组件落在 `frontend/src/components/console/`，用法见 shared-ui-patterns |
| v0.5 | 2026-10-07 | 工作负载概览补指标卡和左右栏；状态可用同色圆点 |
| v0.6 | 2026-10-07 | 指标卡挂在详情 Tab 下；圆点变体的 Running 用成功绿 |

---

## 0. 适用范围（先读）

| 适用 | 说明 |
|------|------|
| 全局壳 | `AppShell`、导航、顶栏、侧栏、主题、密度 |
| 设计 token | 颜色、圆角、字体、间距、sidebar 语义色 — **全站唯一真相** |
| 共享组件 | Button / Table / Badge / Tabs / Dialog / Sheet / Skeleton 等 `components/ui` |
| 业务模块 | PM、流水线、容器、API 测试、用户中心、工作台等 **同一套视觉语言** |
| 运维类面板 | 日志流、Web 终端、YAML/代码区 — 凡用到处样式一致 |

**不在本规范内：** 业务接口、领域状态机、各模块路由结构本身；交付运维独立产品若另开仓库，也应**复用同一 token 与组件库**，而不是另起一套。

---

## 1. 技术栈与目录（参照源）

| 项 | kite 说明 |
|----|-----------|
| UI 根目录 | `ui/` |
| 入口样式 | `ui/src/index.css` → `styles/base.css` + 多套主题 |
| 栈 | React 19、Vite、Tailwind CSS 4、Radix、lucide、CVA、TanStack Query/Table、xterm、sonner |
| shadcn | `style: new-york`，`baseColor: neutral`，`cssVariables: true` |

本仓库目标栈：`frontend/` 为 React 19 + Vite + Tailwind 4 + shadcn/ui（与 kite 同族）；图表继续用 **ECharts**；终端继续用 **xterm**。

---

## 2. 设计 Token（全站唯一）

所有模块只消费这些变量，**禁止**在业务页写死十六进制色或第二套主题。

### 2.1 建议落点（本仓库）

| 职责 | 路径（建议） |
|------|----------------|
| 全局入口 | `frontend/src/index.css` |
| 基础布局 / 圆角 / 字体 / 动画 | `frontend/src/styles/base.css`（可从 kite 结构对齐） |
| 默认亮暗主题 | `frontend/src/styles/themes/default.css` |
| shadcn 配置 | `frontend/components.json` |

### 2.2 布局与圆角（参照 kite `base.css`）

| Token | 参照值 | 说明 |
|-------|--------|------|
| `--header-height` | `3.5rem` | 全站顶栏高度 |
| `--radius` | `0.5rem` | 基准圆角（当前仓库若为 `0.625rem`，统一改为更紧的 `0.5rem`） |
| `--radius-sm` / `md` / `lg` / `xl` | 由 `--radius` 推导 | 经 Tailwind `@theme` 暴露 |
| 字体 sans | `--font-sans` 系统 UI 栈；`--app-font-sans: var(--font-sans)` 给 `body` | 全站正文 |
| 字体 mono | `--font-mono`：`'Maple Mono', ui-monospace, …` | 日志、终端、YAML、代码块 |
| 暗色 | `.dark` + `@custom-variant dark` | 全站一处切换 |

侧栏几何（参照 kite `sidebar.tsx`）：宽 `16rem`，移动端 `18rem`，图标折叠 `3rem`。

字体与 kite `ui/src/styles/base.css` 一致：正文是系统无衬线（`--font-sans` / `--app-font-sans`），`code` / `pre` / `kbd` / `samp` 与 `.font-mono` 使用 Maple Mono。字体文件在 `frontend/src/assets/fonts/`（Maple Mono 与 JetBrains Mono，SIL OFL）。JetBrains Mono 通过 `@font-face` 备用，正文默认仍是系统无衬线。日志和 YAML 用 `font-mono`；xterm 画布读取 `--font-mono`。

### 2.3 默认亮色（参照 kite `default.css` `:root`）

| Token | 参照值 |
|-------|--------|
| `--background` | `oklch(1 0 0)` |
| `--foreground` | `oklch(0.141 0.005 285.823)` |
| `--primary` | `oklch(0.55 0.22 235)`（冷蓝；若品牌要紫，可改 hue，但**全站只保留一套**） |
| `--primary-foreground` | `oklch(0.98 0.01 235)` |
| `--muted` / `--muted-foreground` | `oklch(0.967 …)` / `oklch(0.552 …)` |
| `--destructive` | `oklch(0.577 0.245 27.325)` |
| `--border` / `--input` | `oklch(0.92 0.004 286.32)` |
| `--ring` | 与 `--primary` 一致 |
| `--sidebar*` | 完整侧栏语义色，供全局导航使用 |

### 2.4 默认暗色（`.dark`）

| Token | 参照值 |
|-------|--------|
| `--background` | `oklch(0.141 0.005 285.823)` |
| `--primary` | `oklch(0.65 0.18 235)` |
| `--border` | `oklch(1 0 0 / 10%)` |
| `--input` | `oklch(1 0 0 / 15%)` |

主色拍板原则：**结构对齐 kite；色相全站统一**（蓝或紫二选一，不做模块各自为政）。

---

## 3. 共享组件层（全产品）

目录：`frontend/src/components/ui/`（及后续抽到 `components/console/` 的壳组件）。

### 3.1 必须统一的原语

button、input、label、badge、card、table、tabs / responsive-tabs、dialog、sheet、dropdown-menu、select、popover、tooltip、separator、skeleton、sidebar、breadcrumb、command、sonner、avatar、checkbox、switch、textarea。

**Badge 体量参照：** `text-xs`、`rounded-md`、`px-2 py-0.5`；变体 `default | secondary | destructive | outline`。

### 3.2 建议抽成全站模式组件（不绑业务域）

| 模式组件 | 落点 | 用途 | 适用模块举例 |
|----------|------|------|----------------|
| `AppShell` | `components/console/AppShell.tsx` | 侧栏 + sticky 顶栏 + 内容 inset | 全站 |
| `PageHeader` | `components/console/PageHeader.tsx` | 标题 + 描述 + 主操作 | 全站列表/详情 |
| `DataTable` | `components/console/DataTable.tsx` | 紧凑表、工具栏、空态、加载态 | PM 事项、流水线、集群、API 定义… |
| `StatusIcon` | `components/console/StatusIcon.tsx`、`status.ts` | Ready / Failed / Pending / Warning… | 流水线运行、Pod、事项状态、扫描结果 |
| `DetailShell` + Tabs | `components/console/DetailShell.tsx` | 标题行 + 操作 + 多 Tab | 事项详情、流水线运行、工作负载、集合项… |
| `LogPanel` / `TerminalChrome` | `components/console/LogPanel.tsx` | 底部抽屉、连接指示、等宽区 | 流水线日志、Pod 日志/终端、API 调试响应… |
| `CodePane` | 尚未抽取 | YAML / JSON / 脚本只读或编辑 | 流水线任务脚本、K8s YAML、API body |

用法与试点见 [shared-ui-patterns.md](./shared-ui-patterns.md)。新页面禁止再写私有表格密度或状态色。

业务模块**禁止**各自再造一套侧栏、表格密度或状态色。

---

## 4. 全站 UI 模式

### 4.1 页面壳（所有产品）

- 左侧 inset 侧栏 + `SidebarInset` + sticky 顶栏（`3.5rem`，`bg-background/95 backdrop-blur`，`border-b`）。
- 顶栏信息架构统一：breadcrumb | 全局搜索（可分期）| 主创建 | 主题 | 用户；模块特有开关（如终端）放在约定槽位。
- 内容区水平：`px-4 lg:px-6`；区块间距：`gap-4 md:gap-6`。
- 侧栏：分组标签 uppercase / bold / muted；激活态 accent + `text-sidebar-primary` 图标。

产品切换（PM / 流水线 / 容器等）走**同一壳内的导航分组**，不要每个子系统一套顶栏皮肤。

### 4.2 列表 → 详情（所有产品）

1. 列表页：工具栏 + 筛选 + 紧凑表
2. 行进入详情
3. 详情：`DetailShell` + Tabs

**表格密度（全站统一）：**

| 元素 | 约定 |
|------|------|
| 容器 | `rounded-lg border`，高度策略一致（可贴视口） |
| 表头 | `h-10`、`bg-muted`、`px-2` |
| 单元格 | `p-2` |
| 行 hover | `bg-muted/50` |
| 空状态 | 固定最小高度 + 居中文案 |
| 加载中（已有行） | `opacity-75`，避免整页闪白 |

### 4.3 详情 Tab（跨模块同一壳）

标题行：名称加粗突出 + 次要元信息 muted + 右侧操作按钮（高度 `h-8` / `h-7`）。

Tab **类型**按模块填充，壳不变。示例：

| 模块 | Tab 示例（内容可变，壳相同） |
|------|------------------------------|
| 容器 / 工作负载 | Overview、Pods、Containers、YAML、Logs、Terminal、Volumes、Related、History、Events、Monitor。概览正文是指标卡加左右两栏 |
| 流水线运行 | Overview、Jobs、Logs、Artifacts、Params… |
| PM 事项 | Overview、Activity、Comments、Links、History… |
| API 定义 / 调试 | Request、Response、Tests、History… |

### 4.4 状态语义色（全站字典）

用 Tailwind 语义色 + 图标，**各模块映射到同一字典**，不要 PM 一套绿、容器另一套绿。概览卡可以用同色圆点（`StatusIcon variant="dot"`），不换另一套色。圆点变体里 Running 用成功绿，和 Kite 的 Pod 状态点一致；图标变体不改。

| 语义 | 典型样式 | 映射举例 |
|------|----------|----------|
| 成功 | `green-500` / `dark:green-400` | Ready、通过、成功、已解决 |
| 失败 | `red-500` | Failed、错误、阻断 |
| 警告 | yellow | Warning、降级、待确认 |
| 进行中 | `blue-500` | Pending、Running、构建中 |
| 终止中 / 清理 | orange | Terminating、取消中 |
| 暂停 / 挂起 | purple | Paused、Hold |
| 中性 / 空闲 | gray | Scaled-to-zero、草稿、未启用 |

### 4.5 日志 / 终端 / 等宽区（凡用到处一致）

| 能力 | 约定 |
|------|------|
| 底部抽屉 | 默认可约 40vh，可拖拽，最小高度约 120px |
| 面板头 | `h-10 bg-muted/50` |
| 连接状态 | 绿色呼吸点（成功连接） |
| 字体 | `--font-mono`（Maple Mono），字号与行高全站一致 |

---

## 5. 不要照搬的部分

| 避免 | 原因 |
|------|------|
| Kite 品牌与 Logo | 商标与产品身份 |
| 插件联邦、AI 聊天等产品特有能力 | 与规范无关 |
| 整文件粘贴业务页 | 抄模式；大段源码需遵守 Apache-2.0 |
| 模块私有主题色 | 破坏全产品一致性 |
| 用 recharts 替换 ECharts | 只学布局密度 |

---

## 6. 全产品落地顺序

| 阶段 | 做什么 | 验收 |
|------|--------|------|
| 1. Token | 统一 `index.css` / themes；定主色；补齐 `--sidebar*` | 亮暗切换全站一致 |
| 2. 壳层 | `AppShell`：侧栏 + sticky 顶栏 + breadcrumb 槽 | 任一模块进入视觉一致 |
| 3. 共享模式 | DataTable、Status、DetailShell、Log/Terminal chrome | 新页默认用这些，禁止平行实现 |
| 4. 试点页（跨模块各 1） | 工作台或项目列表、流水线列表、集群列表 | 三处密度与状态色一致 |
| 5. 详情试点 | 事项详情 / 流水线运行详情 / 工作负载详情 | 同一 DetailShell |
| 6. 铺开 | 其余列表与详情按模块迭代 | 无「某模块还是旧皮肤」 |

**不是：** 先把容器改完再考虑别的。  
**而是：** 先全局基建，再各业务同步换皮。

---

## 7. 模块对照清单（实施时勾选）

| 模块 | 列表页 | 详情壳 | 状态色 | 日志/终端/代码区 |
|------|--------|--------|--------|------------------|
| 工作台 | □ | — | □ | — |
| 用户中心 | □ | □ | □ | — |
| PM | □ | □ | □ | — |
| 流水线 / CI | □ | □ | □ | □ |
| 容器平台 | □ | □ | □ | □ |
| API 测试 | □ | □ | □ | □（响应/脚本） |
| 文档生成 / 文件解析 / 图片 | □ | □ | □ | — |

---

## 8. 来源链接

| 资源 | URL |
|------|-----|
| kite 仓库 | https://github.com/kite-org/kite |
| kite UI | https://github.com/kite-org/kite/tree/main/ui |
| 产品站 | https://kitehq.dev |
| 许可证 | Apache-2.0 |

---

*数值与路径来自对 kite 的只读核对；上游变更以仓库当前文件为准。本规范约束的是 hfwas-devops（及共用该前端体系的产品）的视觉与交互一致性。*
