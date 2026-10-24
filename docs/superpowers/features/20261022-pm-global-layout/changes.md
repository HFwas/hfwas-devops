# PM 全局布局代码变更（原型阶段）

> 日期：2026-10-24
> 版本：v0.4
> 状态：原型 + 实现完成
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 记录原型阶段改动与验证 |
| v0.2 | 2026-10-22 | 补充产品选择下拉、产品名称与「侧栏只展示菜单」改动 |
| v0.3 | 2026-10-24 | 回填 React 实现改动、偏差与验证结果 |
| v0.4 | 2026-10-24 | 校验修复：补帮助/通知、侧栏高度 `!important` 覆盖、编辑器高度、偏差 4–6；新增 §5 校验结果 |

---

## 1. 变更清单

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `docs/product/06-prototype/pm/index.html` | 修改 | §2 | `.app` 改为纵向；新增 `.app-body`；topbar 提到 shell 顶层并加入产品选择下拉与产品名称；侧栏移除品牌与项目切换块；`main` 独立滚动 |
| `docs/product/06-prototype/pm/prototype-spec.md` | 修改 | §2 | 升 v1.12：信息层级改为全局 header + 产品菜单 + 内容 |
| `docs/product/06-prototype/pm/prototype-spec.json` | 修改 | §2 | meta 1.12；新增 `shell.globalHeader` |
| `docs/product/06-prototype/pm/README.md` | 修改 | — | 升 v1.17：变更记录与评审记录 |
| `docs/product/06-prototype/pm/prototype-contract.md` | 修改 | §2 | 升 v0.18：新增全局 header 必须一致项 |
| `docs/product/06-prototype/pm/shots/*.png` | 重出 | — | 全部 54 张按新布局重拍 |

## 2. 实现改动

| 文件 | 动作 | 对应设计 | 说明 |
|---|---|---|---|
| `frontend/src/components/console/AppShell.tsx` | 修改 | §2/§2.1 | `SidebarProvider` 改为纵向占满视口；header 作为第一行跨全屏；产品切换器、产品名、面包屑依次排列；补帮助 / 通知按钮；主体为「侧栏 + SidebarInset」行，内容区独立滚动 |
| `frontend/src/components/console/ProductSwitcher.tsx` | 修改 | §2.1 | 新增 `variant="header"` 触发器（H 标记 + HFWAS DevOps / 产品选择 + chevron）；保留旧 sidebar 变体 |
| `frontend/src/components/console/AppSidebar.tsx` | 修改 | §2.1 | 移除 `SidebarHeader` 与产品切换器；`Sidebar` 固定容器 `top-(--header-height)` 并 `h-[...]!` 覆盖 `h-svh`，内容只保留菜单 |
| `frontend/src/modules/pipeline/pages/PipelineEditorPage.tsx` | 修改 | §2.1 | 编辑器高度改为 `calc(100svh - header - 3rem)`，补上 SidebarInset 的 `m-2` |
| `frontend/src/components/console/DetailShell.tsx` | 修改 | §2.1 | sticky 偏移由 `top-(--header-height)` 改为 `top-0`（内容区已是独立滚动容器） |
| `frontend/src/components/console/AppShell.test.tsx` | 修改 | TC-08~11 | 产品名/产品选择断言移到 header；侧栏只断言菜单；面包屑首项为产品内路径 |

## 3. 偏差

| # | 偏差 | 原因 |
|---|---|---|
| 1 | 面包屑中的项目名不可点击切换项目 | 项目下拉未实现；切项目仍走侧栏「全部项目」→ 项目列表 |
| 2 | `ProductSwitcher` 同时保留 `sidebar` 变体但已无调用方 | 降低改动面，后续可删 |
| 3 | 移动端侧栏通过 header 的 `SidebarTrigger` 打开 Sheet，位置与桌面不同 | shadcn Sidebar 既有行为 |
| 4 | header 保留主题切换与租户切换（原型 header 只有帮助/通知/账号） | 现有产品能力，不能移除 |
| 5 | 侧栏未复刻原型的底部「帮助中心 + 用户卡」 | 帮助入口移到 header 帮助按钮，用户入口在 header 头像菜单 |
| 6 | 内容区顶部有 SidebarInset 的 8px 底色缝隙与圆角卡片，原型 `.main` 紧贴 header | shadcn inset 变体既有视觉，属 A2 允许差异 |

## 4. 验证结果

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| 脚本语法 | `node --check` | 通过 |
| 页面渲染 | 本地 HTTP + Chrome，1440×900 / 1280×800 | 列表、总览、详情弹窗、设置均共用全局 header |
| 画板重出 | README §4.1 命令，54 张 | 已更新 |
| 前端类型检查 | `npx tsc --noEmit` | 通过 |
| 前端测试 | `npm test` | 通过：80 files / 334 passed / 1 skipped |
| 前端构建 | `npm run build` | 通过（既有 chunk 体积警告） |
| 真实应用截图 | — | 未执行：本机后端未启动 |

## 5. 校验结果

### 第 1 次独立校验（2026-10-24）

结论：**不通过**。问题与处理：

| # | 问题 | 级别 | 处理 |
|---|---|---|---|
| 1 | header 缺「帮助 / 通知」，与原型与契约 A2 不一致 | P0 | 已补 `CircleHelp` / `Bell`（通知带未读点，`aria-label="通知（有未读）"`） |
| 2 | 侧栏高度类被基础 `h-svh` 覆盖，底部约 56px 菜单不可达 | P0 | 改用 `h-[calc(100svh-var(--header-height))]!`；构建产物含 `!important`，实测 1440×900 bottom=900、1440×720 bottom=720 |
| 3 | 流水线编辑器高度多 16px，出现外层滚动条 | P1 | 公式改为 `calc(100svh - header - 3rem)`；实测 1440×900 `main` client=scroll=828 |
| 4 | 偏差登记不全（主题/租户保留、侧栏底部未复刻、inset 视觉差异） | P2 | 已补偏差 4–6 |
| 5 | 契约 §9 冻结记录过期 | P2 | 改为 v1.13 / 2026-10-24 |
| 6 | 文档文首日期与引用版本未更新 | P3 | 已更新为 2026-10-24、README v1.19 / 契约 v0.20 |
| 7 | 真实应用截图未执行 | P3 | 保持未完成，如实登记 |

### 第 2 次独立校验（2026-10-24）

结论：**通过**。P0-1、P0-2、P1-3 在代码与构建产物层面均确认解决；changes / test-cases / 契约 §9 自洽。复验后补充：

- 帮助 / 通知位于页面级 `actions` 之后、主题与账号之前（原型无页面级 actions，属实现扩展），已在文档说明；
- 未读点补无障碍语义（`aria-label="通知（有未读）"`）；
- `AppShell.test.tsx` 增加侧栏定位类断言（`top-(--header-height)`、`!h-[...]`），防止再次被基础类覆盖。
