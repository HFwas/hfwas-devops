# PM 事项详情弹窗测试用例

> 日期：2026-10-22
> 版本：v0.2
> 状态：原型与实现用例均已执行
> 关联：[design.md](./design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 初版：原型 URL、状态、关闭与回归用例 |
| v0.2 | 2026-10-22 | 补实现用例 TC-14~17 与自动化位置；原型用例仍保留 |

---

## 1. 原型用例

| 编号 | 步骤 | 输入 | 预期 | 验证方式 |
|---|---|---|---|---|
| TC-01 | 打开详情 | `?capture=1#/workbench?detail=1` | 列表背景保留，居中弹窗显示事项详情；顶部条有编号/类型/复制链接/关闭 | 手工 + 画板 28 |
| TC-02 | 旧路由失效 | `#/items/1` | 不再渲染详情页，落到总览 | 手工 |
| TC-03 | 关闭详情 | 弹窗内点 × | URL 只去掉 `detail/dstate/dtab/edit/...`，`type/page/q` 保留 | 手工 |
| TC-04 | 编辑态 | `detail=1&edit=1` | 标题/字段可编辑，底部编辑条固定，页签仅详情 | 画板 29 |
| TC-05 | 校验失败 | `detail=1&edit=1&invalid=1` | 标题与故事点标红，顶部错误提示 | 画板 30 |
| TC-06 | 只读 | `detail=1&dstate=permission` | 隐藏编辑/流转/删除/评论输入，顶部条显示只读 | 画板 31 |
| TC-07 | 页签 | `detail=1&dtab=activity` / `dtab=comments` | 操作记录可翻页；评论可发表 | 画板 39、32 |
| TC-08 | 内联编辑 | `detail=1&inline=status` 等 | 弹窗内弹出编辑器，位置在属性列 | 画板 40–45 |
| TC-09 | 叠加弹层 | `detail=1&overlay=transition` / `delete` | 弹层在详情之上，取消后回到详情 | 画板 33、51 |
| TC-10 | 404 | `detail=999` | 弹窗内 404 态，关闭回列表 | 手工 |
| TC-11 | 加载/失败 | `dstate=loading` / `dstate=error` | 弹窗内骨架/失败态，重试清除 `dstate` | 画板 35、36 |
| TC-12 | 窄屏 | 1280×800 `detail=1` | 弹窗宽度 94vw，双栏不横向溢出 | 画板 53 |
| TC-13 | 快速预览入口 | `overlay=preview&id=1` → 打开完整详情 | 关闭预览并打开详情弹窗 | 手工 |

## 2. 实现用例

| 编号 | 步骤 | 输入 | 预期 | 自动化位置 |
|---|---|---|---|---|
| TC-14 | 打开详情弹窗 | `/pm/projects/10/items/task?detail=9` | 列表渲染，弹窗出现并显示事项标题；`getById(9)` 被调用 | `WorkItemListPage.test.tsx::opensDetailInDialogAndClosesBackToList` |
| TC-15 | 切换详情页签 | 弹窗内点「评论」 | URL 写入 `dtab=comments`，列表其余参数不变 | 同上 |
| TC-16 | 关闭详情 | 点弹窗关闭 | URL 移除 `detail`/`dtab`，列表保持 | 同上 |
| TC-17 | 标题链接 | 列表标题单元格 | href 为 `/pm/projects/10/items/task?detail=9`（保留列表参数） | `WorkItemListPage.test.tsx::titleLinksToDetailParam`、`columns.test.tsx::linksWithItemType` |
| TC-18 | 旧路由 | 访问 `/pm/projects/10/items/task/9` | 不再匹配详情路由，落到 `RouteScreen` 占位 | 手工（路由已移除） |
