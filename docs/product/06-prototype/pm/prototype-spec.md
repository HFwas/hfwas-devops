# PM 模块总原型规格（人读版）

> 日期：2026-10-22
> 版本：v1.11
> 机器可读版本：[prototype-spec.json](./prototype-spec.json)
> 功能设计：[20261019-pm-list-config-bulk/design.md](../../../superpowers/features/20261019-pm-list-config-bulk/design.md)
> 关联原型：[工作台改版](../../../superpowers/features/20261019-pm-workbench-redesign/prototype/index.html)、[事项类型](../../../superpowers/features/20261019-pm-work-item-types/prototype/index.html)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 初版：项目类型导航、配置驱动的显示列/搜索/筛选、批量编辑、导入导出、批量操作配置与模块总览 |
| v1.1 | 2026-10-19 | 筛选升级为字段 + 操作符 + 值 + AND/OR + 嵌套条件组，增加表达式预览与缺值校验 |
| v1.2 | 2026-10-19 | 新增可编辑事项详情：双栏布局、标题/描述/属性编辑、状态流转、评论与活动、校验与未保存确认 |
| v1.3 | 2026-10-20 | 详情描述支持 Markdown：阅读渲染、编辑/预览页签、工具栏插入、HTML 转义与链接白名单 |
| v1.4 | 2026-10-20 | 详情页签上移并改为详情/操作记录/评论；默认详情，右栏属性常驻，编辑态禁止切页签 |
| v1.5 | 2026-10-20 | 详情右栏支持单字段内联更新：状态/优先级点击即改，日期/数字/标签行内保存 |
| v1.6 | 2026-10-20 | 详情全部业务字段支持单字段更新；每次变更追加操作记录；非法状态保持置灰 |
| v1.7 | 2026-10-20 | 操作记录分页展示，默认最新在前，支持每页 5/10/20 条与页码切换 |
| v1.8 | 2026-10-20 | 评审修复：分页 10/20/50/100 默认 10 并自洽；截止时间 key 改 `end_time`；导出范围与字段可交互；只读态封堵写操作；评论/预览接入 Markdown；批量动作遵守启停；统一总数与列宽；多列排序 |
| v1.9 | 2026-10-20 | 复评修复：导出字段勾选、画板 42 `end_time`、页大小文案、AC-29 字段名；删除动作启用态与直链拦截；保存配置/视图、跳页校验、预览 404 |
| v1.10 | 2026-10-22 | 布局评审：删除页头（类型标题 + 平台/视图/总数/更新时间）；导入/导出/新建下移到视图页签行右侧；移除「+ 添加筛选」条件行，筛选入口只保留工具栏「筛选」，已应用条件仅在存在时展示 |
| v1.11 | 2026-10-22 | 事项详情由独立页面改为居中大弹窗：URL 收敛为 `#/workbench?detail=<id>`，移除 `/items/:itemId` 路由；详情状态改用 `dstate`、页签改用 `dtab`；页面数 6、弹层 13 |

---

## 1. 范围

本原型是 PM 模块的唯一总入口，验证：

- 项目启用类型进入左侧导航，并展示各类型数量；
- 显示列、搜索字段、筛选字段由视图配置驱动；
- 批量编辑、导入、导出、删除与动作配置；
- 模块总览把已有工作台与事项类型原型收敛到同一入口。

不在本版：看板列映射、迭代燃尽、工时、自动化规则。

## 2. 页面

| ID | 页面 | 路由（规划） | 状态 |
|----|------|--------------|------|
| P00 | PM 模块总原型 | `/pm/overview` | default |
| P01 | 项目工作项列表 | `/pm/projects/:projectId/items/:typeCode` | default / type / empty / loading / error / permission / dense / long / selected |
| P02 | 类型筛选 | 同上 | default |
| P03 | 事项类型管理 | `/pm/projects/:projectId/settings/types` | 外部原型 |
| P04 | 视图与搜索设置 | `/pm/projects/:projectId/settings/views` | default |
| P05 | 批量操作配置 | `/pm/projects/:projectId/settings/bulk-actions` | default |
| P06 | 事项详情弹窗（叠加在工作项列表上） | `#/workbench?detail=:itemId` | default / edit / invalid / permission / activity / comments / inline / loading / error |

### 2.1 工作台信息层级

```text
左侧：项目切换 / 原型总览 / 工作项类型（全部 + 7 个启用类型 + 数量）/ 协作 / 配置
视图页签行：研发待办 | 我的事项 | 本周到期 | 阻塞风险 | + 保存视图     [导入] [导出] [新建]
工具栏：搜索（N 个字段）| 筛选 | 显示 | 排序 | 密度            共 N 条
条件行：仅在已应用筛选时出现（条件 chip / 清空 / 筛后 N 条）
批量栏：已选 N 项 | 批量编辑 | 状态流转 | 导出 | 删除 | 取消
表格：选择 / 配置列（默认 8 列）/ 操作
分页：总数 / 每页 10（可切换 20/50/100）/ 页码 / 跳页
```

### 2.2 类型导航

| 类型 | code | 数量 | 来源 |
|------|------|------|------|
| 全部工作项 | all | 128 | — |
| 需求 | requirement | 24 | 内置 |
| 任务 | task | 52 | 内置 |
| 缺陷 | bug | 18 | 内置 |
| 迭代 | iteration | 6 | 内置 |
| 测试用例 | test_case | 14 | 内置 |
| 用户故事 | story | 9 | 自定义 |
| 技术债 | tech_debt | 5 | 自定义 |

类型数量合计 128，与「全部工作项」一致。数据来源为项目类型方案，顺序即 scheme 顺序。

## 3. 弹层

| ID | 名称 | 触发 | 状态 |
|----|------|------|------|
| O01 | 视图配置 | 工具栏「显示」/ 搜索范围 chip | columns / search / filter |
| O02 | 批量编辑 | 批量栏「批量编辑」「状态流转」 | default / invalid |
| O03 | 导入事项 | 视图页签行「导入」 | upload / mapping / result |
| O04 | 导出事项 | 视图页签行「导出」/ 批量栏「导出」 | query / selected / all |
| O05 | 保存视图 | 视图页签「保存视图」 | default |
| O06 | 批量动作编辑 | P05「配置」 | default |
| O07 | 筛选构建器 | 工具栏「筛选」 | default / invalid |
| O08 | 批量删除确认 | 批量栏「删除」 | default |
| O09 | 事项快速预览 | 行预览图标 | default |
| O10 | 状态流转 | P06「状态流转」 | default |
| O11 | 放弃修改 | P06 返回/取消（编辑态） | default |
| O12 | 删除事项 | P06「删除」 | default |

## 4. 视图配置

| Tab | 配置项 | 行为 |
|-----|--------|------|
| 显示列 | 勾选列、上移/下移、恢复默认；标准/紧凑；默认排序 | 立即重绘表格 |
| 搜索字段 | 勾选参与搜索的字段，至少 1 个 | 搜索提示与匹配范围同步 |
| 筛选字段 | 勾选筛选面板字段 | 筛选面板只展示勾选项 |

配置与筛选、排序一起保存为视图；修改后页签出现脏标记。

## 4.1 筛选构建器

- 字段来自视图配置的「筛选字段」。
- 根级与条件组各自只能选择一种关系：AND 或 OR。
- 最多 2 层：根 + 条件组；组内可添加/删除条件。
- 操作符按字段类型变化：文本（包含/不包含/等于/不等于/为空）、枚举（属于/不属于/是/不是/为空）、用户、日期（早于/晚于/区间/今天/本周）、数字（大于/小于/区间）。
- 值编辑器：`属于` 使用多值 chip；`在区间` 使用两个输入；`为空/今天/本周` 无需值。
- 实时显示预计匹配条数与表达式预览。
- 缺值条件阻止应用，顶部汇总提示。
- 快速筛选：我的工作项、本周到期、阻塞风险、未分配。

```text
根：AND
├─ 状态 属于 [进行中, 已阻塞]
├─ 优先级 属于 [最高, 高]
└─ 组：OR
   ├─ 负责人 是 陈思远
   └─ 负责人 是 林小满
```

## 4.2 事项详情弹窗

- 双栏：左侧为当前页签内容，右侧为常驻高频属性。
- 呈现为居中大弹窗：宽 `min(1280px, 94vw)`、高 `min(860px, 88vh)`，顶部条固定事项编号/类型/复制链接/关闭，内容区独立滚动，列表作为背景保留。
- 打开方式：列表标题、快速预览「打开完整详情」、深链 `#/workbench?detail=<id>`；关闭只移除 `detail` 等详情参数，列表的视图/搜索/筛选/分页状态不丢。
- 页签固定在标题下方、内容区上方：详情 / 操作记录 / 评论；默认详情。
- 阅读态点击标题、描述或属性行进入编辑态；顶部「编辑」进入完整编辑态。
- 标题必填；描述支持 Markdown；属性含状态、优先级、负责人、截止时间、迭代、故事点、风险、标签。
- 状态流转只展示工作流允许的目标状态。
- 操作记录与评论为顶层页签，不再放在描述卡片内部；评论支持发表与追加。
- 操作记录默认最新在前并分页，支持每页 5/10/20 条、页码与翻页；新增记录出现在第一页顶部。
- 编辑态只保留详情页签并提示保存后可查看其他页签。
- 阅读态点击字段打开内联编辑器：标题直接输入；描述打开 Markdown 编辑器；状态/优先级/类型/负责人/报告人/迭代/风险/修复版本/模块/父事项为选项菜单；截止时间/故事点/标签/环境为输入保存；关注者为多选切换。状态受工作流限制，非法目标置灰。
- 每次字段变更追加操作记录；完整编辑按字段逐条记录；评论追加「添加了评论」。
- 校验失败保留编辑态并显示顶部汇总与字段错误。
- 编辑态返回/取消弹出「放弃未保存的修改？」。
- 只读成员隐藏编辑、流转、删除与评论输入。

## 5. 批量操作

| 动作 | 说明 |
|------|------|
| 批量编辑字段 | 字段白名单 → 设值/清空 → 影响预览 → 提交 |
| 批量状态流转 | 走工作流校验，不允许的流转跳过 |
| 复制事项 | 复制标题、描述与字段 |
| 导出所选 | 范围与字段集可选 |
| 删除事项 | 默认关闭，二次确认写明数量 |
| 导入事项 | 模板 → 映射 → 校验 → 执行 |

配置项：启停、允许角色、单次上限、二次确认、可编辑字段、默认导出字段、默认导入模式、审计。

## 6. URL 参数

| 参数 | 取值 | 说明 |
|------|------|------|
| `type` | 类型 code | 左侧类型筛选 |
| `view` | `dev` / `mine` / `week` / `blocked` | 视图 |
| `q` | 文本 | 搜索 |
| `cols` | 字段 key 列表 | 显示列 |
| `search` | 字段 key 列表 | 搜索字段 |
| `ff` | 字段 key 列表 | 筛选字段 |
| `density` | `standard` / `compact` | 密度 |
| `sort` | `field:ASC\|DESC`，逗号分隔多列 | 排序，如 `priority:ASC,updated:DESC` |
| `page` | 数字 | 当前页码 |
| `size` | `10` / `20` / `50` / `100` | 每页条数，默认 10 |
| `sel` | 行 id 列表 | 当前页选中 |
| `scope` | `selected` / `query` / `all` | 导出范围 |
| `ef` | 字段 key 列表 | 导出字段集 |
| `state` | `empty` / `loading` / `error` / `permission` / `dense` / `long` / `selected` | 页面状态 |
| `overlay` | `view-config` / `bulk-edit` / `import` / `export` / `filter` / `delete` / `save-view` / `preview` / `action-edit` / `transition` / `discard` | 弹层 |
| `detail` | 事项 id | 打开事项详情弹窗 |
| `dstate` | `loading` / `error` / `permission` | 详情弹窗自身状态（不影响背后的列表） |
| `tab` | `columns` / `search` / `filter` | 视图配置页签 |
| `dtab` | `detail` / `activity` / `comments` | 详情弹窗页签 |
| `step` | `upload` / `mapping` / `result` | 导入步骤 |
| `field` | 字段 key | 批量编辑当前字段 |
| `id` | 事项 id | 快速预览目标 |
| `disabledActions` | 动作 code 列表 | 演示批量动作关闭 |
| `enabledActions` | 动作 code 列表 | 演示启用默认关闭的动作（如 `delete`） |
| `code` | 动作 code | 动作编辑弹层目标 |
| `ty` / `rp` / `mod` / `parent` / `fix` / `labels` / `watch` / `env` | 文本 | 详情保存后的扩展字段覆盖 |
| `invalid` | `1` | 批量编辑或详情校验失败 |
| `f` | JSON | 筛选条件树（逻辑、条件、条件组） |
| `fpreset` | `advanced` | 应用内置高级筛选，便于直达演示 |
| `preset` | `incomplete` | 缺值条件演示 |
| `filterInvalid` | `1` | 筛选缺值校验失败 |
| `c_status` / `c_priority` / `c_risk` | 值列表 | 简单筛选条件 |
| `descTab` | `write` / `preview` | 详情描述编辑/预览页签 |
| `desc` | `short` | 短描述样例（用于评论/活动截图） |
| `inline` | `status` / `priority` / `type` / `assignee` / `reporter` / `iteration` / `risk` / `end_time` / `storyPoints` / `fixVersion` / `module` / `parent` / `labels` / `watchers` / `environment` / `title` | 直接打开对应字段的内联编辑器 |
| `descEdit` | `1` | 描述单字段 Markdown 编辑 |
| `recent` | `status` | 操作记录追加示例（用于截图） |
| `actPage` | 数字 | 操作记录页码 |
| `actSize` | `5` / `10` / `20` | 操作记录每页条数 |
| `edit` | `1` | 详情编辑态（只读状态下强制失效） |
| `n` / `d` / `s` / `p` / `a` / `end_time` / `it` / `sp` / `risk` | 文本 | 详情保存后的字段覆盖 |
| `theme` | `dark` | 暗色 |
| `capture` | `1` | 截图模式 |

## 7. 状态矩阵

| 状态 | URL |
|------|-----|
| 默认 | `#/workbench` |
| 第 2 页 | `#/workbench?page=2` |
| 每页 50 条 | `#/workbench?size=50` |
| 类型筛选 | `#/workbench?type=bug` |
| 选中 | `#/workbench?state=selected` |
| 空态 | `#/workbench?state=empty` |
| 加载 | `#/workbench?state=loading` |
| 失败 | `#/workbench?state=error` |
| 无权限 | `#/workbench?state=permission` |
| 大数据 | `#/workbench?state=dense` |
| 长文本 | `#/workbench?state=long` |
| 视图配置 | `#/workbench?overlay=view-config` |
| 批量编辑 | `#/workbench?state=selected&overlay=bulk-edit` |
| 批量编辑错误 | `#/workbench?state=selected&overlay=bulk-edit&invalid=1` |
| 导入上传 | `#/workbench?overlay=import&step=upload` |
| 导入映射 | `#/workbench?overlay=import&step=mapping` |
| 导入校验 | `#/workbench?overlay=import&step=result` |
| 导出 | `#/workbench?overlay=export` |
| 导出全部 | `#/workbench?overlay=export&scope=all` |
| 保存视图 | `#/workbench?overlay=save-view` |
| 快速预览 | `#/workbench?overlay=preview&id=1` |
| 批量配置 | `#/settings/bulk-actions` |
| 筛选构建器 | `#/workbench?overlay=filter` |
| 筛选缺值 | `#/workbench?overlay=filter&preset=incomplete&filterInvalid=1` |
| 已应用高级筛选 | `#/workbench?fpreset=advanced` |
| 事项详情弹窗 | `#/workbench?detail=1` |
| 详情编辑 | `#/workbench?detail=1&edit=1` |
| 状态内联更新 | `#/workbench?detail=1&inline=status` |
| 优先级内联更新 | `#/workbench?detail=1&inline=priority` |
| 截止时间内联更新 | `#/workbench?detail=1&inline=end_time` |
| 类型内联更新 | `#/workbench?detail=1&inline=type` |
| 关注者内联更新 | `#/workbench?detail=1&inline=watchers` |
| 标题内联更新 | `#/workbench?detail=1&inline=title` |
| 描述单字段编辑 | `#/workbench?detail=1&descEdit=1` |
| 操作记录已更新 | `#/workbench?detail=1&dtab=activity&recent=status` |
| 操作记录第 2 页 | `#/workbench?detail=1&dtab=activity&actPage=2` |
| 操作记录 | `#/workbench?detail=1&dtab=activity` |
| 评论 | `#/workbench?detail=1&dtab=comments` |
| 详情 Markdown 预览 | `#/workbench?detail=1&edit=1&descTab=preview` |
| 详情校验失败 | `#/workbench?detail=1&edit=1&invalid=1` |
| 详情只读 | `#/workbench?detail=1&dstate=permission` |
| 只读 + 编辑 URL | `#/workbench?detail=1&dstate=permission&edit=1`（强制只读） |
| 状态流转 | `#/workbench?detail=1&overlay=transition` |
| 放弃修改 | `#/workbench?detail=1&edit=1&overlay=discard` |
| 删除事项 | `#/workbench?detail=1&overlay=delete&enabledActions=delete` |
| 删除动作已关闭 | `#/workbench?detail=1&overlay=delete`（显示动作关闭提示） |
| 事项不存在 | `#/workbench?detail=999` |

## 8. 组件映射

| 组件 | 位置 | 说明 |
|------|------|------|
| `ProjectTypeNav` | `modules/pm/components/ProjectTypeNav` | 项目启用类型与数量 |
| `ListPageShell` | `components/console/ListPageShell` | 视图页签行（含 `rowActions`）、工具栏、条件行、内容、分页 |
| `DataTable` | 扩展既有组件 | 动态列、选择、排序、密度 |
| `Pagination` | 扩展既有组件 | 总数、每页、跳页 |
| `SearchInput` | `components/console/SearchInput` | 防抖与搜索范围 |
| `FilterBar` | `components/console/FilterBar` | 条件 chip |
| `FilterBuilder` | `modules/pm/components/FilterBuilder` | 字段/操作符/值、AND/OR、条件组与表达式预览 |
| `ViewConfigDrawer` | `modules/pm/components/ViewConfigDrawer` | 列/搜索/筛选配置 |
| `BulkActionBar` | `components/console/BulkActionBar` | 底部批量操作 |
| `BulkEditDialog` | `modules/pm/components/BulkEditDialog` | 字段白名单与预览 |
| `ImportWizard` | `modules/pm/components/ImportWizard` | 上传/映射/校验 |
| `ExportDialog` | `modules/pm/components/ExportDialog` | 范围与字段集 |
| `BatchActionSettingsTable` | `modules/pm/components/BatchActionSettingsTable` | 动作配置 |
| `ActionEditDialog` | `modules/pm/components/ActionEditDialog` | 角色/上限/确认/字段 |
| `useListUrlState` | `modules/pm/utils/useListUrlState` | URL 状态同步 |
| `DetailPageShell` | `modules/pm/components/DetailPageShell` | 详情弹窗内容：双栏骨架 |
| `InlinePropertyEditor` | `modules/pm/components/InlinePropertyEditor` | 字段内联编辑 |
| `ActivityTimeline` | `modules/pm/components/ActivityTimeline` | 活动记录 |
| `CommentThread` | `modules/pm/components/CommentThread` | 评论列表与输入 |
| `MarkdownRenderer` | `modules/pm/components/MarkdownRenderer` | 描述/评论 Markdown 渲染，HTML 转义与链接白名单 |
| `MarkdownEditor` | `modules/pm/components/MarkdownEditor` | 编辑/预览页签、工具栏、字符数 |

## 9. 验收标准

见 `prototype-spec.json` 的 `acceptance`（AC-01–AC-30）。其中 AC-26–AC-30 为 2026-10-20 评审后新增：分页、导出范围、`end_time`、多列排序与只读封堵。

## 10. 假设

| 编号 | 假设 | 确认方 |
|------|------|--------|
| A1 | 新增批量更新接口 `POST /pm/work-items/batch-update` | 后端 |
| A2 | 新增批量流转接口 `POST /pm/work-items/batch-transition` | 后端 |
| A3 | 新增批量操作配置接口或项目设置 JSON | 后端 |
| A4 | 字段定义补 `batchEditable` / `batchOrder` | 后端 |
| A5 | 保存视图扩展 listFields/searchFields/filterFields/density/sort | 后端 |
| A6 | 类型列表补 `workItemCount` 或前端聚合 | 后端 |
| A7 | 批量结果明细可下载 | 后端 |
| A8 | 导入失败行明细可下载 | 后端 |
| A9 | 截止时间作为系统字段 `end_time`，支持列、筛选、排序、批量与导入导出 | 后端 |
| A10 | 列表分页支持 `pageNo` / `pageSize`，`pageSize` 取值 10/20/50/100，默认 10 | 后端 |
| A11 | 操作记录接口支持 `pageNo` / `pageSize`，默认时间倒序 | 后端 |
| A12 | 单字段更新接口 `PATCH /pm/work-items/{id}/fields`，携带版本号 | 后端 |
