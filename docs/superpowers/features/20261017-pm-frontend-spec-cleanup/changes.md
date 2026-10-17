# 项目管理前端规范整改代码变更

> 日期：2026-10-17
> 版本：v0.4

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 开工骨架：列出计划新增 / 修改 / 删除的文件及对应设计章节 |
| v0.2 | 2026-10-17 | 补全实际改动、计划外偏差与本地验证结果；独立 agent 校验待执行 |
| v0.3 | 2026-10-17 | 修正删除确认文案描述，写回独立 agent 校验结论 |
| v0.4 | 2026-10-17 | 补记复核结论与可选建议的处理：统一测试名格式、说明「确认」二字微调 |

---

## 1. 变更清单

| 文件 | 动作 | 对应设计章节 |
|------|------|--------------|
| `frontend/src/modules/pm/styles/pm-theme.css` | 删除 | 3、7 |
| `frontend/src/modules/pm/utils/jsonFile.ts` | 删除 | 3 |
| `frontend/src/modules/pm/utils/workItemIoColumns.ts` | 删除 | 3 |
| `frontend/src/modules/pm/utils/transitionActionSummary.ts` | 删除 | 3 |
| `frontend/src/modules/pm/utils/comment.ts` | 删除 | 3 |
| `frontend/src/modules/pm/types/index.ts` | 修改：删除十六进制色板与无调用辅助函数 | 3 |
| `frontend/src/modules/pm/utils/format.ts` | 新增：`formatDateTime` | 3 |
| `frontend/src/modules/pm/utils/format.test.ts` | 新增：时间格式化单测 | 7 |
| `frontend/src/modules/pm/pages/ProjectListPage.tsx` | 迁移：从 `frontend/src/pages/` 移入，改用 `ConfirmDialog` 与 `Link`，修正缩进 | 3、4 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 修改：共用 `formatDateTime`，标题改 `Link` | 3、4 |
| `frontend/src/modules/pm/pages/WorkItemDetailPage.tsx` | 修改：`ConfirmDialog`、`Textarea`、共用 `formatDateTime` | 3、4、5 |
| `frontend/src/router.tsx` | 修改：`ProjectListPage` 引用路径 | 4 |

## 2. 实际改动

### 2.1 死代码清理

- 删除 `pm-theme.css`：移除模块私有 `--pm-*` token、naive-ui 皮肤覆盖与全部裸十六进制颜色。全仓确认该文件无 import。
- 删除 `utils/jsonFile.ts`、`utils/workItemIoColumns.ts`、`utils/transitionActionSummary.ts`、`utils/comment.ts`：删除前 `grep` 确认四个文件无任何引用。

### 2.2 类型层去色

`modules/pm/types/index.ts`：

- `TYPE_META` 仅保留 `label`，移除 `color` 字段。
- 删除 `TYPE_COLOR_FALLBACK`、`typeColor()`、`STATUS_COLOR_PALETTE`、`statusTagColor()`。
- `typeLabel()` 与 `WORK_ITEM_TYPE_CODES` 行为不变。

### 2.3 共享组件替换

- `ProjectListPage` 删除项目：`window.confirm` → `ConfirmDialog`，原文案中的「删除后不可恢复」放入 `description`，`destructive` + `confirmDisabled={remove.isPending}`。
- `WorkItemDetailPage` 删除事项：同样使用 `ConfirmDialog`，并在 `description` 中补充「删除后不可恢复」（原文案只有标题句）。
- `WorkItemDetailPage` 评论输入：裸 `<textarea>` → `@/components/ui/textarea` 的 `Textarea`，保留受控值与 `aria-label`。
- 两个列表页的标题跳转：裸 `<button onClick={navigate}>` → `Link`。

### 2.4 目录与工具收敛

- `frontend/src/pages/ProjectListPage.tsx` → `frontend/src/modules/pm/pages/ProjectListPage.tsx`，`router.tsx` import 同步修改；路由路径不变。
- 新增 `modules/pm/utils/format.ts` 的 `formatDateTime`，替换 `WorkItemListPage`、`WorkItemDetailPage` 中各自的 `formatTime`。
- `ProjectListPage` 返回块缩进修正为两空格层级。

### 2.5 新增测试

- `modules/pm/utils/format.test.ts`：覆盖空值、ISO 时间、毫秒截断，对应 TC-01～TC-05。

## 3. 计划外偏差

- 无。所有改动均在计划清单内。

## 4. 校验结果

### 4.1 本地自动验证（实施者）

| 检查 | 命令 | 结果 |
|------|------|------|
| 类型检查 | `npx tsc --noEmit` | 通过（exit 0） |
| 前端测试 | `npm test` | 61 个文件 / 188 个用例全部通过 |
| 生产构建 | `npx vite build` | 通过（exit 0，仅有既有 chunk 体积警告） |
| TC-09 私有主题 / naive-ui 残留 | `grep` | 无输出 |
| TC-10 PM 目录十六进制颜色 | `grep` | 无输出 |
| TC-11 PM 目录 `window.confirm` | `grep` | 无输出 |
| TC-12 PM 目录裸 `<textarea>` | `grep` | 无输出 |
| TC-13 PM 目录 `formatTime` | `grep` | 无输出 |
| TC-14 文件迁移 | `ls` | 新路径存在，旧路径已删除 |

### 4.2 独立 agent 校验

- 校验方式：新开独立 agent（全新上下文），读取三份文档 + 当前工作区 diff，逐条执行 `test-cases.md`。
- 结论：**通过**。
- 自动化与静态用例：TC-01～TC-14 全部通过（证据：format.test.ts 3/3、statusTone.test.ts 2/2、`npx tsc --noEmit` exit 0、`npm test` 61 文件 / 188 用例通过；TC-09～TC-14 的 grep 均无匹配）。
- 手工 UI 用例：TC-15～TC-22 标记「未执行（缺少可登录前端与后端 API 环境）」，未臆造结果。
- 差异清单：
  - 漏做：无。
  - 多做：功能范围内无。工作区中 `AGENTS.md` 与其它未跟踪 docs 属于本功能范围外的既有改动，提交时应与本功能分离。
  - 不一致（低严重度）：详情页删除弹窗新增「删除后不可恢复」而原 `window.confirm` 无此句，与 design v0.1 第 5 节表述不符。已在 design v0.2 与本文 v0.3 修正文档口径，保留该提示。
- 失败用例：无。
- 必须修复项：无阻断项；上述文档口径已修正。
- 复核：文档修正后由同一独立 agent 复核，结论仍为**通过**；确认代码自首次校验后未变化，`format.test.ts` 复跑 3/3 通过。
- 复核提出的两项可选建议已处理：TC-04 / TC-05 测试名统一为 vitest 实际分隔符格式；design.md 补充「确认」二字微调说明。此两处仅文档记号与说明，不涉及代码或测试行为。
