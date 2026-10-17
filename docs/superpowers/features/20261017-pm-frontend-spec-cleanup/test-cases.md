# 项目管理前端规范整改测试用例

> 日期：2026-10-17
> 版本：v0.2

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：覆盖死代码清理、共享组件替换、文件迁移与时间格式化 |
| v0.2 | 2026-10-17 | 按独立校验反馈把自动化测试位置改为 vitest 实际全名格式 |
| v0.3 | 2026-10-17 | 统一 TC-04、TC-05 的测试名分隔符为 vitest 实际格式 |

---

## 1. 自动化用例

| 编号 | 前置条件 | 步骤 | 输入 | 预期结果 | 自动化位置 |
|------|----------|------|------|----------|------------|
| TC-01 | 已安装依赖 | 运行 `npx vitest run src/modules/pm/utils/format.test.ts` | `undefined` | 返回 `—` | `frontend/src/modules/pm/utils/format.test.ts::formatDateTime > 空值返回占位符` |
| TC-02 | 同上 | 同上 | `null` | 返回 `—` | `frontend/src/modules/pm/utils/format.test.ts::formatDateTime > 空值返回占位符` |
| TC-03 | 同上 | 同上 | `''` | 返回 `—` | `frontend/src/modules/pm/utils/format.test.ts::formatDateTime > 空值返回占位符` |
| TC-04 | 同上 | 同上 | `'2026-10-17T12:34:56'` | 返回 `2026-10-17 12:34:56` | `frontend/src/modules/pm/utils/format.test.ts::formatDateTime > 把 ISO 时间转成空格分隔` |
| TC-05 | 同上 | 同上 | `'2026-10-17T12:34:56.789'` | 返回 `2026-10-17 12:34:56` | `frontend/src/modules/pm/utils/format.test.ts::formatDateTime > 截断毫秒` |
| TC-06 | 已安装依赖 | 运行 `npx vitest run src/modules/pm/statusTone.test.ts` | 现有用例 | 全部通过，工作流 tone 与优先级标签行为不变 | `frontend/src/modules/pm/statusTone.test.ts` |
| TC-07 | 已安装依赖 | 运行 `npx tsc --noEmit` | 当前分支 | 无类型错误 | 命令验证 |
| TC-08 | 已安装依赖 | 运行 `npm test` | 全量前端测试 | 全部通过 | 命令验证 |

## 2. 静态检查用例

| 编号 | 步骤 | 预期结果 |
|------|------|----------|
| TC-09 | `grep -rn "pm-theme\|--pm-\|n-data-table\|n-button\|n-tag\|n-input\|n-menu\|n-badge" frontend/src/modules/pm frontend/src/pages` | 无输出 |
| TC-10 | `grep -rnE "#[0-9a-fA-F]{6}" frontend/src/modules/pm frontend/src/pages` | 无输出 |
| TC-11 | `grep -rn "window.confirm" frontend/src/modules/pm frontend/src/pages/ProjectListPage.tsx` | 无输出（文件已迁移） |
| TC-12 | `grep -rn "<textarea" frontend/src/modules/pm` | 无输出 |
| TC-13 | `grep -rn "formatTime" frontend/src/modules/pm` | 无输出，统一使用 `formatDateTime` |
| TC-14 | 检查 `frontend/src/modules/pm/pages/ProjectListPage.tsx` 存在且 `frontend/src/pages/ProjectListPage.tsx` 不存在 | 成立 |

## 3. 手工验证用例

> 需要可登录前端与后端 API；若环境不可用，记录为「未执行」。

| 编号 | 前置条件 | 步骤 | 预期结果 |
|------|----------|------|----------|
| TC-15 | 已登录，存在至少一个项目 | 打开 `/pm/projects`，点击任意项目删除图标 | 弹出站内 `AlertDialog`，标题/说明为删除项目文案，不是浏览器原生 confirm |
| TC-16 | 同上 | 在 TC-15 弹窗点「取消」 | 弹窗关闭，项目未被删除 |
| TC-17 | 同上 | 在 TC-15 弹窗点「确认」 | 项目被删除，列表刷新并提示「项目已删除」 |
| TC-18 | 已登录，进入任意事项详情 | 点击右上角「删除」 | 弹出站内 `AlertDialog`；取消不删除，确认后删除并返回列表 |
| TC-19 | 进入事项详情「评论」Tab | 在输入框输入文本并发表 | 输入框样式与全站 `Textarea` 一致，提交成功后评论出现在列表，输入框清空 |
| TC-20 | 打开 `/pm/projects`，观察浏览器地址栏链接 | 使用 Ctrl/Cmd + 点击项目名 | 在新标签页打开该项目事项列表（`Link` 锚点行为） |
| TC-21 | 打开事项列表/详情 | 查看更新时间列与详情字段 | 时间格式仍为 `YYYY-MM-DD HH:mm:ss`，空值显示 `—` |
| TC-22 | 切换亮色/暗色主题 | 重新查看删除确认弹窗与评论输入框 | 颜色跟随全站 token，无模块私有色或固定亮色背景 |

## 4. 回归范围

- PM 项目列表、事项列表、事项详情、看板、工作流、项目监控页面可正常渲染。
- `npx tsc --noEmit` 与 `npm test` 通过。
- 其它模块未受影响（本整改不修改其代码）。
