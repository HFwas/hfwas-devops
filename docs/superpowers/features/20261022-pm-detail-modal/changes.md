# PM 事项详情弹窗代码变更（原型阶段）

> 日期：2026-10-22
> 版本：v0.2
> 状态：原型 + 实现完成
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 记录原型阶段改动文件与验证结果 |
| v0.2 | 2026-10-22 | 回填实现改动：新增详情弹窗组件、移除详情路由、标题/看板入口迁移，补测试与验证 |

---

## 1. 变更清单

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `docs/product/06-prototype/pm/index.html` | 修改 | §3/§4 | 详情改为 `.detail-backdrop` + `.detail-modal`；`detailPage` 只返回内容；新增 `detailModal`；`detailItem` 读 `?detail`；详情状态 `dstate`、页签 `dtab`；新增 `closeDetail()` 与 `close-detail` 事件；面包屑追加事项编号 |
| `docs/product/06-prototype/pm/prototype-spec.md` | 修改 | §4 | 升 v1.11：P06 改为弹窗、URL 参数与状态矩阵更新 |
| `docs/product/06-prototype/pm/prototype-spec.json` | 修改 | §4 | meta 1.11；P06 route/hash/overlay；O10–O12 链接 |
| `docs/product/06-prototype/pm/README.md` | 修改 | §3 | 升 v1.15：路由、画板清单、评审记录 |
| `docs/product/06-prototype/pm/prototype-contract.md` | 修改 | §4 | 升 v0.16：新增弹窗必须一致项与 URL 契约 |
| `docs/product/06-prototype/pm/shots/{01,28–48,51,53,54}-*.png` | 重出 | — | 详情画板改为弹窗；overview 计数与 chip 更新 |

## 2. 实现改动

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `frontend/src/modules/pm/components/WorkItemDetailDialog.tsx` | 新增 | §3/§6.1 | 居中大弹窗：顶部条（编号/类型/状态/删除）、页签概览/评论/活动、独立滚动内容；数据与流转/评论/删除 mutation 从原详情页迁移 |
| `frontend/src/modules/pm/pages/WorkItemDetailPage.tsx` | 删除 | §6.2 | 不再有独立详情页 |
| `frontend/src/router.tsx` | 修改 | §6.2 | 移除 `pm/projects/:projectId/items/:typeCode/:itemId` 路由与导入 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 修改 | §6.1 | 读取 `detail`/`dtab`；挂载 `WorkItemDetailDialog`；关闭只移除详情参数；`CellContext` 注入 `detailHref` |
| `frontend/src/modules/pm/utils/columns.tsx` | 修改 | §6.1 | 标题单元格改用 `ctx.detailHref(item)`，保留列表参数 |
| `frontend/src/modules/pm/pages/BoardPage.tsx` | 修改 | §6.1 | 卡片链接改为列表路由 + `?detail=` |
| `frontend/src/modules/pm/pages/WorkItemListPage.test.tsx` | 修改 | TC-14~17 | 新增打开/切页签/关闭/标题链接用例，补详情 API mock |
| `frontend/src/modules/pm/utils/columns.test.tsx` | 修改 | TC-17 | 断言详情参数链接 |

## 3. 与原型 v1.11 的差异

| # | 差异 | 原因 |
|---|---|---|
| 23 | 实现使用 Radix Dialog 的默认关闭按钮，未额外做「复制链接」 | 当前无复制工具函数；后续补 |
| 24 | 详情页签为「概览 / 评论 / 活动」（顺序与文案均不同），原型为「详情 / 操作记录 / 评论」 | 沿用实现既有能力与文案，避免与后端 activity/comment 语义混淆；URL 值已是 `detail/comments/activity` |
| 25 | 未实现原型的双栏主体、右栏属性、编辑条、完整字段编辑、内联更新与 Markdown 描述编辑器 | 现有后端能力只有状态流转、评论、删除；实现为单栏概览 |
| 26 | 未做遮罩点击关闭与 ESC 之外的焦点陷阱细节 | Radix Dialog 自带 ESC 与焦点管理；点击遮罩关闭由 `onOpenChange` 覆盖 |
| 27 | 只读态与独立 404 态未呈现：`getById` 404 会显示「事项加载失败」，`dstate` 参数未在 React 侧消费 | 本轮未接项目角色接口，也无 404 专用文案；后续补 |
| 28 | 旧详情路由命中 `*` → `RouteScreen` 占位，不是 404 文案 | 路由兜底即此行为，未额外加 404 页 |

## 4. 验证结果

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| 脚本语法 | 提取 `<script>` 后 `node --check` | 通过 |
| 原型渲染 | 本地 HTTP + Chrome，1440×900 与 1280×800 | 默认/编辑/校验/只读/页签/内联/弹层/404/加载/失败 均正常 |
| URL 冒烟 | Playwright 逐条加载 16 个详情/列表 URL | 详情 URL 均出现弹窗，列表与总览无弹窗；0 个页面异常 |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过：80 files / 333 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| 详情参数保持 | `usePmListUrlState` + 列表用例 | 分页夹取等列表写入后 `detail` 仍在；切页签/关闭后 `page` 等参数保留（`WorkItemListPage.test.tsx::keepsDetailWhenListWrites`） |
| 真实应用截图 | — | 未执行：本机后端 8080 未启动 |
| 关闭行为 | 点击顶部 × | URL 仅移除详情参数，列表 10 行仍在 |
| 画板重出 | README §4.1 命令 | 01、28–48、51、53、54 已更新 |
