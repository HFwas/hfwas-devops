# PM 事项详情改为弹窗

> 日期：2026-10-22
> 版本：v0.2
> 状态：已实现（原型 v1.11 + React 实现）
> 关联：[PM 模块总原型](../../../product/06-prototype/pm/README.md) v1.16、[原型规格](../../../product/06-prototype/pm/prototype-spec.md) v1.11、[原型契约](../../../product/06-prototype/pm/prototype-contract.md) v0.17

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-22 | 初版：详情由独立页面改为居中大弹窗，URL 收敛到列表 `detail` 参数 |
| v0.2 | 2026-10-22 | 实现落地：新增 `WorkItemDetailDialog`，删除 `WorkItemDetailPage` 与 `/items/:itemId` 路由，标题/看板入口改为 `?detail=` |

---

## 1. 背景与目标

事项详情当前是独立页面 `/pm/projects/:projectId/items/:typeCode/:itemId`，打开后列表上下文（视图、搜索、筛选、分页）全部丢失，返回需要重新定位。

目标：列表内以**居中大弹窗**查看与编辑详情，关闭后列表状态保持不变；详情深链仍可分享。

## 2. 范围

本轮（原型）：`docs/product/06-prototype/pm/index.html` 的详情呈现、URL 与状态参数、画板与规格文档。

非目标：详情字段的完整编辑态（本实现沿用现有概览/评论/活动能力，不做完整编辑表单）。

## 3. 形态与交互

- 弹窗尺寸：宽 `min(1280px, 94vw)`，高 `min(860px, 88vh)`；居中，遮罩 `oklch(0 0 0 / .42)`。
- 结构：顶部条（事项编号、类型、只读标记、复制链接、关闭）× 独立滚动内容区（标题、元信息、页签、双栏主体、编辑条）。
- 打开：点击列表标题、快速预览「打开完整详情」、深链。
- 关闭：右上角 ×、点击遮罩、ESC（实现阶段补）→ 只移除详情相关参数，列表参数保留。
- 详情内的状态流转 / 放弃修改 / 删除弹层叠加在详情弹窗之上；只读态不提供写操作。

## 4. URL 契约

| 参数 | 取值 | 说明 |
|---|---|---|
| `detail` | 事项 id | 打开详情弹窗；为空则关闭 |
| `dstate` | `loading` / `error` / `permission` | 详情自身状态，不影响列表 |
| `dtab` | `detail` / `activity` / `comments` | 详情页签 |
| `edit` / `invalid` / `inline` / `descEdit` / `recent` / `actPage` / `actSize` | 同 v1.10 | 详情编辑与内联状态 |
| `overlay` | `transition` / `discard` / `delete` | 叠加在详情上 |

移除：`#/items/:itemId` 路由。

## 5. 异常与边界

1. `detail` 指向不存在的事项 → 弹窗内展示 404 态，关闭后回到列表。
2. 详情关闭时不得清空 `type/view/q/f/page/size` 等列表参数。
3. 列表与详情同名的 `state`/`tab` 参数已拆分（`dstate`/`dtab`），避免互相污染。
4. 只读态叠加删除/流转弹层 URL 时，强制展示只读提示。

## 6.1 实现映射

| 原型 | 实现 |
|---|---|
| 弹窗外壳、遮罩、顶部条 | `WorkItemDetailDialog` + `components/ui/dialog`（`DialogContent` 覆盖为 `min(1280px,94vw)` × `min(860px,88vh)`） |
| 顶部条编号/类型/状态/删除/关闭 | 同组件顶部条 + `ConfirmDialog` 删除；关闭使用 Radix Dialog 自带关闭按钮 |
| 页签详情/操作记录/评论 | `dtab=detail|comments|activity`，标签为「概览 / 评论 / 活动」 |
| 列表标题 → 弹窗 | `columns.tsx` 的 `CellContext.detailHref` 生成 `items/:typeCode?detail=<id>` |
| 看板卡片 → 弹窗 | `BoardPage` 跳转到列表路由并带 `detail` |
| 关闭保留列表状态 | 只删除 `detail` / `dtab`，其余 query 原样保留 |

## 6.2 实现层面的取舍

- 详情页签参数使用 `dtab`：列表 URL 上的 `tab` 已被视图配置抽屉占用，不能复用。
- 详情内的「完整编辑」未实现：现有后端能力只有状态流转、评论与删除；完整字段编辑留到后续。
- 路由 `/items/:typeCode/:itemId` 直接移除，旧链接不兼容（与原型的「移除详情路由」一致）。

## 7. 验收标准

| 编号 | 标准 |
|---|---|
| AC-01 | 打开详情为弹窗，列表仍渲染在背景，遮罩覆盖 |
| AC-02 | `#/workbench?detail=1` 直达弹窗；`#/items/1` 不再是页面 |
| AC-03 | 关闭后 URL 只去掉详情参数，列表视图/搜索/筛选/分页不变 |
| AC-04 | 详情页签、编辑、校验、内联、操作记录分页、评论行为与改版前一致 |
| AC-05 | 加载、失败、不存在状态在弹窗内呈现；只读与独立 404 分支未实现，登记为偏差 27 |
| AC-06 | 1440×900 与 1280×800 下弹窗不溢出，内容区独立滚动 |
| AC-07 | 详情页签写入 `dtab`，不影响列表 `tab`（视图配置页签） |
