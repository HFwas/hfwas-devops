# PM 模块总原型

> 日期：2026-10-22
> 版本：v1.16
> 状态：**已冻结**（v1.11，2026-10-22 用户确认后进入实现；v1.10 为上一基线）
> 功能设计：[20261019-pm-list-config-bulk/design.md](../../../superpowers/features/20261019-pm-list-config-bulk/design.md)
> 原型规格：[prototype-spec.md](./prototype-spec.md)

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v1.0 | 2026-10-19 | 初版：PM 模块总览、项目类型导航、配置驱动的列表/搜索、批量编辑、导入导出与批量操作配置 |
| v1.1 | 2026-10-19 | 筛选升级为高级构建器：字段/操作符/值、AND/OR、嵌套条件组、表达式预览与缺值校验 |
| v1.2 | 2026-10-19 | 新增可编辑事项详情：双栏布局、标题/描述/属性编辑、状态流转、评论与活动、校验与未保存确认 |
| v1.3 | 2026-10-20 | 详情描述支持 Markdown：阅读渲染、编辑/预览页签、工具栏插入、HTML 转义与链接白名单 |
| v1.4 | 2026-10-20 | 详情页签上移并改为详情/操作记录/评论；默认详情，右栏属性常驻，编辑态禁止切页签 |
| v1.5 | 2026-10-20 | 详情右栏支持单字段内联更新：状态/优先级点击即改，日期/数字/标签行内保存 |
| v1.6 | 2026-10-20 | 详情全部业务字段支持单字段更新；每次变更追加操作记录；非法状态保持置灰 |
| v1.7 | 2026-10-20 | 操作记录分页展示，默认最新在前，支持每页 5/10/20 条与页码切换 |
| v1.8 | 2026-10-20 | 评审修复：分页 10/20/50/100 默认 10 并修复循环取页；截止时间统一 `end_time`；导出范围/字段可交互；只读态封堵写操作；评论与预览接入 Markdown；批量动作遵守启停；统一总数、列宽与选中数；补多列排序与 404 态 |
| v1.9 | 2026-10-20 | 复评修复：导出字段勾选恢复交互；42 号画板改用 `end_time`；删除动作可显式启用并拦截关闭直链；保存配置/视图落内存状态；只读保留导出；跳页校验；预览 404；参数表补齐 |
| v1.10 | 2026-10-21 | 角色评审（独立 agent 代行产品/前端/测试）：有条件通过，登记列排序、筛选字段越界、批量编辑假成功、批量配置仅启停可用 4 项冻结前必须关闭项 |
| v1.11 | 2026-10-21 | 角色复评：4 项 P1 全部关闭，AC-03/05/10 复验通过；结论改为通过 / 可冻结，保留历史评审行 |
| v1.12 | 2026-10-21 | 用户确认冻结：v1.11（视觉基线 v1.9）冻结于提交 6741712；作为 PM 改造阶段 1 及后续页面的唯一验收基线 |
| v1.13 | 2026-10-22 | 布局修订 v1.10：删除页头行；导入/导出/新建移到视图页签行右侧；移除「+ 添加筛选」条件行，筛选只保留工具栏入口；重出 02–27、49、50、52 号画板 |
| v1.14 | 2026-10-22 | 用户确认冻结 v1.10 作为新视觉基线；三角色评审待实施阶段补签 |
| v1.15 | 2026-10-22 | 事项详情由独立页面改为居中大弹窗：`#/workbench?detail=<id>`，移除 `/items/:itemId` 路由；详情状态参数改 `dstate`、页签改 `dtab`；重出 01、28–48、51、53、54 号画板 |
| v1.16 | 2026-10-22 | 用户确认 v1.11 进入实现并冻结为当前视觉基线 |

---

## 1. 怎么打开

零依赖单文件，直接双击：

```text
docs/product/06-prototype/pm/index.html
```

默认进入 PM 模块总览 `#/overview`；直接访问 `#/workbench` 进入项目工作项列表。右下角原型导航可切换全部状态；`capture=1` 时隐藏导航与版本角标。

## 2. 路由

| 页面 | Hash |
|------|------|
| PM 模块总览（默认） | `#/overview` |
| 项目工作项列表 | `#/workbench` |
| 事项详情弹窗（叠加在列表上） | `#/workbench?detail=1` |
| 类型筛选 | `#/workbench?type=requirement|task|bug|iteration|test_case|story|tech_debt` |
| 视图与搜索设置 | `#/settings/views` |
| 批量操作配置 | `#/settings/bulk-actions` |

事项类型管理仍在专题原型：[20261019-pm-work-item-types](../../../superpowers/features/20261019-pm-work-item-types/prototype/index.html)。

## 3. 状态参数

| 参数 | 取值 |
|------|------|
| `type` | 类型 code |
| `view` | `dev` / `mine` / `week` / `blocked` |
| `q` | 搜索关键词 |
| `cols` | 显示列字段 key，逗号分隔 |
| `search` | 搜索字段 key，逗号分隔 |
| `ff` | 筛选字段 key，逗号分隔 |
| `density` | `standard` / `compact` |
| `sort` | `field:ASC` / `field:DESC`，多列用逗号分隔，如 `priority:ASC,updated:DESC` |
| `page` | 当前页码 |
| `size` | `10` / `20` / `50` / `100`（默认 10） |
| `sel` | 选中行 id，逗号分隔（仅当前页） |
| `scope` | `selected` / `query` / `all`（导出范围） |
| `ef` | 导出字段 key，逗号分隔 |
| `state` | `empty` / `loading` / `error` / `permission` / `dense` / `long` / `selected` |
| `overlay` | `view-config` / `bulk-edit` / `import` / `export` / `filter` / `delete` / `save-view` / `preview` / `action-edit` / `transition` / `discard` |
| `tab` | 视图配置：`columns` / `search` / `filter` |
| `detail` | 事项 id（打开详情弹窗） |
| `dstate` | 详情弹窗状态：`loading` / `error` / `permission` |
| `dtab` | 详情弹窗页签：`detail` / `activity` / `comments` |
| `step` | `upload` / `mapping` / `result` |
| `field` | 批量编辑当前字段 key |
| `id` | 快速预览事项 id |
| `disabledActions` | 演示关闭的批量动作 code，逗号分隔 |
| `enabledActions` | 演示启用默认关闭的动作（如 `delete`） |
| `code` | 动作编辑弹层目标动作 code |
| `ty` / `rp` / `mod` / `parent` / `fix` / `labels` / `watch` / `env` | 详情保存后的扩展字段覆盖 |
| `invalid` | `1`（批量编辑或详情校验失败） |
| `f` | 筛选条件树 JSON |
| `fpreset` | `advanced`（应用内置高级筛选） |
| `preset` | `incomplete`（缺值条件演示） |
| `filterInvalid` | `1`（筛选缺值校验失败） |
| `c_status` / `c_priority` / `c_risk` | 简单筛选条件值，逗号分隔 |
| `edit` | `1`（详情编辑态；只读状态下强制失效） |
| `descTab` | `write` / `preview`（描述编辑/预览） |
| `desc` | `short`（评论截图用短描述） |
| `inline` | `status` / `priority` / `type` / `assignee` / `reporter` / `iteration` / `risk` / `end_time` / `storyPoints` / `fixVersion` / `module` / `parent` / `labels` / `watchers` / `environment` / `title`（直接打开内联编辑器） |
| `descEdit` | `1`（描述单字段 Markdown 编辑） |
| `recent` | `status`（操作记录追加示例） |
| `actPage` / `actSize` | 操作记录页码 / 每页条数（5、10、20） |
| `n` / `d` / `s` / `p` / `a` / `end_time` / `it` / `sp` / `risk` | 详情保存后的字段覆盖 |
| `theme` | `dark` |
| `capture` | `1` |

## 4. 画板清单

除标注 1280 的三张外，其余为 1440×900 @2x。

| # | 文件 | URL |
|---|------|-----|
| 01 | `shots/01-overview.png` | `?capture=1#/overview` |
| 02 | `shots/02-workbench-default.png` | `?capture=1#/workbench` |
| 03 | `shots/03-workbench-type-bug.png` | `?capture=1#/workbench?type=bug` |
| 04 | `shots/04-workbench-empty.png` | `?capture=1#/workbench?state=empty` |
| 05 | `shots/05-workbench-loading.png` | `?capture=1#/workbench?state=loading` |
| 06 | `shots/06-workbench-error.png` | `?capture=1#/workbench?state=error` |
| 07 | `shots/07-workbench-permission.png` | `?capture=1#/workbench?state=permission` |
| 08 | `shots/08-workbench-dense.png` | `?capture=1#/workbench?state=dense` |
| 09 | `shots/09-workbench-long.png` | `?capture=1#/workbench?state=long` |
| 10 | `shots/10-workbench-selected.png` | `?capture=1#/workbench?state=selected` |
| 11 | `shots/11-overlay-view-config.png` | `?capture=1#/workbench?overlay=view-config&tab=columns` |
| 12 | `shots/12-overlay-view-search.png` | `?capture=1#/workbench?overlay=view-config&tab=search` |
| 13 | `shots/13-overlay-bulk-edit.png` | `?capture=1#/workbench?state=selected&overlay=bulk-edit` |
| 14 | `shots/14-overlay-bulk-edit-invalid.png` | `?capture=1#/workbench?state=selected&overlay=bulk-edit&invalid=1` |
| 15 | `shots/15-overlay-import-upload.png` | `?capture=1#/workbench?overlay=import&step=upload` |
| 16 | `shots/16-overlay-import-mapping.png` | `?capture=1#/workbench?overlay=import&step=mapping` |
| 17 | `shots/17-overlay-import-result.png` | `?capture=1#/workbench?overlay=import&step=result` |
| 18 | `shots/18-overlay-export.png` | `?capture=1#/workbench?overlay=export` |
| 19 | `shots/19-overlay-filter.png` | `?capture=1#/workbench?overlay=filter` |
| 20 | `shots/20-overlay-delete.png` | `?capture=1#/workbench?state=selected&overlay=delete&enabledActions=delete` |
| 21 | `shots/21-overlay-preview.png` | `?capture=1#/workbench?overlay=preview&id=1` |
| 22 | `shots/22-settings-views.png` | `?capture=1#/settings/views` |
| 23 | `shots/23-settings-bulk-actions.png` | `?capture=1#/settings/bulk-actions` |
| 24 | `shots/24-overlay-action-edit.png` | `?capture=1#/settings/bulk-actions?overlay=action-edit&code=bulk-edit` |
| 25 | `shots/25-workbench-dark.png` | `?capture=1#/workbench?theme=dark` |
| 26 | `shots/26-overlay-filter-invalid.png` | `?capture=1#/workbench?overlay=filter&preset=incomplete&filterInvalid=1` |
| 27 | `shots/27-workbench-filtered.png` | `?capture=1#/workbench?fpreset=advanced` |
| 28 | `shots/28-detail-default.png` | `?capture=1#/workbench?detail=1` |
| 29 | `shots/29-detail-edit.png` | `?capture=1#/workbench?detail=1&edit=1` |
| 30 | `shots/30-detail-invalid.png` | `?capture=1#/workbench?detail=1&edit=1&invalid=1` |
| 31 | `shots/31-detail-permission.png` | `?capture=1#/workbench?detail=1&dstate=permission` |
| 32 | `shots/32-detail-comments.png` | `?capture=1#/workbench?detail=1&dtab=comments` |
| 33 | `shots/33-overlay-transition.png` | `?capture=1#/workbench?detail=1&overlay=transition` |
| 34 | `shots/34-overlay-discard.png` | `?capture=1#/workbench?detail=1&edit=1&overlay=discard` |
| 35 | `shots/35-detail-loading.png` | `?capture=1#/workbench?detail=1&dstate=loading` |
| 36 | `shots/36-detail-error.png` | `?capture=1#/workbench?detail=1&dstate=error` |
| 37 | `shots/37-detail-dark.png` | `?capture=1#/workbench?detail=1&theme=dark` |
| 38 | `shots/38-detail-markdown-preview.png` | `?capture=1#/workbench?detail=1&edit=1&descTab=preview` |
| 39 | `shots/39-detail-activity.png` | `?capture=1#/workbench?detail=1&dtab=activity` |
| 40 | `shots/40-inline-status.png` | `?capture=1#/workbench?detail=1&inline=status` |
| 41 | `shots/41-inline-priority.png` | `?capture=1#/workbench?detail=1&inline=priority` |
| 42 | `shots/42-inline-date.png` | `?capture=1#/workbench?detail=1&inline=end_time` |
| 43 | `shots/43-inline-type.png` | `?capture=1#/workbench?detail=1&inline=type` |
| 44 | `shots/44-inline-watchers.png` | `?capture=1#/workbench?detail=1&inline=watchers` |
| 45 | `shots/45-inline-title.png` | `?capture=1#/workbench?detail=1&inline=title` |
| 46 | `shots/46-description-edit.png` | `?capture=1#/workbench?detail=1&descEdit=1` |
| 47 | `shots/47-activity-updated.png` | `?capture=1#/workbench?detail=1&dtab=activity&recent=status` |
| 48 | `shots/48-activity-page2.png` | `?capture=1#/workbench?detail=1&dtab=activity&actPage=2` |
| 49 | `shots/49-overlay-save-view.png` | `?capture=1#/workbench?overlay=save-view` |
| 50 | `shots/50-overlay-preview.png` | `?capture=1#/workbench?overlay=preview&id=2` |
| 51 | `shots/51-detail-delete.png` | `?capture=1#/workbench?detail=1&overlay=delete&enabledActions=delete` |
| 52 | `shots/52-workbench-1280.png` | `?capture=1#/workbench`（1280×800 @2x） |
| 53 | `shots/53-detail-1280.png` | `?capture=1#/workbench?detail=1`（1280×800 @2x） |
| 54 | `shots/54-overview-1280.png` | `?capture=1#/overview`（1280×800 @2x） |

### 4.1 重新截图

```bash
cd docs/product/06-prototype/pm
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
BASE="file://$PWD/index.html"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars \
  --force-device-scale-factor=2 --window-size=1440,900 --virtual-time-budget=2500 \
  --screenshot=shots/01-overview.png "$BASE?capture=1#/overview"
```

## 5. 已实现的原型交互

- 左侧项目类型导航，7 个启用类型与数量，点击即筛选；
- 类型数量合计与「全部工作项」一致；
- 视图页签、脏标记与保存视图；
- 显示列勾选、顺序、恢复默认、密度、默认排序；
- 搜索字段配置，搜索提示与匹配范围同步；
- 筛选字段配置与条件 chip；
- 高级筛选构建器：字段、操作符、值、AND/OR、嵌套条件组、表达式预览、预计匹配数；
- 缺值条件阻止应用并汇总提示；
- 快速筛选：我的工作项、本周到期、阻塞风险、未分配；
- 行选择、全选当前页、底部批量栏；翻页、切类型、切视图会清空选择；
- 分页每页 10/20/50/100（默认 10），页码、跳页与每页条数写入 URL；视图页签行、工具栏与分页使用同一总数；
- 事项详情以弹窗叠加在列表上，关闭后列表的视图/搜索/筛选/分页状态保持不变；
- Shift+点击表头追加多列排序；
- 批量编辑字段切换、清空值、影响预览、校验失败；
- 导入三段式：上传 → 字段映射 → 校验与执行；
- 导出范围（选中/查询/全部）与字段勾选可交互，条数与摘要随范围变化，至少保留 1 个字段；
- 批量操作配置：动作启停、角色、上限、二次确认、字段白名单；关闭的动作不出现在批量栏与行操作，直接构造弹层 URL 会提示动作已关闭；
- 默认关闭的删除可在批量配置中显式启用；
- 保存配置与保存视图在当前会话内生效，脏标记随之清除；
- 无权限状态隐藏全部写操作；直接构造 `edit=1`、`inline=*`、写操作弹层也强制只读；
- 事项详情双栏，顶部页签「详情 / 操作记录 / 评论」，右栏属性常驻；
- 标题/描述/属性内联编辑、完整编辑态、状态流转、评论与活动、校验与未保存确认；
- 描述支持 Markdown：阅读态渲染、编辑/预览页签、工具栏插入语法、字符数统计；
- 评论与快速预览共用 Markdown 渲染器，评论发表后立即刷新计数与列表；
- 事项不存在时展示 404 态，快速预览与详情一致；跳页超出范围会提示并不写入 URL；
- 亮 / 暗主题。

## 6. 覆盖与资产

| 资产 | 位置 |
|------|------|
| 模块总览 | 本原型 `#/overview` |
| 工作台改版专题 | [20261019-pm-workbench-redesign](../../../superpowers/features/20261019-pm-workbench-redesign/prototype/index.html) |
| 事项类型专题 | [20261019-pm-work-item-types](../../../superpowers/features/20261019-pm-work-item-types/prototype/index.html) |
| 列表配置与批量设计 | [design.md](../../../superpowers/features/20261019-pm-list-config-bulk/design.md) |
| PM 设计 / 接口 | [pm-design.md](../../../pm/pm-design.md)、[pm-api.md](../../../pm/pm-api.md) |

## 7. 评审记录

| 版本 | 日期 | 评审人 | 结论 | 冻结提交 | 备注 |
|------|------|--------|------|----------|------|
| 1.0 | 2026-10-19 | 产品 / 前端 / 测试 | 待评审 | — | 等待查看效果 |
| 1.1 | 2026-10-19 | 产品 / 前端 / 测试 | 待评审 | — | 新增高级筛选构建器 |
| 1.2 | 2026-10-19 | 产品 / 前端 / 测试 | 待评审 | — | 新增可编辑事项详情 |
| 1.3 | 2026-10-20 | 产品 / 前端 / 测试 | 待评审 | — | 详情描述支持 Markdown |
| 1.4 | 2026-10-20 | 产品 / 前端 / 测试 | 待评审 | — | 详情页签上移并改为详情/操作记录/评论 |
| 1.5 | 2026-10-20 | 产品 / 前端 / 测试 | 待评审 | — | 详情支持单字段内联更新 |
| 1.6 | 2026-10-20 | 产品 / 前端 / 测试 | 待评审 | — | 全部字段单字段更新 + 操作记录 |
| 1.7 | 2026-10-20 | 产品 / 前端 / 测试 | 待评审 | — | 操作记录分页 + 最新在前 |
| 1.8 | 2026-10-20 | 独立评审 agent | **不可冻结 → 已修复，待复评** | — | 发现 P0 2 项、P1 7 项、P2 14 项、P3 12 项；P0/P1 与必须修复项已修复，等待再次独立评审 |
| 1.9 | 2026-10-20 | 独立复评 agent | **4 个阻断项已修复，待二次复评** | — | 复评确认 8 项通过、4 项未通过；导出字段勾选、画板 42、规格页大小与 AC-29 字段名已修复 |
| 1.9 | 2026-10-20 | 独立验证 agent | **通过，可进入冻结流程** | 待签字后填写 | 4 个阻断项全部关闭；12 个 URL 回归无 JS 错误与横向滚动；待产品 / 前端 / 测试签字 |
| 1.9 | 2026-10-21 | 产品 / 前端 / 测试（独立评审 agent 代行） | **有条件通过** | 待用户确认提交号 | 需先关闭 4 项 P1：显示列上移/下移无处理器（AC-03）、筛选面板越出筛选字段配置（AC-05）、批量配置仅启停可设置（AC-10）、批量编辑空值假成功；其余 P2 见 contract §9 |
| 1.9 | 2026-10-21 | 产品 / 前端 / 测试（独立复评 agent 代行） | **通过，可冻结** | 6741712 | 4 项 P1 全部关闭；列排序、筛选白名单、批量编辑空值校验/清空、批量配置保存复验通过；52 个 URL×视口回归 0 JS 错误、0 横向滚动。遗留 P2：`actionConfig` 仅随当前 URL 保留，「返回工作项」或侧栏入口会丢失，建议实现阶段补会话变量或参数透传。 |
| 1.10 | 2026-10-22 | 用户（布局评审） | **已冻结** | 本分支本次提交 | 删除页头；导入/导出/新建移至视图页签行；移除「+ 添加筛选」条件行，筛选只保留工具栏入口；独立 agent 两轮校验通过（见 changes.md §6.5/§6.6） |
| 1.11 | 2026-10-22 | 用户（详情形态） | **已冻结** | 本分支本次提交 | 事项详情改为居中大弹窗，URL 收敛到列表 `detail` 参数并移除 `/items` 路由；React 实现已对齐，差异见 `20261022-pm-detail-modal/changes.md` 偏差 23–28 |

## 8. 维护规则

1. 本页是 PM 模块唯一原型入口；新增页面、状态或弹层必须先登记到 `#/overview`。
2. 功能目录只引用本原型，不复制页面，避免出现多份基线。
3. 修改核心画面后同步更新 `shots/` 与 `prototype-spec.json`，并升版本。
4. 事项类型与工作台专题原型保留为细节参考，模块总览负责串联。

## 9. 已知限制

- 批量更新、批量流转、批量操作配置接口尚未落地，原型为交互规格；
- 导入导出的列与错误明细为固定样例；
- 视图配置、保存视图与批量动作配置仅会话/URL 生效，刷新后回到默认；
- 批量动作的角色、字段白名单、单次上限与二次确认在当前会话/URL 内生效，刷新后重置；
- 详情校验错误汇总固定为「标题 + 故事点」两个字段，用于演示字段级错误；
- 类型数量与 128 条事项为样例数据，需后端聚合；
- 看板、燃尽、工时、自动化未在本版原型范围。
- 首版只提供可视化筛选构建器，不提供原始 JQL/表达式编辑；条件组最多 2 层。
- 详情描述使用内置 Markdown 渲染器，支持常用语法；生产接入统一 Markdown 渲染器并做安全审计。
- 评论与活动首版分页签；Markdown 暂不支持图片上传、@ 提及与内联评论。
