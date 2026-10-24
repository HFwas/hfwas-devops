# PM 前端与冻结原型偏差修复 — AI 协作进度日志

> 日期：2026-10-24
> 版本：v1.0
> 说明：本文件为追加式日志。`版本` 表示格式版本，不随每轮追加递增；每轮记录见文末。

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-24 | 建立日志，记录第一批偏差修复 |

---

## 第 1 轮 · 2026-10-24 16:05 · 审计结论落库与第一批修复

- **状态**：待评审
- **触发**：用户确认「无权限态暂不考虑；批量栏按冻结画板 10 改为工具栏下方内联条 + 行高亮；审计写回文档并开始实施」。
- **目标**：关闭审计中的 2 个阻塞项与 6 个高优先级项，顺带关闭 14 个中低项；剩余项显式登记。
- **读取**：`docs/product/06-prototype/pm/prototype-contract.md`、`README.md`、`docs/frontend/prototype-first-standard.md` §10.5、`WorkItemListPage.tsx`、`WorkItemDetailDialog.tsx`、`FilterBuilder.tsx`、`ViewConfigDrawer.tsx`、`AppSidebar.tsx`、`WorkItemViewTabs.tsx`、`DataTable.tsx`、`BulkActionBar.tsx`、`breadcrumbs.ts`、画板 04/10/11/28/39/49。

### 做了什么

1. 新建功能目录与 `design.md` / `test-cases.md` / `changes.md`，登记用户两条决策与本轮范围。
2. 修复详情弹窗结构：标题/页签/右栏跨页签常驻，页签只切换左栏。
3. 修复筛选应用被旧 URL 状态覆盖，应用后正确写入 `f=` 并重查。
4. 修复视图配置页脚被裁切、侧栏计数 `010000`、故事点/风险字段文案。
5. 新增「保存视图」入口与命名弹层；已保存视图与内置视图去重。
6. 详情右栏补齐 15 项字段；标题 30px；校验顶部汇总；取消编辑二次确认；状态内联非法项置灰；优先级选中即保存；403/404 文案。
7. 空态改为表内空行保留表头并补「新建工作项」；骨架 8 行；失败态展示 requestId。
8. 批量栏改为工具栏下方内联条；选中行提高高亮。
9. 面包屑补项目名与事项编号；全局搜索文案对齐原型。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `frontend/src/modules/pm/components/WorkItemDetailDialog.tsx` | 修改 | 结构、右栏、校验、取消确认、内联状态/优先级 |
| `frontend/src/modules/pm/components/FilterBuilder.tsx` | 修改 | 单次提交；筛选字段只含可筛选项 |
| `frontend/src/modules/pm/components/ViewConfigDrawer.tsx` | 修改 | 固定页脚、独立滚动、数量文案、字段键 |
| `frontend/src/modules/pm/components/SaveViewDialog.tsx` | 新增 | 保存视图弹层 |
| `frontend/src/modules/pm/components/WorkItemViewTabs.tsx` | 修改 | 去重、保存视图入口 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 修改 | 保存视图、空态、骨架、requestId、内联批量栏 |
| `frontend/src/modules/pm/utils/columns.tsx` | 修改 | 故事点/风险元数据与渲染 |
| `frontend/src/modules/pm/utils/listUrlState.ts` | 修改 | overlay 增加 `save-view` |
| `frontend/src/components/console/AppSidebar.tsx` | 修改 | 计数数值化 |
| `frontend/src/components/console/ListPageShell.tsx` | 修改 | `batchBar` 插槽 |
| `frontend/src/components/console/BulkActionBar.tsx` | 修改 | `inline` 形态、`countPrefix` |
| `frontend/src/components/console/DataTable.tsx` | 修改 | 骨架 8 行、选中高亮 |
| `frontend/src/components/console/breadcrumbs.ts`、`AppShell.tsx` | 修改 | 项目名/事项编号面包屑 |
| `frontend/src/shared/console/shellSearch.ts` | 修改 | PM 搜索文案 |
| `frontend/src/shared/types/common.ts`、`errors/apiError.ts`、`api/request.ts` | 修改 | requestId 透传 |
| `docs/product/06-prototype/pm/prototype-contract.md` | 修改 | v0.22：批量栏以画板 10 为准、无权限态暂缓、偏差 30–33 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/*` | 新增 | design / test-cases / changes / progress |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 单测 | `npm test` | 84 文件 / 368 通过 / 1 跳过 / 0 失败 |
| 构建 | `npm run build` | 通过（既有 chunk 体积告警） |
| 真实应用筛选 | admin 登录 → 筛选 → 我的工作项 → 应用 | 通过：URL 写入 `f=`，条件行与「共 0 条」同步 |
| 真实详情页签 | `?detail=1` → 操作记录 | 通过：标题/3 页签/右栏均在 |
| 真实右栏字段 | `?detail=1` | 通过：15 项字段，空值「未设置」，标题 30px |
| 真实视图配置 | `?overlay=view-config&tab=columns` | 通过：保存按钮 bottom 888 < 900，页脚左右分列 |
| 真实批量栏 | 选中第 1 行 | 通过：inline、static、整宽 1128px、已选当前页 1 项 |
| 真实侧栏/表头 | 全部工作项 | 通过：计数 1，表头「故事点」 |
| 真实重复页签 | `/items/requirement` | 通过：仅 4 个内置页签 |
| 真实面包屑 | 列表页 | 通过：`kokokoda / 工作项` |
| 保存视图弹层 | 页签行「保存视图」 | 通过：默认名与配置摘要正确；未提交，避免写数据 |

### 决策与取舍

- **决策**：批量栏按画板 10 做内联条。**理由**：用户 2026-10-24 明确以冻结画板为准。**未选方案**：按契约正文保留底部悬浮。
- **决策**：故事点/风险暂不开放筛选/排序。**理由**：项目无对应字段定义，`custom.*` 查询会被后端拒绝。**未选方案**：前端硬编码 JSON 过滤（会绕过字段权限与类型校验）。
- **决策**：空态改用表内空行。**理由**：原型 04 要求保留表头。**未选方案**：保留独立 `EmptyState`（丢表头）。
- **决策**：无权限态不实现。**理由**：用户明确本轮暂不考虑。

### 偏差与假设

- 见 `changes.md` §3：故事点/风险可筛选性、空态实现方式、优先级触发方式、面包屑查询位置、产物同步方式。
- `ASSUMPTION`：后端 `PmWorkItem` 的扩展字段键使用 camelCase（`storyPoints`），同时兼容 snake_case。
- `ASSUMPTION`：保存视图的「共享范围」后端无字段，暂时固定为「仅自己可见」。

### 未完成 / 风险

- 设置页（视图与搜索、批量操作）、导入/导出/快速预览/动作编辑、侧栏「协作」「配置」组、状态流转居中弹层、批量编辑内联影响预览未做。
- 审计中的无权限/只读模式按用户决策暂缓。
- 尚未按原型逐页逐状态重出截图；本轮只对 8 个关键状态做了真实浏览器取证。

### 下一步

- **唯一动作**：新开独立 agent，按 `test-cases.md` 与当前 diff 做文档一致性与用例复验，结论回填 `changes.md` §5。

---

## 第 2 轮 · 2026-10-24 16:20 · 独立校验第 1 轮不通过，修正文档

- **状态**：待评审（等待复验）
- **触发**：独立校验 agent 返回「不通过，阻塞 3 项」。
- **目标**：修正测试用例映射与过期预期、design/契约措辞，然后请同一 agent 复验。

### 做了什么

1. 复核校验结论：3 项阻塞均为测试文档映射/预期问题，代码与测试本身通过；无实现回归。
2. `test-cases.md` 升至 v0.3：TC-10 映射改到 `AppShell.test.tsx`；TC-20 拆成端到端 + 纯函数两处；TC-11 明确风险筛选当前不开放；TC-21 改为动态 N；TC-03 细化空值文案。
3. `design.md` 升至 v0.3：H3、§5.3、§7 #5 改为「故事点/风险当前只做显示，筛选排序待字段定义」。
4. `prototype-contract.md` 升至 v0.23：批量栏「导出」、视图页签行「导入/导出」标注后续阶段，并注明已实现「保存视图」。
5. `changes.md` 升至 v0.3：回填第 1 轮校验结论与修正清单；测试文件数改为 9。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261024-pm-prototype-alignment/test-cases.md` | 修改 | v0.3：映射与预期修正 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/design.md` | 修改 | v0.3：故事点/风险范围修正 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/changes.md` | 修改 | v0.3：校验结论与修正清单 |
| `docs/product/06-prototype/pm/prototype-contract.md` | 修改 | v0.23：批量栏/页签行注记 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 类型检查 | `npx tsc --noEmit` | 通过 |
| 单测 | `npm test` | 84 文件 / 368 通过 / 1 跳过 / 0 失败 |
| 文档映射 | 逐一核对 `test-cases.md` 中的 `文件::测试名` | 修正后均存在 |
| 构建 | 未重跑（本轮仅文档改动；上一轮 dist 已同步） | 不适用 |

### 决策与取舍

- **决策**：保留 `DEFAULT_FILTER_FIELDS` 中的 `risk`。**理由**：与冻结原型默认配置一致；当前不可见/不可选，但后端补字段定义后会自动生效。**未选方案**：立即从默认配置移除（会造成前后端字段定义就绪后默认筛选缺项）。

### 偏差与假设

- 无新增代码偏差。

### 未完成 / 风险

- 等待独立 agent 复验；若仍有文档差异继续修正。

### 下一步

- **唯一动作**：让同一独立 agent 复核修正后的文档，确认阻塞项关闭并把最终结论回填 `changes.md` §5。

---

## 第 3 轮 · 2026-10-24 16:25 · 第 2 轮校验通过，收尾文档

- **状态**：已完成（第一批）
- **触发**：独立 agent 第 2 轮复验返回「通过，阻塞 0 项」，并提两条低优先级文档精度建议。
- **目标**：回填最终校验结论，同步建议项，关闭第一批。

### 做了什么

1. `changes.md` 升至 v0.4：§5 写入第 2 轮通过结论、剩余风险；§2 #4/#6 补代码增量说明。
2. `design.md` 升至 v0.4：H5 与 §7 #7 空值文案细化为「未设置 / 未分配 / 未归属 / 无父事项」。
3. 记录基线漂移：16:06–16:08 的同名字段能力合并与 tablist 结构调整已在第 2 轮按当前 diff 复核。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261024-pm-prototype-alignment/changes.md` | 修改 | v0.4：第 2 轮结论与两处补充 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/design.md` | 修改 | v0.4：空值文案 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/progress.md` | 修改 | 追加本轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 类型检查 | `npx tsc --noEmit` | 通过（第 2 轮） |
| 单测 | `npm test -- --run` | 84 文件 / 368 通过 / 1 跳过 / 0 失败（第 2 轮） |
| 独立复验 | 独立 agent | 通过，阻塞 0 项 |

### 决策与取舍

- **决策**：接受第 2 轮结论为第一批最终结论。**理由**：3 项阻塞全部关闭，剩余仅有只读审计限制与用户暂缓项。

### 偏差与假设

- 无新增。

### 未完成 / 风险

- 第一批非目标（设置页、导入/导出、只读模式等）已登记在 `changes.md` §4 与契约 §8 偏差 30–33。
- 写操作链路仍需在有写权限的环境单独回归。

### 下一步

- **唯一动作**：等待用户确认是否进入第二批（设置页、导入/导出、侧栏配置组等）；未确认前不改代码。

---

## 第 4 轮 · 2026-10-24 16:32 · 第 3 轮确认通过，清理空值措辞

- **状态**：已完成（第一批）
- **触发**：独立 agent 第 3 轮轻量确认「通过，阻塞 0 项」，指出 2 处可选清理的笼统空值措辞。
- **目标**：消除文档内部口径差异，关闭第一批。

### 做了什么

1. `design.md` 升至 v0.5：§5.3 第 5 点空值文案改为「未设置 / 未分配 / 未归属 / 无父事项」。
2. `changes.md` 升至 v0.5：§2 #1 同步；§5 增加第 3 轮确认小节。
3. 确认无代码变化，沿用第 2 轮 `tsc` / `npm test` 结果。

### 变更文件

| 文件 | 动作 | 说明 |
|------|------|------|
| `docs/superpowers/features/20261024-pm-prototype-alignment/design.md` | 修改 | v0.5：§5.3 空值文案 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/changes.md` | 修改 | v0.5：§2 #1 与第 3 轮确认 |
| `docs/superpowers/features/20261024-pm-prototype-alignment/progress.md` | 修改 | 追加本轮 |

### 验证

| 检查 | 命令 / 方式 | 结果 |
|------|-------------|------|
| 代码变化 | `git diff` 对比第 2 轮 | 无变化 |
| 类型检查 | `npx tsc --noEmit`（第 2 轮） | 通过 |
| 单测 | `npm test -- --run`（第 2 轮） | 84 文件 / 368 通过 / 1 跳过 / 0 失败 |
| 独立确认 | 独立 agent 第 3 轮 | 通过，阻塞 0 项 |

### 决策与取舍

- **决策**：只改文档，不重跑浏览器。**理由**：代码 diff 未变化，第 2 轮浏览器结论仍有效。

### 偏差与假设

- 无。

### 未完成 / 风险

- 第一批非目标与只读审计限制不变，已登记。

### 下一步

- **唯一动作**：等待用户决定是否开始第二批。
