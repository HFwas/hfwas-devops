# 前端样式规范（参照 Kite，全产品共用）

> 日期：2026-10-12
> 版本：v0.8
> 来源参照：[kite-org/kite](https://github.com/kite-org/kite)（默认分支 `main`，许可证 Apache-2.0）；产品站 [kitehq.dev](https://kitehq.dev/)
> 适用范围：**hfwas-devops 全部前端产品面**（工作台、用户中心、PM、流水线/CI、容器平台、API 测试、文档生成、文件解析、图片处理等）
> 原则：**学规范与模式，不整仓拷贝**。本文是 `frontend/` 视觉的唯一真相。落地节奏见 [frontend-style-unification-plan.md](./frontend-style-unification-plan.md)，组件用法见 [shared-ui-patterns.md](./shared-ui-patterns.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：从 kite `ui/` 提炼 token、壳层、表格、状态、详情 Tab、日志/终端规范 |
| v0.2 | 2026-10-07 | 明确全产品共用；落地顺序改为全局壳与 token → 各业务模块；去掉「仅容器」表述 |
| v0.3 | 2026-10-07 | 主色锁定为 kite 冷蓝 hue 235；全站侧栏化；Cloud Agent 分阶段落地 |
| v0.4 | 2026-10-07 | 共享模式组件落在 `frontend/src/components/console/`，用法见 shared-ui-patterns |
| v0.5 | 2026-10-07 | 工作负载概览补指标卡和左右栏；状态可用同色圆点 |
| v0.6 | 2026-10-07 | 指标卡挂在详情 Tab 下；圆点变体的 Running 用成功绿 |
| v0.7 | 2026-10-12 | 纠正字体：kite 默认整站 Maple Mono，本仓库推荐默认保持系统 sans + 代码区 mono；纳入 tabular-nums，display-scale 95% 只作可选密度档；补齐 token、材质、信息层级与可复制片段，并标明已落地 / 待落地 |
| v0.8 | 2026-10-12 | Phase 6：指标卡与字段名收到 `text-xs`，数值与 `DataTable` 单元格补 `tabular-nums`；用量条落地为 `UsageBar`。默认字体仍是系统 sans |

---

## 0. 怎么用

新页面按这个顺序做：

1. 壳和列表用 `frontend/src/components/console/` 里已有组件，名字以第 8 节为准。
2. 颜色只写语义 token（`bg-background`、`text-muted-foreground`、`bg-primary`、`border-border`）。
3. 标识用 `font-mono text-xs`，计数和容量用 `tabular-nums`。
4. 默认字体保持系统无衬线。不要在业务页改 `--app-font-sans`。

文中标记：

| 标记 | 含义 |
|------|------|
| 已落地 | `frontend/` 里已经这样写，新代码照抄 |
| 待落地 | 规范要求如此，组件或页面还没改齐。本文件只定约定，不在同一次改动里改业务 UI |

---

## 1. 适用范围

| 适用 | 说明 |
|------|------|
| 全局壳 | `AppShell`、导航、顶栏、侧栏、主题 |
| 设计 token | 颜色、圆角、字体、间距、sidebar 语义色 |
| 共享组件 | `components/ui` 的 Button / Table / Badge / Dialog 等，以及 `components/console/` |
| 业务模块 | PM、流水线、容器、API 测试、用户中心、工作台，同一套视觉语言 |
| 等宽区 | 日志流、Web 终端、YAML / JSON / 脚本 |

业务接口、领域状态机、各模块路由结构不在本文。交付运维若另开仓库，复用同一 token 与组件库。

---

## 2. 技术栈与文件

| 项 | 本仓库（已落地） | kite 参照 |
|----|------------------|-----------|
| 根目录 | `frontend/` | `ui/` |
| 入口 | `frontend/src/index.css` 引入 `styles/base.css`、`styles/themes/default.css` | `ui/src/index.css` |
| 栈 | React 19、Vite、Tailwind CSS 4、Radix、lucide、CVA、TanStack Query、xterm、sonner | 同族；图表他们用 Recharts |
| shadcn | `frontend/components.json`：`style: new-york`，`baseColor: neutral`，`cssVariables: true`，`iconLibrary: lucide` | 相同 |
| 图表 | **ECharts** | Recharts。只学密度，不换库 |
| 图标 | 只用 Lucide | 另有 Tabler。不要引进第二套图标库 |

暗色：`frontend/src/shared/console/useConsoleTheme.ts` 在 `document.documentElement` 上切换 `dark`。`base.css` 有 `@custom-variant dark (&:is(.dark *))`。

---

## 3. 字体

### 3.1 kite 当前默认（参照，2026-10-12 复核）

kite 的 `ui/src/styles/base.css` 声明了三套栈：系统 UI、Maple Mono、JetBrains Mono。**产品默认不走系统栈。** `FontProvider` 默认 `maple`，把整站 UI（侧栏、表格、按钮、正文）设成 Maple Mono。JetBrains Mono 和系统无衬线是可选项。`display-scale` 默认 **95%**。

官网 [kitehq.dev](https://kitehq.dev/) 是另一套：正文 DM Mono，大标题 Cormorant Garamond，纸色底。那是营销页，不是控制台。

v0.6 写「字体与 kite `base.css` 一致、正文是系统无衬线」，把 CSS 里的栈默认值当成了产品默认。本版按 FontProvider 的实际默认纠正。

### 3.2 本仓库推荐默认（已落地）

正文用系统无衬线，Maple Mono 只给代码、日志、终端、YAML、以及 ID / 镜像 / 键名。

| 变量 | 值 | 谁在用 |
|------|----|--------|
| `--font-sans` | `ui-sans-serif, system-ui, -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, "Noto Sans", sans-serif, …` | Tailwind `font-sans` 的族 |
| `--app-font-sans` | `var(--font-sans)` | `body` 与 `.font-sans` |
| `--font-mono` | `"Maple Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace` | `code` / `pre` / `kbd` / `samp`、`.font-mono`、`.xterm` |

文件：`frontend/src/styles/base.css`。字体文件在 `frontend/src/assets/fonts/`（Latin 子集，SIL OFL）：

- `maple-mono-latin-400-normal.woff2`（字重 400）
- `jetbrains-mono-latin-wght-normal.woff2`（可变字重 100–800，`@font-face` 已注册，没有接到正文）

保持这个默认，原因：

1. 内置的是 **Latin 子集**。中文标签会回退到系统字体。整站改成 Maple Mono 后，汉字和拉丁混排是两套字宽。
2. 控制台以中文标签为主，系统无衬线在 100% 根字号下更清楚。
3. 该约定已经铺到全站。默认路径不再改 `--app-font-sans`。

JetBrains Mono 留在 `@font-face` 里，供第 3.3 节的可选档使用。不要在业务组件里写 `font-family: "JetBrains Mono"`。

### 3.3 可选高级密度档（待落地，默认关闭）

向 kite 的 maple 默认靠拢时，做成显式档位，不替换第 3.2 节的推荐默认。

| 档 | `--app-font-sans` | 根字号 |
|----|-------------------|--------|
| `system`（默认，已落地） | `var(--font-sans)` | `100%` |
| `maple` | `"Maple Mono", var(--font-sans)` | `95%`（对齐 kite display-scale） |
| `jetbrains` | `"JetBrains Mono", var(--font-sans)` | `95%` |

等宽档必须把系统 sans 留在回退链末尾，缺字的汉字仍能显示。根字号用 `font-size: 95%`，不要用 `transform: scale` 缩整页。

落地时只加一个开关（例如 `<html data-font="system|maple|jetbrains">`），持久化到本地设置。不要新增字体组件库，不要改 `components/ui` 的尺寸档。

风险（未补 CJK 字重前，不要把 maple 设成默认）：

- Latin 子集导致中英基线不齐、汉字突然变宽。
- 95% 会缩小中文标签，侧栏和表格更挤。
- 已有截图和视觉回归要重跑。
- Maple Mono 目前只注册了 400。粗体标题会由浏览器去合成，和 JetBrains 可变字重不一致。

---

## 4. 密度与数字

### 4.1 `tabular-nums`（已落地）

计数、副本数、容量、百分比、耗时、时间戳、指标卡上的数字使用 `tabular-nums`，避免位数变化时列宽跳动。

| 状态 | 位置 |
|------|------|
| 已落地 | `sidebar.tsx` 的侧栏徽标；`podContainerColumns.tsx` 的重启列；`OverviewMetricCards` 的数值；`DataTable` 单元格默认带 `tabular-nums` |
| 调用方 | 不必在每个业务页再写一遍。标识列继续加 `font-mono text-xs` |

`tabular-nums` 只改数字宽度，不改字体族。标识仍然用 `font-mono`。默认正文字体仍是系统 sans。

### 4.2 显示密度（display-scale 不作为默认）

kite 默认把界面缩到 95%。本仓库推荐默认保持 **100%**，不引入 DisplayScale Provider。

95% 只属于第 3.3 节的 maple / jetbrains 档。系统 sans 档保持 100%。没有用户开关之前，不要在 `:root` 上写 `font-size: 95%`。

---

## 5. Token 与材质

色值以 `frontend/src/styles/themes/default.css` 为准。业务页禁止写死十六进制，禁止模块私有 `:root`。

### 5.1 圆角与顶栏（已落地）

`frontend/src/styles/base.css`：

| Token | 值 | Tailwind |
|-------|----|----------|
| `--header-height` | `3.5rem` | 顶栏 `h-(--header-height)` |
| `--radius` | `0.5rem` | — |
| `--radius-sm` | `calc(var(--radius) - 4px)`（4px） | `rounded-sm` |
| `--radius-md` | `calc(var(--radius) - 2px)`（6px） | `rounded-md`：按钮、徽章、输入框 |
| `--radius-lg` | `var(--radius)`（8px） | `rounded-lg`：表、指标卡、日志面板 |
| `--radius-xl` | `calc(var(--radius) + 4px)`（12px） | `rounded-xl`：`Card`、对话框 |

侧栏几何在 `frontend/src/components/ui/sidebar.tsx`：宽 `16rem`，移动端 `18rem`，图标折叠 `3rem`。

### 5.2 亮色（已落地）

| Token | 值 |
|-------|----|
| `--background` / `--card` / `--popover` | `oklch(1 0 0)` |
| `--foreground` / `--card-foreground` | `oklch(0.141 0.005 285.823)` |
| `--primary` / `--ring` / `--sidebar-primary` / `--sidebar-ring` | `oklch(0.55 0.22 235)` |
| `--primary-foreground` | `oklch(0.98 0.01 235)` |
| `--secondary` / `--muted` / `--accent` | `oklch(0.967 0.001 286.375)` |
| `--secondary-foreground` / `--accent-foreground` | `oklch(0.21 0.006 285.885)` |
| `--muted-foreground` | `oklch(0.552 0.016 285.938)` |
| `--destructive` | `oklch(0.577 0.245 27.325)` |
| `--border` / `--input` / `--sidebar-border` | `oklch(0.92 0.004 286.32)` |
| `--sidebar` | `oklch(0.985 0 0)` |
| `--sidebar-foreground` | `oklch(0.141 0.005 285.823)` |
| `--sidebar-accent` | `oklch(0.967 0.001 286.375)` |
| `--chart-1` | `oklch(0.646 0.222 41.116)` |
| `--chart-2` | `oklch(0.6 0.118 184.704)` |
| `--chart-3` | `oklch(0.398 0.07 227.392)` |
| `--chart-4` | `oklch(0.828 0.189 84.429)` |
| `--chart-5` | `oklch(0.769 0.188 70.08)` |

主色锁定冷蓝 **hue 235**。模块不得改 hue，也不得改成紫色。

`--border` 是极淡灰（与白底几乎贴边）。分隔靠这道边，不靠阴影。

会话区另有 `--term-bg` / `--term-fg` / `--term-danger` / `--term-warn`，只给 `LogPanel` 的 `terminal` 变体，不拿来画普通卡片。

### 5.3 暗色（已落地，`.dark`）

| Token | 值 |
|-------|----|
| `--background` | `oklch(0.141 0.005 285.823)` |
| `--foreground` | `oklch(0.985 0 0)` |
| `--card` / `--popover` | `oklch(0.21 0.006 285.885)` |
| `--primary` / `--ring` / `--sidebar-primary` / `--sidebar-ring` | `oklch(0.65 0.18 235)` |
| `--primary-foreground` | `oklch(0.15 0.02 235)` |
| `--secondary` / `--muted` / `--accent` | `oklch(0.274 0.006 286.033)` |
| `--muted-foreground` | `oklch(0.705 0.015 286.067)` |
| `--destructive` | `oklch(0.704 0.191 22.216)` |
| `--border` / `--sidebar-border` | `oklch(1 0 0 / 10%)` |
| `--input` | `oklch(1 0 0 / 15%)` |
| `--sidebar` | `oklch(0.21 0.006 285.885)` |
| `--chart-1` | `oklch(0.488 0.243 264.376)` |
| `--chart-2` | `oklch(0.696 0.17 162.48)` |
| `--chart-3` | `oklch(0.769 0.188 70.08)` |
| `--chart-4` | `oklch(0.627 0.265 303.9)` |
| `--chart-5` | `oklch(0.645 0.246 16.439)` |

Tailwind 映射在 `base.css` 的 `@theme inline`：`bg-primary`、`border-border`、`bg-chart-1`、`bg-sidebar` 都读这些变量。

### 5.4 材质

| 表面 | 类 | 状态 |
|------|----|------|
| 内容卡 | `rounded-xl border bg-card text-card-foreground shadow-sm` | 已落地：`components/ui/card.tsx` |
| 指标卡 / 概览块 | `rounded-lg border bg-card shadow-sm` | 已落地：`OverviewMetricCards`、`OverviewCard` |
| 表容器 | `rounded-lg border` | 已落地：`components/ui/table.tsx`、`DataTable` |
| 对话框 | `rounded-xl border shadow-lg` | 已落地：`dialog.tsx` |
| 下拉 | `rounded-md border shadow-md` | 已落地：`dropdown-menu.tsx` |
| 抽屉 | `shadow-lg` | 已落地：`sheet.tsx` |

静止卡片的阴影到 `shadow-sm` 为止。`shadow-md` / `shadow-lg` 只表示浮层层级。业务页不要给普通卡片写静止态重阴影，也不要写自定义 `box-shadow`。悬停可以加 `hover:border-primary/40`；已有的工作台卡片 `hover:shadow-md` 可以留着，新卡片优先改边框。

### 5.5 语义色点缀

橙、紫、绿、黄不进入 `--primary`。它们只出现在两处：

1. **状态**，唯一字典是 `frontend/src/components/console/status.ts` 的 `STATUS_TONE_CLASS` / `STATUS_DOT_CLASS` / `STATUS_BORDER_CLASS`。
2. **图表**，系列色只用 `chart-1` … `chart-5`（ECharts 读对应 CSS 变量）。不要把状态色当成图表系列，也不要把 `chart-*` 当成 Pod 状态。

| 语义 | 类（与 `status.ts` 一致） | 映射举例 |
|------|--------------------------|----------|
| success | `text-green-500 dark:text-green-400` | Ready、通过、已解决 |
| failed | `text-red-500 dark:text-red-400` | Failed、错误、阻断 |
| warning | `text-yellow-500 dark:text-yellow-400` | Warning、降级、待确认 |
| progress | `text-blue-500 dark:text-blue-400` | Pending、构建中 |
| terminating | `text-orange-500 dark:text-orange-400` | Terminating、取消中 |
| paused | `text-purple-500 dark:text-purple-400` | Paused、Hold |
| neutral | `text-muted-foreground` | 草稿、未知、未启用 |

圆点用 `StatusIcon variant="dot"`。圆点变体里 Running 用成功绿（已落地），图标变体不变。未知状态码落在 neutral；PM 自定义工作流码用 `tone` 显式传入，不要在页面里另写颜色。

---

## 6. 信息层级

三层固定，全站一样：

| 层 | 类 | 例子 |
|----|----|------|
| 标签 | `text-xs text-muted-foreground` | 指标名、字段名、表头、侧栏分组 |
| 数值 | `text-sm`；指标强调 `text-2xl font-bold tracking-tight tabular-nums` | 副本数、容量、百分比 |
| 标识 | `font-mono text-xs`，长文本加 `break-all` 或 `truncate` | UID、镜像、编码、选择器、键 |

### 6.1 侧栏（已落地）

- 分组：`SidebarGroupLabel` 自带 `text-xs`。`AppSidebar` 再加 `font-semibold uppercase tracking-wide text-muted-foreground`。
- 菜单项：`h-8 text-sm`。激活态是 `data-[active=true]:bg-sidebar-accent`，图标 `text-sidebar-primary`（`sidebar.tsx` 与 `AppSidebar` 的 `activeIconClass`）。
- kite 的激活行是 `bg-primary/10`。本仓库保持 accent 底 + 主色图标，不并行写一套 `bg-primary/10`。
- 产品切换用 `ProductSwitcher`，侧栏只渲染当前产品的菜单。

### 6.2 表格（已落地）

列表用 `DataTable`，不要手写另一套密度。

| 元素 | 类 |
|------|----|
| 容器 | `rounded-lg border`（在 `components/ui/table.tsx`） |
| 表头 | `h-10 bg-muted px-2` |
| 单元格 | `p-2 text-sm` |
| 行 hover | `hover:bg-muted/50` |
| 空态 | 单元格 `h-40`，`text-sm text-muted-foreground` 居中 |
| 加载中且已有行 | 容器 `opacity-75` |
| 标识列 | 列 `className: 'font-mono text-xs'` |
| 数字列 | `DataTable` 单元格已带 `tabular-nums`。右对齐时再加 `text-right` |

`components/ui/table.tsx` 的原始单元格是 `px-3 py-2`、行 hover 是 `hover:bg-muted/40`。`DataTable` 会盖住表头和单元格。新列表走 `DataTable`，避免两套密度。

### 6.3 详情（已落地）

- 列表页标题：`PageHeader`，`text-xl font-semibold tracking-tight`，描述 `text-sm text-muted-foreground`。
- 详情：`DetailShell`。标题 `text-2xl font-bold tracking-tight`，说明 `text-sm text-muted-foreground`。标题行 `sticky top-(--header-height)`，底边 `border-b`，底 `bg-background/95 backdrop-blur`。
- Tab：选中 `border-primary font-medium text-primary`，未选中 `text-muted-foreground`。
- 操作按钮：`Button size="sm"`（`h-8`）。Button 没有 `h-7` 档。侧栏小按钮用 `SidebarMenuButton size="sm"`。
- 指标卡挂在 `DetailShell` 的 `metrics` 上，由 `OverviewMetricCards` 渲染。左右栏用 `ResourceOverview`（主栏 + 侧栏 `xl:grid-cols-[minmax(0,1.5fr)_minmax(18rem,0.85fr)]`）。
- 字段表用 `InfoGrid` / `InfoPairs`。镜像用 `ImageList`，标签和选择器用 `PillList`（`font-mono text-xs`）。单行复制用 `EllipsisCopy`。

`OverviewMetricCards` 的标签是 `text-xs text-muted-foreground`，数值带 `tabular-nums`（强调态 `text-2xl font-bold`，其余 `font-semibold`）。`InfoGrid` / `InfoPairs` 的字段名是 `text-xs text-muted-foreground`。字号只改这两个组件，不要在每个详情页各写一套。

### 6.4 用量条（已落地）

共享组件是 `UsageBar`（`frontend/src/components/console/UsageBar.tsx`）。轨道 `h-2`，不要再做一条更高的进度条。阈值：

| 条 | 低于 60% | 大于 60% | 大于 90% |
|----|----------|----------|----------|
| Requests | `bg-primary` | `bg-yellow-500` | `bg-red-500` |
| Limits | `bg-orange-500` | `bg-yellow-500` | `bg-red-500` |

轨道 `h-2 rounded-full bg-muted`。旁边的核心数、GiB、百分比用 `text-xs tabular-nums`。Limits 的橙色是语义点缀，不是第二主色。

Deployment 容器表的 CPU / 内存列原来就是「请求 / 上限」文本，现由 `RequestLimitCell` 渲染：两边都能解析时，以上限为分母画 **Requests** 条。这一行没有节点容量，不另画一条总会顶满的 Limits 条。`usageFill(..., 'limits')` 的橙色留给已经有容量分母的调用。集群监控趋势图仍延期，不在用量条里重做。

### 6.5 日志 / 终端 / 等宽区（已落地）

`LogPanel` / `TerminalChrome`（`frontend/src/components/console/LogPanel.tsx`）：

| 能力 | 约定 |
|------|------|
| 高度 | 默认 `h-[40vh]`，最小 `min-h-[120px]`，底边可拖 |
| 顶栏 | `min-h-10`，普通变体 `bg-muted/50`；工具栏 `flex-wrap` |
| 连接 | 成功为绿色呼吸点 |
| 正文 | `font-mono text-xs leading-5` |
| terminal 变体 | 底 `--term-bg`，字 `--term-fg`，不随亮暗翻转 |
| xterm | 画布字体读 `--font-mono`（`PodShell`） |

协议留在业务组件。外观不要各写一套。

---

## 7. 可复制片段

片段与当前组件一致的，直接用。标明待落地的，是目标写法，现有组件还没改到。

### 7.1 推荐默认字体（已落地，不要在页面里重复声明）

```css
:root {
  --header-height: 3.5rem;
  --radius: 0.5rem;
  --app-font-sans: var(--font-sans);
}
```

### 7.2 可选密度档（待落地，默认不要打开）

```css
:root[data-font="maple"] {
  --app-font-sans: "Maple Mono", var(--font-sans);
  font-size: 95%;
}

:root[data-font="jetbrains"] {
  --app-font-sans: "JetBrains Mono", var(--font-sans);
  font-size: 95%;
}
```

### 7.3 指标卡

`OverviewMetricCards` 的结构（标签 `text-xs`，数字 `tabular-nums`；强调态 `text-2xl font-bold`）：

```tsx
<div className="min-w-0 overflow-hidden rounded-lg border bg-card px-4 py-3 shadow-sm">
  <div className="text-xs text-muted-foreground">就绪</div>
  <div className="mt-2 text-2xl font-bold tracking-tight tabular-nums">2/2</div>
  <div className="mt-1 text-xs text-muted-foreground">pods</div>
</div>
```

页面把数据交给 `DetailShell` 的 `metrics`。字号在 `OverviewMetricCards` 里改，业务页不要复制这段 div。

### 7.4 表格列

`DataTable` 单元格已经带 `tabular-nums`。标识列再加 `font-mono text-xs`。需要右对齐时加 `text-right`。

```tsx
const columns = [
  {
    id: 'code',
    header: '编码',
    className: 'font-mono text-xs',
    cell: (row) => row.code,
  },
  {
    id: 'ready',
    header: '就绪',
    className: 'text-right tabular-nums',
    cell: (row) => row.ready,
  },
]
```

### 7.5 字段行

`InfoGrid` 已按这个结构收字段名。新详情优先用 `InfoGrid`，标识用 `PillList` / `ImageList`：

```tsx
<div className="grid grid-cols-[7.5rem_minmax(0,1fr)] gap-x-3 gap-y-3 text-sm">
  <dt className="text-xs text-muted-foreground">UID</dt>
  <dd className="min-w-0 font-mono text-xs break-all">{uid}</dd>
</div>
```

### 7.6 用量条

用 `UsageBar`，不要在页面里再写一条轨道。`ratio` 是用量除以容量（或限额），大于 1 时宽度截到满条。

```tsx
import { UsageBar } from '@/components/console/UsageBar'

<UsageBar kind="requests" ratio={2.9 / 8} caption="2.9 / 8.00 cores" />
<UsageBar kind="limits" ratio={4.1 / 8} caption="4.1 / 8.00 cores" />
```

### 7.7 页面内容区（已落地于 `AppShell`）

```tsx
<header className="sticky top-0 z-40 flex h-(--header-height) shrink-0 items-center gap-2 border-b bg-background/95 px-4 backdrop-blur" />
<div className="flex flex-1 flex-col gap-4 px-4 py-4 md:gap-6 lg:px-6">{children}</div>
```

顶栏槽位：`SidebarTrigger`、面包屑、搜索、`ShellActions`、主题、用户。模块开关放进 `ShellActions`，不要再做一条顶栏。

---

## 8. 共享组件

目录：`frontend/src/components/ui/`（shadcn 原语）和 `frontend/src/components/console/`（模式）。用法见 [shared-ui-patterns.md](./shared-ui-patterns.md)。

### 8.1 原语

button、input、label、badge、card、table、tabs、dialog、sheet、dropdown-menu、select、popover、tooltip、separator、skeleton、sidebar、breadcrumb、command、sonner、avatar、checkbox、switch、textarea。

`Badge`（已落地）变体是 `default | secondary | outline`：`text-xs rounded-md px-2 py-0.5`。没有 `destructive` 变体。失败态用 `StatusIcon` 或 `Button variant="destructive"`，不要在业务页给 Badge 手写红底。

`Button` 尺寸：`default` 为 `h-9`，`sm` 为 `h-8`，`icon` 为 `size-8`。

### 8.2 模式组件（名字已占用，不要另起同名）

| 组件 | 文件 | 用途 |
|------|------|------|
| `AppShell` | `AppShell.tsx` | inset 侧栏 + sticky 顶栏 + 内容区 |
| `AppSidebar` / `ProductSwitcher` | `AppSidebar.tsx`、`ProductSwitcher.tsx` | 当前产品菜单、产品切换 |
| `PageHeader` | `PageHeader.tsx` | 列表标题 + 描述 + 操作 |
| `DataTable` | `DataTable.tsx` | 紧凑表、工具栏、空态、加载 |
| `StatusIcon` + `status.ts` | `StatusIcon.tsx`、`status.ts` | 全站状态字典 |
| `DetailShell` | `DetailShell.tsx` | 详情标题、操作、Tab、`metrics` 槽 |
| `OverviewMetricCards` / `ResourceOverview` / `OverviewCard` / `InfoGrid` / `InfoPairs` / `ImageList` / `PillList` | `ResourceOverview.tsx` | 概览指标、左右栏、字段、镜像、标签 |
| `LogPanel` / `TerminalChrome` | `LogPanel.tsx` | 日志与终端外壳 |
| `EllipsisCopy` | `EllipsisCopy.tsx` | 单行省略 + 复制 |
| `UsageBar` | `UsageBar.tsx` | Requests / Limits 细用量条。阈值色在组件内 |

`CodePane` 尚未抽取。YAML / 脚本继续用现有 `YamlEditor` 或 `pre` 的 `font-mono text-xs leading-5`。不要为了对齐本文新造一个同职责组件，除非一次改动把这些入口收拢。

业务模块禁止再造侧栏、表格密度或状态色。

---

## 9. 页面模式

### 9.1 壳

左侧 inset 侧栏 + `SidebarInset` + 第 7.7 节的顶栏和内容区。产品（PM、流水线、容器等）在同一壳里切换，不各做一套顶栏皮肤。

### 9.2 列表 → 详情

1. 工具栏 + 筛选 + `DataTable`
2. 行进入详情（`onRowClick` 已忽略链接、按钮和输入框）
3. `DetailShell` + Tab

Tab 内容按模块填，壳不变。例如：容器工作负载用 Overview / Pods / YAML / Logs；流水线运行用 Overview / Logs；PM 事项用 Overview / Activity。概览正文是指标卡加左右栏。

### 9.3 状态

页面从 `resolveStatusTone()` 取七种语义，类名只用 `status.ts` 导出的三张表。概览卡上的圆点与图标、描边同色。

---

## 10. 不要照搬

| 避免 | 原因 |
|------|------|
| Kite 品牌、Logo、产品名 | 商标与产品身份 |
| 官网的 Cormorant / DM Mono、纸色 `#faf9f6`、网格背景 | 营销页。控制台保持第 5 节 token |
| 整站 Maple Mono 当作本仓库默认 | 见第 3.2 节。可选档见第 3.3 节 |
| `display-scale: 95%` 写进默认 `:root` | 系统 sans 档保持 100% |
| 插件联邦、AI 聊天等 kite 产品能力 | 与视觉规范无关 |
| 整文件粘贴 kite 业务页 | 抄模式。大段源码须遵守 Apache-2.0 |
| Tabler、Recharts、第二套组件库 | `components.json` 已锁定 Lucide；图表用 ECharts |
| 模块私有主题色、业务页十六进制 | 破坏全站 token |
| 把字体文件改成 Apache-2.0 | 字体是 SIL OFL 1.1，见 `frontend/src/assets/fonts/ATTRIBUTION.txt` |

kite 仓库代码是 Apache-2.0。可以读、可以按本规范重写。不要把上游源文件整段拷进 `frontend/` 而不保留许可证要求。字体文件已按 OFL 放入仓库，应用代码的许可证不覆盖字体。

---

## 11. 对照清单

| 约定 | 状态 | 落点 |
|------|------|------|
| 系统 sans 正文，Maple Mono 代码区 | 已落地 | `styles/base.css` |
| JetBrains `@font-face` | 已落地，未接开关 | `styles/base.css` |
| 字体档 / display-scale 95% | 待落地，默认关闭 | 无 Provider |
| `--radius: 0.5rem`、冷蓝 hue 235、sidebar token | 已落地 | `styles/themes/default.css` |
| 卡片 `shadow-sm` + 极淡边框 | 已落地 | `components/ui/card.tsx` |
| `tabular-nums` | 已落地 | 侧栏徽标、指标卡数值、`DataTable` 单元格 |
| 指标卡 / 字段名 `text-xs` | 已落地 | `OverviewMetricCards`、`InfoGrid`、`InfoPairs` |
| 侧栏分组与激活态 | 已落地 | `AppSidebar.tsx`、`sidebar.tsx` |
| `DataTable` 密度 | 已落地 | `DataTable.tsx` |
| 状态字典与圆点 | 已落地 | `status.ts`、`StatusIcon.tsx` |
| `AppShell` / `DetailShell` / `LogPanel` | 已落地 | `components/console/` |
| 用量条阈值色 | 已落地 | `UsageBar`；容器请求/上限列画 Requests 条。无容量分母时不画 Limits 条 |
| `CodePane` | 待落地 | 页面暂用 `font-mono text-xs` |
| `Badge` 的 destructive 变体 | 不要求 | 失败态走 `StatusIcon` 或 destructive 按钮 |

铺开进度和延期项以 [frontend-style-unification-plan.md](./frontend-style-unification-plan.md) 为准。本文不重复那份勾选表。

---

## 12. 来源

| 资源 | URL |
|------|-----|
| kite 仓库 | https://github.com/kite-org/kite |
| kite UI | https://github.com/kite-org/kite/tree/main/ui |
| 产品站 | https://kitehq.dev |
| 许可证 | Apache-2.0（代码）；字体 SIL OFL 1.1 |

复核时看过的上游文件：`ui/src/styles/base.css`、`ui/src/styles/themes/default.css`、`ui/src/components/font-provider.tsx`，以及官网主题样式。上游若再改 FontProvider 默认值，以仓库当前文件为准，并升本文版本。

---

*本规范约束 hfwas-devops（及共用该前端体系的产品）的视觉与交互。实现以本文为准；组件 props 与试点页面以 shared-ui-patterns 为准。*
