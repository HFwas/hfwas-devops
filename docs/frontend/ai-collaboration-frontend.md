# AI 协作前端规范

> 日期：2026-10-07
> 版本：v0.3
> 状态：强制。Agent 写前端代码前必须遵守；执行摘要在 `AGENTS.md`「前端」一节。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-01 | 初版：新建前端强制 React 19 + Tailwind v4 + shadcn/ui；既有 `frontend/` 走 Vue + shadcn-vue |
| v0.2 | 2026-10-01 | `frontend/` 破坏性改为 React 轨，取消 Vue 轨道 |
| v0.3 | 2026-10-07 | 新 UI 必须使用全局 CSS 变量与共享 token；主色为冷蓝；禁止模块私有主题 |

---

## 1. 结论

适合和 AI 一起改的前端，组件源码在仓库里，样式是可预测的工具类，类型检查给模型当反馈回路。

| 场景 | 强制栈 |
|------|--------|
| 本仓库 `frontend/` 以及新建前端 | **React 19 + TypeScript + Vite + Tailwind CSS v4 + shadcn/ui** |

后端已经是独立 API（本仓库为 Spring Boot）时，用 Vite SPA。需要 SSR / React Server Components 时，再由用户明确要求后使用 Next.js。

## 2. 依据

2026-10-01 检索 GitHub 与业界生成器（v0、Lovable、Replit、Claude Code）后的收敛结果：

| 仓库 | Star（约） | 作用 |
|------|-----------:|------|
| [shadcn-ui/ui](https://github.com/shadcn-ui/ui) | 125,000 | 组件源码进仓库。CLI v4 面向 coding agent：skills、preset、`info`、MCP |
| [vercel/ai](https://github.com/vercel/ai) | 27,000 | AI SDK，React 优先 |
| [assistant-ui/assistant-ui](https://github.com/assistant-ui/assistant-ui) | 12,400 | 生产级 AI 对话 UI，主题走 shadcn |
| [unovue/shadcn-vue](https://github.com/unovue/shadcn-vue) | 10,700 | 同一设计系统的 Vue 移植，带 skills / MCP |
| [vercel/ai-elements](https://github.com/vercel/ai-elements) | 2,500 | 建在 shadcn 上的对话、推理、工具调用组件 |

同一模式出现在 FastAPI 全栈模板、vue-vben-admin、shadcn-admin、open-saas：后台与 SaaS 脚手架使用 Tailwind + shadcn。Magic UI 是该组合上的动效补充，不另起样式体系。

模型写得准，靠三件事：公开语料里 React + Tailwind 密度最高；shadcn 把组件放进仓库，没有黑盒运行时；Tailwind 类名是有限词表，主题落在 CSS 变量上。

## 3. 新建前端（默认）

### 3.1 技术栈

```text
React 19 + TypeScript + Vite
  Tailwind CSS v4（@theme、OKLCH CSS 变量、class 暗色模式）
  shadcn/ui（源码在 src/components/ui，Radix 或 Base UI）
  TanStack Query
  Zod
  Lucide
  需要 AI 界面时：Vercel AI SDK + AI Elements（或 assistant-ui）
```

表单用 React Hook Form + Zod。客户端全局状态优先少放；需要时用 Zustand。路由用 TanStack Router，或与 Vite 官方模板一致的 React Router，一个应用只选一种。

### 3.2 必须遵守

1. 用 `npx shadcn@latest init` 初始化，组件用 `npx shadcn@latest add <name>` 安装。安装后的源码提交进仓库，Agent 直接改这些文件。
2. 保留 `components.json`。改组件前先读它，确认别名、图标库、已安装组件和 Tailwind 版本。
3. 样式只写 Tailwind 工具类，颜色、圆角、字体、暗色只改全局 CSS 变量（`@theme` / `:root` / `.dark`）。业务组件引用 `bg-background`、`text-foreground`、`bg-primary` 这类语义色，不写死十六进制颜色。
4. 新 UI 必须使用全站共享 token，禁止为单个模块另起 `:root`、私有品牌色或第二套主题。主色是冷蓝：亮色 `--primary: oklch(0.55 0.22 235)`，暗色 `--primary: oklch(0.65 0.18 235)`，`--ring` 同色相。基准圆角 `--radius: 0.5rem`。规范见 `docs/frontend/kite-frontend-style-guide.md`。
5. 新按钮、输入框、对话框、下拉菜单从 `src/components/ui` 组合而来。禁止平行手写一套同名基础组件。
6. 图标只用 Lucide（与 `components.json` 的 icon library 一致）。
7. 接口数据用 TanStack Query。校验与表单 schema 用 Zod。
8. TypeScript 打开严格模式。生成代码后以类型检查结果为准再改。
9. 仓库内放置 `components.json`；若使用 Cursor / Claude，安装官方 shadcn skill，使 Agent 读取项目里的真实组件而不是猜 API。

### 3.3 目录

```text
src/
  components/ui/            # shadcn 组件，可改
  components/               # 业务组件，只组合 ui/
  lib/utils.ts              # cn()
  index.css                 # Tailwind 入口，引入 base 与默认主题
  styles/base.css           # 圆角、字体、顶栏高度、@theme 映射
  styles/themes/default.css # 亮/暗语义色（含 sidebar、冷蓝 primary）
components.json
```

路径别名保持 `@/`。

## 4. `frontend/`

`frontend/` 只使用第 3 节的 React 栈。禁止再引入 Vue、Pinia、Vue Router、naive-ui、shadcn-vue。

路由用 React Router。服务端状态用 TanStack Query。需要跨页面的客户端状态时用 Zustand。接口层继续放在各模块的 `api/` 与 `types/`。

## 5. 禁止

- 禁止 Vue，以及 Element Plus、Ant Design、naive-ui 等封闭组件库。
- 禁止 CSS-in-JS（styled-components、Emotion）和除 Tailwind 以外的第二套原子化 CSS。
- 禁止把基础组件封进私有运行时黑盒。视觉定制改仓库里的组件源码和 CSS 变量。
- 禁止在业务代码里堆裸十六进制颜色和各写各的间距。先用全局主题 token。
- 禁止模块私有主题（独立 `--primary`、硬编码品牌色、第二套 `:root`）。全站只消费共享 token，主色为冷蓝。
- 交付运维平台是独立产品，界面禁止落到本仓库 `frontend/`（见 `AGENTS.md`）。

## 6. Agent 动手前

1. 读 `frontend/components.json`，确认别名、图标库和已安装组件。
2. 缺基础组件时用 `npx shadcn@latest add <name>`，再在业务组件里组合。
3. 改完跑 `tsc --noEmit`。
