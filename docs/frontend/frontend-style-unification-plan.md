# 前端样式统一落地 Plan（参照 Kite，全产品共用）

> 日期：2026-10-07
> 版本：v0.3
> 关联规范：[kite-frontend-style-guide.md](./kite-frontend-style-guide.md) v0.3
> 目标仓库：[HFwas/hfwas-devops](https://github.com/HFwas/hfwas-devops)（`frontend/`，默认分支 `dev`）
> 目标：全站统一视觉与交互语言（token / 壳层 / 列表 / 详情 / 状态 / 日志终端），各业务模块共用，不限于容器

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-07 | 初版：分阶段里程碑、验收标准、模块铺开顺序与风险 |
| v0.2 | 2026-10-07 | 锁定决策：主色 kite 冷蓝；全站立刻侧栏化；Cloud Agent 按阶段开 PR |
| v0.3 | 2026-10-07 | 入库 `docs/frontend/`；关联规范改为 v0.3；标明 Phase 1 token 落点 |

---

## 1. 背景与目标

### 1.1 现状

- 栈已对齐方向：React 19 + Vite + Tailwind 4 + Radix/shadcn + lucide。
- 视觉与密度不统一：圆角/主色与 kite 不一致；缺完整 sidebar token；各业务页观感偏散。
- 用户要求：**全产品共用一套规范**，以 [kite-org/kite](https://github.com/kite-org/kite) 为主要参照。

### 1.2 成功标准

| # | 标准 |
|---|------|
| S1 | 全站亮/暗主题与 `--radius` / `--primary` / `--sidebar*` 等 token 唯一来源 |
| S2 | 所有一级模块走同一 `AppShell`（侧栏 + sticky 顶栏 + 内容区约定） |
| S3 | 列表/详情/状态色/日志终端使用共享模式组件，无模块私有「平行皮肤」 |
| S4 | 至少覆盖：工作台、用户中心、PM、流水线、容器、API 测试；其余工具页同规范 |
| S5 | 不引入 Vue / 第二套组件库；不整仓拷贝 kite 品牌与插件能力 |

### 1.3 非目标（本 Plan 不做）

- 重写后端或改业务 API
- 替换 ECharts → recharts、替换鉴权/Keycloak 流程
- 一次做完 kite 全部多主题包与插件联邦
- 自动 commit / push（需你明确指令）

---

## 2. 原则

1. **先基建，后铺开**：token → 壳 → 共享模式 → 跨模块试点 → 全量换皮。
2. **学模式不抄仓库**：对齐 token 与交互；业务代码自写；大段粘贴需 Apache-2.0 合规。
3. **主色全站唯一**：冷蓝（kite）或保留品牌紫，二选一后写入规范，模块不得私自改 hue。
4. **增量可回滚**：每阶段独立 PR；先暗色/亮色与壳，再单页，避免巨型 PR。
5. **对照规范文档**：实现以 `kite-frontend-style-guide.md` 为准，本 Plan 管节奏与范围。

---

## 3. 已锁定决策（2026-10-07）

| 项 | 决定 |
|----|------|
| 主色 | 对齐 kite 冷蓝：`--primary: oklch(0.55 0.22 235)`（亮）/ `oklch(0.65 0.18 235)`（暗）；`--ring` 同色相 |
| 壳层范围 | **全站立刻侧栏化**（Phase 2 全站 AppShell inset sidebar） |
| 落地方式 | **Cloud Agent 按阶段开 PR**（基于 `dev`） |

后续阶段以本表为准，不再二次确认除非你改口。

---

## 4. 阶段计划

### Phase 0 — 规范冻结（0.5 天）

| 事项 | 产出 |
|------|------|
| 规范文档入库 | `docs/frontend/kite-frontend-style-guide.md`（或现名） |
| Plan 入库 | `docs/frontend/frontend-style-unification-plan.md` |
| 主色决定写入规范文首 | 更新 v0.x 变更记录 |

**验收：** 文档在 `dev` 可查；团队约定「新 UI 必须走共享组件」。

---

### Phase 1 — Design Token 统一（1–2 天）

| 事项 | 路径 / 动作 |
|------|-------------|
| 整理 CSS 入口 | `frontend/src/index.css` 引入 `styles/base.css`、`styles/themes/default.css` |
| 对齐圆角 | `--radius: 0.5rem` + 派生阶梯 |
| 对齐语义色 | background / foreground / primary / muted / destructive / border / ring |
| 补齐侧栏 token | `--sidebar*` + `@theme` 映射 |
| 暗色 `.dark` | 与亮色成对 |
| 扫一遍硬编码色 | 业务里 `#hex` / 随意 `bg-blue-*` 改为 token 或状态字典 |

**验收：**

- [ ] 切换亮暗，工作台/登录/任一模块背景与边框一致
- [ ] 无第二套「模块级 :root」
- [ ] Story/抽查：Button、Badge、Card 随 token 变化

**风险：** 旧页依赖错误对比度 → 用对比抽查登录态与表格。

---

### Phase 2 — AppShell 全站壳（2–3 天）

| 事项 | 动作 |
|------|------|
| 升级 `AppShell` | 侧栏 inset + sticky 顶栏（高 `3.5rem`、毛玻璃） |
| 导航信息模型 | PM / 流水线 / 容器 / API 测试 / 用户… 分组；激活态统一 |
| 顶栏槽位 | breadcrumb \| 搜索（可先占位）\| 主操作 \| 主题 \| 用户 |
| 内容区约定 | `px-4 lg:px-6`、`gap-4 md:gap-6` |
| 路由接入 | 所有需登录业务路由包在同一壳内；登录页除外 |

**验收：**

- [ ] 从侧栏进入 ≥4 个模块，壳层不错位、不换肤
- [ ] 面包屑与当前路由一致
- [ ] 移动端侧栏可折叠（或达可用最低标准）

---

### Phase 3 — 共享模式组件（2–3 天）

在 `frontend/src/components/`（或 `components/console/`）落地：

| 组件 | 职责 | 参照 kite |
|------|------|-----------|
| `DataTable` | 紧凑表 + toolbar 槽 + 空态/加载 | `resource-table*` |
| `StatusIcon` + `status.ts` | 全站状态字典 | `*-status-icon.tsx` |
| `PageHeader` | 标题/描述/操作 | site header 信息密度 |
| `DetailShell` + Tabs | 详情标题行 + Tab | `resource-detail-shell` |
| `LogPanel` / `TerminalChrome` | 底栏抽屉、呼吸灯、mono | `floating-terminal` 等 |

补齐缺失 shadcn 原语：sidebar、skeleton、sheet、separator、tooltip、responsive-tabs 等。

**验收：**

- [ ] 新页面文档要求：禁止再写私有 Table 皮肤
- [ ] Status 字典被至少 2 个模块引用
- [ ] DetailShell 有简单 demo 或 Story 级用法说明（可写在 `docs/frontend/`）

---

### Phase 4 — 跨模块试点（3–4 天）

每个子系统改 **1 个列表 + 1 个详情（若有）**，验证规范可复用：

| 模块 | 列表试点 | 详情试点 |
|------|----------|----------|
| 工作台 / 入口 | 工作台或首页卡片密度 | — |
| PM | 项目列表或事项列表 | 事项详情（Tab 壳） |
| 流水线 | 流水线列表 | 运行详情（含日志 chrome） |
| 容器 | 集群列表 | 工作负载详情 |
| API 测试 | 定义或集合列表 | 调试工作台布局密度 |

**验收：**

- [ ] 五处列表密度一致（表头 `h-10`、单元格 `p-2`、圆角边框容器）
- [ ] 三处详情共用 DetailShell
- [ ] 流水线日志与容器日志 chrome 同源组件

---

### Phase 5 — 全量铺开（5–8 天，可并行）

按模块清清单（勾选制）：

| 模块 | 列表 | 详情 | 状态色 | 日志/终端/代码 |
|------|------|------|--------|----------------|
| 工作台 | □ | — | □ | — |
| 用户中心 | □ | □ | □ | — |
| PM（看板/设置/工作流…） | □ | □ | □ | — |
| 流水线（凭证/任务市场/依赖…） | □ | □ | □ | □ |
| 容器（节点/Pod/网络/存储/监控/仓库…） | □ | □ | □ | □ |
| API 测试（环境/历史/集合…） | □ | □ | □ | □ |
| 文档生成 / 文件解析 / 图片 | □ | □ | □ | — |

**验收：**

- [ ] 清单全部勾完或明确「延期项」写入规范变更记录
- [ ] 无「旧 Naive/随意间距」残留页（抽检路由表）
- [ ] `npm run build` + 关键路径手测通过

---

### Phase 6 — 收尾与守护（1–2 天）

| 事项 | 动作 |
|------|------|
| 规范升版 | style-guide 根据落地结果升 `v0.3+` |
| AI/协作约束 | 更新 `docs/frontend/ai-collaboration-frontend.md`：强制用共享组件与 token |
| Cursor rules（可选） | `.cursor/rules` 增加「禁止业务页硬编码色 / 禁止私有 Table 皮肤」 |
| 视觉回归清单 | 登录、工作台、PM、流水线、容器、API 测试各 3 个关键屏 |

---

## 5. 建议排期（示意）

| 周次 | 内容 |
|------|------|
| W1 | Phase 0–2（规范 + token + AppShell） |
| W2 | Phase 3–4（共享组件 + 跨模块试点） |
| W3 | Phase 5 前半（PM + 流水线 + 容器铺开） |
| W4 | Phase 5 后半 + Phase 6（其余模块 + 守护） |

单人可串行；有 Cloud Agent 时可 **Phase 5 按模块拆多个 PR 并行**。

---

## 6. PR / 分支策略

| 建议 | 说明 |
|------|------|
| 分支前缀 | `ui/style-phase-N-…` 基于 `dev` |
| 粒度 | 一阶段一 PR；Phase 5 按模块拆 PR |
| 禁止 | 单 PR 同时改 token + 全部业务页 |
| 预览 | 本地 `./scripts/start-frontend.sh` 或 compose；截图贴 PR |

---

## 7. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 主色争议导致返工 | Phase 0 先书面拍板 |
| 壳层改动影响全路由 | 登录/错误页排除；Feature 开关或先暗发壳 |
| 表格抽象不适配复杂页（看板、DAG） | DataTable 只管「表格式列表」；看板/Vue Flow 区只统一外围间距与色 |
| 终端/日志行为回归 | chrome 与数据源解耦；先换皮不改 WS 协议 |
| 范围膨胀 | 非目标清单；多主题/插件不做 |

---

## 8. 执行状态

1. ~~拍板~~ 已完成（冷蓝 + 全站侧栏 + Cloud Agent）。
2. **本轮 PR：** Phase 0（规范 + Plan 入库）+ Phase 1（Design Token，落在 `frontend/src/styles/base.css` 与 `frontend/src/styles/themes/default.css`）。
3. 随后：Phase 2 AppShell 全站侧栏 → Phase 3 共享组件 → …

---

## 9. 相关链接

- 规范：`kite-frontend-style-guide.md`（同目录或本机 `/workspace/docs/`）
- 参照：https://github.com/kite-org/kite/tree/main/ui
- 仓库：https://github.com/HFwas/hfwas-devops
