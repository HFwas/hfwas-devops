# PM 全局布局代码变更（原型阶段）

> 日期：2026-10-22
> 版本：v0.2
> 状态：原型完成；实现未开始
> 关联：[design.md](./design.md)、[test-cases.md](./test-cases.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 记录原型阶段改动与验证 |
| v0.2 | 2026-10-22 | 补充产品选择下拉、产品名称与「侧栏只展示菜单」改动 |

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

## 2. 实现阶段（下一轮）

未改 `frontend/`。实现时需要：

1. `AppShell` 把 header 提到 `SidebarProvider` 之外，跨全屏；
2. 品牌 / 产品切换移入 header 左侧，侧栏只保留菜单；
3. `SidebarInset` 与内容区改为独立滚动，修正 `--header-height` 相关 sticky 偏移；
4. 校验 AppShell / 列表 / 详情弹窗的既有测试。

## 3. 验证结果

| 检查 | 命令/方式 | 结果 |
|---|---|---|
| 脚本语法 | `node --check` | 通过 |
| 页面渲染 | 本地 HTTP + Chrome，1440×900 / 1280×800 | 列表、总览、详情弹窗、设置均共用全局 header |
| 画板重出 | README §4.1 命令，54 张 | 已更新 |
