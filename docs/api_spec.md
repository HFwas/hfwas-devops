# 接口管理模块 — API 接口规范

> 基础路径：`/api/apitest`
> 响应格式：`BaseResult<T>` 统一包装
> 日期：2026-08-12

---

## 一、通用响应格式

```json
{
  "code": 0,
  "msg": "success",
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 0-成功，非0-失败 |
| `msg` | String | 提示信息 |
| `data` | T | 业务数据 |

---

## 二、分组管理 API

### 2.1 创建分组

```
POST /api/apitest/groups?userId={userId}
```

**Request Body：**
```json
{
  "projectId": 1001,
  "parentId": null,
  "name": "用户管理",
  "sortOrder": 1,
  "description": "用户相关接口"
}
```

**Response：**
```json
{
  "code": 0,
  "msg": null,
  "data": {
    "id": 1234567890,
    "projectId": 1001,
    "parentId": null,
    "name": "用户管理",
    "sortOrder": 1,
    "description": "用户相关接口",
    "children": null,
    "apiCount": null,
    "createdBy": 1,
    "createTime": "2026-08-12T10:00:00",
    "updateTime": "2026-08-12T10:00:00"
  }
}
```

### 2.2 更新分组

```
PUT /api/apitest/groups/{id}?userId={userId}
```

**Request Body：**
```json
{
  "name": "用户管理",
  "sortOrder": 1,
  "description": "用户相关接口"
}
```

### 2.3 删除分组

```
DELETE /api/apitest/groups/{id}
```

**约束：** 分组下无子分组、无接口时方可删除。

### 2.4 获取分组树

```
GET /api/apitest/groups/tree?projectId={projectId}
```

**Response：**
```json
{
  "code": 0,
  "data": [
    {
      "id": 1,
      "name": "用户管理",
      "children": [
        {
          "id": 2,
          "name": "登录注册",
          "children": [],
          "apiCount": 3
        }
      ],
      "apiCount": 5
    }
  ]
}
```

### 2.5 获取分组详情

```
GET /api/apitest/groups/{id}
```

---

## 三、接口定义 API

### 3.1 分页查询接口列表

```
GET /api/apitest/definitions/page
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectId` | Long | 是 | 项目ID |
| `groupId` | Long | 否 | 分组筛选 |
| `keyword` | String | 否 | 名称/路径模糊搜索 |
| `method` | String | 否 | 请求方式筛选 |
| `status` | String | 否 | 状态筛选 |
| `tags` | String[] | 否 | 标签交集筛选 |
| `pageNo` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**Response：**
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 1234567890,
        "projectId": 1001,
        "groupId": 1,
        "groupName": "用户管理",
        "name": "获取用户列表",
        "path": "/api/users",
        "method": "GET",
        "status": "PUBLISHED",
        "version": "1.0.0",
        "tags": ["user", "list"],
        "description": "分页获取用户列表",
        "protocol": "HTTP",
        "createdBy": 1,
        "createTime": "2026-08-12T10:00:00",
        "updateTime": "2026-08-12T10:00:00"
      }
    ],
    "total": 100,
    "current": 1,
    "size": 20
  }
}
```

### 3.2 获取接口详情

```
GET /api/apitest/definitions/{id}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "id": 1234567890,
    "projectId": 1001,
    "groupId": 1,
    "groupName": "用户管理",
    "name": "获取用户列表",
    "path": "/api/users",
    "method": "GET",
    "status": "PUBLISHED",
    "version": "1.0.0",
    "tags": ["user"],
    "description": "分页获取用户列表",
    "protocol": "HTTP",
    "host": "http://localhost:8080",
    "contentType": null,
    "params": [
      {
        "id": 1,
        "definitionId": 1234567890,
        "paramType": "query",
        "name": "page",
        "dataType": "integer",
        "required": true,
        "defaultValue": "1",
        "description": "页码",
        "sortOrder": 0,
        "example": "1"
      },
      {
        "id": 2,
        "paramType": "query",
        "name": "size",
        "dataType": "integer",
        "required": true,
        "defaultValue": "10",
        "description": "每页条数",
        "sortOrder": 1,
        "example": "10"
      },
      {
        "id": 3,
        "paramType": "header",
        "name": "Authorization",
        "dataType": "string",
        "required": true,
        "description": "Bearer Token",
        "sortOrder": 0,
        "example": "Bearer eyJhbGci..."
      }
    ],
    "responses": [
      {
        "id": 1,
        "definitionId": 1234567890,
        "statusCode": 200,
        "contentType": "application/json",
        "description": "成功返回",
        "bodySchema": null,
        "bodyExample": {
          "code": 0,
          "data": {
            "total": 100,
            "list": [{"id": 1, "name": "张三"}]
          }
        }
      }
    ],
    "createdBy": 1,
    "createTime": "2026-08-12T10:00:00",
    "updateTime": "2026-08-12T10:00:00"
  }
}
```

### 3.3 创建接口定义

```
POST /api/apitest/definitions?userId={userId}
```

**Request Body：**
```json
{
  "projectId": 1001,
  "groupId": 1,
  "name": "获取用户列表",
  "path": "/api/users",
  "method": "GET",
  "tags": ["user", "list"],
  "description": "分页获取用户列表",
  "protocol": "HTTP",
  "host": "http://localhost:8080",
  "contentType": null,
  "params": [
    {
      "paramType": "query",
      "name": "page",
      "dataType": "integer",
      "required": true,
      "defaultValue": "1",
      "description": "页码",
      "sortOrder": 0
    },
    {
      "paramType": "header",
      "name": "Authorization",
      "dataType": "string",
      "required": true,
      "description": "Bearer Token",
      "sortOrder": 0
    }
  ],
  "responses": [
    {
      "statusCode": 200,
      "contentType": "application/json",
      "description": "成功返回",
      "bodyExample": {"code": 0, "data": {}}
    }
  ]
}
```

### 3.4 更新接口定义

```
PUT /api/apitest/definitions/{id}?userId={userId}
```

**Request Body：** 同创建接口，但不含 `projectId`。

### 3.5 删除接口定义

```
DELETE /api/apitest/definitions/{id}
```

**约束：** 逻辑删除，同时删除关联的参数和响应定义。

### 3.6 状态流转

| 操作 | API | 说明 |
|------|-----|------|
| 发布 | `POST /api/apitest/definitions/{id}/publish?userId={userId}` | DRAFT → PUBLISHED |
| 废弃 | `POST /api/apitest/definitions/{id}/deprecate?userId={userId}` | PUBLISHED → DEPRECATED |
| 恢复草稿 | `POST /api/apitest/definitions/{id}/revert-draft?userId={userId}` | PUBLISHED/DEPRECATED → DRAFT |

---

## 四、错误码

| Code | 说明 |
|------|------|
| 0 | 成功 |
| 400 | 参数校验失败 |
| 400 | 业务异常（如分组名重复、接口已存在） |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 五、API 路径汇总

```
分组管理
  POST   /api/apitest/groups                 创建分组
  PUT    /api/apitest/groups/{id}            更新分组
  DELETE /api/apitest/groups/{id}            删除分组
  GET    /api/apitest/groups/tree            获取分组树
  GET    /api/apitest/groups/{id}            获取分组详情

接口定义
  GET    /api/apitest/definitions/page       分页查询接口列表
  GET    /api/apitest/definitions/{id}       获取接口详情
  POST   /api/apitest/definitions            创建接口定义
  PUT    /api/apitest/definitions/{id}       更新接口定义
  DELETE /api/apitest/definitions/{id}       删除接口定义
  POST   /api/apitest/definitions/{id}/publish       发布
  POST   /api/apitest/definitions/{id}/deprecate     废弃
  POST   /api/apitest/definitions/{id}/revert-draft  恢复草稿
```