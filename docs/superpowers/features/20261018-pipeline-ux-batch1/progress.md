# 流水线页面调教 · 第 1 批 — AI 协作进度日志

> 日期：2026-10-18
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-18 | 建立日志；记录设计文档与 v0.2 原型产出，等待原型评审冻结 |

---

## 第 1 轮 · 2026-10-18 15:40 · 设计文档与原型 v0.2 产出

- **状态**：待评审
- **触发**：用户要求按 `docs/pipeline/pipeline-ux-audit.md` 开始后续流程
- **目标**：完成第 1 批（UX-01/02/06/08/09/19/20）的功能设计，并按前端原型先行规范更新可点击原型
- **读取**：`pipeline-ux-audit.md` §4-§7、`docs/frontend/ai-collaboration-frontend.md`、`docs/frontend/prototype-first-standard.md`、`frontend/src/modules/pipeline/**` 相关页面与测试、`docs/pipeline/prototype/index.html`

### 做了什么

1. 将审计 §6.1 的 7 项收敛为本批范围，写明交互、边界与验收标准。
2. 更新模块原型到 v0.2：侧栏“配置”分组、顶栏搜索“搜索流水线名称”、列表 `⋯` 操作菜单与整行可点、运行详情来源感知返回、凭证页与名称校验失败弹窗。
3. 同步 `pipeline-management-prototype-spec.json` 到 v0.2，新增凭证页与 `O07` 弹层。
4. 重新生成 20 张 2x 画板（新增 18/19/20）。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261018-pipeline-ux-batch1/design.md` | 新增 | 第 1 批功能设计 |
| `docs/superpowers/features/20261018-pipeline-ux-batch1/progress.md` | 新增 | 本进度日志 |
| `docs/pipeline/prototype/index.html` | 修改 | 原型 v0.2 |
| `docs/pipeline/prototype/README.md` | 修改 | 路由、参数、画板、评审记录 |
| `docs/pipeline/prototype/shots/01-20*.png` | 修改 / 新增 | 重新生成；新增凭证与行菜单画板 |
| `docs/pipeline/pipeline-management-prototype-spec.json` | 修改 | v0.2 规格增量 |
| `docs/pipeline/pipeline-management-prototype-spec.md` | 修改 | 版本与增量说明 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 规格 JSON 语法 | `node -e "JSON.parse(...)"` | 通过 |
| 画板生成 | Chrome headless 20 张 | 通过 |
| 视觉抽查 | 查看 `20-list-row-menu.png`、`19-credential-invalid.png` | 通过 |

### 决策与取舍

- **决策**：按审计 §6.1 先做第 1 批，不夹带需要后端改动的 UX-03/04/05。　**理由**：批次边界清晰，可单独验收。　**未选方案**：一次做完全部 P0。
- **决策**：原型更新落在既有模块原型 `docs/pipeline/prototype/`，功能目录只放引用说明。　**理由**：该原型是长期基线，避免复制两份。　**未选方案**：在功能目录重建整套原型。

### 偏差与假设

- `ASSUMPTION: 审计文档 §2.2 建议的 3-5 人真实走查尚未执行；本轮以用户“开始后续流程”视为确认继续。`
- 任务市场未纳入原型（仅文案调整，按“单文件文案豁免”处理），代码阶段会在 `changes.md` 记录该豁免。

### 未完成 / 风险

- 原型尚未评审冻结，代码实现未开始。
- 名称校验失败态只覆盖“名称必填”，未覆盖后端错误内联展示。

### 下一步

- **唯一动作**：用户评审并冻结 `docs/pipeline/prototype` v0.2（重点看列表 `⋯` 菜单、凭证校验失败态、侧栏分组）。冻结后编写 `test-cases.md`、`changes.md` 并开始前端实现。

---

## 第 2 轮 · 2026-10-18 15:56 · 第 1 批前端实现与本地验证

- **状态**：待评审（等待独立 agent 校验）
- **触发**：用户确认“开始实施”，视为原型 v0.2 冻结
- **目标**：按 `design.md` / `test-cases.md` 完成 7 项前端改动，测试、类型检查与构建通过
- **读取**：`design.md`、`test-cases.md`、`changes.md`、相关页面与测试、`ConfirmDialog`、`DataTable`、Radix 菜单实现

### 做了什么

1. 原型评审记录标记为**已冻结**。
2. 实现 UX-01/02/06/08/09/19/20 的前端改动。
3. 为删除入口引入页面级受控 `ConfirmDialog`，并保持非受控用法兼容。
4. 更新/新增 8 个测试文件，覆盖正常、边界与异常路径。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/shared/console/shellSearch.ts` | 修改 | 新增 pipeline 搜索作用域 |
| `frontend/src/components/console/AppShell.tsx` | 修改 | 流水线搜索提交跳列表 |
| `frontend/src/shared/console/navigation.ts` | 修改 | 侧栏“配置”分组 |
| `frontend/src/modules/pipeline/pages/PipelineListPage.tsx` | 修改 | 行点击、`⋯` 菜单、受控删除确认 |
| `frontend/src/modules/pipeline/pages/PipelineRunDetailPage.tsx` | 修改 | 来源感知返回、触发方式中文化 |
| `frontend/src/modules/pipeline/pages/CredentialListPage.tsx` | 修改 | 字段级错误与聚焦 |
| `frontend/src/modules/pipeline/pages/TaskKindListPage.tsx` | 修改 | “任务类型”文案 |
| `frontend/src/components/console/ConfirmDialog.tsx` | 修改 | 可选受控模式 |
| `frontend/src/**/**.test.ts(x)` | 新增 / 修改 | 见 `changes.md` §2 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 受影响用例 | `npx vitest run <8 个文件>` | 通过 57/57 |
| 全量前端测试 | `cd frontend && npm test` | 通过 242，跳过 1 |
| 类型检查 | `cd frontend && npx tsc --noEmit` | 通过 |
| 生产构建 | `cd frontend && npm run build` | 通过 |
| 真实浏览器走查 | — | 未执行（需要登录与完整租户数据） |

### 决策与取舍

- **决策**：扩展 `ConfirmDialog` 支持受控开关，而不是在列表页复制一套 AlertDialog。　**理由**：复用全站共享模式，避免私有弹窗。　**未选方案**：菜单内嵌非受控确认框（菜单关闭会卸载弹窗）。
- **决策**：返回来源判断使用 `window.history.state.idx`。　**理由**：React Router 8 的浏览器历史会写入 `idx`，无需新增路由 state 约定。　**未选方案**：从每个入口传 `state.from`。

### 偏差与假设

- 点击行内“运行”在无参数时会直接启动并跳转运行详情（既有行为）；用例相应验证“不进入流水线详情页”。
- 凭证页纳入原型范围（v0.1 曾排除）。
- `ASSUMPTION: 生产环境由 React Router 写入 window.history.state.idx；非浏览器环境回退到运行历史。`

### 未完成 / 风险

- 独立 agent 校验尚未执行。
- 未做真实浏览器 E2E；返回行为在 MemoryRouter + 模拟 `idx` 下验证。

### 下一步

- **唯一动作**：运行独立 agent 校验（文档一致性 + 逐条执行 `test-cases.md`），把结论写回 `changes.md` §5。

---

## 第 3 轮 · 2026-10-18 16:02 · 独立校验通过

- **状态**：已完成
- **触发**：第 2 轮独立复核报告返回
- **目标**：关闭校验差异，写回结论
- **读取**：`changes.md` §5、独立复核报告

### 做了什么

1. 第 1 轮 8 条文档/测试差异全部关闭（test-cases 自动化位置、`?keyword=`、changes 记录、design 刷新语义、测试补强）。
2. 第 2 轮独立复核结论：**通过**；22 处自动化位置全部匹配，三份文档与 diff 无未记录差异。
3. 补写 `design.md` 状态、`changes.md` §1 原型文件与 §5 两轮校验结论。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `design.md` | 修改 | v0.2；刷新语义、状态 |
| `test-cases.md` | 修改 | v0.2；自动化位置与预期 |
| `changes.md` | 修改 | v0.2；实际改动、偏差、校验结果 |
| `progress.md` | 修改 | 追加第 2、3 轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 全量前端测试 | `npm test` | 通过 242 / 跳过 1 |
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 生产构建 | `npm run build` | 通过 |
| 独立复核 | 独立 agent 第 2 轮 | 通过 |

### 决策与取舍

- 无新增决策。

### 偏差与假设

- 3 条手工用例（TC-UX02-04、TC-UX19-03、TC-REG-04）未执行，需真实浏览器与登录环境。

### 未完成 / 风险

- 手工用例待做；本批未部署到运行中的前端容器。

### 下一步

- **唯一动作**：在真实浏览器完成 3 条手工用例（可先重新构建 `frontend` 容器），然后进入审计 §6.2 第 2 个双周批次。

---

## 第 4 轮 · 2026-10-18 17:05 · 移除列表“历史”入口

- **状态**：已完成
- **触发**：用户要求移除流水线列表上的历史按钮
- **目标**：列表 `⋯` 菜单只保留编辑/删除；运行历史继续从详情页页签进入
- **读取**：`PipelineListPage.tsx`、`PipelineListPage.test.tsx`、原型列表节点

### 做了什么

1. 删除列表行内“历史”菜单项及 `History` 图标引用。
2. 更新组件测试：菜单项断言为 `['编辑', '删除']`，并断言不存在 history 链接。
3. 原型升到 v0.6，移除行菜单历史项；重新生成 `20-list-row-menu.png`。
4. 同步批 1 测试用例与变更文档。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/modules/pipeline/pages/PipelineListPage.tsx` | 修改 | 移除历史入口 |
| `frontend/src/modules/pipeline/pages/PipelineListPage.test.tsx` | 修改 | 菜单断言 |
| `docs/pipeline/prototype/index.html` | 修改 | v0.6 |
| `docs/pipeline/prototype/README.md` | 修改 | v0.6 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | v0.6 |
| `test-cases.md` / `changes.md` | 修改 | 批 1 v0.3 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 列表测试 | `npx vitest run src/modules/pipeline/pages/PipelineListPage.test.tsx` | 通过 |
| 规格 JSON | `node -e JSON.parse(...)` | 通过 |

### 决策与取舍

- **决策**：只移除列表快捷入口，保留详情页“运行历史”页签与直接 URL。**理由**：用户只要求移除列表按钮，历史能力仍需要。

### 偏差与假设

- 无。

### 未完成 / 风险

- 全量前端测试待本轮结束前重新执行。

### 下一步

- **唯一动作**：跑全量前端测试与构建，然后继续编辑器重构原型冻结。
