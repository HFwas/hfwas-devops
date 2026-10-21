# 产品原型索引

> 日期：2026-10-20
> 版本：v0.14
> 状态：部分完成
> 数据来源：`docs/pipeline/prototype/`、`docs/pipeline/pipeline-management-ui-prototype.md`、`docs/pipeline/pipeline-management-prototype-spec.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：现有原型入口、截图清单与待建清单 |
| v0.2 | 2026-10-18 | 原型规范改为引用 `docs/frontend/prototype-first-standard.md`，明确新增页面必须先冻结原型 |
| v0.3 | 2026-10-19 | 流水线入口改为模块总原型 `#/overview`（默认），登记 8 页面 / 7 弹层 / 5 项待建；补充 PM 工作台与事项类型两个新原型 |
| v0.4 | 2026-10-19 | 新增 PM 模块总原型 `docs/product/06-prototype/pm/`：项目类型导航、配置驱动列表、批量编辑/导入导出/批量操作配置；工作台与事项类型作为专题资产引用 |
| v0.5 | 2026-10-19 | PM 筛选取代简单弹层：支持字段、操作符、值、AND/OR、嵌套条件组、表达式预览与缺值校验 |
| v0.6 | 2026-10-19 | PM 新增可编辑事项详情：双栏、内联字段编辑、状态流转、评论/活动、校验与未保存确认 |
| v0.7 | 2026-10-20 | PM 详情描述支持 Markdown：阅读渲染、编辑/预览、工具栏与安全转义 |
| v0.8 | 2026-10-20 | PM 详情页签上移并改为详情/操作记录/评论，右栏属性常驻 |
| v0.9 | 2026-10-20 | PM 详情右栏支持单字段内联更新：状态/优先级点击即改 |
| v0.10 | 2026-10-20 | PM 详情全部业务字段支持单字段更新，变更实时写入操作记录 |
| v0.11 | 2026-10-20 | PM 操作记录分页并默认最新在前，支持每页 5/10/20 条 |
| v0.12 | 2026-10-20 | PM 原型入口补充设计文档链接：PM 文档索引与 pm-design v2.0 |
| v0.13 | 2026-10-20 | PM 原型升级 v1.8：评审修复、画板扩至 54 张（含 1280 宽度） |
| v0.14 | 2026-10-20 | PM 原型升级 v1.9：复评 4 个阻断项修复，画板重出 |

---

## 1. 现有原型

### 1.1 流水线管理（高保真）

| 项目 | 内容 |
|------|------|
| 入口 | [docs/pipeline/prototype/index.html#/overview](../../pipeline/prototype/index.html)（默认总览） |
| 说明 | [docs/pipeline/prototype/README.md](../../pipeline/prototype/README.md) |
| 规格 | [pipeline-management-prototype-spec.md](../../pipeline/pipeline-management-prototype-spec.md)（同时有 `.json`） |
| UI 说明 | [pipeline-management-ui-prototype.md](../../pipeline/pipeline-management-ui-prototype.md) |
| 交互 | 单文件可点击原型，hash 路由；总览 + 8 个页面 + 7 个弹层 |
| 截图 | `docs/pipeline/prototype/shots/00~21*.png`（1440×900 @2x） |
| 总原型图 | `docs/pipeline/prototype/shots/00-overview.png` |
| 待建能力 | 任务市场、依赖分析、Pod 终端、Kubectl 任务、运行参数与资源规格（已在总览登记） |

> 总览 `#/overview` 是流水线模块唯一原型入口，包含生命周期、核心流程、覆盖情况、页面/弹层清单与原型资产索引。各功能目录只引用该原型，不再复制页面。

查看方式：直接用浏览器打开 `index.html`，无需启动服务。

页面与截图：

| 页面 | 截图 |
|------|------|
| 模块总原型 / 总览 | `shots/00-overview.png` |
| 流水线列表（含空态、行菜单） | `shots/01-list.png`、`02-list-empty.png`、`20-list-row-menu.png` |
| 流水线详情 | `shots/03-detail.png` |
| 新建 / 编辑 / 校验失败 / 放弃修改 | `shots/21-editor-new.png`、`04-editor.png`、`05-editor-add-task.png`、`16-editor-invalid.png`、`17-modal-discard.png` |
| 运行历史（含空态） | `shots/06-history.png`、`07-history-empty.png` |
| 运行详情（运行中 / 待审批 / 失败） | `shots/08-run-running.png`、`09-run-waiting.png`、`10-run-failed.png` |
| 弹层（运行参数 / 删除 / 取消 / 日志） | `shots/11-modal-run-params.png`、`12-modal-delete.png`、`13-modal-cancel-run.png`、`14-log-drawer.png` |
| 凭证（含新建校验失败） | `shots/18-credentials.png`、`19-credential-invalid.png` |
| 早期流程矢量图（历史参考） | `shots/15-flow-map.svg` |

> 具体文件名以 `shots/` 目录为准；总入口以 `#/overview` 为准。

### 1.2 项目管理（高保真）

| 项目 | 内容 |
|------|------|
| 入口 | [docs/product/06-prototype/pm/index.html#/overview](pm/index.html)（默认总览） |
| 说明 | [pm/README.md](pm/README.md) |
| 规格 | [pm/prototype-spec.md](pm/prototype-spec.md)（同时有 `.json`） |
| 契约 | [pm/prototype-contract.md](pm/prototype-contract.md)（A2） |
| 设计 | [PM 文档索引](../../pm/README.md)、[pm-design.md](../../pm/pm-design.md)（v2.0） |
| 交互 | 单文件可点击原型；总览 + 项目类型导航 + 工作项列表 + 可编辑详情 + 视图配置 + 批量编辑/导入/导出 + 批量操作配置 |
| 截图 | `docs/product/06-prototype/pm/shots/01~54*.png`（其中 52–54 为 1280×800 @2x，其余 1440×900 @2x） |
| 专题资产 | [工作台改版](../../superpowers/features/20261019-pm-workbench-redesign/prototype/index.html)、[事项类型](../../superpowers/features/20261019-pm-work-item-types/prototype/index.html) |
| 待建能力 | 看板列映射、迭代燃尽、工时与容量、自动化规则 |

> 总览 `#/overview` 是 PM 模块唯一原型入口。左侧工作项类型来自项目启用方案，类型数量合计与「全部工作项」一致；列表显示列、搜索字段、筛选字段由视图配置驱动；筛选用「字段 + 操作符 + 值」配合 AND/OR 与条件组；详情支持标题、描述与高频属性内联编辑、Markdown 描述编辑/预览、状态流转、评论与活动；批量动作可配置启停、角色、上限、确认与字段白名单。

页面与截图：

| 页面 | 截图 |
|------|------|
| 模块总原型 / 总览 | `shots/01-overview.png` |
| 工作项列表（默认 / 类型筛选 / 选中） | `shots/02-workbench-default.png`、`03-workbench-type-bug.png`、`10-workbench-selected.png` |
| 状态（空 / 加载 / 失败 / 无权限 / 大数据 / 长文本） | `shots/04~09-*.png` |
| 视图配置（显示列 / 搜索字段） | `shots/11-overlay-view-config.png`、`12-overlay-view-search.png` |
| 批量编辑（正常 / 校验失败） | `shots/13-overlay-bulk-edit.png`、`14-overlay-bulk-edit-invalid.png` |
| 导入（上传 / 映射 / 校验） | `shots/15~17-overlay-import-*.png` |
| 导出 / 筛选 / 删除 / 预览 | `shots/18-overlay-export.png`、`19-overlay-filter.png`、`20-overlay-delete.png`、`21-overlay-preview.png` |
| 高级筛选与已应用筛选 | `shots/26-overlay-filter-invalid.png`、`27-workbench-filtered.png` |
| 事项详情（默认 / 编辑 / 校验失败） | `shots/28-detail-default.png`、`29-detail-edit.png`、`30-detail-invalid.png` |
| 详情只读 / 评论 | `shots/31-detail-permission.png`、`32-detail-comments.png` |
| 状态流转 / 放弃修改 / 删除 | `shots/33-overlay-transition.png`、`34-overlay-discard.png` |
| 详情加载 / 失败 / 暗色 | `shots/35-detail-loading.png`、`36-detail-error.png`、`37-detail-dark.png` |
| 详情 Markdown 预览 | `shots/38-detail-markdown-preview.png` |
| 详情操作记录页签 | `shots/39-detail-activity.png` |
| 单字段内联更新 | `shots/40-inline-status.png`、`41-inline-priority.png`、`42-inline-date.png`、`43-inline-type.png`、`44-inline-watchers.png`、`45-inline-title.png` |
| 描述单字段编辑 | `shots/46-description-edit.png` |
| 操作记录联动 | `shots/47-activity-updated.png` |
| 操作记录分页 | `shots/48-activity-page2.png` |
| 保存视图 / 快速预览 / 删除事项 | `shots/49-overlay-save-view.png`、`50-overlay-preview.png`、`51-detail-delete.png` |
| 1280 宽度 | `shots/52-workbench-1280.png`、`53-detail-1280.png`、`54-overview-1280.png` |
| 视图与搜索 / 批量操作配置 / 动作编辑 | `shots/22-settings-views.png`、`23-settings-bulk-actions.png`、`24-overlay-action-edit.png` |
| 暗色主题 | `shots/25-workbench-dark.png` |

## 2. 其他模块原型状态

| 模块 | 原型 | 状态 |
|------|------|------|
| 项目管理（PM） | [PM 模块总原型](pm/index.html) | 评审通过，可冻结 |
| 用户 / 租户 | 无 | 待建 |
| 容器平台 | 无（仅有设计文档与页面实现） | 待建 |
| API 测试平台 | 无（仅有设计文档与页面实现） | 待建 |
| Docgen | 无 | 待建 |
| 文件解析 | 无 | 待建 |
| 图片处理 | 无 | 待建 |
| 工作台 / 产品切换器 | 无 | 待建 |

## 3. 原型规范

新增或改版页面统一遵守 [前端原型先行规范](../../frontend/prototype-first-standard.md)：

1. 先写 `prototype-spec.json` / `prototype-spec.md`，再生成单文件可点击 `index.html`；
2. 关键页面与状态输出 1440×900 @2x 截图到 `shots/`；
3. 同步提供 `README.md`（页面清单、路由、状态参数、画板清单、评审与冻结记录）；
4. 原型与 [feature-catalog.md](../02-features/feature-catalog.md) 的功能项一一对应；
5. 原型中的路由与 `frontend/src/router.tsx` 保持一致，避免“原型有、实现无”；
6. 原型评审冻结后才能进入测试用例与编码，冻结后修改必须升版并重新评审。

## 4. 待确认

1. 新增模块级原型统一放在 `docs/product/06-prototype/<module>/`；流水线与 PM 已按此执行，历史 `docs/pipeline/prototype/` 保留。
2. 原型是否作为发布产物的一部分交付给客户。
3. 设计工具（Figma/MasterGo）的源文件位置与维护人。
