# 项目管理（PM）数据字典

> 日期：2026-10-21
> 版本：v0.2
> 状态：已完成（表级）
> 数据来源：`backend/db-migration/src/main/resources/db/migration/V1__initial_schema.sql`、`V2__seed_system_data.sql`、`V3__pm_backend_shared_foundation.sql`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：15 张 pm_* 表 + 重复 sys_user 说明 |
| v0.2 | 2026-10-21 | V3：事项补 `end_time`/`version`；类型补内置/描述/模板；字段补批量属性；活动批次号改字符串并放宽值长度；新增关注者与批量动作配置表；补索引与迭代种子 |

---

共 17 张 `pm_*` 表，另有 1 段重复的 `sys_user` 定义（见文末风险）。

## pm_project

项目主表。

| 字段 | 说明 |
|------|------|
| `id` | 主键（雪花） |
| `tenant_id` | 所属租户 |
| `code` | 项目编码，租户内唯一 |
| `name` / `description` | 名称与描述 |
| `settings` | 项目配置 JSON |
| `create_by` / `update_by` | 操作人 |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_pm_project_tenant_code`、`idx_pm_project_tenant`。

## pm_project_member

项目成员。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `project_id` / `user_id` | 项目与用户 |
| `role` | 项目内角色（取值待确认） |
| `create_time` | 加入时间 |

索引：`idx_pm_project_member`。

## pm_work_item_type

事项类型定义。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `code` | 类型编码（如 requirement/task/bug/iteration/test_case） |
| `name` / `icon` / `color` / `sort_order` | 展示属性 |
| `enabled` | 是否启用 |
| `built_in` | 是否内置（1 = 内置，不可删除；名称/图标/颜色只读） |
| `description` | 类型说明 |
| `template_code` | 创建工作流时使用的内置模板编码 |

种子：5 个内置类型 `requirement` / `task` / `bug` / `iteration` / `test_case`，均为 `built_in=1`。迭代默认工作流为 `open → in_progress → done → closed`。

## pm_project_issue_type

项目启用的类型及排序。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `project_id` / `type_code` / `sort_order` | 项目、类型、顺序 |

索引：`idx_pm_project_issue_type`。

## pm_work_item

统一事项表。

| 字段 | 说明 |
|------|------|
| `id` | 主键（雪花） |
| `project_id` | 所属项目 |
| `item_no` | 项目内递增序号 |
| `type_code` | 事项类型 |
| `title` / `description` | 标题与描述 |
| `status` | 当前状态 |
| `priority` | 优先级 |
| `assignee_id` / `reporter_id` | 处理人/报告人 |
| `parent_id` | 父事项 |
| `module_id` | 所属模块 |
| `sprint_id` | 迭代 |
| `end_time` | 截止时间（UTC naive DATETIME，系统字段） |
| `custom_fields` | 动态字段 JSON |
| `version` | 乐观锁版本号，单字段/批量更新使用 `WHERE version=?` 条件更新 |
| `create_by` / `update_by` / `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_pm_work_item_project`、`idx_pm_work_item_status`、`idx_pm_work_item_module`、`idx_pm_work_item_end_time (project_id, end_time)`。

## pm_project_sequence

事项序号分配器。

| 字段 | 说明 |
|------|------|
| `project_id` | 项目（主键） |
| `next_item_no` | 下一个序号 |

## pm_project_module

项目模块树。

| 字段 | 说明 |
|------|------|
| `id` / `project_id` / `parent_id` | 主键、项目、父模块 |
| `name` / `description` / `sort_order` / `enabled` | 名称、描述、排序、启用 |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_pm_project_module`。

## pm_field_definition

动态字段定义。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `project_id` | 项目（系统字段可为空） |
| `scope` | 作用域（system/project） |
| `field_key` / `field_name` / `field_type` | 字段标识、名称、类型 |
| `config` | 字段配置 JSON |
| `applicable_types` | 适用类型 |
| `required_flag` | 是否必填 |
| `sort_order` / `system_flag` | 排序与系统字段标记 |
| `batch_editable` | 是否允许批量编辑（1 允许） |
| `batch_order` | 批量编辑字段顺序 |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

## pm_field_option

字段选项。

| 字段 | 说明 |
|------|------|
| `id` / `field_id` | 主键与字段 |
| `option_key` / `option_label` / `sort_order` | 选项值、显示名、排序 |

## pm_work_item_link

事项关联。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `source_id` / `target_id` | 源/目标事项 |
| `link_type` | 关联类型 |
| `create_time` | 创建时间 |

## pm_work_item_comment

事项评论。

| 字段 | 说明 |
|------|------|
| `id` / `work_item_id` / `parent_id` | 主键、事项、父评论 |
| `content` | 内容 |
| `author_name` / `create_by` / `create_time` / `del_flag` | 作者与审计 |

索引：`idx_pm_work_item_comment`。

## pm_work_item_activity

事项活动流水。

| 字段 | 说明 |
|------|------|
| `id` / `work_item_id` / `batch_id` | 主键、事项、批次（UUID 字符串 `VARCHAR(64)`） |
| `event_type` | 事件类型（创建/更新/流转/评论等） |
| `actor_id` / `actor_name` | 操作人 |
| `field_key` / `field_name` / `field_type` | 变更字段 |
| `old_value` / `new_value` | 变更前后原始值（`MEDIUMTEXT`，支持长文本/JSON） |
| `old_label` / `new_label` | 展示标签（`VARCHAR(1024)`） |
| `extra_json` / `create_time` | 扩展与时间 |

索引：`idx_pm_work_item_activity`、`idx_pm_work_item_activity_batch (batch_id)`。

## pm_status_definition

状态与工作流定义。

| 字段 | 说明 |
|------|------|
| `id` / `project_id` / `type_code` | 主键、项目、类型 |
| `status_code` / `status_name` | 状态编码与名称 |
| `sort_order` | 排序 |
| `is_initial` / `is_final` | 初始/终态 |
| `layout_x` / `layout_y` | 画布坐标 |
| `transitions` | 流转配置 JSON（含 validator/post-function） |

## pm_saved_view

保存视图。

| 字段 | 说明 |
|------|------|
| `id` / `project_id` / `user_id` | 主键、项目、用户 |
| `name` / `type_code` | 名称、类型 |
| `query_spec` / `columns` | 查询条件与列配置 |
| `is_default` | 是否默认视图 |
| `create_time` / `update_time` | 时间 |

## pm_type_field_layout

类型字段布局。

| 字段 | 说明 |
|------|------|
| `id` / `project_id` / `type_code` | 主键、项目、类型 |
| `layout_config` | 布局 JSON |
| `create_time` / `update_time` | 时间 |

## pm_work_item_watcher

事项关注者。

| 字段 | 说明 |
|------|------|
| `id` | 主键（自增） |
| `work_item_id` / `user_id` | 事项与用户 |
| `create_time` | 关注时间 |

唯一键 `uk_pm_work_item_watcher_item_user (work_item_id, user_id)`；索引 `idx_pm_work_item_watcher_user (user_id)`。

## pm_batch_action_config

项目级批量动作配置；未保存的动作由服务端返回默认值。

| 字段 | 说明 |
|------|------|
| `id` | 主键（自增） |
| `project_id` | 项目 |
| `action_code` | `UPDATE_FIELDS` / `TRANSITION` / `IMPORT` / `EXPORT` / `DELETE` / `COPY` |
| `enabled` | 是否启用（删除默认关闭） |
| `max_items` | 单次上限（普通 1–1000，导入/导出 1–5000） |
| `require_confirm` | 是否需要二次确认 |
| `allowed_roles` | JSON 数组，如 `["admin","user"]`；空数组表示不限制 |
| `field_whitelist` | JSON 数组，批量编辑允许的字段 key；仅 `UPDATE_FIELDS` 使用 |
| `create_time` / `update_time` | 时间 |

唯一键 `uk_pm_batch_action_project_code (project_id, action_code)`。

JSON 结构：

```json
{
  "allowed_roles": ["admin", "user"],
  "field_whitelist": ["priority", "assignee_id", "module_id", "end_time"]
}
```

## 迁移与 DDL

| 迁移 | 内容 |
|------|------|
| `V1__initial_schema.sql` | 56 张表基线，含 15 张 `pm_*` 表 |
| `V2__seed_system_data.sql` | 系统种子：4 个事项类型、16 条状态定义 |
| `V3__pm_backend_shared_foundation.sql` | 截止时间/乐观锁、类型元数据、字段批量属性、活动批次号、关注者、批量动作配置、迭代类型与索引 |

V3 新增/修改：

- `pm_work_item`：`end_time DATETIME NULL`、`version BIGINT NOT NULL DEFAULT 0`；索引 `idx_pm_work_item_end_time`。
- `pm_work_item_type`：`built_in`、`description`、`template_code`；种子 `iteration`（排序 4），`test_case` 排序改 5。
- `pm_field_definition`：`batch_editable`、`batch_order`。
- `pm_work_item_activity`：`batch_id` 改 `VARCHAR(64)`；`old_value/new_value` 改 `MEDIUMTEXT`；label 放宽到 `VARCHAR(1024)`；新增批次索引。
- 新表 `pm_work_item_watcher`、`pm_batch_action_config`。

> 本文档为表级字典（历史格式）。完整 10 列字段字典按 `database-documentation-standard.md` 在后续数据库专项中补齐；本轮变更字段、表、索引与枚举已同步。

## 重复的 sys_user（已解决）

原 SQLite 时代 PM schema 重复定义了 `sys_user`。MySQL 基线 V1 已删除该重复定义，只保留 user 模块的统一 `sys_user`。

## 写入方

| 表 | 归属 |
|----|------|
| 项目/成员/类型/模块 | `PmProjectService`、`PmProjectModuleService`、`PmProjectIssueTypeService` |
| 事项/关联/评论/活动 | `PmWorkItemService`、`PmWorkItemFieldUpdateService`、`PmWorkItemBatchService`、`PmWorkItemCommentService`、`PmWorkItemActivityService` |
| 批量动作配置 | `BatchActionConfigService` |
| 字段/布局/方案 | `PmFieldDefinitionService`、`PmFieldLayoutService`、`PmIssueTypeSchemeService` |
| 状态工作流 | `PmStatusWorkflowService` |
| 保存视图 | `PmSavedViewService` |
