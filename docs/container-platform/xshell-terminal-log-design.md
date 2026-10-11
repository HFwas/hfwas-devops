# 终端 / 日志界面设计方案（参考 Xshell）

> 日期：2026-10-11  
> 状态：待评审  
> 版本：v0.2  
> 适用仓库：hfwas-devops  
> 关联：`docs/frontend/shared-ui-patterns.md`、`docs/container-platform/pod-console-upload-download-design.md`、`docs/frontend/kite-frontend-style-guide.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-11 | 初版：对照现状与 Xshell，统一终端/日志表面、工具栏、多会话与分期 |
| v0.2 | 2026-10-11 | 拍板：默认深色；多会话 Tab→P2；ERROR/WARN 可开关默认开；落库 container-platform |

---

## 1. 背景与目标

### 1.1 诉求

容器、流水线、Helm、API 调试等页面都有「像终端一样看输出 / 进容器敲命令」的需求。希望视觉和操作接近 **Xshell**：深色会话区、清晰工具栏、可搜索滚动缓冲、连接状态明确，而不是一块普通白底 `pre`。

### 1.2 目标

1. **统一表面**：交互终端与只读日志共用一套「会话壳」（外观、字体、连接态、工具栏槽位），协议仍分业务组件实现。
2. **Xshell 级常用能力**：清屏、复制、搜索、字号、自动滚底 / 锁定、下载全文、关键字高亮（分期落地）。
3. **不破坏现有协议**：Pod Shell / Pod Logs WebSocket、流水线静态 `logText`、上传下载 REST 保持兼容；先改壳与交互，再增强能力。
4. **与 kite 控制台一致**：仍挂在 `DetailShell` 的 Logs / Terminal Tab 或底栏面板里，主色与 AppShell 对齐；**会话内容区**允许独立深色主题（Xshell 习惯）。

### 1.3 非目标（本期不做）

- 完整复刻 Xshell 的会话管理器、隧道、密钥管理、本地 Shell。
- 浏览器内嵌完整 SFTP 树（可沿用已有上传/下载；文件浏览器另立项）。
- 替换 Fabric8 exec / 日志流后端协议。

---

## 2. 现状盘点

| 场景 | 组件 | 呈现 | 能力缺口（相对 Xshell） |
|------|------|------|-------------------------|
| Pod 终端 | `PodShell` + `TerminalChrome` + xterm.js | 浅色面板内嵌 xterm，Maple Mono | 无清屏/搜索/字号/多 Tab；工具栏混入路径+上传下载；断线后需切容器重挂 |
| Pod 日志 | `PodLogPanel` + `LogPanel` | REST 拉 tail + WS follow，`<pre>` 拼接 | 无搜索/高亮/下载；follow 关开粗暴；浅色等宽区 |
| 流水线运行 / Helm / API 调试 | 各自塞进 `LogPanel` | 多为静态文本 | 工具栏不统一；无「会话」感 |
| 共享壳 | `LogPanel` / `TerminalChrome` | 40vh、可拖高、顶栏连接灯 | 仅外壳；无标准工具栏语义；内容区未规定深色主题 |
| 上传下载 | `PodShell` 工具栏 + 既有设计文档 | 路径 Input + 按钮 | 与「终端会话动作」挤在同一行，信息架构乱 |

结论：协议和上传下载已有基础；缺的是 **Xshell 式会话 UI 规范** 和 **只读日志与交互终端的统一体验**。

---

## 3. 从 Xshell 抽取的可借鉴点

| Xshell 能力 | 是否纳入本方案 | 落点 |
|-------------|----------------|------|
| 深色终端内容区 + 等宽字体 | 是（P0） | `TerminalSurface` 内容主题 |
| 标题栏：会话名 / 连接状态 | 是（P0） | 扩展现有 `ConnectionIndicator` |
| 工具栏：清屏、复制、查找、字号、重连 | 是（P0–P1） | 标准 `SessionToolbar` |
| 滚动缓冲 + 搜索 | 是（P1） | xterm SearchAddon / 日志虚拟列表搜索 |
| 自动滚底 / 暂停跟随 | 是（P0 日志，P1 终端） | follow / scroll-lock |
| 多会话 Tab（多容器 / 多 Pod） | 是（P2） | Session Tabs |
| 右键菜单：复制/粘贴/清屏/查找 | 是（P1） | xterm 右键 / 日志选区 |
| 关键字着色（ERROR/WARN） | 是（P1 日志） | 行级高亮规则 |
| 另存为日志文件 | 是（P1） | 下载 `.log` |
| 快捷命令栏 | 可选（P3） | 预置命令芯片 |
| SFTP / 本地文件夹 | 部分（已有上传下载，P2 增强） | 对齐既有 upload/download 设计 |
| 配色方案切换 | 可选（P2） | 预设 2–3 套，默认 Dark+ |

不借鉴：Xshell 本地主机管理、跳板机、串口等与 K8s 控制台无关的能力。

---

## 4. 信息架构

### 4.1 两种会话模式

```
TerminalSurface
├── mode: interactive   → Pod 进入容器（xterm + WS shell）
└── mode: stream        → 日志跟随 / 静态日志（只读）
```

同一外壳，不同内容适配器：

| mode | 内容实现 | 典型入口 |
|------|----------|----------|
| `interactive` | xterm.js + FitAddon（+ 后续 SearchAddon） | Pod「终端」Tab |
| `stream` | 虚拟化只读行列表（首期可仍用 `pre`，P1 换） | Pod「日志」、流水线任务日志、Helm 日志、调试响应 |

### 4.2 布局（单会话）

```
┌─ SessionHeader ─────────────────────────────────────────┐
│ 标题（Pod/任务） · 容器下拉 · ●已连接 · [标准工具栏…]     │
├─ SessionBody（深色、等宽、可滚动）───────────────────────┤
│                                                         │
│   输出 / TTY                                             │
│                                                         │
├─ SessionFooter（可选）──────────────────────────────────┤
│ 行列 · 编码 UTF-8 · 已缓冲 n 行 · 路径快捷（仅 interactive）│
└─ 拖拽调高条 ────────────────────────────────────────────┘
```

详情页内：继续作为 `DetailShell` 的 Tab 内容或底栏，默认高度约 `40vh`，可拖，与现 `LogPanel` 一致。

### 4.3 工具栏分组（对齐 Xshell，避免「一个大杂烩」）

| 分组 | 动作 | interactive | stream |
|------|------|:-----------:|:------:|
| 会话 | 重连 / 断开 | ✓ | follow 开/关 |
| 视图 | 清屏、字号 +/-、自动滚底 | ✓ | ✓ |
| 编辑 | 复制选区、全选、查找 | ✓ | ✓ |
| 导出 | 下载当前缓冲 | ✓（可选） | ✓ |
| 文件 | 上传 / 下载（容器路径） | ✓ | — |
| 上下文 | 容器选择、previous / timestamps（日志） | ✓ | ✓ |

**文件类**从主工具栏折叠到「文件」菜单或抽屉，避免和清屏/查找抢视线（现状最大问题之一）。

---

## 5. 视觉规范（会话内容区）

在保留全站 kite 冷蓝壳的前提下，**仅 SessionBody** 使用终端主题：

| Token（建议） | 含义 | 默认 Dark+ |
|---------------|------|------------|
| `--term-bg` | 内容底 | `#0c0c0c` 或 `oklch(0.18 0.01 235)` |
| `--term-fg` | 正文 | `#cccccc` |
| `--term-dim` | 次要 | `#6a6a6a` |
| `--term-accent` | 选中/查找 | 冷蓝 accent |
| `--term-danger` | ERROR 高亮 | red-400 |
| `--term-warn` | WARN | yellow-400 |
| `--term-font` | 字体 | 现有 `--font-mono`（Maple Mono） |
| `--term-size` | 字号 | 13（12–16 可调） |

Header / Footer 用 `bg-muted/50` 与现 `LogPanel` 一致，保证嵌在详情页不突兀。  
xterm `theme` 与 stream 行样式共用同一套 CSS 变量。

连接指示沿用现有语义：未连接 / 连接中（黄脉冲）/ 已连接（绿脉冲）/ 已断开（红）。

---

## 6. 交互细节

### 6.1 交互终端（PodShell）

1. 进入 Tab 即连（保持现状）；容器切换 = 断旧连新，并提示「已切换容器」。
2. 断线：内容区保留缓冲；工具栏「重连」；自动重连可选（默认关，避免狂连）。
3. 清屏：只清本地 xterm 缓冲，不影响容器进程。
4. 查找：P1 用 `@xterm/addon-search`；快捷键 Ctrl/Cmd+F 聚焦查找条。
5. 字号：写入 localStorage `term.fontSize`，全局会话壳共用。
6. 上传下载：按 `pod-console-upload-download-design.md`；入口收纳到「文件」，路径默认 `/tmp`，成功后 toast，不在终端里刷协议噪音。

### 6.2 日志流（PodLogPanel 等）

1. 首屏：REST `tailLines`（可配置 200/500/1000）+ 可选 WS follow。
2. **跟随**：开 = 自动滚底；用户上滚超过阈值 → 自动暂停跟随并显示「有新日志 · 回到底部」（Xshell/IDE 常见）。
3. **查找**：过滤或高亮当前缓冲（P1）；大小写、正则可选（P2）。
4. **高亮规则（可开关，默认开）**：行内匹配 `\bERROR\b` / `\bWARN(ING)?\b` / `\bFATAL\b` 着色，不改原文。
5. **下载**：把当前缓冲存为 `{pod}-{container}-{time}.log`。
6. **时间戳 / previous**：工具栏开关，映射已有或后续 API 参数，不要做假数据。

### 6.3 静态日志（流水线等）

同一 `stream` 壳：无 WebSocket 时连接灯为「未连接」或改为「静态」文案（`idle` 显示「快照」），仍提供查找、下载、字号、清屏（仅清视图）。

---

## 7. 组件改造建议

```
components/console/
  LogPanel.tsx              → 保留为矮层布局（可改名 SessionChrome）
  TerminalSurface.tsx       → 新增：主题 + SessionToolbar 槽 + body
  SessionToolbar.tsx        → 标准动作按钮组
  sessionTheme.ts           → CSS 变量 / xterm theme
modules/container/components/
  PodShell.tsx              → 接入 TerminalSurface(interactive)
  PodLogPanel.tsx           → 接入 TerminalSurface(stream)
```

业务页（Workload 详情、Pipeline 运行、Helm、API 调试）只换壳与工具栏，不改各自数据源。

---

## 8. 分期落地

| 阶段 | 内容 | 验收 |
|------|------|------|
| **P0** | Session 深色内容区；标准工具栏骨架（清屏、字号、跟随/重连、下载日志）；文件入口收纳；Pod 日志/终端 + 流水线一处试点 | 观感接近 Xshell；现有 WS/上传下载不回归 |
| **P1** | 查找、选区复制、日志关键字高亮、上滚暂停跟随、xterm SearchAddon、右键基础菜单 | 大日志可搜；ERROR 可见 |
| **P2** | 多会话 Tab（同 Pod 多容器或「再开一个」）；配色预设；上传下载对话框打磨；日志虚拟列表 | 双容器并行不互相踢线 |
| **P3** | 快捷命令、会话布局记忆、可选「弹出为独立窗」 | 按需 |

建议 **先合设计文档再开 P0 PR**，避免和 Phase 6 画布项抢带宽时可串行。

---

## 9. 与既有文档关系

- **上传/下载**：仍以 `pod-console-upload-download-design.md` 为准；本方案只改入口信息架构（归入「文件」分组）。
- **共享模式**：更新 `shared-ui-patterns.md` 中 `LogPanel`/`TerminalChrome` 一节，指向 `TerminalSurface`。
- **样式**：字体继续 Maple Mono；会话区允许偏离页面浅色底，需在 style guide 记一笔「终端例外」。

---

## 10. 风险与约束

| 风险 | 缓解 |
|------|------|
| 超大日志把 `live` 字符串撑爆内存 | P1 起环形缓冲（如最多 2 万行）+ 虚拟列表 |
| 深色区与浅色详情对比过强 | Header 保持 muted；仅 body 深色；提供浅色终端预设兜底 |
| 多 Tab 多路 WS | 限制同页最多 N 路；切走 Tab 可断开或保活策略写明 |
| 快捷键与浏览器冲突 | Ctrl/Cmd+F 仅在面板 focus 时拦截 |

---

## 11. 已拍板（2026-10-11）

1. **默认主题**：会话内容区默认深色（Xshell 风格）；详情壳仍为浅色控制台。
2. **多会话**：要，同屏多会话 Tab（如同一 Pod 多容器）放进 **P2**；P0/P1 仍用容器下拉。
3. **日志高亮**：ERROR/WARN 着色 **可开关，默认开**；不改原文。
4. **文档落库**：进 `docs/container-platform/`。

---

## 12. 一句话总结

把现在的 `LogPanel`/`PodShell`/`PodLogPanel` 收成 **Xshell 风格的统一会话表面**：深色等宽内容区 + 分组工具栏 + 连接态；交互终端与只读日志共用壳、分模式；先 P0 观感与工具栏，再 P1 搜索/高亮，P2 多会话与文件体验。
