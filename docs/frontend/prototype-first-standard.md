# 前端原型先行规范

> 日期：2026-10-18
> 版本：v0.2
> 状态：强制。任何新增或改版前端页面，未完成并通过评审的原型不得进入实现阶段。
> 关联：[ai-collaboration-frontend.md](./ai-collaboration-frontend.md) v0.7、[kite-frontend-style-guide.md](./kite-frontend-style-guide.md)、[shared-ui-patterns.md](./shared-ui-patterns.md)、[visual-regression-checklist.md](./visual-regression-checklist.md)、[prototype-contract-template.md](./prototype-contract-template.md)、[产品原型索引](../product/06-prototype/prototype-index.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：调研 v0 / Lovable / Figma Make / OpenUI / html.to.design / Spec Kit / Playwright 视觉回归后，建立「原型规格 → 高保真可点击原型 → 评审冻结 → 实现 → 视觉校验」门禁与交付物标准 |
| v0.2 | 2026-10-18 | 新增原型 / 前端 / 设计系统权责矩阵与实现参照清单；新增 A0–A3 对齐等级、偏差分类模板、视觉验收数据 profile 与共享组件对账要求；补外部参考与落地检查项 |

---

## 1. 结论

前端页面类需求一律走「原型先行」：

```text
功能设计 design.md
  → 原型规格 prototype-spec.json / .md
  → 高保真可点击原型 index.html + 2x 画板
  → 原型评审（产品 / 前端 / 测试）
  → 冻结原型版本
  → 写测试用例与代码
  → 视觉对齐校验
```

三条硬性规则：

1. **没有原型，不写页面代码。** 页面、弹层、空态、错误态都属于原型范围。
2. **原型必须可点击、可截图、可评审。** 只给一张静态图或一段文字描述，不算完成。
3. **实现以冻结原型为准。** 代码与原型不一致时，要么改代码，要么先升版原型并重新评审，不允许静默偏离。

原型的目的不是“画得好看”，而是把**信息结构、交互路径、状态矩阵、异常边界**在写代码前固定下来，让 AI 生成和人工实现都有唯一视觉真相。

### 1.1 原型、前端与设计系统的权责边界

每个维度只能有一个最终依据。原型不可能同时承担视觉稿、交互稿、数据契约和业务规格，必须按下面分权：

| 维度 | 冻结原型负责 | 生产前端负责 | 最终依据 |
|------|--------------|--------------|----------|
| 页面清单、信息架构、页面层级 | 定义 | 实现 | 冻结原型 |
| 布局骨架、间距节奏、区块顺序 | 定义 1440×900 基准 | 实现并扩展响应式 | 原型定基准，前端定断点 |
| 颜色、字体、圆角、阴影、状态色 | 引用同一套 token | 消费同一套 token | 设计系统 token |
| 组件组合方式 | 指定用哪些共享组件 | 落到真实组件 | 原型 + 前端共同 |
| 交互路径、页签、弹层、返回路径 | 定义 | 实现 | 冻结原型 |
| 状态矩阵与状态文案 | 定义有哪些状态 | 实现真实状态 | `design.md` + 原型 |
| 静态文案、列名、按钮文案、空态文案 | 定义 | 实现 | 冻结原型 |
| 业务规则、校验、权限 | 只做演示 | 实现 | `design.md` / API 文档 |
| 数据内容、数据量、真实边界 | 给代表性样例 | 真实数据 | API / 后端 |
| 可访问性、性能、安全、浏览器兼容 | 不负责 | 负责 | 前端工程 |
| 1440×900 之外的响应式 | 只给参考视口 | 负责 | 前端工程 |
| 组件内部 DOM 与实现 | 不负责 | 负责 | 前端 / 组件库 |

一句话：**原型定「长什么样、有哪些状态、怎么走」，前端定「真实数据下怎么正确、稳定、可访问地跑起来」，设计系统定「颜色字体间距」。**

### 1.2 原型不是什么

- **不是生产代码**：不要求可维护、可复用、可访问，也不进入构建产物。
- **不是数据契约**：样例数据只表达形状和量级，不代表后端真实返回。
- **不是像素合同**：除非契约里明确声明 A3 像素对齐，否则不要求逐像素一致。
- **不是组件库**：组件内部实现以 `src/components/ui`、`src/components/console` 为准。
- **不是业务规格的唯一来源**：业务规则、权限、状态机以 `design.md`、`test-cases.md` 和接口文档为准。

### 1.3 实现阶段参照原型什么

**必须一致：**

- 页面组成顺序：头部、页签、指标、表格 / 画布、分页的位置关系。
- 导航与返回路径：入口、页签切换、弹层关闭、来源感知返回。
- 表格列顺序、列含义、列宽比例；表单字段顺序、标签、必填标记。
- 按钮层级和语义：主按钮、次按钮、危险按钮分别对应什么动作。
- 状态语义和文案：成功 / 失败 / 排队 / 待审批 / 取消分别怎么显示。
- 共享组件选型：列表用 `DataTable`，详情用 `DetailShell`，状态用 `StatusIcon`，指标用 `OverviewMetricCards`。
- token 名称与值：颜色、字体、圆角、间距只能来自全局主题。
- 1440×900 参考视口下的布局骨架和间距节奏。

**允许不同，但必须记录：**

- 真实数据值、数量、状态分布。
- 条件渲染导致的显示 / 隐藏（如无 commit 时隐藏「最近克隆」、终态隐藏「取消」、单租户隐藏租户切换）。
- 加载、空态、失败、无权限的真实实现。
- 其他视口下的响应式布局。
- 可访问性增强、性能优化、错误边界。
- 字体抗锯齿、系统字体回退造成的细微渲染差异。
- 原型静态写法与生产共享组件默认值造成的非结构性差异。

**禁止照抄：**

- 原型里的 inline CSS、绝对定位、写死 px。
- 原型的 mock 数据直接变成生产 fixture。
- 原型右下角的导航面板、状态切换参数。
- 原型为了演示伪造的成功态。
- 原型里没有的私有颜色、私有字体、私有表格皮肤。

## 2. 适用范围

### 2.1 必须做原型

- 新增页面、页面级页签、向导、编辑器、看板。
- 新增弹窗、抽屉、确认框、批量操作等交互层。
- 已有页面改版：布局调整、信息层级调整、状态体系调整。
- 跨 3 个以上前端文件的 UI 改动。

### 2.2 可豁免

| 场景 | 要求 |
|------|------|
| 单文件小修：文案、错别字、图标替换 | 豁免，回复中说明理由 |
| 纯样式修复：颜色 token 对齐、间距修正 | 豁免点击原型，但必须在 `changes.md` 记录修改前后截图、对齐等级与偏差分类；不得借豁免修改信息结构或交互 |
| 纯后端 / 配置 / 文档改动 | 豁免 |
| 紧急线上故障修复 | 可先修复，24 小时内补原型或截图记录 |
| 复用既有页面模式、无新交互的新增列表页 | 可只交画板，不要求可点击原型 |

豁免必须在当次回复和 `changes.md` 中写明，不得默认跳过。

## 3. 业界方案调研与取舍

2026-10 检索 GitHub 与业界工具后的结论：**没有单一工具能同时满足“高保真、可评审、可截图、可交给 AI 实现、不绑定 SaaS”**，成熟做法是组合拳。

| 方案 | 代表 | 成熟点 | 局限 | 本规范取舍 |
|------|------|--------|------|-----------|
| Prompt → 应用 | [v0](https://v0.dev)、Lovable、Bolt、Replit | 出图快，直接生成 React/shadcn 代码 | 生成结果即应用，探索成本高；代码质量与可维护性不稳定 | 只用于早期发散，不作为交付原型 |
| 设计工具内 AI | [Figma Make](https://www.figma.com/resource-library/ai-prototyping-tools/)、Google Stitch、Magic Patterns | 设计元数据完整，便于设计团队接手 | 需要多轮迭代；产出的代码硬编码多、复用差 | 设计团队可选；交付仍以仓库内原型为准 |
| 开源生成式 UI | [thesysdev/openui](https://github.com/thesysdev/openui) | 组件契约 + DSL，模型只能组合已注册组件 | 面向运行时生成 UI，不是静态可评审原型 | 借鉴“组件白名单 + schema 约束”思想 |
| HTML → Figma | [html.to.design](https://html.to.design/) | 单文件 HTML 可导入为可编辑图层，支持本地文件与 AI 工具产物 | 自由版有次数限制；复杂定位易失真 | 作为可选的设计交接通道 |
| 规格驱动开发 | [GitHub Spec Kit](https://github.com/github/spec-kit) | spec → plan → tasks → implement，规范先行 | 官方流程**没有原型阶段**，需自行扩展 | 采纳“规格先行”，并补上原型门禁 |
| 视觉回归 | [Playwright `toHaveScreenshot`](https://playwright.dev/docs/test-snapshots)、Chromatic、pixelguard | 基线截图 + diff + 审批 | 像素差异受字体/渲染影响，不能当唯一标准 | 作为“原型 ↔ 实现”对齐的辅助证据 |
| 设计系统约束 | [shadcn/ui](https://github.com/shadcn-ui/ui)、Tailwind v4 `@theme` + OKLCH | 语义 token，组件源码在仓库，AI 可读可改 | 需要团队自律，否则仍会写死颜色 | 强制原型与实现共用同一套 token 名称 |

**采用组合：** 单文件 HTML 可点击原型（主交付）+ 1440×900 @2x 画板（评审与归档）+ `prototype-spec.json`（机器可读真相）+ 可选 html.to.design（Figma 交接）+ Playwright 截图基线（实现校验）。

**不采用：** 把 v0 / Lovable 生成的应用直接当原型；只在 Figma 里维护、仓库里没有可打开的原型；用视频或口头描述代替状态矩阵。

## 4. 原型分级

| 等级 | 名称 | 产物 | 何时要求 |
|------|------|------|----------|
| L0 | 结构原型 | Mermaid / ASCII 流程图 | 仅用于复杂流程早期沟通，**不计入交付** |
| L1 | 高保真画板 | `shots/*.png`，1440×900 @2x | 所有新增/改版页面的最低要求 |
| L2 | 高保真可点击原型 | 单文件 `index.html` + hash 路由 + 状态参数 | **默认强制**：多页面、多状态、含弹层或流程跳转 |
| L3 | 生产级原型 | 直接复用 `frontend/src/components` 的 Storybook / 沙箱页 | 设计系统级改动、组件库升级 |

判定规则：只要涉及“页面之间跳转”“弹层”“空态/错误态/加载态”“批量操作”中任意一项，就必须做到 L2。只做 L1 时，评审需明确记录理由。

## 5. 交付物与目录

### 5.1 功能级原型

随功能三份文档放在同一功能目录：

```text
docs/superpowers/features/YYYYMMDD-<feature>/
├── design.md
├── test-cases.md
├── changes.md
└── prototype/
    ├── index.html                 # L2 单文件可点击原型
    ├── README.md                  # 页面清单、路由、状态参数、画板清单、评审记录
    ├── prototype-spec.json        # 机器可读规格（唯一事实源）
    ├── prototype-spec.md          # 人读规格
    ├── shots/                     # 1440×900 @2x 画板
    │   ├── 01-<page>.png
    │   └── 15-flow-map.svg        # 交互流程总览
    └── flows/                     # 可选：复杂流程单独拆图
```

### 5.2 模块级原型

模块整体改版或需要长期维护的基线原型，放 `docs/product/06-prototype/<module>/`，并在功能目录的 `prototype/README.md` 中引用。既有流水线原型保留在 `docs/pipeline/prototype/`，不强制迁移，新增模块统一使用新路径。

### 5.3 命名

- 页面画板：`NN-<page>[-<state>].png`，两位序号。
- Frame 名：`P01_PipelineList_Desktop`、`O01_RunParams_Dialog`（P=页面，O=弹层）。
- 版本：原型 README 与 `prototype-spec.json` 的 `meta.version` 同步递增，冻结版本写入 `changes.md`。

## 6. 原型技术要求

### 6.1 形式

1. **单文件 HTML。** CSS 与 JS 内联，图标用内联 SVG，图片用 data URI；禁止 CDN、外部字体、外部图片和网络请求。双击即可打开。
2. **零构建、零依赖。** 不引入 React/Vue/组件库；原型是“可丢弃的规格”，不是生产代码。
3. **hash 路由。** `#/list`、`#/detail/1`、`#/editor/new`；页面与生产路由建立一一对应表。
4. **状态参数化。** 用查询参数切换状态，保证每个状态都能独立截图：

   | 参数 | 示例 | 作用 |
   |------|------|------|
   | `state` | `empty` / `loading` / `error` / `invalid` / `waiting` / `failed` / `succeeded` | 页面状态 |
   | `overlay` | `delete` / `cancel` / `run-params` / `log` | 直接打开弹层 |
   | `panel` | `add` | 侧边面板 |
   | `focus` | `overall` / `stage` / `job` | 抽屉焦点 |
   | `zoom` | `75` / `100` / `125` / `150` | 画布缩放 |
   | `capture` | `1` | 隐藏原型导航面板，供截图使用 |

5. **原型标识。** 页面角落固定显示「原型 vX.Y · 非生产环境」，`capture=1` 时可隐藏，避免被误认为线上系统。

### 6.2 视觉

1. **共用设计 token。** 原型 `:root` / `.dark` 中的变量名必须与实现一致：`--background`、`--foreground`、`--card`、`--primary`、`--muted`、`--muted-foreground`、`--border`、`--destructive`、`--radius` 等；颜色用 OKLCH。
2. **禁止硬编码。** 业务界面不写 `#hex`、`rgb()` 或 `bg-blue-500` 一类非语义色阶；主色、状态色、终端色都走 token。
3. **栅格与尺寸。** 桌面基准 1440×900；侧栏、顶栏、内容区宽度与生产壳一致；圆角 `--radius: 0.5rem`。
4. **字体。** 正文系统 sans，数字 `tabular-nums`，标识与日志用等宽字体；原型不得引入新字体文件。
5. **亮暗双主题。** 至少主流程页面提供暗色变量；评审时抽一屏切换验证。
6. **真实数据。** 使用中文界面文案和接近真实的名称、编号、时间、长文本；禁止 `Lorem ipsum`、`test1`、`123`。

### 6.3 必须覆盖的状态矩阵

每个页面至少检查下列状态，适用的必须在原型中可到达：

| 状态 | 要求 |
|------|------|
| 默认 | 有数据、分页正常、操作可用 |
| 空态 | 无数据文案 + 主行动按钮 |
| 加载中 | 骨架屏或明确的加载文案 |
| 失败 | 错误文案 + 重试入口；区分网络错误与业务错误 |
| 无权限 | 只读或禁止访问的展示 |
| 表单校验失败 | 字段级错误 + 汇总错误 |
| 大数据量 | 长列表、分页边界、数字位数变化时列宽稳定 |
| 长文本 | 超长名称、分支名、错误信息换行或省略规则 |
| 危险操作 | 二次确认弹层，按钮文案明确后果 |
| 窄屏 | 至少验证 1280 宽度；产品要求移动端时补 390 宽度 |

状态覆盖写进 `prototype-spec.json` 的 `acceptance`，测试用例逐条引用。

### 6.4 可访问性与可导入性

1. 可交互元素用真实 `button` / `a` / `input`，支持 Tab 焦点与 Esc 关闭弹层。
2. 关键容器加 `aria-label` 或 `data-layer`，便于 html.to.design 生成可读图层名。
3. 布局优先 Flexbox / Grid，避免绝对定位堆叠，保证导入 Figma 后仍是可编辑结构。
4. 文本对比度满足 WCAG AA；状态不能只靠颜色区分，需同时有图标或文案。

### 6.5 确定性

- 关闭动画与过渡，或提供 `?capture=1` 时禁用。
- 时间、随机数、相对时间全部固定，保证同一 URL 每次截图一致。
- 截图脚本与画板清单写入 `prototype/README.md`。

## 7. 原型规格（prototype-spec.json）

`prototype-spec.json` 是原型与实现的唯一事实源，`prototype-spec.md` 是它的人读版本。最小结构：

```json
{
  "meta": {
    "name": "pipeline-management",
    "version": "1.0",
    "date": "2026-10-17",
    "viewport": { "width": 1440, "height": 900, "scale": 2 },
    "source": ["docs/superpowers/features/.../design.md"]
  },
  "tokens": {
    "color": { "background": "oklch(...)", "primary": "oklch(...)" },
    "radius": { "md": "0.5rem" },
    "font": { "sans": "system-ui", "mono": "Maple Mono" },
    "layout": { "sidebarWidth": "16rem", "headerHeight": "3.5rem" }
  },
  "shell": { "sidebar": {}, "header": {}, "breadcrumbs": [] },
  "pages": [
    { "id": "P01", "name": "流水线列表", "route": "/pipeline/pipelines", "hash": "#/list", "states": ["default", "empty"] }
  ],
  "overlays": [
    { "id": "O01", "name": "运行参数弹窗", "trigger": "P01.运行", "states": ["default", "invalid"] }
  ],
  "components": [
    { "name": "DataTable", "shared": "components/console/DataTable", "usage": ["P01", "P04"] }
  ],
  "navigation": [
    { "from": "P01", "action": "新建流水线", "to": "P03" }
  ],
  "statusDictionary": { "runStatus": [] },
  "acceptance": [
    { "id": "AC-01", "page": "P01", "state": "empty", "criterion": "无数据时显示空态与新建入口" }
  ]
}
```

约束：

- `pages[].route` 必须与 `frontend/src/router.tsx` 最终路由一致；原型阶段可标记 `"routeStatus": "planned"`。
- `components[]` 必须优先映射到 `src/components/ui` 与 `src/components/console` 的既有组件。
- 每条验收标准（AC）必须映射到具体页面与状态；无映射的 AC 视为未设计。
- 原型迭代必须先改 spec，再改 HTML 与画板，三者版本一致。

## 8. AI 生成原型流程

### 8.1 步骤

1. **读输入。** 读 `design.md`、功能清单、相关接口文档、`ai-collaboration-frontend.md`、`kite-frontend-style-guide.md` 和既有共享组件。
2. **写规格。** 先产出 `prototype-spec.md` 与 `prototype-spec.json`：页面、弹层、状态、导航、token、AC 映射。此步不写 HTML。
3. **生成 HTML。** 按 spec 一次性生成 `index.html`，只使用内联资源与既有 token。
4. **逐状态补齐。** 用 `state` / `overlay` 参数把状态矩阵补全，确保每个状态可独立打开。
5. **截图。** 用 Chrome headless 输出 2x 画板，检查是否有溢出、错位、字体回退。
6. **自检。** 对照第 10 节清单逐条勾选，修正后升 `meta.version`。
7. **评审。** 提交原型入口、画板、spec 和评审记录，等待通过。

### 8.2 提示词骨架

```text
你是本仓库的前端原型生成器。只产出单文件 HTML 原型，不写生产代码。

输入：
- 功能设计：<design.md 路径>
- 原型规格：<prototype-spec.json 路径>
- 视觉规范：docs/frontend/kite-frontend-style-guide.md
- 共享组件：frontend/src/components/console/

硬性约束：
1. 单文件，CSS/JS 内联，图标内联 SVG，禁止任何外部请求与 CDN。
2. hash 路由 + state/overlay/panel/capture 查询参数。
3. 只用语义 token（--background/--primary/--muted/--border/--destructive），禁止 hex 与色阶类。
4. 覆盖 default/empty/loading/error/invalid/permission/dense 状态。
5. 中文真实感数据；日期固定；关闭动画。
6. 1440×900 桌面布局，与生产壳的侧栏/顶栏尺寸一致。

输出：
- prototype/index.html
- prototype/README.md（路由、参数、画板清单、评审记录）
- 更新 prototype-spec.json 的 pages/overlays/acceptance
- 列出所有 ASSUMPTION（规格未覆盖、由你推断的点）
```

### 8.3 迭代规则

- 每轮迭代只针对评审提出的问题，不做顺手重设计。
- 任何业务规则推断必须标 `ASSUMPTION`，由产品确认后写回 spec。
- 视觉保真不达标时优先修 token、间距、层级，不要靠加装饰掩盖。
- 单页迭代超过 3 轮仍不达标，退回规格层修改，不要继续堆样式。

### 8.4 截图命令

```bash
cd docs/superpowers/features/YYYYMMDD-<feature>/prototype
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BASE="file://$PWD/index.html"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=1440,900 --virtual-time-budget=3000 \
  --screenshot=shots/01-list.png "$BASE?capture=1#/list"
```

每个画板对应一组 `?state=` / `?overlay=` / hash，清单写入 README。

## 9. 评审与冻结

### 9.1 评审角色

| 角色 | 关注点 |
|------|--------|
| 产品 | 信息结构、业务流程、文案、权限与异常是否符合需求 |
| 前端 | 布局可实现性、组件复用、token 一致性、路由映射 |
| 测试 | 状态矩阵是否可覆盖、验收标准是否可验证 |
| 设计（如有） | 视觉层级、间距、亮暗主题、导入 Figma 的质量 |

### 9.2 评审清单

- [ ] 页面、弹层、状态与功能设计一一对应，无遗漏、无多余。
- [ ] 主流程可从入口点到终态，返回路径清晰。
- [ ] 空态、加载、失败、无权限、校验失败全部可到达。
- [ ] 危险操作有二次确认，按钮文案说明后果。
- [ ] 长文本、长数字、分页边界不破坏布局。
- [ ] 使用共享 token，没有 hex、色阶类和私有主题。
- [ ] 路由与 `frontend/src/router.tsx` 的规划一致。
- [ ] 每条 AC 都能在 `acceptance` 中找到对应页面与状态。
- [ ] 画板齐全，文件名与 Frame 名符合第 5.3 节。
- [ ] 亮暗主题各抽查一屏。

### 9.3 冻结记录

在 `prototype/README.md` 记录：

| 版本 | 日期 | 评审人 | 结论 | 冻结提交 | 备注 |
|------|------|--------|------|----------|------|
| 1.0 | 2026-10-17 | 产品/前端/测试 | 通过 | `<commit>` | 进入实现 |

冻结后：

- 原型目录只允许通过升版修改，修改必须重新评审。
- `changes.md` 记录实现所依据的原型版本。
- 实现阶段发现的规格问题，先改原型与 spec，再改代码。
- **禁止追认式修改**：不得在实现完成后直接改原型去“对齐”代码而不升版、不评审；这类修改视为原型过期，必须补评审记录。
- 冻结版本与实现采用的契约写入功能目录的 `prototype-contract.md`（模板见 [prototype-contract-template.md](./prototype-contract-template.md)）。

## 10. 实现阶段对齐

### 10.0 对齐等级（A0–A3）

实现前必须在原型契约中选定对齐等级，未声明时按 **A2** 处理。

| 等级 | 名称 | 要求 | 适用 |
|------|------|------|------|
| A0 | 结构对齐 | 页面、状态、路径一致 | 内部工具、快速交付 |
| A1 | 语义对齐 | 组件、token、信息层级一致 | 大多数业务页面 |
| A2 | 布局对齐 | 1440×900 下结构、间距、列宽、字号一致；允许字体渲染差异 | **默认** |
| A3 | 像素对齐 | 固定环境下截图 diff 在阈值内，动态区域 mask | 品牌页、设计系统、重点演示 |

对齐等级写入 `prototype-spec.json`：

```json
"contract": {
  "alignmentLevel": "A2",
  "referenceViewport": { "width": 1440, "height": 900, "scale": 2 },
  "mustMatch": ["layout", "spacing", "typography", "component", "copy", "state"],
  "mayDiffer": ["dynamicData", "conditionalVisibility", "responsive", "fontRendering"],
  "dataProfile": "pipeline-visual-fixtures-v1"
}
```

规则：

- 降低对齐等级必须由产品与前端确认，并写进 `changes.md`。
- A3 的截图基线必须用固定环境生成，跨 OS / 浏览器差异通过 Docker 固定镜像消除。
- 对齐等级只约束视觉与结构，不降低功能、状态和可访问性要求。

### 10.1 映射表

`changes.md` 必须包含「原型 → 实现」映射：

| 原型 | 路由 | 实现文件 | 共享组件 | 状态 |
|------|------|----------|----------|------|
| P01 列表 | `/pipeline/pipelines` | `PipelineListPage.tsx` | `PageHeader`、`DataTable` | 已实现 |
| O01 运行参数 | 叠加 | `StartRunButton.tsx` | `Dialog` | 已实现 |

### 10.2 Token 与组件

- 原型 CSS 变量名 = 实现 CSS 变量名；实现中不得新增同名不同值的私有变量。
- 原型里的每个控件必须落到 `src/components/ui` 或 `src/components/console` 的既有组件；找不到时先补共享组件，再写页面。
- 原型里的状态色必须映射到 `StatusIcon` / `StatusBadge` 的语义状态，不新增颜色。

**共享组件对账必须在写页面前完成。** 逐项列出原型控件与共享组件的默认行为差异，并记录决策：

| 原型控件 | 共享组件 | 默认行为差异 | 决策 |
|----------|----------|--------------|------|
| 详情指标 5 列 | `OverviewMetricCards` | 默认 `xl:grid-cols-6`，5 张卡留空一列 | 增加 `columns` 参数，页面传 5 |
| 单页分页 | `Pagination` | 单页只显示文字 | 保持全局行为，记录为允许差异 |
| 列表运行按钮 | `StartRunButton` | 使用 default / outline variant | 按原型改 variant，补其他页面回归 |

冲突处理优先级：**复用现有组件 → 给共享组件增加参数 → 页面级覆盖 → 新增共享组件**。禁止在业务页复制私有表格皮肤、私有状态色或第二套弹窗。

### 10.3 视觉校验

视觉校验分两个目标，不要混为一谈：

1. **设计验收（一次性）**：真实应用截图 ↔ 冻结原型画板。确认结构、间距、层级、token、状态文案。差异按 §10.5 分类记录。
2. **视觉回归（持续）**：真实应用截图 ↔ 自身基线。防止后续改动悄悄漂移。

执行要求：

1. 用相同 URL 参数、视口（默认 1440×900 @2x）和固定环境，对实现页面截图。
2. 与原型画板并排比较：结构、间距、层级、token、状态文案。
3. Pixel diff 只作为线索，不作为唯一判据；字体渲染差异可接受，布局错位不可接受。
4. 使用 Playwright `toHaveScreenshot()` 建立基线：首次生成基线，人工审批后提交；后续变更走 diff → 审批 → 更新基线。
5. 基线生成与比较必须在同一环境：优先使用固定版本的 Playwright Docker 镜像，避免 macOS / Linux 字体与抗锯齿差异。
6. 截图配置固定：`animations: 'disabled'`、`caret: 'hide'`、`scale: 'css'`，对时间、头像、实时数据等动态区域使用 `mask`。
7. 差异必须记录在 `changes.md` 的「计划外偏差」：原因、影响、是否回写原型；同时写入原型契约的偏差登记。

### 10.4 独立 agent 校验

沿用 `AGENTS.md` 的独立 agent 校验，并增加一项：

- 对照冻结原型逐页、逐状态检查实现，列出漏做、多做、行为不符的地方。
- 按 `test-cases.md` 执行用例。
- 校验结论写回 `changes.md` 的「校验结果」。未重新校验不得声称完成。

### 10.5 偏差分类与记录模板

任何「原型 ↔ 实现」差异必须先分类，再决定处理方式。禁止用一句“有点不一样”带过。

| 分类 | 含义 | 处理 |
|------|------|------|
| `实现漂移` | 实现没有按冻结原型做，或共享组件默认值未对齐 | 改实现；改共享组件需补测试与其他页面回归 |
| `数据导致` | 真实数据、数量、状态、条件渲染造成 | 不改代码；补 fixture 与真实状态用例 |
| `原型过期` | 原型已被实现或业务演进淘汰 | 升版原型、重新评审，再同步 spec |
| `产品决策` | 有意偏离原型 | 记录决策人、理由与生效版本 |
| `环境差异` | 字体渲染、抗锯齿、浏览器差异 | 不作为失败条件；A3 用固定环境消除 |

记录模板：

```text
偏差：<一句话描述看到的差异>
位置：<页面 / 状态 / URL 或画板>
分类：实现漂移 / 数据导致 / 原型过期 / 产品决策 / 环境差异
原因：<定位到的具体原因，不写猜测>
处理：<改代码 / 改组件 / 升原型 / 保持>
是否回写原型：<是 / 否 / 不适用>
验证：<截图路径、测试用例编号或命令>
```

### 10.6 视觉验收数据 profile

视觉对比不能拿生产数据碰运气，必须定义一组确定性的验收数据：

| 状态 | 数据要求 | 截图处理 |
|------|----------|----------|
| 默认 | 每条页面至少 1 条完整数据 | 正常截图 |
| 空态 | 0 条数据 | 正常截图 |
| 加载 | 延迟接口或骨架屏 | 正常截图 |
| 失败 | 业务失败与网络失败各 1 条 | 正常截图 |
| 无权限 | 只读账号或禁用入口 | 正常截图 |
| 长文本 | 超长名称、分支、错误信息 | 验证换行 / 省略 |
| 大数据量 | 分页边界、最大位数 | 验证列宽稳定 |
| 终态 | 成功、失败、取消、待审批 | 验证条件按钮与状态文案 |

要求：

- 每个功能在原型契约中写明 `dataProfile` 名称与 fixture / seed 位置。
- 动态时间、头像、实时日志、随机数在截图中 mask，或使用固定值。
- 生产数据不得作为视觉基线；需要真实数据时使用脱敏 fixture。
- 状态覆盖与 `test-cases.md` 的用例编号一一对应。

## 11. Figma 交接（可选）

需要设计团队接手时：

1. 启动本地静态服务：`python3 -m http.server 8099`。
2. Figma 安装 **html.to.design**，用 File tab 导入 `index.html`，或 URL tab 导入 `http://localhost:8099/index.html#/list`。
3. 每个 hash / 状态参数导入一次，Frame 按第 5.3 节命名。
4. 也可直接把 `shots/*.png` 拖入 Figma 作为位图基线；流程总览优先用 SVG。
5. Figma 中的修改必须回写仓库原型与 spec，否则以仓库版本为准。

## 12. 禁止事项

- 禁止跳过原型直接写页面，或“先写代码再补原型图”。
- 禁止只交静态图、视频、口头描述，没有可点击原型和状态参数。
- 禁止原型引入生产组件库、构建工具或外部 CDN。
- 禁止原型使用与生产无关的颜色、字体、圆角，形成第二套视觉体系。
- 禁止遗漏空态、错误态、无权限、校验失败等负向路径。
- 禁止用伪造的成功态掩盖未定的业务流程；未定项标 `ASSUMPTION`。
- 禁止在原型中放入真实客户数据、密钥、内网地址。
- 禁止实现阶段静默偏离冻结原型；偏离必须记录并回写。
- 禁止实现完成后追认式修改原型去对齐代码而不升版、不评审。
- 禁止把原型 mock 数据当生产 fixture 或数据契约。
- 禁止用“纯样式豁免”修改信息结构、交互路径或状态矩阵。
- 禁止把 v0 / Lovable 等生成的应用直接当交付原型。

## 13. 落地检查清单

写页面代码前，逐条确认：

- [ ] `prototype-spec.json` 与 `.md` 已存在，版本一致。
- [ ] `index.html` 可双击打开，无外部依赖。
- [ ] 所有页面、弹层、状态可通过 URL 直达。
- [ ] `shots/` 画板齐全，1440×900 @2x，命名规范。
- [ ] 状态矩阵与 AC 映射完整。
- [ ] 使用共享 token，亮暗主题可用。
- [ ] 评审通过，冻结记录已写入 README。
- [ ] `changes.md` 已写明依据的原型版本与映射表。
- [ ] `prototype-contract.md` 已填写：对齐等级、参考视口、必须一致项、允许差异项、数据 profile。
- [ ] 共享组件对账完成，默认行为冲突已有决策记录。
- [ ] 视觉验收 fixture / seed 已定义，动态区域已确定 mask 规则。
- [ ] 真实应用截图已与冻结原型对比，偏差已按 §10.5 分类并登记。
- [ ] 视觉回归基线已建立；首次基线已人工审批。

## 14. 参考

| 主题 | 链接 |
|------|------|
| shadcn/ui | https://github.com/shadcn-ui/ui |
| Tailwind v4 + shadcn token | https://ui.shadcn.com/docs/tailwind-v4 |
| OpenUI 生成式 UI 标准 | https://github.com/thesysdev/openui |
| GitHub Spec Kit | https://github.com/github/spec-kit |
| Figma AI 原型工具综述 | https://www.figma.com/resource-library/ai-prototyping-tools/ |
| html.to.design 文档 | https://html.to.design/docs/file-tab |
| Playwright 截图断言 | https://playwright.dev/docs/test-snapshots |
| Playwright 视觉回归 CI 实践（Argos） | https://argos-ci.com/blog/playwright-visual-regression-testing-ci |
| NN/g 低保真与高保真原型 | https://www.nngroup.com/articles/ux-prototype-hi-lo-fidelity/ |
| DTCG 设计 token 格式规范 | https://www.designtokens.org/tr/2025.10/format/ |
| zeroheight 设计 token 管线 | https://zeroheight.com/learn/the-design-token-pipeline-from-design-tool-to-codebase/ |
| Carbon 组件检查清单 | https://www.carbondesignsystem.com/getting-started/contributing/component-checklist |
| Figma Code Connect | https://developers.figma.com/docs/code-connect/ |
| Storybook 视觉测试 | https://storybook.js.org/docs/writing-tests/visual-testing |
| Chromatic 组件驱动开发 | https://www.chromatic.com/docs/in-development/ |
| 本仓库原型契约模板 | [prototype-contract-template.md](./prototype-contract-template.md) |
| 本仓库流水线原型范例 | [docs/pipeline/prototype/README.md](../pipeline/prototype/README.md) |
| 本仓库 UI 说明范例 | [docs/pipeline/pipeline-management-ui-prototype.md](../pipeline/pipeline-management-ui-prototype.md) |
