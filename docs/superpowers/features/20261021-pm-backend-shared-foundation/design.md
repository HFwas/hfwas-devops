# PM 项目管理改造 · 阶段 1：后端增量与共享列表组件

> 日期：2026-10-21
> 版本：v0.1
> 状态：已批准实施
> 关联：[pm-design.md](../../../pm/pm-design.md) v2.5、[PM 模块总原型](../../../product/06-prototype/pm/README.md) v1.11、[prototype-contract.md](../../../product/06-prototype/pm/prototype-contract.md) v0.12

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-21 | 初版：冻结记录、阶段 1 范围、V3 迁移、增量接口、共享组件契约与验收标准 |

---

## 1. 背景与目标

### 1.1 背景

PM 模块总原型已完成三角色评审，但冻结记录停在「待用户确认」，页面实现被原型门禁阻塞。原型覆盖列表工作台、可编辑详情、类型管理三块，一次性落地范围过大。

用户确认：

1. 冻结 PM 模块总原型 v1.11（视觉基线 v1.9）；
2. 按 `pm-design.md` §14 分阶段实施；
3. 阶段 1 只做**后端增量接口/迁移 + 前端共享列表组件**，不改写 PM 页面；
4. 批量接口采用真实实现，不做逐条串行的降级方案。

### 1.2 目标

- 补齐后续列表、详情、类型管理页面依赖的数据结构与接口：截止时间、乐观锁、迭代类型、单字段更新、批量更新/流转、活动分页、批量动作配置、类型计数与模板。
- 修复屏蔽后续能力的查询缺陷：顶层 `logic=OR` 不生效、自定义字段 key 可注入、数值/日期比较操作符缺失。
- 提供可复用的列表组件：可选/可排序/可调密度的 `DataTable`、带总数与页大小的 `Pagination`、搜索、筛选条、批量栏、空/错/无权限状态与列表外壳。
- 保持现有 PM 页面、现有接口调用方和现有测试零回归。

### 1.3 范围

| 在范围 | 不在范围 |
|---|---|
| Flyway V3 迁移与迁移测试 | PM 列表/详情/类型管理页面改造 |
| 单字段更新、批量更新、批量流转接口 | 前端筛选构建器、视图配置、导入导出向导 |
| 活动分页、批量动作配置、类型计数/模板 | Markdown 渲染/编辑器与依赖 |
| `end_time` 全链路（查询、排序、导入导出、活动） | 看板拖拽、工作流画布 |
| 前端共享列表组件与状态组件 | 真实应用截图与原型逐页对比（页面阶段再做） |
| 接口/数据库/设计文档同步 | 项目成员与只读角色建模 |

### 1.4 原型豁免说明

本阶段不新增或修改任何前端页面，只交付共享组件，因此不新建 `prototype/` 目录。后续页面改造继续以冻结的 PM 模块总原型 v1.11 为唯一视觉与交互基线。此豁免在 `changes.md` 中同步登记。

## 2. 冻结记录

| 项 | 值 |
|---|---|
| 原型入口 | `docs/product/06-prototype/pm/index.html#/overview` |
| README 版本 | v1.12（v1.11 内容冻结） |
| 视觉基线 | v1.9 |
| 原型规格 | `prototype-spec.md` v1.9 |
| 原型契约 | `prototype-contract.md` v0.13 |
| 冻结日期 | 2026-10-21 |
| 冻结提交 | `6741712` |
| 冻结确认 | 用户于 2026-10-21 明确确认 |
| 后续修改 | 冻结后修改原型必须升版并重新评审，禁止实现完成后追认式修改 |

## 3. 数据模型与迁移

### 3.1 迁移文件

新增 `backend/db-migration/src/main/resources/db/migration/V3__pm_backend_shared_foundation.sql`；禁止修改 V1/V2。

### 3.2 表结构变更

| 表 | 变更 |
|---|---|
| `pm_work_item` | 新增 `end_time DATETIME NULL`、`version BIGINT NOT NULL DEFAULT 0`；索引 `idx_pm_work_item_end_time (project_id, end_time)` |
| `pm_work_item_type` | 新增 `built_in TINYINT NOT NULL DEFAULT 0`、`description VARCHAR(512)`、`template_code VARCHAR(64)`；既有 4 类型回填 `built_in=1` |
| `pm_field_definition` | 新增 `batch_editable TINYINT NOT NULL DEFAULT 0`、`batch_order INT NOT NULL DEFAULT 0` |
| `pm_work_item_activity` | `batch_id` 改 `VARCHAR(64)`；`old_value/new_value` 改 `MEDIUMTEXT`；label 放宽到 `VARCHAR(1024)`；新增 `batch_id` 索引 |
| `pm_work_item_watcher` | 新表：`id, work_item_id, user_id, create_time`；唯一键 `(work_item_id, user_id)` |
| `pm_batch_action_config` | 新表：`id, project_id, action_code, enabled, max_items, require_confirm, allowed_roles, field_whitelist, create_time, update_time`；两个列表字段按仓库约定存 `MEDIUMTEXT` JSON 文本；唯一键 `(project_id, action_code)` |

### 3.3 种子数据

- 新增内置类型 `iteration`（id=5，排序 4）；`test_case` 排序改为 5。
- 新增迭代默认工作流：`open → in_progress → done → closed`，状态 id 复用 V2 空号 5/10/15/20。
- 既有 4 类型补 `built_in=1`、`template_code=<code>` 与中文描述。

### 3.4 兼容性

- 重建库、不迁旧数据；已有项目不会自动启用 `iteration`，需通过类型方案显式启用。
- 迁移失败时按仓库规则重建库，不写兼容分支。

## 4. 后端接口

所有接口返回 `BaseResult<T>`；ID 按现有约定序列化为字符串。

### 4.1 单字段更新

`PATCH /pm/work-items/{id}/fields`

请求：

```json
{ "field": "priority", "value": "high", "version": 7, "transitionId": null }
```

- `version` 必填，与库中不一致返回 `21033`。
- 系统字段白名单：`title`、`description`、`priority`、`assignee_id`、`reporter_id`、`parent_id`、`module_id`、`sprint_id`、`end_time`。
- 自定义字段必须存在且属于当前项目/类型。
- `value=null` 表示清空（标题除外）。
- `field=status` 必须走工作流：优先使用 `transitionId`；未提供时仅在目标状态唯一可达时自动解析，否则返回 `21012`。
- 成功返回更新后事项、字段 key、old/new value 与 label。

### 4.2 批量更新

`POST /pm/work-items/batch-update`

```json
{
  "ids": ["9101", "9102"],
  "patch": { "priority": "high", "end_time": "2026-11-01 18:00:00" },
  "versions": { "9101": 7 },
  "dryRun": false
}
```

- 逐条独立事务；单条失败不回滚已成功项。
- `dryRun=true` 只校验，不写库、不记活动、不发通知。
- 返回：`batchId, dryRun, total, success, skipped, failed, items[]`。
- 不可流转/字段不适用 = `SKIPPED`；校验失败/版本冲突 = `FAILED`。
- `status` 不允许出现在 `patch`，必须使用批量流转。

### 4.3 批量流转

`POST /pm/work-items/batch-transition`

```json
{
  "ids": ["9101", "9102"],
  "toStatus": "done",
  "transitionId": null,
  "fields": { "priority": "high" },
  "versions": { "9101": 7 },
  "dryRun": false
}
```

- 显式 `transitionId` 必须对当前状态合法；否则按 `toStatus` 唯一定位流转。
- 无合法流转 = `SKIPPED`；校验器/必填字段失败 = `FAILED`。
- 每条结果包含 `fromStatus`、`toStatus`、`version`（dry-run 为目标值）。

### 4.4 活动分页

`GET /pm/work-items/{id}/activities/page?pageNo=1&pageSize=10`

- 返回 `IPage<WorkItemActivityVo>`，最新在前。
- 保留 `GET /pm/work-items/{id}/activities` 全量列表，标记为兼容接口，现有详情页继续可用。

### 4.5 批量动作配置

`GET /pm/projects/{projectId}/batch-action-configs`

- 合并已保存配置与默认值，动作：`UPDATE_FIELDS`、`TRANSITION`、`IMPORT`、`EXPORT`、`DELETE`、`COPY`。
- 默认：编辑/流转/导出启用；删除关闭；上限 100（导出 5000）；删除/流转需二次确认。

`POST /pm/projects/{projectId}/batch-action-configs`

- 仅平台管理员；字段白名单必须存在且可批量编辑；上限 1–1000（导入/导出 1–5000）。

### 4.6 类型元数据

- `POST /pm/projects/issue-types/list` 返回项新增 `builtIn`、`description`、`templateCode`、`workItemCount`、`fieldCount`。
- `GET /pm/meta/types/templates` 返回内置类型模板（含工作流模板编码）。
- 自定义类型创建使用请求中的 `templateCode`（默认 `task`）复制工作流；内置类型禁止删除。

### 4.7 查询修复

- 顶层 `QuerySpec.logic=OR` 生效；项目/类型基础过滤仍为 AND。
- 系统字段与排序白名单新增 `end_time`。
- 自定义字段 key 必须匹配 `^[A-Za-z_][A-Za-z0-9_]{0,63}$`。
- 自定义字段 `NUMBER/DATE/DATETIME` 支持 `GT/GTE/LT/LTE/BETWEEN`；其他类型返回 `22019`。

## 5. 前端共享组件契约

组件放在 `frontend/src/components/console/`，不得 import PM 模块，颜色只用语义 token。

### 5.1 DataTable 扩展（向后兼容）

```ts
type DataTableDensity = 'compact' | 'standard'
type DataTableSort = { field: string; order: 'ASC' | 'DESC' }

interface DataTableColumn<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
  headClassName?: string
  sortable?: boolean
}
```

新增可选 props：`selectable`、`selectedIds`、`onSelectionChange`、`sorts`、`onSortChange`、`density`（默认 `compact`）、`stickyHeader`、`maxHeight`。

- 默认密度与现有结构完全一致，现有调用方与测试不受影响。
- 选择列支持全选当前页、半选、Shift 区间选择；勾选不触发行点击。
- 表头点击循环排序，Shift 点击追加多列排序。

### 5.2 Pagination 扩展（向后兼容）

新增：`total`、`pageSize`、`pageSizeOptions`（默认 `[10,20,50,100]`）、`onPageSizeChange`、`showJump`、`disabled`。

- 未传 `total` 时保持现有摘要与结构。
- 传入 `total` 时摘要为 `共 N 条 · 第 x / y 页`。
- 跳页输入非法值不写回，Enter/失焦时纠正到合法范围。
- 组件保持受控，不自行重置页码。

### 5.3 新增组件

| 组件 | 职责 |
|---|---|
| `ListPageShell` | 统一列表页头、工具栏、筛选条、内容、加载/失败/无权限/空态与重试 |
| `SearchInput` | 300ms 防抖、Enter 立即触发、清空按钮、禁用态 |
| `FilterBar` | 已应用条件 chip、单条移除、全部重置、额外操作区 |
| `BulkActionBar` | 已选 N 项、动作按钮、清空选择、忙碌态 |
| `EmptyState` | 空数据/无结果两种文案与操作 |
| `ErrorState` | 失败说明、requestId、重试 |
| `NoPermissionState` | 只读/无权限说明与操作 |

### 5.4 新增 shadcn/ui 原语

`checkbox`、`switch`、`tabs`、`radio-group`、`toggle-group`、`scroll-area`，使用 shadcn CLI 生成，不手写平行基础组件。

## 6. 异常与边界

| 场景 | 行为 |
|---|---|
| 版本过期 | `21033`，不写库 |
| 批量跨项目 | 整批拒绝 `21037` |
| 批量超上限/动作禁用 | `21034` / `21035` |
| 批量字段不在白名单 | 单条 `SKIPPED` + `21036` |
| 状态流转非法 | 单条 `SKIPPED` + `21012` |
| 单条校验失败 | 单条 `FAILED`，其余继续 |
| `end_time` 非法 | `21032` |
| 自定义字段 key 非法 | 条件过滤返回 `22018`，禁止拼入 SQL；排序字段非法沿用 `400`（`BAD_REQUEST`） |
| 内置类型删除 | `21038` |

## 7. 验收标准

| 编号 | 标准 |
|---|---|
| AC-01 | V3 在空库执行成功；新列、新表、新索引、迭代种子与工作流齐备 |
| AC-02 | 既有迁移测试计数更新后通过（Docker 可用时）；不可用时明确记录未实跑 |
| AC-03 | 单字段 PATCH 正常更新、清空、版本冲突、状态流转四条路径通过 |
| AC-04 | 批量更新/流转逐条隔离，部分失败不回滚成功项 |
| AC-05 | dry-run 不产生任何写库、活动或通知副作用 |
| AC-06 | 顶层 OR、`end_time` 查询/排序、自定义字段比较操作符与 key 校验通过 |
| AC-07 | 活动分页最新在前，旧全量接口保持兼容 |
| AC-08 | 批量动作配置默认值、管理员限制、上限与白名单校验通过 |
| AC-09 | 类型列表返回计数与内置标识；内置类型不可删除；模板可查 |
| AC-10 | DataTable/Pagination 新能力通过测试，既有默认行为断言不变 |
| AC-11 | 新增共享组件通过交互测试，且不依赖 PM 模块 |
| AC-12 | 接口文档、Postman、数据库文档与实现一致 |
| AC-13 | 后端 `mvn test`、前端 `tsc`/`npm test`/`npm run build` 通过 |

## 8. 假设与风险

- **A1**：项目级成员/只读角色尚未建模，批量角色控制暂按 `admin`/`user`。
- **A2**：`iteration` 仅全局种子，既有项目需手动启用。
- **A3**：dry-run 不代表状态锁定，执行时可能因并发变化而改变结果。
- **A4**：Docker 未启动时 Testcontainers 迁移测试跳过，需在 CI 或启动 Docker 后补验。
- **A5**：`pm_work_item_activity` 的 `MEDIUMTEXT` 变更在大数据量环境需评估 DDL 锁。
