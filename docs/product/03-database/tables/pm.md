# 项目管理（PM）数据字典

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（表级）
> 数据来源：`backend/server/src/main/resources/db/pm-schema.sql`、`backend/server/.../pm/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：15 张 pm_* 表 + 重复 sys_user 说明 |

---

共 15 张 `pm_*` 表，另有 1 段重复的 `sys_user` 定义（见文末风险）。

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
| `code` | 类型编码（如 requirement/task/bug） |
| `name` / `icon` / `color` / `sort_order` | 展示属性 |
| `enabled` | 是否启用 |

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
| `custom_fields` | 动态字段 JSON |
| `create_by` / `update_by` / `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_pm_work_item_project`、`idx_pm_work_item_status`、`idx_pm_work_item_module`。

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
| `id` / `work_item_id` / `batch_id` | 主键、事项、批次 |
| `event_type` | 事件类型（创建/更新/流转/评论等） |
| `actor_id` / `actor_name` | 操作人 |
| `field_key` / `field_name` / `field_type` | 变更字段 |
| `old_value` / `new_value` / `old_label` / `new_label` | 变更前后值 |
| `extra_json` / `create_time` | 扩展与时间 |

索引：`idx_pm_work_item_activity`。

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

## 风险：重复的 sys_user

`pm-schema.sql` 第 224 行再次 `CREATE TABLE IF NOT EXISTS sys_user`，列集合比 user 模块少（缺 `auth_source`、`external_id`、`connector_id`）。embedded 模式下 `SqliteSchemaInitializer` 在 `UserSchemaMigration` 之后执行，`IF NOT EXISTS` 不会覆盖已有表，但两处定义漂移会导致新环境/迁移场景行为不一致。建议移除 PM schema 中的 `sys_user` 定义。

## 写入方

| 表 | 归属 |
|----|------|
| 项目/成员/类型/模块 | `PmProjectService`、`PmProjectModuleService`、`PmProjectIssueTypeService` |
| 事项/关联/评论/活动 | `PmWorkItemService`、`PmWorkItemCommentService`、`PmWorkItemActivityService` |
| 字段/布局/方案 | `PmFieldDefinitionService`、`PmFieldLayoutService`、`PmIssueTypeSchemeService` |
| 状态工作流 | `PmStatusWorkflowService` |
| 保存视图 | `PmSavedViewService` |
