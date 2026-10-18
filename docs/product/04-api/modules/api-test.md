# API 测试平台接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架（路径与用途已核对；请求/响应/失败示例待补）
> 数据来源：`backend/api-test-core/.../controller/`、`docs/backend/api_spec.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：10 个 Controller 的方法与路径清单 |

---

基础路径：`/apitest`。均要求已认证，按 `project_id` 隔离。字段级示例见 [docs/backend/api_spec.md](../../../backend/api_spec.md)（需与代码核对）。

## 接口定义

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/apitest/definitions/page` | 分页 |
| POST | `/apitest/definitions` | 创建 |
| GET | `/apitest/definitions/{id}` | 详情 |
| PUT | `/apitest/definitions/{id}` | 更新 |
| DELETE | `/apitest/definitions/{id}` | 删除 |
| POST | `/apitest/definitions/{id}/publish` | 发布 |
| POST | `/apitest/definitions/{id}/deprecate` | 废弃 |
| POST | `/apitest/definitions/{id}/revert-draft` | 回退草稿 |

## 分组

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/apitest/groups` | 创建分组 |
| GET | `/apitest/groups/tree` | 分组树 |
| GET | `/apitest/groups/{id}` | 分组详情 |
| PUT | `/apitest/groups/{id}` | 更新分组 |
| DELETE | `/apitest/groups/{id}` | 删除分组 |

## 调试

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/apitest/debug/execute` | 执行调试请求 |
| POST | `/apitest/curl/parse` | 解析 cURL |

## 调试历史

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/apitest/debug-histories/page` | 分页 |
| GET | `/apitest/debug-histories/{id}` | 详情 |
| GET | `/apitest/debug-histories/by-definition` | 按接口查询 |
| DELETE | `/apitest/debug-histories/{id}` | 删除 |
| DELETE | `/apitest/debug-histories/batch` | 批量删除 |

## 环境

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/apitest/environments/page` | 分页 |
| GET | `/apitest/environments/list` | 全部环境 |
| GET | `/apitest/environments/{id}` | 详情 |
| POST | `/apitest/environments` | 创建 |
| PUT | `/apitest/environments/{id}` | 更新 |
| DELETE | `/apitest/environments/{id}` | 删除 |

## 集合

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/apitest/collections/page` | 分页 |
| GET | `/apitest/collections/{id}` | 详情 |
| POST | `/apitest/collections` | 创建 |
| PUT | `/apitest/collections/{id}` | 更新 |
| DELETE | `/apitest/collections/{id}` | 删除 |

## 集合文件夹

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/apitest/collections/{collectionId}/folders/tree` | 文件夹树 |
| POST | `/apitest/collections/{collectionId}/folders` | 创建 |
| PUT | `/apitest/collections/{collectionId}/folders/{folderId}` | 更新 |
| DELETE | `/apitest/collections/{collectionId}/folders/{folderId}` | 删除 |

## 集合项

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/apitest/collections/{collectionId}/items` | 添加 |
| POST | `/apitest/collections/{collectionId}/items/batch` | 批量添加 |
| PUT | `/apitest/collections/{collectionId}/items/{itemId}` | 更新 |
| DELETE | `/apitest/collections/{collectionId}/items/{itemId}` | 删除 |
| PUT | `/apitest/collections/{collectionId}/items/reorder` | 排序 |

## 集合运行

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/apitest/collections/{collectionId}/run` | 触发批量运行 |
| GET | `/apitest/collections/{collectionId}/runs` | 运行分页 |
| GET | `/apitest/collections/runs/{runId}` | 运行详情 |
| DELETE | `/apitest/collections/runs/{runId}` | 删除运行 |

## 待补

1. 请求/响应字段、参数校验、断言与脚本字段。
2. 失败示例：参数校验、项目越权、接口不存在、运行失败。
3. Postman collection：`docs/api/api-test/api-test-api.postman_collection.json`。
4. `api_spec.md` 路径与实际代码的一致性核对（尤其是 POST 基础路径与子路径）。
