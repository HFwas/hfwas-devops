# PM 接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架（路径与用途已核对；请求/响应/失败示例待补）
> 数据来源：`backend/server/src/main/java/com/hfwas/devops/pm/api/controller/`、`docs/pm/pm-api.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：14 个 Controller 的方法与路径清单 |

---

基础路径：`/pm`。除特别说明外均要求已认证，按 `tenant_id` + `project_id` 隔离。完整字段级文档见 [docs/pm/pm-api.md](../../../pm/pm-api.md)（需与代码核对）。

## 项目

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/pm/projects/page` | 项目分页 |
| POST | `/pm/projects/save` | 创建/更新项目 |
| GET | `/pm/projects/{id}` | 项目详情 |
| GET | `/pm/projects/{id}/access-context` | 当前用户对项目的访问上下文 |
| POST | `/pm/projects/delete` | 删除项目 |
| POST | `/pm/projects/issue-types/list` | 项目启用的类型列表 |
| POST | `/pm/projects/issue-types/save` | 保存项目类型配置 |

## 事项

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/pm/work-items/page` | 事项分页/组合查询 |
| POST | `/pm/work-items/save` | 创建/更新事项 |
| GET | `/pm/work-items/{id}` | 事项详情 |
| POST | `/pm/work-items/delete` | 删除事项 |
| POST | `/pm/work-items/{id}/transition` | 状态流转 |
| GET | `/pm/work-items/{id}/links` | 事项关联列表 |
| POST | `/pm/work-items/links/save` | 新增关联 |
| GET | `/pm/work-items/{id}/activities` | 活动流水 |

## 评论

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/pm/work-items/{id}/comments` | 评论列表 |
| GET | `/pm/work-items/{id}/comments/count` | 单事项评论数 |
| POST | `/pm/work-items/comments/counts` | 批量评论数 |
| POST | `/pm/work-items/comments/save` | 新增/编辑评论 |
| POST | `/pm/work-items/comments/delete` | 删除评论 |

## 导入导出

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/pm/work-items/io/columns` | 可导入/导出列 |
| POST | `/pm/work-items/io/import/template` | 下载导入模板 |
| POST | `/pm/work-items/io/export` | 导出事项 |
| POST | `/pm/work-items/io/import/preview` | 导入预览 |
| POST | `/pm/work-items/io/import` | 正式导入 |

## 字段与布局

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/pm/fields/definitions/list` | 字段定义列表 |
| POST | `/pm/fields/definitions/catalog` | 可用字段类型目录 |
| POST | `/pm/fields/definitions/available` | 可添加到类型的字段 |
| POST | `/pm/fields/definitions/add-to-type` | 字段绑定到类型 |
| POST | `/pm/fields/definitions/remove-from-type` | 解除字段与类型绑定 |
| POST | `/pm/fields/definitions/save` | 保存字段定义 |
| POST | `/pm/fields/definitions/delete` | 删除字段定义 |
| GET | `/pm/fields/definitions/{id}` | 字段详情 |
| GET | `/pm/fields/definitions/options` | 字段选项 |
| GET | `/pm/fields/definitions/options/resolve` | 解析选项标签 |
| POST | `/pm/fields/definitions/options/remote/preview` | 远程选项预览 |
| POST | `/pm/fields/layout/get` | 获取类型字段布局 |
| POST | `/pm/fields/layout/save` | 保存类型字段布局 |

## 状态工作流

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/pm/status/workflow/get` | 获取工作流定义 |
| POST | `/pm/status/workflow/options` | 状态选项 |
| POST | `/pm/status/workflow/allowed` | 当前状态允许的流转 |
| POST | `/pm/status/workflow/post-function-meta` | 后置动作元数据 |
| POST | `/pm/status/workflow/transition-meta` | 流转配置元数据 |
| POST | `/pm/status/workflow/save` | 保存工作流 |
| POST | `/pm/status/workflow/reset` | 重置为默认工作流 |

## 类型方案

| 方法 | 路径 | 用途 |
|------|------|------|
| POST | `/pm/issue-type-schemes/export` | 导出类型方案 |
| POST | `/pm/issue-type-schemes/export-project` | 导出项目配置 |
| POST | `/pm/issue-type-schemes/preview` | 导入预览 |
| POST | `/pm/issue-type-schemes/import` | 导入类型方案 |
| POST | `/pm/issue-type-schemes/import-project` | 导入项目配置 |

## 模块、视图与元数据

| 方法 | 路径 | 用途 |
|------|------|------|
| GET | `/pm/project-modules/tree` | 模块树 |
| GET | `/pm/project-modules/flat` | 模块平铺 |
| POST | `/pm/project-modules/save` | 保存模块 |
| POST | `/pm/project-modules/delete` | 删除模块 |
| POST | `/pm/views/save` | 保存视图 |
| POST | `/pm/views/list` | 视图列表 |
| POST | `/pm/views/delete` | 删除视图 |
| POST | `/pm/meta/types` | 事项类型元数据 |
| POST | `/pm/meta/types/save` | 保存类型 |
| POST | `/pm/meta/types/delete` | 删除类型 |
| POST | `/pm/meta/detail-tabs` | 详情页 Tab 配置 |
| POST | `/pm/meta/features` | 类型特性开关 |
| POST | `/pm/meta/board` | 看板数据 |

## 待补

1. 每个接口的请求参数、校验规则、正常/失败响应示例。
2. `pm-api.md` 与当前代码的字段差异核对。
3. Postman collection：`docs/api/pm/pm-api.postman_collection.json`。
