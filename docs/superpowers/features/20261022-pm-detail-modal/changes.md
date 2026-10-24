# PM 事项详情弹窗代码变更（原型阶段）

> 日期：2026-10-24
> 版本：v0.4
> 状态：原型 + 实现完成
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 记录原型阶段改动文件与验证结果 |
| v0.2 | 2026-10-22 | 回填实现改动：新增详情弹窗组件、移除详情路由、标题/看板入口迁移，补测试与验证 |
| v0.3 | 2026-10-24 | 对齐原型布局：双栏 + 属性分组 + 页签改名 + 复制链接 + 完整编辑 + 内联编辑 + Markdown 渲染 |
| v0.4 | 2026-10-24 | 校验修复：单字段更新接口、行内按钮误显、取消编辑重置、描述工具栏、编辑态锁页签、评论 Markdown、Markdown 斜体/代码/mailto |

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

## 2.1 对齐原型的改动（2026-10-24）

| 文件 | 动作 | 说明 |
|---|---|---|
| `frontend/src/modules/pm/components/WorkItemDetailDialog.tsx` | 重写 | 双栏布局、属性分组、页签详情/操作记录/评论（评论计数）、复制链接、完整编辑（标题/描述/优先级/负责人/截止时间/模块，状态走流转）、内联编辑（优先级/负责人/截止时间/模块、状态流转菜单）、只读模式 |
| `frontend/src/modules/pm/utils/markdown.ts` | 新增 | 极简安全 Markdown 渲染：标题/列表/引用/代码块/行内代码/加粗/斜体/http(s) 链接 |
| `frontend/src/modules/pm/utils/markdown.test.ts` | 新增 | 渲染与 XSS 转义用例 |
| `frontend/src/modules/pm/components/WorkItemDetailDialog.test.tsx` | 新增 | 布局、完整编辑、内联优先级、只读用例 |
| `frontend/src/modules/pm/pages/WorkItemListPage.tsx` | 修改 | 传 `readOnly={forbidden}` |
| `frontend/src/modules/pm/pages/WorkItemListPage.test.tsx` | 修改 | 补 countComments mock、页签按 role=tab 选择 |

## 3. 与原型 v1.13 的差异

| # | 差异 | 原因 |
|---|---|---|
| 23 | ~~未提供复制链接~~ | 已补齐 |
| 24 | ~~页签文案/顺序不同~~ | 已改为详情 / 操作记录 / 评论 |
| 25 | 内联编辑覆盖优先级、负责人、截止时间、所属模块与状态流转；报告人、类型、迭代、父事项、标签、关注者、自定义字段仍只读 | 类型与迭代等控件待补；已通过单字段更新接口支持清空 |
| 26 | 遮罩点击关闭与焦点陷阱沿用 Radix 默认 | 可接受 |
| 27 | 独立 404 态仍未实现（404 显示「事项加载失败」）；只读态已由 `readOnly` 支持 | 404 文案后续补 |
| 28 | 旧详情路由命中 `*` → `RouteScreen` 占位 | 路由兜底即此行为 |
| 29 | Markdown 为极简渲染器（标题/列表/引用/代码/加粗/斜体/链接，支持 http(s) 与 mailto），不支持表格、图片 | 不引入第三方依赖 |

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
| 前端测试（弹窗） | `npx vitest run src/modules/pm/components/WorkItemDetailDialog.test.tsx src/modules/pm/utils/markdown.test.ts` | 通过：8 tests |
| 真实应用截图 | — | 未执行：本机后端 8080 未启动 |
| 关闭行为 | 点击顶部 × | URL 仅移除详情参数，列表 10 行仍在 |
| 画板重出 | README §4.1 命令 | 01、28–48、51、53、54 已更新 |
