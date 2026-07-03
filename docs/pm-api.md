# PM 模块接口文档

> 版本：1.1  
> 更新日期：2026-07-03  
> 适用范围：hfwas-devops 项目管理（PM）REST API

---

## 1. 通用说明

### 1.1 服务地址

| 环境 | 后端直连 | 前端开发代理 |
|------|----------|--------------|
| 本地 | `http://localhost:8089` | `http://localhost:5173/api` → 转发至 `8089` |

前端请求统一加前缀 `/api`，Vite 代理会去掉该前缀后转发到后端。

### 1.2 统一响应格式

所有业务接口返回 `BaseResult<T>`：

```json
{
  "code": 0,
  "msg": "",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | integer | `0` 成功，非 `0` 失败 |
| msg | string | 错误信息，成功时通常为空 |
| data | T | 业务数据 |

### 1.3 分页结构

分页接口的 `data` 为 MyBatis-Plus `IPage` 结构（前端可映射为 `PageResult`）：

```json
{
  "records": [],
  "total": 100,
  "size": 20,
  "current": 1,
  "pages": 5
}
```

通用分页请求字段（继承 `PmPageRequest`）：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNo | integer | 1 | 页码 |
| pageSize | integer | 20 | 每页条数 |

### 1.4 ID 序列化说明

Long 类型 ID 在 JSON 中序列化为 **字符串**（避免 JavaScript 精度丢失），例如 `"id": "42"`。  
事项另有 Jira 式展示字段 `itemKey`（如 `DEMO-1`），由 `项目 code + item_no` 组成。

### 1.5 接口风格约定

| 约定 | 说明 |
|------|------|
| 前缀 | 业务接口均以 `/pm/` 开头 |
| 创建/更新 | 多数资源使用 `POST .../save`（按 body 中是否有 id 区分） |
| 删除 | `POST .../delete?id={id}`（query 参数） |
| 列表/查询 | 多数使用 `POST` + JSON body |
| 单条查询 | 使用 `GET /{id}` |

---

## 2. 健康检查

### GET `/health/check`

检查服务是否存活。

**响应示例：**

```
UP
```

> 该接口不包装 `BaseResult`，直接返回字符串。

---

## 3. 项目管理

Base path: `/pm/projects`

### 3.1 项目分页

**POST** `/pm/projects/page`

**请求体：**

```json
{
  "pageNo": 1,
  "pageSize": 20,
  "keyword": "demo"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| pageNo | integer | 否 | 页码 |
| pageSize | integer | 否 | 每页条数 |
| keyword | string | 否 | 按名称/编码模糊搜索 |

**响应 data：** `IPage<PmProject>`

**PmProject 主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 项目 ID |
| code | string | 项目编码（用于事项 itemKey，如 `demo` → `DEMO-1`） |
| name | string | 项目名称 |
| description | string | 描述 |
| settings | object | 扩展配置 JSON |

---

### 3.2 创建/更新项目

**POST** `/pm/projects/save`

**请求体：** `PmProject`（`id` 为空则创建，否则更新）

**响应 data：** `Long` — 项目 ID

---

### 3.3 项目详情

**GET** `/pm/projects/{id}`

**路径参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 项目 ID |

**响应 data：** `PmProject`

---

### 3.4 删除项目

**POST** `/pm/projects/delete?id={id}`

**Query 参数：**

| 参数 | 类型 | 说明 |
|------|------|------|
| id | long | 项目 ID |

**响应 data：** `null`

---

### 3.5 功能模块

Base path: `/pm/project-modules`

项目级功能模块（类似 Jira Component），支持树形层级。事项通过 `moduleId` 归属单一模块。

#### 3.5.1 模块树

**GET** `/pm/project-modules/tree?projectId={projectId}`

**响应 data：** `PmProjectModule[]`（根节点列表，含 `children`）

#### 3.5.2 模块扁平列表（下拉选项）

**GET** `/pm/project-modules/flat?projectId={projectId}`

**响应 data：** `PmProjectModule[]`，每项含 `pathLabel`（如 `订单 / 支付`）

#### 3.5.3 创建/更新模块

**POST** `/pm/project-modules/save`

**请求体：**

```json
{
  "id": null,
  "projectId": 1,
  "parentId": null,
  "name": "用户中心",
  "description": "账号、权限相关"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| id | long | 更新时必填 |
| projectId | long | 项目 ID |
| parentId | long | 上级模块 ID，顶级为 null |
| name | string | 名称，同级唯一 |
| description | string | 描述（可选） |

**响应 data：** `Long` — 模块 ID

#### 3.5.4 删除模块

**POST** `/pm/project-modules/delete?id={id}`

存在子模块或关联事项时拒绝删除。

---

## 4. 事项（Work Item）

Base path: `/pm/work-items`

### 4.1 事项分页/组合查询

**POST** `/pm/work-items/page`

**请求体：** `QuerySpec`

```json
{
  "projectId": 1,
  "typeCode": "bug",
  "logic": "AND",
  "conditions": [
    { "field": "status", "operator": "EQ", "value": "open" },
    { "field": "title", "operator": "LIKE", "value": "登录" }
  ],
  "groups": [],
  "sort": [{ "field": "update_time", "order": "DESC" }],
  "pageNo": 1,
  "pageSize": 20
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| projectId | long | 项目 ID |
| typeCode | string | 事项类型：`requirement` / `task` / `bug` / `test_case` |
| logic | string | 顶层条件逻辑：`AND` / `OR` |
| conditions | array | 查询条件列表 |
| groups | array | 嵌套条件组（可选） |
| sort | array | 排序，`order` 为 `ASC` / `DESC` |

**QueryCondition：**

| 字段 | 类型 | 说明 |
|------|------|------|
| field | string | 字段名。系统字段如 `title`、`status`；自定义字段用 `custom.{fieldKey}` |
| operator | string | 见下方运算符 |
| value | any | 条件值 |

**支持运算符：** `EQ` `NE` `GT` `GTE` `LT` `LTE` `LIKE` `IN` `NOT_IN` `BETWEEN` `IS_NULL` `IS_NOT_NULL`

**响应 data：** `IPage<PmWorkItem>`

**PmWorkItem 主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 内部自增主键 |
| projectId | long | 项目 ID |
| itemNo | integer | 项目内序号 |
| itemKey | string | 展示键，如 `DEMO-1`（响应时组装，非库字段） |
| typeCode | string | 事项类型 |
| title | string | 标题 |
| description | string | 描述（Markdown） |
| status | string | 状态 |
| priority | string | 优先级 |
| assigneeId | string | 负责人 ID |
| reporterId | string | 报告人 ID |
| parentId | string | 父事项 ID |
| moduleId | long | 功能模块 ID（可选） |
| customFields | object | 自定义字段 JSON |
| createTime | string | 创建时间 |
| updateTime | string | 更新时间 |

---

### 4.2 创建/更新事项

**POST** `/pm/work-items/save`

**请求体：** `PmWorkItem`（`id` 为空则创建并自动分配 `itemNo`）

**响应 data：** `Long` — 事项内部 ID

---

### 4.3 事项详情

**GET** `/pm/work-items/{id}`

**响应 data：** `PmWorkItem`（含 `itemKey`）

---

### 4.4 删除事项

**POST** `/pm/work-items/delete?id={id}`

**响应 data：** `null`

---

### 4.5 更新状态

**POST** `/pm/work-items/{id}/transition`

> 当前不做流转规则校验，直接更新状态字段。

**路径参数：** `id` — 事项 ID

**请求体：**

```json
{
  "toStatus": "in_progress"
}
```

**响应 data：** `null`

---

### 4.6 添加事项关联

**POST** `/pm/work-items/links/save`

**请求体：**

```json
{
  "sourceId": 1,
  "targetId": 2,
  "linkType": "relates_to"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| sourceId | long | 源事项 ID |
| targetId | long | 目标事项 ID |
| linkType | string | 关联类型：`relates_to` / `blocks` / `duplicates` |

**响应 data：** `Long` — 关联记录 ID

---

### 4.7 查询事项关联

**GET** `/pm/work-items/{id}/links`

**响应 data：** `PmWorkItemLink[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 关联 ID |
| sourceId | string | 源事项 ID |
| targetId | string | 目标事项 ID |
| linkType | string | 关联类型 |
| createTime | string | 创建时间 |

---

## 5. 事项评论

Base path: `/pm/work-items`（与事项共用前缀）

### 5.1 评论列表

**GET** `/pm/work-items/{id}/comments`

**路径参数：** `id` — 事项 ID

**响应 data：** `WorkItemCommentVo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 评论 ID |
| workItemId | string | 事项 ID |
| parentId | string | 回复目标评论 ID，顶级评论为空 |
| content | string | 评论内容 |
| authorName | string | 作者显示名 |
| authorId | string | 作者 ID |
| createTime | string | 创建时间 |
| deletable | boolean | 当前用户是否可删除 |

---

### 5.2 单条事项评论数

**GET** `/pm/work-items/{id}/comments/count`

**响应 data：** `Long`

---

### 5.3 批量评论数

**POST** `/pm/work-items/comments/counts`

**请求体：**

```json
[1, 2, 3]
```

**响应 data：**

```json
{
  "1": 3,
  "2": 0,
  "3": 1
}
```

Key 为事项 ID 字符串，Value 为评论数量。

---

### 5.4 发表评论

**POST** `/pm/work-items/comments/save`

**请求体：**

```json
{
  "workItemId": 1,
  "content": "这是一条评论",
  "parentId": null,
  "authorName": "张三"
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| workItemId | long | 是 | 事项 ID |
| content | string | 是 | 评论内容 |
| parentId | long | 否 | 回复的评论 ID |
| authorName | string | 否 | 作者显示名，默认「匿名用户」 |

**响应 data：** `Long` — 评论 ID

---

### 5.5 删除评论

**POST** `/pm/work-items/comments/delete?id={id}`

**响应 data：** `null`

---

## 6. 字段定义

Base path: `/pm/fields/definitions`

### 6.1 按项目+类型获取字段 Schema

**POST** `/pm/fields/definitions/list`

**请求体：**

```json
{
  "projectId": 1,
  "typeCode": "bug"
}
```

**响应 data：** `FieldDefinition[]`（含布局标记 `showInList` / `searchable` / `showInCreate` / `listOrder`）

**FieldDefinition 主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 字段定义 ID |
| projectId | string | 项目 ID |
| fieldKey | string | 字段编码 |
| fieldName | string | 字段名称 |
| fieldType | string | 类型：`TEXT` / `MARKDOWN` / `SELECT` / `STATUS` 等 |
| systemFlag | integer | 1=系统字段 |
| requiredFlag | integer | 1=必填 |
| config | object | 字段配置 |
| showInList | boolean | 是否在列表展示 |
| searchable | boolean | 是否可搜索 |
| showInCreate | boolean | 是否在新建表单展示 |

---

### 6.2 字段目录（项目下全部字段）

**POST** `/pm/fields/definitions/catalog`

**请求体：**

```json
{
  "projectId": 1
}
```

**响应 data：** `FieldDefinition[]`

---

### 6.3 字段详情

**GET** `/pm/fields/definitions/{id}`

**响应 data：** `FieldDefinition`

---

### 6.4 保存字段定义

**POST** `/pm/fields/definitions/save`

**请求体：**

```json
{
  "definition": {
    "projectId": 1,
    "fieldKey": "severity",
    "fieldName": "严重程度",
    "fieldType": "SELECT",
    "applicableTypes": ["bug"]
  },
  "options": [
    { "optionKey": "critical", "optionLabel": "严重" },
    { "optionKey": "major", "optionLabel": "一般" }
  ]
}
```

**响应 data：** `Long` — 字段定义 ID

---

### 6.5 删除字段

**POST** `/pm/fields/definitions/delete?id={id}`

> 系统字段不可删除。

**响应 data：** `null`

---

### 6.6 字段选项列表

**GET** `/pm/fields/definitions/options?fieldId={fieldId}`

**响应 data：** `FieldOption[]`

---

## 7. 字段布局

Base path: `/pm/fields/layout`

控制各事项类型下字段在列表、搜索、新建中的启用状态。

### 7.1 获取布局配置

**POST** `/pm/fields/layout/get`

**请求体：**

```json
{
  "projectId": 1,
  "typeCode": "bug"
}
```

**响应 data：** `TypeFieldLayoutConfig`

```json
{
  "listFields": ["title", "status", "priority", "assignee_id"],
  "searchFields": ["title", "status", "priority"],
  "createFields": ["title", "description"]
}
```

---

### 7.2 保存布局配置

**POST** `/pm/fields/layout/save`

**请求体：**

```json
{
  "projectId": 1,
  "typeCode": "bug",
  "layout": {
    "listFields": ["title", "status"],
    "searchFields": ["title"],
    "createFields": ["title"]
  }
}
```

**响应 data：** `null`

---

## 8. 保存视图

Base path: `/pm/views`

### 8.1 保存视图

**POST** `/pm/views/save`

**请求体：** `PmSavedView`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 为空则创建 |
| projectId | long | 项目 ID |
| name | string | 视图名称 |
| typeCode | string | 事项类型 |
| querySpec | object | 查询条件（同 QuerySpec 结构） |
| columns | array | 列配置 |
| isDefault | integer | 是否默认 |

**响应 data：** `Long` — 视图 ID

---

### 8.2 视图列表

**POST** `/pm/views/list`

**请求体：**

```json
{
  "projectId": 1,
  "typeCode": "bug"
}
```

**响应 data：** `PmSavedView[]`

---

### 8.3 删除视图

**POST** `/pm/views/delete?id={id}`

**响应 data：** `null`

---

## 9. 元数据与看板

Base path: `/pm`

### 9.1 事项类型列表

**POST** `/pm/meta/types`

**请求体：** `{}`（空对象即可）

**响应 data：** `PmWorkItemType[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 类型 ID |
| code | string | 类型编码 |
| name | string | 类型名称 |
| icon | string | 图标 |
| sortOrder | integer | 排序 |

**内置类型：**

| code | name |
|------|------|
| requirement | 需求 |
| task | 任务 |
| bug | 缺陷 |
| test_case | 测试用例 |

---

### 9.2 看板数据

**POST** `/pm/board`

**请求体：**

```json
{
  "projectId": 1,
  "typeCode": "task"
}
```

**响应 data：**

```json
{
  "open": [ /* PmWorkItem[] */ ],
  "in_progress": [ /* PmWorkItem[] */ ],
  "done": [ /* PmWorkItem[] */ ],
  "closed": [ /* PmWorkItem[] */ ]
}
```

按状态分组的事项列表，每项含 `itemKey`。

---

## 10. 接口索引

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 健康 | GET | `/health/check` | 健康检查 |
| 项目 | POST | `/pm/projects/page` | 项目分页 |
| 项目 | POST | `/pm/projects/save` | 创建/更新项目 |
| 项目 | GET | `/pm/projects/{id}` | 项目详情 |
| 项目 | POST | `/pm/projects/delete` | 删除项目 |
| 事项 | POST | `/pm/work-items/page` | 事项分页查询 |
| 事项 | POST | `/pm/work-items/save` | 创建/更新事项 |
| 事项 | GET | `/pm/work-items/{id}` | 事项详情 |
| 事项 | POST | `/pm/work-items/delete` | 删除事项 |
| 事项 | POST | `/pm/work-items/{id}/transition` | 更新状态 |
| 事项 | POST | `/pm/work-items/links/save` | 添加关联 |
| 事项 | GET | `/pm/work-items/{id}/links` | 查询关联 |
| 评论 | GET | `/pm/work-items/{id}/comments` | 评论列表 |
| 评论 | GET | `/pm/work-items/{id}/comments/count` | 评论数 |
| 评论 | POST | `/pm/work-items/comments/counts` | 批量评论数 |
| 评论 | POST | `/pm/work-items/comments/save` | 发表评论 |
| 评论 | POST | `/pm/work-items/comments/delete` | 删除评论 |
| 字段 | POST | `/pm/fields/definitions/list` | 字段 Schema |
| 字段 | POST | `/pm/fields/definitions/catalog` | 字段目录 |
| 字段 | GET | `/pm/fields/definitions/{id}` | 字段详情 |
| 字段 | POST | `/pm/fields/definitions/save` | 保存字段 |
| 字段 | POST | `/pm/fields/definitions/delete` | 删除字段 |
| 字段 | GET | `/pm/fields/definitions/options` | 字段选项 |
| 布局 | POST | `/pm/fields/layout/get` | 获取布局 |
| 布局 | POST | `/pm/fields/layout/save` | 保存布局 |
| 视图 | POST | `/pm/views/save` | 保存视图 |
| 视图 | POST | `/pm/views/list` | 视图列表 |
| 视图 | POST | `/pm/views/delete` | 删除视图 |
| 元数据 | POST | `/pm/meta/types` | 事项类型 |
| 看板 | POST | `/pm/board` | 看板数据 |

---

## 11. 错误示例

业务异常时 `code != 0`：

```json
{
  "code": 1,
  "msg": "事项不存在",
  "data": null
}
```

前端 axios 拦截器会在 `code !== 0` 时 reject，并抛出 `Error(msg)`。
