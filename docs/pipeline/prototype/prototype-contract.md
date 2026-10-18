# 流水线模块原型契约（Prototype Contract）

> 日期：2026-10-18
> 版本：v0.2
> 模块：`pipeline`
> 关联：[prototype-first-standard.md](../../frontend/prototype-first-standard.md) v0.2、[prototype-contract-template.md](../../frontend/prototype-contract-template.md) v0.1
> 原型版本：`docs/pipeline/prototype` v0.11（画面基线；README 文档索引 v0.12）
> 冻结状态：v0.2、v0.8 已冻结；v0.9–v0.11 待评审
> 实现基线：`dev` 分支工作区（基于 `c5d9b7a`，含 2026-10-18 未提交改动）

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 初版：记录流水线模块对齐等级 A2、权责边界、数据 profile、共享组件对账与 20 条实际差异分类；附真实应用截图证据 |
| v0.2 | 2026-10-18 | 关闭 D05、D06、D07、D17；D13 复核为与原型一致（非偏差）；更新修复后截图与测试验证 |

---

## 1. 基本信息

| 项 | 值 |
|----|----|
| 功能目录 | [20261018-pipeline-ux-batch1](../../superpowers/features/20261018-pipeline-ux-batch1/)、[20261018-pipeline-editor-refactor](../../superpowers/features/20261018-pipeline-editor-refactor/) |
| 原型入口 | [`index.html`](./index.html) |
| 原型规格 | [`pipeline-management-prototype-spec.json`](../pipeline-management-prototype-spec.json) / [`.md`](../pipeline-management-prototype-spec.md) |
| 原型版本 | `v0.11`（画面基线） |
| 文档索引 | [`README.md`](./README.md) v0.12（仅索引与契约引用，不改变画面） |
| 冻结状态 | v0.2、v0.8 已冻结；v0.9–v0.11 仍为「待评审」，本契约以 v0.11 为参考基线，不等同于冻结评审 |
| 对齐等级 | **A2**（布局对齐：1440×900 下结构、间距、列宽、字号一致；允许字体渲染差异） |
| 参考视口 | 1440×900 @2x |
| 验收环境 | Chrome / Playwright，`http://localhost:8000`，`admin` / 默认租户 |
| 数据 profile | `pipeline-visual-fixtures-v1`（待建立，见 §5） |
| 实现基线 | `c5d9b7a`，运行中前端容器构建于 2026-10-18 10:01 |

## 2. 权责确认

- [x] 页面清单、信息架构、页面层级以冻结原型为准。
- [x] 颜色、字体、圆角、阴影、状态色以设计系统 token 为准；原型与实现的 token 名称和值一致。
- [x] 布局骨架与间距节奏以原型 1440×900 为基准；其他视口由前端定义。
- [x] 交互路径、页签、弹层、返回路径以冻结原型为准。
- [x] 业务规则、校验、权限以 `design.md`、`test-cases.md`、接口文档为准。
- [x] 真实数据、数据量、状态分布以 API 与后端为准。
- [x] 可访问性、性能、安全、浏览器兼容由前端负责。
- [x] 组件内部 DOM 与实现以 `src/components/ui`、`src/components/console` 为准。

## 3. 必须一致

- [x] 页面组成顺序：头部、页签、指标、表格 / 画布、分页。
- [x] 导航与返回路径：列表 → 详情 / 编辑 / 运行历史 / 运行详情；来源感知返回。
- [x] 列表列顺序与含义：流水线名称、仓库 / 分支、阶段预览、最近运行、操作。
- [x] 详情顺序：返回与名称、编辑 / 运行、页签、指标、画布；指标只在「详情」页签显示。
- [x] 编辑器结构：左侧画布、右侧任务配置；任务名称 / 类型只读；克隆任务内配置仓库、分支、凭证。
- [x] 状态语义和文案：成功 / 失败 / 运行中 / 排队中 / 待审批 / 已取消。
- [x] 共享组件选型：`DataTable`、`DetailShell`、`StatusIcon`、`OverviewMetricCards`、`Pagination`。
- [x] token 名称与值：`--background`、`--primary`、`--muted`、`--border`、`--radius` 等与实现一致。
- [x] 1440×900 下的布局骨架和间距节奏。

## 4. 允许差异

以下差异不视为实现错误，但必须保留记录：

| # | 差异 | 原因 | 分类 | 处理 |
|---|------|------|------|------|
| 1 | 真实流水线名称、数量、状态分布与原型样例不同 | 生产数据与原型理想数据不同 | 数据导致 | 不加代码；补 fixture |
| 2 | 无 `commitSha` 时「最近克隆」隐藏、Commit 显示 `—` | 条件渲染 | 数据导致 | 补「无 commit」用例 |
| 3 | 终态运行不显示「取消」按钮 | 条件渲染 | 数据导致 | 补终态用例 |
| 4 | 单租户时顶栏租户切换隐藏 | 仅多租户渲染 | 数据导致 | 补多租户用例后再对照 |
| 5 | 加载、空态、失败、无权限的真实实现与原型静态页不同 | 真实状态由接口驱动 | 数据导致 | 用固定 fixture 截图 |
| 6 | 其他视口下的响应式布局 | 原型只定义 1440×900 | 前端负责 | 至少保证 1440 对齐 |
| 7 | 字体抗锯齿、系统字体回退 | 渲染环境差异 | 环境差异 | 不作为失败条件 |
| 8 | 共享组件默认行为（如单页分页） | 组件全局约定 | 产品决策（待确认） | 见 D02 |
| 9 | 真实错误信息长度与原型样例不同 | 后端返回真实错误 | 数据导致 | 用长文本 fixture 验证换行 |

## 5. 数据 profile

> `pipeline-visual-fixtures-v1` 尚未建立。当前只能依赖运行中的真实数据，导致部分状态无法稳定截图。建议 fixture 放在 `frontend/e2e/fixtures/pipeline/` 或后端测试种子中，并在 CI 前固定。

| 状态 | 数据要求 | 当前可复现性 |
|------|----------|--------------|
| 默认 | ≥2 条流水线，成功 / 失败混合，含 commit | 部分：当前 4 条全部失败，无 commit |
| 空态 | 0 条流水线 | 未复现 |
| 加载 | 延迟接口或骨架屏 | 未复现 |
| 失败 | 业务失败与网络失败各 1 条 | 未复现 |
| 无权限 | 只读账号或禁用入口 | 未复现 |
| 无 commit | 运行记录 `commitSha` 为空 | 可复现（当前生产数据） |
| 长文本 | 超长名称、分支、仓库、错误信息 | 部分：运行错误信息可复现 |
| 大数据量 | >10 条流水线，分页边界 | 未复现 |
| 终态 | 成功、失败、取消、待审批 | 仅失败可复现 |
| 多租户 | 用户属于 ≥2 个租户 | 未复现（当前单租户） |

## 6. 原型 → 实现映射

| 原型 | 路由 | 实现文件 | 共享组件 | 状态 |
|------|------|----------|----------|------|
| P01 列表 | `/pipeline/pipelines` | [`PipelineListPage.tsx`](../../../frontend/src/modules/pipeline/pages/PipelineListPage.tsx) | `PageHeader`、`DataTable`、`Pagination`、`StartRunButton` | 已实现 |
| P02 详情 | `/pipeline/pipelines/:id` | [`PipelineDetailPage.tsx`](../../../frontend/src/modules/pipeline/pages/PipelineDetailPage.tsx) | `DetailShell`、`OverviewMetricCards`、`PipelineDefinitionCanvas` | 已实现 |
| P03 新建 | `/pipeline/pipelines/new` | [`PipelineEditorPage.tsx`](../../../frontend/src/modules/pipeline/pages/PipelineEditorPage.tsx) | `Input`、`Select`、`AsyncSelect`、`ConfirmDialog` | 已实现 |
| P03B 编辑 | `/pipeline/pipelines/:id/edit` | 同上 | 同上 | 已实现 |
| P04 运行历史 | `/pipeline/pipelines/:id/history` | `PipelineDetailPage.tsx` | `DetailShell`、`DataTable`、`Pagination` | 已实现 |
| P05 运行详情 | `/pipeline/pipelines/:id/runs/:runId` | [`PipelineRunDetailPage.tsx`](../../../frontend/src/modules/pipeline/pages/PipelineRunDetailPage.tsx) | `DetailShell`、`RunSwimlane`、`LogPanel` | 已实现 |
| P06 凭证 | `/pipeline/credentials` | [`CredentialListPage.tsx`](../../../frontend/src/modules/pipeline/pages/CredentialListPage.tsx) | `PageHeader`、`DataTable` | 已实现 |
| O01 运行参数 | 叠加 | [`StartRunButton.tsx`](../../../frontend/src/modules/pipeline/components/StartRunButton.tsx) | `Dialog` | 已实现 |
| O03 删除流水线 | 叠加 | `PipelineListPage.tsx` | `ConfirmDialog` | 已实现 |
| O04 取消运行 | 叠加 | `PipelineRunDetailPage.tsx` | `ConfirmDialog` | 已实现 |
| O05 放弃修改 | 叠加 | `PipelineEditorPage.tsx` | `ConfirmDialog` | 已实现 |
| O02 日志抽屉 | 叠加 | `PipelineRunDetailPage.tsx` | `LogPanel` | 已实现 |

## 7. 共享组件对账

| 原型控件 | 共享组件 | 默认行为差异 | 决策 | 影响页面 |
|----------|----------|--------------|------|----------|
| 详情 5 张指标卡 | `OverviewMetricCards` | 默认 `xl:grid-cols-6`，5 张卡右侧留空 | **已修复**：增加 `columns` 参数，流水线详情传 5；默认 6 的页面行为不变 | 所有使用指标卡的详情页 |
| 单页分页 | `Pagination` | `pageCount <= 1` 时不渲染页码与箭头 | 待产品确认；当前保持全局行为（D02） | 所有列表 |
| 列表「运行」按钮 | `StartRunButton` | 默认 `variant="outline"`，原型为 ghost 文字按钮 | 待确认；按原型改 variant 或升原型（D01） | 列表、详情 |
| 详情标题状态行 | `DetailShell` | 只渲染传入的 `title`，当前未传 `description` | **已修复**：传 `description`，详情与运行历史都显示（D05） | 详情、运行历史 |
| 阶段名称输入 | `Input` / 页面内 input | 透明边框，hover / focus 才显示 | 保持透明边框；原型同样如此（D13 非偏差） | 编辑器 |
| 运行任务状态 | `RunSwimlane` | 任务框只显示名称 / 日志，不显示状态文案 | 待确认；保持现状则升原型（D12） | 运行详情 |
| 租户入口 | `AppShell` / `AppSidebar` | 单租户隐藏顶栏切换；侧栏无底部租户栏 | 待产品确认；不建议为对齐造假数据（D03） | 全站壳层 |
| 面包屑 | `breadcrumbs.ts` | 使用静态标签「详情 / 运行历史 / 编辑流水线」 | 扩展实体名称或升原型（D04） | 全站详情页 |

冲突处理优先级：复用现有组件 → 给共享组件增加参数 → 页面级覆盖 → 新增共享组件。禁止在业务页复制私有皮肤。

## 8. 偏差登记

| ID | 页面 / 状态 | 差异 | 分类 | 原因 | 处理 | 回写原型 | 验证 |
|----|-------------|------|------|------|------|----------|------|
| D01 | 列表默认 | 运行按钮实际为 outline 带边框，原型为 ghost 文字按钮 | 实现漂移 | `StartRunButton` 默认 `variant="outline"` | 页面传 ghost，或产品确认后升原型 | 待定 | `actual/01-list.png` ↔ `shots/01-list.png` |
| D02 | 列表 / 历史单页 | 原型显示箭头 + 页码；实际只显示「第 1 / 1 页」 | 产品决策（待确认） | `Pagination` 单页隐藏控件 | 确认是否全局改 `Pagination`；当前保持 | 否 | `actual/01-list.png`、`actual/06-history.png` |
| D03 | 列表壳层 | 原型固定显示顶栏「默认租户」与侧栏底部「默认租户」；实际单租户隐藏 | 产品决策（待确认） | `AppShell` 仅在多租户渲染；`AppSidebar` 无 footer | 确认壳层是否补租户入口；不建议单租户造假 | 否 | `actual/01-list.png` ↔ `shots/01-list.png` |
| D04 | 详情 / 历史 / 编辑 | 原型「流水线 / 流水线名」，实际「流水线 > 详情 / 运行历史 / 编辑流水线」，分隔符也不同 | 实现漂移 | `resolveBreadcrumbs` 使用静态标签 | 扩展面包屑元数据使用实体名称，或明确升原型 | 待定 | `actual/03-detail.png`、`actual/06-history.png` |
| D05 | 详情 | 原型标题下有「成功 / 失败 + 时间」，实际没有 | 实现漂移 | `DetailShell` 未传 `description` | **已修复**：传入最近运行状态与相对时间；详情与历史页签都显示 | 否 | `actual/03-detail.png`、`actual/06-history.png`（修复后）；`PipelineDetailPage.test.tsx` |
| D06 | 详情 | 5 张指标卡按 6 列网格排布，右侧空一列 | 实现漂移 | `OverviewMetricCards` 使用 `xl:grid-cols-6` | **已修复**：`OverviewMetricCards` 增加 `columns`，`DetailShell` 透传 `metricColumns`，详情传 5 | 否 | `actual/03-detail.png`（修复后）；`patterns.test.tsx` |
| D07 | 详情定义画布 | 阶段卡实际宽 224px（`w-56`），原型 196px | 实现漂移 | `PipelineDefinitionCanvas` 固定 `w-56` | **已修复**：阶段卡改为 `w-[196px]` | 否 | `actual/03-detail.png`（修复后） |
| D08 | 详情 | Commit 实际显示 `—` | 数据导致 | 运行记录无 `commitSha` | 不改代码；补有 commit fixture | 不适用 | `actual/03-detail.png` |
| D09 | 运行历史 | 运行编号为 19 位 ID，副标题为触发人；原型 `#128` + commit | 数据导致 | 真实运行 ID 与 commit 缺失 | 不改代码；fixture 覆盖 | 不适用 | `actual/06-history.png` ↔ `shots/06-history.png` |
| D10 | 运行详情失败态 | 标题下直接显示大段原始 Tekton 错误；原型显示分支 / commit + 状态 | 产品决策（待确认） | 错误信息直接暴露 | 确认错误展示位置；建议独立错误区、可复制、默认折叠 | 待定 | `actual/08-run-failed.png` ↔ `shots/08-run-running.png` |
| D11 | 运行详情终态 | 无「取消」按钮 | 数据导致 | 运行已失败 | 不改代码；补终态用例 | 不适用 | `actual/08-run-failed.png` |
| D12 | 运行详情 | 任务框只显示名称 / 日志，原型显示状态 + 名称 | 实现漂移 / 原型过期（待确认） | `RunSwimlane` 任务框未渲染状态文案 | 保持现状则升原型；否则补 `StatusIcon` | 待定 | `actual/08-run-failed.png` |
| D13 | 编辑器任务配置 | 阶段名称输入框默认无边框；原型 CSS 同样是透明边框、hover 才显示 | **与原型一致（非偏差）** | 原型 `.stage-name-input` 为 `border: transparent` | 无需修改；此前分类有误 | 不适用 | `index.html:241-242`；`actual/04-editor.png` |
| D14 | 编辑器任务卡 | 实际显示最近运行状态「失败」，原型没有 | 原型过期 | 实现新增运行状态回显 | 升原型补充该状态 | 是 | `actual/04-editor.png` ↔ `shots/04-editor.png` |
| D15 | 编辑器 | 「最近克隆」不显示 | 数据导致 | 无 `commitSha` | 不改代码；补有 commit fixture | 不适用 | `actual/04-editor.png` |
| D16 | 编辑器克隆任务 | 参数绑定为空，原型有 `GIT_USERNAME` / `GIT_PASSWORD` | 数据导致 | 该流水线未配置参数 | 不改代码；补配置 fixture | 不适用 | `actual/04-editor.png` |
| D17 | 编辑器克隆任务 | 出现两条提示，信息重复 | 实现漂移 | 固定提示与 `kindMeta.hint` 同时渲染 | **已修复**：CLONE 不再渲染 `kindMeta.hint`，只保留一条平台提示 | 否 | `actual/04-editor.png`（修复后）；`PipelineEditorPage.test.tsx` |
| D18 | 新建编辑器 | 实际为空画布 +「添加阶段」+ 空选择提示；原型展示 3 个阶段和已选任务 | 原型过期 | 原型未覆盖新建默认空态 | 升原型补「新建空态 / 未选任务」画板 | 是 | `actual/21-editor-new.png` ↔ `shots/21-editor-new.png` |
| D19 | 新建 / 编辑编辑器 | 面包屑为「新建流水线 / 编辑流水线」，原型为「流水线 / 名称 / 编辑」 | 实现漂移 | 同 D04 | 同 D04 | 待定 | `actual/21-editor-new.png` |
| D20 | 全站 | 字体抗锯齿与渲染细微差异 | 环境差异 | macOS / Chrome 渲染 | 不作为失败条件；A3 用 Docker 固定环境 | 不适用 | 所有对比图 |

## 9. 验证记录

### 9.1 已执行

| 项 | 内容 |
|----|------|
| 初始对比 | 2026-10-18 18:06–18:09 |
| 修复后复验 | 2026-10-18 18:35（D05、D06、D07、D17） |
| 环境 | Chrome（Playwright `channel: chrome`），1440×900，deviceScaleFactor 2 |
| 入口 | `http://localhost:8000`（Kong），登录 `admin` / 默认租户 |
| 数据 | 当前真实数据：4 条流水线，全部最近运行为失败，无 commit；1 条凭证；单租户 |
| 对比页面 | 列表、详情、运行历史、编辑、新建、运行详情、凭证 |
| 证据 | 修复后截图见 [`actual/`](./actual/)；原型画板见 [`shots/`](./shots/) |
| 自动化 | `npx vitest run patterns.test.tsx PipelineDetailPage.test.tsx PipelineEditorPage.test.tsx`：27 passed |
| 全量前端 | `npm test`：255 passed / 1 skipped；`npx tsc --noEmit` 通过；`npm run build` 通过 |
| 交付验证 | `docker compose build frontend-build` + `docker compose run --rm frontend-build`；`http://localhost:8000` 已提供 `index-D48zBzR5.js` |

### 9.2 未执行 / 风险

- 未建立 Playwright 视觉回归基线；本次为一次性人工截图对比。
- 未覆盖空态、加载、失败、无权限、大数据量、长文本（除运行错误）、多租户。
- 未覆盖暗色主题。
- 未在 Docker 固定环境中生成截图，跨 OS 比较仍有字体差异。
- 运行详情实际截图包含本地 Tekton / k3s 错误文本，仅作差异证据。
- 本轮已重建前端产物并导出到 `artifacts/frontend`（`index-D48zBzR5.js`），截图基于该产物；后续代码变更需重新截图。

### 9.3 待办

1. 建立 `pipeline-visual-fixtures-v1`：默认、空态、加载、失败、无权限、无 commit、长文本、大数据量、终态、多租户。
2. 用 Playwright `toHaveScreenshot` 在固定 Docker 镜像中生成基线，动态区域 mask。
3. D05、D06、D07、D17 已关闭；D13 复核为非偏差。继续关闭 D01、D02、D03、D04、D10、D12、D19。
4. 把 D14、D18 回写到原型并升版，按流程重新评审。

## 10. 验收与冻结

- [x] 原型版本与实现基线已记录。
- [x] 对齐等级 A2 已确认。
- [x] 权责边界已确认。
- [ ] 原型 v0.11 已正式冻结（当前待评审）。
- [ ] 共享组件对账中的待确认项已关闭。
- [ ] 数据 profile 与 fixture 已就绪。
- [ ] 真实应用截图与冻结原型逐页逐状态对比完成（当前覆盖主路径）。
- [ ] 偏差已全部关闭或转为正式允许差异。
- [ ] 视觉回归基线已建立并人工审批。
- [ ] 独立 agent 校验结论已写回 `changes.md`。

| 角色 | 姓名 | 结论 | 日期 |
|------|------|------|------|
| 产品 | | | |
| 前端 | | | |
| 测试 | | | |

## 11. 变更控制

- 原型冻结后修改必须升版、重新评审，并更新本契约。
- 实现阶段发现规格问题：先改原型与 `prototype-spec.json`，再改代码。
- 禁止实现完成后追认式修改原型去对齐代码而不升版、不评审。
- 纯样式豁免必须记录修改前后截图、对齐等级与偏差分类，不得借豁免修改信息结构或交互。
- 本契约随原型版本与实现基线更新；每次关闭偏差都要更新 §8 的「处理 / 验证」列。
