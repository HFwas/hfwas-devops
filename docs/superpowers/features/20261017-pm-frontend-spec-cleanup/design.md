# 项目管理前端规范整改设计

> 日期：2026-10-17
> 版本：v0.3

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：梳理 PM 前端与 `docs/frontend/ai-collaboration-frontend.md` 的偏差并给出整改设计 |
| v0.2 | 2026-10-17 | 修正删除确认文案说明：项目列表沿用原文案，事项详情显式补「删除后不可恢复」 |
| v0.3 | 2026-10-17 | 补充说明：项目列表确认弹窗标题去掉原文案开头的「确认」二字，语义不变 |

---

## 1. 背景与目标

对 `frontend/src/modules/pm/` 与 `frontend/src/pages/ProjectListPage.tsx` 做规范审计后，发现以下偏差：

1. 存在未引用的模块私有主题 `pm-theme.css`，含 `--pm-*` 私有 token、naive-ui 皮肤覆盖和裸十六进制颜色。
2. `modules/pm/types/index.ts` 与 `modules/pm/utils/comment.ts` 保留十六进制状态色板，相关辅助函数无任何调用。
3. 删除确认仍用 `window.confirm`，未使用共享 `ConfirmDialog`。
4. 事项详情评论输入框为裸 `<textarea>`，未使用 `components/ui/textarea`。
5. PM 项目列表页放在全局 `pages/`，与其它 PM 页面目录不一致。
6. `formatTime` 在两个 PM 页面重复实现。

目标：在不改变业务行为、接口调用和页面可见功能的前提下，消除上述偏差，使 PM 前端符合强制前端规范。

## 2. 范围 / 非目标

**范围**

- 删除无引用的模块私有主题与死工具文件。
- 移除 TypeScript 中的十六进制色板与无调用辅助函数。
- 两处删除确认改用 `ConfirmDialog`。
- 评论输入框改用 `Textarea`。
- `ProjectListPage` 迁入 `modules/pm/pages/`。
- 抽出 PM 共享的 `formatDateTime` 工具。
- 标题跳转由裸 `<button>` 改为 `Link`。

**非目标**

- 不新增 PM 业务功能，不修改后端接口、请求参数或返回结构。
- 不调整看板、工作流、项目监控的布局与交互。
- 不改动其它模块（pipeline / container / api-test）的既有重复工具和 `window.confirm`。
- 不引入新依赖。

## 3. 接口与数据结构

无接口变更。仅删除以下前端导出，删除前已确认全仓无引用：

| 导出 | 文件 |
|------|------|
| `TYPE_META[*].color` | `frontend/src/modules/pm/types/index.ts` |
| `TYPE_COLOR_FALLBACK` | 同上 |
| `typeColor()` | 同上 |
| `STATUS_COLOR_PALETTE` | 同上 |
| `statusTagColor()` | 同上 |
| `downloadJsonFile` / `readJsonFile` / `schemeExportFilename` | `frontend/src/modules/pm/utils/jsonFile.ts` |
| `buildIoColumns` | `frontend/src/modules/pm/utils/workItemIoColumns.ts` |
| `transitionActionSummary` 系列 | `frontend/src/modules/pm/utils/transitionActionSummary.ts` |
| `getCurrentAuthorName` / `setCurrentAuthorName` / `avatarColor` / `avatarInitial` / `formatDateTime` / `formatCommentHtml` | `frontend/src/modules/pm/utils/comment.ts` |
| `.pm-theme` 与全部 `pm-*` 样式 | `frontend/src/modules/pm/styles/pm-theme.css` |

新增：

```ts
// frontend/src/modules/pm/utils/format.ts
export function formatDateTime(value?: string | null): string
```

行为：空值返回 `—`；否则把 ISO 字符串中的 `T` 替换为空格并截取前 19 位。

## 4. 关键流程

1. 列表/详情页继续通过 TanStack Query 获取数据。
2. 删除操作：`ConfirmDialog` 触发 → `onConfirm` 调用原 mutation；删除期间确认按钮 `confirmDisabled`。
3. 评论提交：`Textarea` 受控绑定原 `comment` state，提交逻辑不变。
4. 项目列表路由：`router.tsx` 的 import 指向 `@/modules/pm/pages/ProjectListPage`，路由路径不变。
5. 标题跳转：`Link` 渲染为真实锚点，Ctrl/Cmd + 点击等浏览器默认行为恢复；原 `navigate` 调用移除。

## 5. 异常与边界

- `ConfirmDialog` 的标题保留原 `window.confirm` 的主体文案。项目列表把原文案中的「删除后不可恢复」拆到 `description`；事项详情原文案没有该句，本次在 `description` 中显式补充，删除不可恢复的语义不丢失。
- 删除 mutation pending 时确认按钮禁用，避免重复提交。
- `formatDateTime` 对 `undefined` / `null` / 空串统一返回 `—`，与原页面 `formatTime` 一致。
- 迁移 `ProjectListPage` 只改文件位置与 import，不改导出名，避免路由编译失败。

## 6. 兼容性与迁移

- 无数据库、接口或持久化变更。
- 无用户可见功能变更；视觉仅有两处确认弹窗从浏览器原生样式变为站内 `AlertDialog`，且事项详情弹窗补充了「删除后不可恢复」的说明文案。
- 项目列表确认弹窗标题去掉了原文案开头的「确认」二字（「确认删除项目「x」？」→「删除项目「x」？」），语义不变。
- 死文件删除不影响构建：Vite 仅打包被 import 的模块，且已确认无引用。

## 7. 验收标准

1. 全仓 grep 不再命中 `pm-theme`、`--pm-`、naive-ui 类名与 PM 目录下的十六进制颜色。
2. PM 页面不再出现 `window.confirm`。
3. PM 页面不再出现裸 `<textarea>`。
4. `ProjectListPage` 位于 `modules/pm/pages/`，`router.tsx` 引用正确。
5. `formatDateTime` 只有一个实现，列表与详情共用。
6. `npx tsc --noEmit` 通过，现有与新增 Vitest 用例全部通过。
