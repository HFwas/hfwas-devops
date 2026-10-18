# API 测试平台数据字典

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（表级）
> 数据来源：`backend/server/src/main/resources/db/api-test-schema.sql`、`docs/backend/db_design.md`、`backend/api-test-core/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：16 张表汇总；字段明细引用现有 db_design.md |

---

共 16 张表。完整字段、枚举与 ER 关系见 [docs/backend/db_design.md](../../../backend/db_design.md)（该文档以 MySQL 为基准，SQLite 差异见其文首说明）。

## 接口定义相关（8 张）

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `api_group` | 接口分组树 | `project_id`、`parent_id`、`name`、`sort_order` |
| `api_definition` | 接口定义主表 | `project_id`、`group_id`、`path`、`method`、`status`、`version`、`protocol`、`host`、`content_type` |
| `api_definition_param` | 请求参数 | `definition_id`、`param_type`（path/query/header/body）、`name`、`data_type`、`required`、`parent_id` |
| `api_definition_response` | 响应定义 | `definition_id`、`status_code`、`content_type`、`body_schema`、`body_example` |
| `api_definition_version` | 版本快照 | `definition_id`、`version`、`change_log`、`snapshot_*` |
| `api_definition_script` | 前置/后置脚本 | `definition_id`、`script_type`、`content`、`enabled` |
| `api_definition_assertion` | 响应断言 | `definition_id`、`source`、`compare_type`、`expression`、`expected_value` |
| `api_definition_extract` | 变量提取 | `definition_id`、`variable_name`、`expression`、`source` |

索引：`idx_def_project`、`idx_def_group`、`idx_def_path_method`、`idx_def_status`、`idx_group_project`、`idx_group_sort`、各子表 `idx_*_definition`。

## 环境与调试（3 张）

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `api_environment` | 环境 | `project_id`、`name`、`description`、`sort_order` |
| `api_environment_variable` | 环境变量 | `environment_id`、`name`、`value`、`is_secret`、`sort_order` |
| `api_debug_history` | 调试历史 | 请求/响应全量报文、`duration_ms`、`status`、`assertion_results`、`extracted_variables` |

索引：`idx_env_project`、`idx_env_var_env`、`idx_debug_definition`、`idx_debug_project`。

## 集合与批量运行（5 张）

| 表 | 用途 | 关键字段 |
|----|------|----------|
| `api_collection` | 集合 | `project_id`、`name`、`description`、`sort_order` |
| `api_collection_folder` | 集合文件夹树 | `collection_id`、`parent_id`、`name`、`sort_order` |
| `api_collection_item` | 集合项（引用定义） | `collection_id`、`folder_id`、`definition_id`、`enabled`、`sort_order` |
| `api_collection_run` | 批量运行汇总 | `collection_id`、`project_id`、`environment_id`、`status`、`total/passed/failed/error_count`、`duration_ms`、`trigger_mode` |
| `api_collection_run_item` | 批量运行明细 | `run_id`、`collection_item_id`、`definition_id`、请求/响应报文、`status`、`assertion_results` |

索引：`idx_collection_project`、`idx_collection_folder`、`idx_collection_item_collection`、`idx_collection_run_collection`、`idx_collection_run_item_run`。

## 公共字段

- 全部表带 `deleted`（0/1）逻辑删除；多数带 `create_by` / `update_by` / `create_time` / `update_time`。
- 无 `tenant_id`，通过 `project_id` 隔离。
- 主键为 `INTEGER PRIMARY KEY`，由 MyBatis-Plus 雪花 ID 生成。

## 写入方

| 表 | 归属 |
|----|------|
| 分组/定义/参数/响应/版本/脚本/断言/提取 | `ApiGroupService`、`ApiDefinitionService` 及子服务 |
| 环境与变量 | `EnvironmentService` |
| 调试历史 | `DebugHistoryService` |
| 集合/文件夹/集合项 | `CollectionService`、`CollectionFolderService`、`CollectionItemService` |
| 批量运行 | `CollectionRunService` |

## 注意

1. `db_design.md` 以 MySQL 为基准，实际 SQLite 字段名 `created_by → create_by`、`updated_by → update_by`，类型按 TEXT/INTEGER 映射。
2. 调试历史与批量运行明细包含完整请求/响应体，未做脱敏，敏感信息需在使用侧注意。
3. 测试库 schema 见 `backend/api-test-core/src/test/resources/sql/init.sql`（先 DROP 再建 16 张表）。
