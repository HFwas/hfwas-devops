# 接口测试平台 — API 接口规范

> 基础路径：`/api/apitest`
> 响应格式：`BaseResult<T>` 统一包装
> 日期：2026-08-28

---

## 一、通用响应格式

```json
{
  "code": 0,
  "msg": "success",
  "data": {},
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 0-成功，非0-失败 |
| `msg` | String | 提示信息 |
| `data` | T | 业务数据 |
| `requestId` | String | 请求追踪 ID，由 `RequestIdResponseAdvice` 自动注入 |

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

## 四、接口调试 API（Phase2 新增）

### 4.1 执行接口调试

```
POST /api/apitest/debug/execute
```

**Request Body：**
```json
{
  "projectId": 1001,
  "definitionId": 1234567890,
  "environmentId": 3001,
  "url": "{{base_url}}/api/users?page={{page}}",
  "method": "GET",
  "headers": {
    "Authorization": "Bearer {{token}}",
    "Content-Type": "application/json"
  },
  "queryParams": {
    "page": "1",
    "size": "20"
  },
  "body": null,
  "contentType": "application/json",
  "timeoutMs": 30000,
  "followRedirects": true,
  "preRequestScript": "console.log('前置脚本执行'); pm.request.headers['X-Custom'] = 'value';",
  "postResponseScript": "console.log('后置脚本执行'); pm.environment.set('userId', pm.response.body.data.id);",
  "assertions": [
    {
      "name": "状态码为200",
      "source": "RESPONSE_STATUS",
      "compareType": "EQUALS",
      "expression": null,
      "expectedValue": "200"
    },
    {
      "name": "响应包含data字段",
      "source": "RESPONSE_BODY",
      "compareType": "CONTAINS",
      "expression": null,
      "expectedValue": "data"
    }
  ],
  "extracts": [
    {
      "variableName": "userId",
      "expression": "$.data.id",
      "source": "RESPONSE_BODY"
    }
  ]
}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "historyId": 4001,
    "requestUrl": "http://test-api.example.com/api/users?page=1&size=20",
    "requestMethod": "GET",
    "requestHeaders": {
      "Authorization": "Bearer eyJhbGciOiJIUzI1NiIs...",
      "Content-Type": "application/json",
      "X-Custom": "value"
    },
    "requestQuery": {"page": "1", "size": "20"},
    "requestBody": null,
    "requestContentType": "application/json",
    "responseStatusCode": 200,
    "responseHeaders": {"Content-Type": "application/json; charset=utf-8"},
    "responseBody": "{\"code\":0,\"data\":{\"id\":1,\"name\":\"张三\"}}",
    "responseContentType": "application/json; charset=utf-8",
    "responseSize": 48,
    "durationMs": 156,
    "status": "SUCCESS",
    "errorMessage": null,
    "preRequestLogs": ["[sandbox] 前置脚本执行完成"],
    "postResponseLogs": ["[sandbox] 后置脚本执行完成"],
    "assertionResults": [
      {"name": "状态码为200", "source": "RESPONSE_STATUS", "compareType": "EQUALS", "expression": null, "expected": "200", "actual": "200", "passed": true},
      {"name": "响应包含data字段", "source": "RESPONSE_BODY", "compareType": "CONTAINS", "expression": null, "expected": "data", "actual": "{\"code\":0,\"data\":{\"id\":1}}", "passed": true}
    ],
    "allAssertionsPassed": true,
    "extractedVariables": {"userId": "1"}
  }
}
```

---

## 五、环境变量管理 API（Phase2 新增）

### 5.1 分页查询环境列表

```
GET /api/apitest/environments/page
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectId` | Long | 是 | 项目ID |
| `keyword` | String | 否 | 关键词搜索 |
| `pageNo` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**Response：**
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 3001,
        "projectId": 1001,
        "name": "测试环境",
        "description": "内网测试环境",
        "variableCount": 3,
        "sortOrder": 0,
        "createTime": "2026-08-13T10:00:00",
        "updateTime": "2026-08-13T10:00:00"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 20
  }
}
```

### 5.2 查询所有环境列表（不分页）

```
GET /api/apitest/environments/list?projectId={projectId}
```

### 5.3 获取环境详情

```
GET /api/apitest/environments/{id}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "id": 3001,
    "projectId": 1001,
    "name": "测试环境",
    "description": "内网测试环境",
    "sortOrder": 0,
    "variables": [
      {
        "id": 1,
        "name": "base_url",
        "value": "http://test-api.example.com",
        "description": "基础URL",
        "isSecret": false,
        "sortOrder": 0
      },
      {
        "id": 2,
        "name": "token",
        "value": "******",
        "description": "认证Token",
        "isSecret": true,
        "sortOrder": 1
      }
    ],
    "createTime": "2026-08-13T10:00:00",
    "updateTime": "2026-08-13T10:00:00"
  }
}
```

### 5.4 创建环境

```
POST /api/apitest/environments?projectId={projectId}&userId={userId}
```

**Request Body：**
```json
{
  "name": "测试环境",
  "description": "内网测试环境",
  "sortOrder": 0,
  "variables": [
    {
      "name": "base_url",
      "value": "http://test-api.example.com",
      "description": "基础URL",
      "isSecret": false,
      "sortOrder": 0
    },
    {
      "name": "token",
      "value": "eyJhbGciOiJIUzI1NiIs...",
      "description": "认证Token",
      "isSecret": true,
      "sortOrder": 1
    }
  ]
}
```

### 5.5 更新环境

```
PUT /api/apitest/environments/{id}?userId={userId}
```

**Request Body：** 同创建，不含 `projectId`。

### 5.6 删除环境

```
DELETE /api/apitest/environments/{id}
```

---

## 六、调试历史 API（Phase2 新增）

### 6.1 分页查询调试历史

```
GET /api/apitest/debug-histories/page
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectId` | Long | 是 | 项目ID |
| `definitionId` | Long | 否 | 接口定义ID筛选 |
| `status` | String | 否 | 状态筛选 SUCCESS/FAILURE/ERROR |
| `keyword` | String | 否 | 关键词搜索 |
| `startTime` | String | 否 | 开始时间 |
| `endTime` | String | 否 | 结束时间 |
| `pageNo` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**Response：**
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 4001,
        "definitionId": 1234567890,
        "environmentId": 3001,
        "name": "GET /api/users",
        "requestUrl": "http://test-api.example.com/api/users?page=1",
        "requestMethod": "GET",
        "responseStatusCode": 200,
        "responseSize": 2048,
        "durationMs": 156,
        "status": "SUCCESS",
        "allAssertionsPassed": true,
        "createTime": "2026-08-13T10:00:00"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 20
  }
}
```

### 6.2 获取调试历史详情

```
GET /api/apitest/debug-histories/{id}
```

**Response：** 返回完整的请求/响应报文、断言结果、提取变量。

### 6.3 查询某接口的调试历史

```
GET /api/apitest/debug-histories/by-definition?definitionId={definitionId}&limit=20
```

### 6.4 删除调试历史

```
DELETE /api/apitest/debug-histories/{id}
```

### 6.5 批量删除调试历史

```
DELETE /api/apitest/debug-histories/batch?ids=1,2,3
```

---

## 七、集合管理 API（Phase3 新增）

### 7.1 创建集合

```
POST /api/apitest/collections?projectId={projectId}&userId={userId}
```

**Request Body：**
```json
{
  "name": "用户管理接口集合",
  "description": "用户模块所有接口",
  "sortOrder": 0
}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "id": 5001,
    "projectId": 1001,
    "name": "用户管理接口集合",
    "description": "用户模块所有接口",
    "sortOrder": 0,
    "folderCount": 0,
    "itemCount": 0,
    "createTime": "2026-08-13T10:00:00",
    "updateTime": "2026-08-13T10:00:00"
  }
}
```

### 7.2 更新集合

```
PUT /api/apitest/collections/{id}?userId={userId}
```

**Request Body：**
```json
{
  "name": "用户管理接口集合",
  "description": "用户模块所有接口（含认证）",
  "sortOrder": 1
}
```

### 7.3 删除集合

```
DELETE /api/apitest/collections/{id}
```

**约束：** 逻辑删除，同时删除关联的文件夹、集合项和执行记录。

### 7.4 分页查询集合列表

```
GET /api/apitest/collections/page
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `projectId` | Long | 是 | 项目ID |
| `keyword` | String | 否 | 名称搜索 |
| `pageNo` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

**Response：**
```json
{
  "code": 0,
  "data": {
    "records": [
      {
        "id": 5001,
        "projectId": 1001,
        "name": "用户管理接口集合",
        "description": "用户模块所有接口",
        "sortOrder": 0,
        "folderCount": 2,
        "itemCount": 10,
        "createTime": "2026-08-13T10:00:00",
        "updateTime": "2026-08-13T10:00:00"
      }
    ],
    "total": 1,
    "current": 1,
    "size": 20
  }
}
```

### 7.5 获取集合详情（含树形结构）

```
GET /api/apitest/collections/{id}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "id": 5001,
    "projectId": 1001,
    "name": "用户管理接口集合",
    "description": "用户模块所有接口",
    "sortOrder": 0,
    "folders": [
      {
        "id": 6001,
        "collectionId": 5001,
        "parentId": null,
        "name": "用户查询",
        "description": "用户查询相关接口",
        "sortOrder": 0,
        "children": [
          {
            "id": 6002,
            "parentId": 6001,
            "name": "分页查询",
            "children": [],
            "items": [
              {
                "id": 7001,
                "definitionId": 1234567890,
                "name": "获取用户列表",
                "method": "GET",
                "path": "/api/users",
                "enabled": true,
                "sortOrder": 0
              }
            ]
          }
        ],
        "items": []
      }
    ],
    "items": [
      {
        "id": 7002,
        "definitionId": 1234567891,
        "name": "创建用户",
        "method": "POST",
        "path": "/api/users",
        "enabled": true,
        "sortOrder": 1
      }
    ],
    "createTime": "2026-08-13T10:00:00",
    "updateTime": "2026-08-13T10:00:00"
  }
}
```

### 7.6 创建文件夹

```
POST /api/apitest/collections/{collectionId}/folders?userId={userId}
```

**Request Body：**
```json
{
  "parentId": null,
  "name": "用户查询",
  "description": "用户查询相关接口",
  "sortOrder": 0
}
```

### 7.7 更新文件夹

```
PUT /api/apitest/collections/{collectionId}/folders/{folderId}?userId={userId}
```

### 7.8 删除文件夹

```
DELETE /api/apitest/collections/{collectionId}/folders/{folderId}
```

### 7.9 获取文件夹树

```
GET /api/apitest/collections/{collectionId}/folders/tree
```

### 7.10 添加集合项

```
POST /api/apitest/collections/{collectionId}/items?userId={userId}
```

**Request Body：**
```json
{
  "folderId": null,
  "definitionId": 1234567890,
  "name": "获取用户列表（覆盖名）",
  "description": null,
  "enabled": true
}
```

### 7.11 更新集合项

```
PUT /api/apitest/collections/{collectionId}/items/{itemId}
```

### 7.12 删除集合项

```
DELETE /api/apitest/collections/{collectionId}/items/{itemId}
```

### 7.13 重排序

```
PUT /api/apitest/collections/{collectionId}/items/reorder
```

**Request Body：**
```json
{
  "itemIds": [7001, 7002, 7003]
}
```

### 7.14 批量添加

```
POST /api/apitest/collections/{collectionId}/items/batch?userId={userId}
```

**Request Body：**
```json
{
  "folderId": 6001,
  "definitionIds": [1234567890, 1234567891, 1234567892]
}
```

### 7.15 执行集合

```
POST /api/apitest/collections/{collectionId}/run?environmentId={environmentId}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "runId": 8001,
    "collectionId": 5001,
    "name": "Run #1 - 用户管理接口集合",
    "status": "RUNNING",
    "totalCount": 10,
    "passedCount": 0,
    "failedCount": 0,
    "errorCount": 0,
    "durationMs": 0,
    "createTime": "2026-08-13T10:00:00"
  }
}
```

### 7.16 运行历史列表

```
GET /api/apitest/collections/{collectionId}/runs
```

**Query Parameters：**

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `pageNo` | Integer | 否 | 页码，默认1 |
| `pageSize` | Integer | 否 | 每页条数，默认20 |

### 7.17 运行详情

```
GET /api/apitest/collections/runs/{runId}
```

**Response：**
```json
{
  "code": 0,
  "data": {
    "id": 8001,
    "collectionId": 5001,
    "name": "Run #1 - 用户管理接口集合",
    "status": "COMPLETED",
    "totalCount": 10,
    "passedCount": 8,
    "failedCount": 1,
    "errorCount": 1,
    "durationMs": 2560,
    "items": [
      {
        "id": 9001,
        "name": "获取用户列表",
        "requestUrl": "http://test-api.example.com/api/users?page=1",
        "requestMethod": "GET",
        "responseStatusCode": 200,
        "durationMs": 156,
        "status": "SUCCESS",
        "allAssertionsPassed": true,
        "errorMessage": null
      },
      {
        "id": 9002,
        "name": "创建用户",
        "requestUrl": "http://test-api.example.com/api/users",
        "requestMethod": "POST",
        "responseStatusCode": 500,
        "durationMs": 203,
        "status": "FAILURE",
        "allAssertionsPassed": false,
        "errorMessage": null
      }
    ],
    "createTime": "2026-08-13T10:00:00"
  }
}
```

### 7.18 删除运行记录

```
DELETE /api/apitest/collections/runs/{runId}
```

---

## 八、错误码

| Code | 说明 |
|------|------|
| 0 | 成功 |
| 400 | 参数校验失败 |
| 400 | 业务异常（如分组名重复、接口已存在、环境名重复） |
| 401 | 未认证 |
| 403 | 无权限 |
| 404 | 资源不存在 |
| 500 | 服务器内部错误 |

---

## 八、API 路径汇总

### Phase1 · 接口管理

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

### Phase2 · 接口调试

```
调试执行
  POST   /api/apitest/debug/execute         执行接口调试

环境变量管理
  GET    /api/apitest/environments/page      分页查询环境列表
  GET    /api/apitest/environments/list      查询所有环境列表
  GET    /api/apitest/environments/{id}      获取环境详情
  POST   /api/apitest/environments           创建环境
  PUT    /api/apitest/environments/{id}      更新环境
  DELETE /api/apitest/environments/{id}      删除环境

调试历史
  GET    /api/apitest/debug-histories/page        分页查询调试历史
  GET    /api/apitest/debug-histories/{id}        获取调试历史详情
  GET    /api/apitest/debug-histories/by-definition 查询某接口的调试历史
  DELETE /api/apitest/debug-histories/{id}        删除调试历史
  DELETE /api/apitest/debug-histories/batch       批量删除调试历史
```

### Phase3 · 集合管理

```
集合CRUD
  POST   /api/apitest/collections?projectId={projectId}&userId={userId}    创建集合
  PUT    /api/apitest/collections/{id}?userId={userId}                     更新集合
  DELETE /api/apitest/collections/{id}                                     删除集合
  GET    /api/apitest/collections/page                                     分页查询集合列表
  GET    /api/apitest/collections/{id}                                     获取集合详情（含树形结构）

文件夹CRUD
  POST   /api/apitest/collections/{collectionId}/folders?userId={userId}   创建文件夹
  PUT    /api/apitest/collections/{collectionId}/folders/{folderId}?userId={userId} 更新文件夹
  DELETE /api/apitest/collections/{collectionId}/folders/{folderId}        删除文件夹
  GET    /api/apitest/collections/{collectionId}/folders/tree              获取文件夹树

集合项管理
  POST   /api/apitest/collections/{collectionId}/items?userId={userId}     添加集合项
  PUT    /api/apitest/collections/{collectionId}/items/{itemId}            更新集合项
  DELETE /api/apitest/collections/{collectionId}/items/{itemId}            删除集合项
  PUT    /api/apitest/collections/{collectionId}/items/reorder             重排序
  POST   /api/apitest/collections/{collectionId}/items/batch               批量添加

集合执行
  POST   /api/apitest/collections/{collectionId}/run?environmentId={environmentId} 执行集合
  GET    /api/apitest/collections/{collectionId}/runs                      运行历史列表
  GET    /api/apitest/collections/runs/{runId}                             运行详情
  DELETE /api/apitest/collections/runs/{runId}                             删除运行记录
```