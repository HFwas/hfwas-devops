# API 测试平台功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/api-test-core/`、`frontend/src/modules/api-test/`、`docs/backend/api_spec.md`、`docs/backend/db_design.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：接口定义、调试、环境、集合批量运行 |

---

## 1. 概述

API 测试平台提供类 Postman 的接口定义、调试与集合运行能力：接口分组与版本、参数/响应/脚本/断言、环境变量、cURL 导入、调试历史、集合批量执行。数据按 `project_id` 隔离（无 `tenant_id`）。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 测试工程师 | 维护接口定义、调试接口、批量回归 |
| 研发工程师 | 调试联调接口、从 cURL 导入 |
| 项目负责人 | 查看集合运行结果与断言通过率 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 接口定义列表 | 分页、分组筛选 | `/api-test/definitions` | 可用 |
| 接口定义详情 | 参数、响应、脚本、断言、调试工作台 | `/api-test/definitions/:id` | 可用 |
| 版本与状态 | 发布、废弃、回退草稿 | 详情页 | 可用 |
| 分组 | 树、创建、编辑、删除 | 定义列表侧栏 | 可用 |
| 单接口调试 | 构建请求、发送、响应/断言/脚本日志 | 详情页 | 可用 |
| cURL 导入 | 解析 cURL 命令 | 调试工作台 | 可用 |
| 调试历史 | 分页、详情、按接口查询、删除 | 详情页 | 可用 |
| 环境管理 | 环境 CRUD、变量（含 secret） | `/api-test/environments` | 可用 |
| 集合管理 | 集合 CRUD、文件夹树、集合项、排序、批量添加 | `/api-test/collections` | 可用 |
| 集合运行 | 批量执行、运行列表、运行详情 | 集合详情 | 可用 |

## 4. 核心流程

### 4.1 单接口调试

```text
选择接口与环境
  → 渲染路径/查询/Header/Body 参数
  → 变量插值（环境变量 + 提取变量）
  → 执行前置脚本
  → 发送 HTTP 请求
  → 执行后置脚本
  → 执行断言、提取变量
  → 保存 api_debug_history
  → 返回响应、耗时、断言结果与日志
```

### 4.2 集合批量运行

```text
选择集合与环境
  → 按 sort_order 读取启用项
  → 逐条执行（复用单接口调试引擎）
  → 记录 api_collection_run_item
  → 汇总 total/passed/failed/error/duration
  → 更新 api_collection_run
```

### 4.3 cURL 导入

```text
粘贴 cURL
  → /apitest/curl/parse
  → 解析 method/url/headers/body
  → 填充调试工作台
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 项目隔离 | 所有表通过 `project_id` 关联，无 `tenant_id` |
| 状态流转 | 草稿 `draft` → 发布 `published` → 废弃 `deprecated`；可回退草稿 |
| 版本快照 | 发布时写入 `api_definition_version`，包含路径、方法、参数、响应 |
| 逻辑删除 | `deleted`（0/1） |
| 变量优先级 | 待确认（环境变量 / 提取变量 / 局部变量的覆盖顺序） |
| Secret 变量 | `api_environment_variable.is_secret` 标记，返回时是否脱敏待确认 |
| 集合项 | 引用 `api_definition`，支持 `enabled` 与排序 |
| 断言 | `source` + `compare_type` + `expression` + `expected_value` |

## 6. 异常与边界

| 场景 | 系统行为 |
|------|----------|
| 目标接口超时/不可达 | 记录 `status=error` 与 `error_message` |
| 断言失败 | 调试结果标记 `all_assertions_passed=false` |
| 前置脚本异常 | 中止请求并记录脚本日志 |
| 环境不存在 | 拒绝调试/运行 |
| 集合为空 | 拒绝运行或返回 0 项结果 |
| 集合运行中单条失败 | 继续执行后续项，汇总 failed_count |
| 跨项目访问 | 应拒绝（具体错误码待确认） |

## 7. 接口与数据

- 接口：`/apitest/definitions/*`、`/apitest/groups/*`、`/apitest/debug/*`、`/apitest/debug-histories/*`、`/apitest/environments/*`、`/apitest/collections/*`、`/apitest/curl/*`
- 详见 [04-api/modules/api-test.md](../../04-api/modules/api-test.md)
- 数据表：`api_group`、`api_definition`、`api_definition_param`、`api_definition_response`、`api_definition_version`、`api_definition_script`、`api_definition_assertion`、`api_definition_extract`、`api_environment`、`api_environment_variable`、`api_debug_history`、`api_collection`、`api_collection_folder`、`api_collection_item`、`api_collection_run`、`api_collection_run_item`
- 详见 [03-database/tables/api-test.md](../../03-database/tables/api-test.md) 与 [docs/backend/db_design.md](../../../backend/db_design.md)

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-API-01 | 接口定义的参数/响应/脚本/断言可完整保存与回显 |
| AC-API-02 | 发布后生成版本快照，回退草稿后状态正确 |
| AC-API-03 | cURL 导入结果与原始命令语义一致 |
| AC-API-04 | 环境变量与提取变量能正确插值 |
| AC-API-05 | 断言失败时结果标记失败并保留响应 |
| AC-API-06 | 集合批量运行汇总数量与逐条结果一致 |
| AC-API-07 | 跨项目无法访问其他项目数据 |

## 9. 待确认

1. 变量优先级与作用域规则。
2. Secret 变量在响应与历史中的脱敏策略。
3. 脚本引擎能力边界（GraalVM JS，见 `ScriptSandbox`）。
4. 是否支持 OpenAPI 导入/导出（当前设计文档提及兼容，代码待确认）。
