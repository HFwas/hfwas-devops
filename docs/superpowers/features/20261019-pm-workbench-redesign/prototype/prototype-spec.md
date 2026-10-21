# PM 工作台原型规格（人读版）

> 日期：2026-10-19
> 版本：v1.0
> 机器可读版本：[prototype-spec.json](./prototype-spec.json)
> 功能设计：[../design.md](../design.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 初版：定义 PM 工作项列表、项目列表、筛选、展示配置、批量操作与状态矩阵 |

---

## 1. 目标与范围

本原型验证 PM 工作台改版的核心结构：**统一列表页骨架 + 搜索 + 组合筛选 + 视图页签 + 可配置列表 + 服务端分页 + 批量操作**。原型覆盖 8 个页面状态、5 个弹层，全部可通过 URL 直达。

不在本版：看板、迭代燃尽、工时报备、权限配置页面。

## 2. 视口与 Token

| 项 | 值 |
|----|----|
| 参考视口 | 1440 × 900 @2x |
| 侧栏 | 256px，inset 风格 |
| 顶栏 | 56px |
| 内容区 | 左右 24px，纵向 12px 节奏 |
| 圆角 | `--radius: 0.5rem` |
| 主色 | `oklch(0.55 0.22 235)` |
| 字体 | 系统 sans；编号与标识用 Maple Mono |
| 行高 | 标准 44px / 紧凑 36px |

颜色、圆角、状态色全部使用语义 token，禁止业务层写死色值。

## 3. 页面

| ID | 页面 | 路由（规划） | 状态 |
|----|------|--------------|------|
| P01 | 工作项列表 | `/pm/projects/:projectId/items/:typeCode` | default / filtered / selected / dense / long |
| P02 | 工作项列表 · 空态 | 同上 | empty |
| P03 | 工作项列表 · 加载 | 同上 | loading |
| P04 | 工作项列表 · 失败 | 同上 | error |
| P05 | 工作项列表 · 无权限 | 同上 | permission |
| P06 | 工作项列表 · 大数据量 | 同上 | dense |
| P07 | 工作项列表 · 长文本 | 同上 | long |
| P08 | 项目列表 | `/pm/projects` | default |

### 3.1 工作项列表信息层级

```text
页头：标题 / 项目 / 排序说明 / 更新时间            [导入] [导出] [新建工作项]
视图页签：全部工作项 | 我的待办 | 本周到期 | 缺陷攻坚 | + 另存为
工具栏：搜索 | 筛选 | 显示 | 排序 | 类型 | 刷新        标准/紧凑  共 N 条
条件行：状态 是 进行中 ×   优先级 是 高 ×   + 添加筛选   清空全部
批量栏（选中时）：已选当前页 N 项 … 改状态 改负责人 导出 删除
表格：选择 | 编号 | 类型 | 标题 | 状态 | 优先级 | 负责人 | 截止时间 | 更新时间 | 操作
分页：共 N 条 · 第 x / y 页   每页 10 条（可切换 20/50/100）      上一页 页码 下一页 跳至 N 页
```

列宽：选择 36、编号 104、类型 72、标题弹性、状态 96、优先级 72、负责人 128、截止 92、更新 120、操作 44。

## 4. 弹层

| ID | 名称 | 触发 | 状态 |
|----|------|------|------|
| O01 | 筛选面板 | P01「筛选」 | default |
| O02 | 展示设置 | P01「显示」 | default |
| O03 | 批量删除确认 | P01「删除」 | default |
| O04 | 快速预览抽屉 | P01 行「预览」/ 标题 | default |
| O05 | 新建工作项 | P01「新建工作项」 | default / invalid |

## 5. 组件与实现映射

| 组件 | 目标位置 | 复用范围 |
|------|----------|----------|
| `ListPageShell` | `components/console/ListPageShell.tsx` | PM 项目 / 工作项，后续其他模块 |
| `SearchInput` | `components/console/SearchInput.tsx` | 所有列表 |
| `FilterBar` | `components/console/FilterBar.tsx` | 所有列表 |
| `FilterBuilder` | `modules/pm/components/FilterBuilder.tsx` | PM 工作项 / 项目 |
| `ViewTabs` | `modules/pm/components/ViewTabs.tsx` | PM 工作项 |
| `DisplayMenu` | `modules/pm/components/DisplayMenu.tsx` | PM 工作项 |
| `DataTable`（扩展） | `components/console/DataTable.tsx` | 保持向后兼容 |
| `BulkActionBar` | `components/console/BulkActionBar.tsx` | 所有可选列表 |
| `Pagination`（扩展） | `components/console/Pagination.tsx` | 保持向后兼容 |
| `QuickViewDrawer` | `components/console/QuickViewDrawer.tsx` | PM 工作项、流水线运行 |
| `StatusPill` / `PriorityIcon` | `modules/pm/components/` | PM 各页 |
| `UserAvatar` | `components/console/UserAvatar.tsx` | 全站 |
| `EmptyState` / `ErrorState` / `SkeletonRows` / `NoPermissionState` | `components/console/` | 所有列表 |
| `useListUrlState` | `modules/pm/utils/useListUrlState.ts` | PM 列表 |

## 6. 查询状态与 URL

| 参数 | 含义 | 示例 |
|------|------|------|
| `q` | 搜索关键词 | `q=Helm` |
| `view` | 视图 | `view=mine` |
| `c_status` | 状态筛选（多值逗号） | `c_status=in_progress,blocked` |
| `c_priority` | 优先级筛选 | `c_priority=high` |
| `c_assignee` | 负责人筛选 | `c_assignee=陈思远` |
| `c_type` | 类型筛选 | `c_type=bug` |
| `sort` | 排序 `field:ASC|DESC` | `sort=priority:ASC` |
| `cols` | 列顺序 | `cols=key,type,title,status` |
| `density` | 密度 | `density=compact` |
| `page` / `size` | 分页 | `page=2&size=50` |
| `sel` | 当前页选中项 | `sel=2,5,8` |
| `state` | 页面状态 | `empty/loading/error/permission/dense/long/selected` |
| `overlay` | 弹层 | `filter/columns/delete/detail/create/saveview` |
| `theme` | 主题 | `dark` |
| `capture` | 截图模式 | `1` |

规则：

1. 查询条件变化时 `page` 重置为 1。
2. 搜索防抖 350ms；回车立即生效。
3. 视图切换保留列配置与密度。
4. 选中仅限当前页，翻页清空。

## 7. 状态矩阵

| 状态 | 表现 | URL |
|------|------|-----|
| 默认 | 10 行数据、分页 128 条 | `#/list` |
| 筛选 | 条件 chip、结果计数、脏标记 | `#/list?c_status=in_progress&c_priority=high&dirty=1` |
| 选中 | 3 行高亮、批量栏出现 | `#/list?state=selected` |
| 空态 | 图标 + 文案 + 新建 / 清空搜索 | `#/list?state=empty` |
| 加载 | 表头保留、8 行骨架 | `#/list?state=loading` |
| 失败 | 503 说明 + 重试 + 请求编号 | `#/list?state=error` |
| 无权限 | 锁图标 + 申请权限，隐藏新建/筛选/分页 | `#/list?state=permission` |
| 大数据量 | 2486 条、紧凑密度、125 页 | `#/list?state=dense` |
| 长文本 | 超长标题省略、长负责人名截断 | `#/list?state=long` |
| 校验失败 | 顶部汇总 + 字段级错误 | `#/list?overlay=create&state=invalid` |
| 危险操作 | 删除 N 个，按钮写明后果 | `#/list?state=selected&overlay=delete` |

## 8. 验收标准

| ID | 标准 |
|----|------|
| AC-01 | 一屏可见 8 个业务字段，分页显示总数与每页条数 |
| AC-02 | 搜索过滤生效，空结果可清空 |
| AC-03 | 筛选面板可选字段/运算符/值，应用后生成可删除 chip |
| AC-04 | 修改查询后视图页签出现脏标记 |
| AC-05 | 列可隐藏、排序、恢复默认；密度可切换 |
| AC-06 | 表头排序三态切换并显示箭头 |
| AC-07 | 分页支持每页条数、页码、跳页 |
| AC-08 | 批量删除二次确认写明数量 |
| AC-09 | 快速预览抽屉展示关键字段，关闭后列表状态保留 |
| AC-10 | 空/加载/失败/无权限四态可直达 |
| AC-11 | 长标题省略且不撑破列宽 |
| AC-12 | 无权限隐藏新建、筛选应用与批量操作 |

## 9. 假设

| 编号 | 假设 | 需要确认 |
|------|------|----------|
| A1 | 分页响应补负责人显示名 | 后端 |
| A2 | `end_time` 作为系统字段加入查询 | 后端 / 产品 |
| A3 | 批量操作首版逐条调用 | 后端 |
| A4 | 保存视图接口可用 | 后端 |
| A5 | 跨页选择不做，批量栏注明「当前页」 | 产品 |
