# 流水线页面调教 · 第 1 批 —— 代码变更文档

> 日期：2026-10-18
> 版本：v0.3
> 状态：待独立复核
> 对应设计：[`design.md`](./design.md) v0.2
> 对应用例：[`test-cases.md`](./test-cases.md) v0.2

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-18 | 开工骨架：列出计划新增/修改文件及对应设计章节 |
| v0.2 | 2026-10-18 | 补全实际改动、计划外偏差、原型交付物；按首轮独立校验修正文档并补强测试 |
| v0.3 | 2026-10-18 | 后续调整：移除流水线列表 `⋯` 菜单中的“历史”入口 |

---

## 1. 计划改动

| 文件 | 动作 | 对应设计 | 说明 |
|------|------|----------|------|
| `frontend/src/shared/console/shellSearch.ts` | 修改 | §3.1 | `kind` 增加 `pipeline` 分支 |
| `frontend/src/components/console/AppShell.tsx` | 修改 | §3.1 | 流水线搜索提交跳列表 `?q=` |
| `frontend/src/shared/console/navigation.ts` | 修改 | §3.6 | pipeline 菜单拆为工作对象组与“配置”组 |
| `frontend/src/modules/pipeline/pages/PipelineListPage.tsx` | 修改 | §3.4 | 整行点击；操作收敛为运行 + `⋯` 菜单 |
| `frontend/src/modules/pipeline/pages/PipelineRunDetailPage.tsx` | 修改 | §3.2、§3.5 | 返回来源感知；触发方式走 `triggerLabel()` |
| `frontend/src/modules/pipeline/pages/CredentialListPage.tsx` | 修改 | §3.3 | 名称字段级错误、聚焦与无障碍属性 |
| `frontend/src/modules/pipeline/pages/TaskKindListPage.tsx` | 修改 | §3.7 | 任务类型文案 |
| `frontend/src/shared/console/shellSearch.test.ts` | 修改 | §3.1 | 增加流水线搜索文案映射 |
| `frontend/src/components/console/AppShell.test.tsx` | 修改 | §3.1 | 流水线/项目/空关键词提交行为 |
| `frontend/src/shared/console/navigation.test.ts` | 修改 | §3.6 | 分组断言 |
| `frontend/src/modules/pipeline/pages/PipelineListPage.test.tsx` | 修改 | §3.4 | 行点击、菜单、运行不跳转 |
| `frontend/src/modules/pipeline/pages/PipelineRunDetailPage.test.tsx` | 修改 | §3.2、§3.5 | 返回来源、触发文案 |
| `frontend/src/modules/pipeline/pages/CredentialPages.test.tsx` | 修改 | §3.3 | 错误可见、聚焦、aria |
| `frontend/src/modules/pipeline/pages/TaskKindPages.test.tsx` | 修改 | §3.7 | 术语断言 |
| `frontend/src/components/console/ConfirmDialog.tsx` | 修改 | §3.4 | 增加受控开关，支持菜单项触发删除确认（计划外，见 §3） |
| `docs/pipeline/prototype/index.html` | 修改 | §7 | 原型 v0.2 交互与状态 |
| `docs/pipeline/prototype/README.md` | 修改 | §7 | 路由、参数、画板与冻结记录 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 修改 | §7 | 规格同步 v0.2 |
| `docs/pipeline/prototype/shots/18-20*.png` | 新增 | §7 | 凭证页、校验失败、行菜单画板 |

## 2. 实际改动

| 文件 | 实际改动 |
|------|----------|
| `shellSearch.ts` | `kind` 增加 `pipeline`；`/pipeline` 前缀返回“搜索流水线名称 / 搜索流水线” |
| `AppShell.tsx` | `onSearch` 增加 pipeline 分支，跳 `/pipeline/pipelines?q=`；其余作用域行为不变 |
| `navigation.ts` | pipeline 拆为 `pipeline-work`（流水线）与 `pipeline-config`（标签“配置”，含任务市场、凭证） |
| `PipelineListPage.tsx` | `useNavigate` + `DataTable.onRowClick`；操作列改为“运行 + `⋯` 下拉（编辑/删除；历史入口已于 v0.3 按用户要求移除）”；删除改为页面级受控确认框；列宽 220→120 |
| `PipelineRunDetailPage.tsx` | 返回按钮改为 `goBack()`：`window.history.state.idx > 0` 时 `navigate(-1)`，否则回运行历史；触发字段改用 `triggerLabel()` |
| `CredentialListPage.tsx` | 名称字段渲染 `errors.name.message`、`aria-invalid`、`aria-describedby`、`role="alert"`；invalid 时 `setFocus('name')`；表单 `noValidate` |
| `TaskKindListPage.tsx` | 统计、空态、搜索 aria/placeholder、分组 aria 统一为“任务类型” |
| `ConfirmDialog.tsx` | 新增可选 `open` / `onOpenChange`，`children` 改为可选；非受控用法保持兼容 |
| `docs/pipeline/prototype/index.html` | 原型升到 v0.2：侧栏配置分组、顶栏搜索、列表行菜单、运行详情返回、凭证页与校验失败态 |
| `docs/pipeline/prototype/README.md` | 路由、参数、画板清单、评审记录更新；v0.2 标记已冻结 |
| `docs/pipeline/pipeline-management-prototype-spec.json` / `.md` | 规格同步 v0.2，新增凭证页与 `O07` 弹层 |
| `docs/pipeline/prototype/shots/01-20*.png` | 重新生成 17 张，新增 `18-credentials`、`19-credential-invalid`、`20-list-row-menu` |
| 测试 | 修改 `shellSearch.test.ts`；更新列表、运行详情、凭证、任务市场、AppShell、导航、触发文案测试 |

## 3. 计划外偏差

| 偏差 | 原因 | 影响 |
|------|------|------|
| 修改共享组件 `ConfirmDialog` | 列表删除入口从常驻按钮移入下拉菜单后，Radix 菜单关闭会卸载菜单内弹窗；需要页面级受控确认框 | 向后兼容，既有非受控用法不变 |
| TC-UX08-03 预期调整为“点击运行启动运行并进入运行详情” | `StartRunButton` 无参数时直接启动并跳转，这是既有行为；要验证的是“不触发行跳转” | 用例文档已同步 |
| 原型新增凭证页（原 v0.1 明确不在范围） | UX-06 涉及凭证表单错误态，原型门禁要求覆盖被改页面 | 原型 v0.2 已纳入并冻结 |

## 4. 豁免说明

- 任务市场文案调整（UX-20）为纯文案改动，未单独制作页面原型，依据 `prototype-first-standard.md` §2.2“单文件小修：文案”豁免。
- UX-09 触发方式文案为纯文案映射，原型 v0.2 已显示“手动”。

## 5. 校验结果

### 第 1 轮独立校验（2026-10-18）

**结论：不通过（仅文档一致性问题；代码与自动化检查全部通过）。**

| 类别 | 结果 |
|------|------|
| 功能实现 | 7 项均已落地，未发现漏做 |
| `npm test` | 通过：64 个文件，242 passed / 1 skipped |
| `npx tsc --noEmit` | 通过 |
| `npm run build` | 通过 |
| 文档一致性 | 不通过 |

差异与处理：

| # | 差异 | 处理 |
|---|------|------|
| 1 | `test-cases.md` TC-UX01-03 写成 `?q=` | 已改为 `?keyword=` |
| 2 | 6 处自动化位置指向不存在的测试名 | 已改为真实测试名 |
| 3 | 部分测试名缺 TC 前缀 | 已按实际名称更新文档 |
| 4 | `changes.md` 把 `shellSearch.test.ts` 记为新增 | 已改为修改 |
| 5 | UX-20 placeholder 与设计字面不符 | 代码已改回“搜索任务类型” |
| 6 | `changes.md` 未记录原型交付物 | 已补原型文件与 3 张新画板 |
| 7 | UX-02 刷新语义与设计表述不符 | 已修订 `design.md` v0.2 |
| 8 | 测试覆盖偏弱 | 已补名称链接点击断言、`共 N 个任务类型` 断言，并重命名陈旧测试 |

### 第 2 轮独立校验（2026-10-18）

**结论：通过。**

| 检查 | 结果 |
|------|------|
| 第 1 轮 8 条差异 | 全部关闭 |
| 文档中的自动化位置 | 22 处全部真实存在且名称匹配 |
| design ↔ test-cases ↔ changes ↔ diff | 无未记录的实质性差异 |
| `npm test` | 通过：64 个文件，242 passed / 1 skipped |
| 受影响 8 个测试文件 | 通过：57 passed |
| `npx tsc --noEmit` | 通过 |
| `npm run build` | 通过 |

剩余非阻塞项（已处理）：

- `design.md` 状态改为“原型 v0.2 已冻结，本批已实施并通过独立复核”。
- `changes.md` §1 补齐原型文件。
- 本节写入第 2 轮结论。

仍需人工验证（未执行，需真实浏览器与登录环境）：

| 用例 | 原因 |
|------|------|
| TC-UX02-04 | 日志抽屉开关后的返回目标需真实浏览器 |
| TC-UX19-03 | 侧栏折叠态 tooltip 需真实浏览器 |
| TC-REG-04 | `/pipeline/pipelines?q=x&page=2` 真实数据验证 |
