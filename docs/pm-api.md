# PM 模块接口文档

> 版本：1.2  
> 更新日期：2026-07-05  
> 适用范围：hfwas-devops 项目管理（PM）REST API

---

## 1. 通用说明

### 1.1 服务地址

| 环境 | 后端直连 | 前端开发代理 |
|------|----------|--------------|
| 本地 | `http://localhost:8089` | `http://localhost:5173/api` → 转发至 `8089` |

前端请求统一加前缀 `/api`，Vite 代理会去掉该前缀后转发到后端。

### 1.2 认证与租户

除登录、健康检查等白名单接口外，所有 PM 接口需要登录。

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 是 | `Bearer {JWT}` |
| `X-Tenant-Id` | 推荐 | 当前操作租户 ID（雪花 ID 字符串）。前端切换租户后持久化并随请求携带；后端优先于 JWT 中的租户解析上下文 |

未登录返回 HTTP `401`，body 示例：

```json
{ "code": 11001, "msg": "未登录或登录已过期", "data": null }
```

无权访问返回 HTTP `403`，body 示例：

```json
{ "code": 11002, "msg": "无权访问", "data": null }
```

### 1.3 统一响应格式

业务接口返回 `BaseResult<T>`：

```json
{
  "code": 0,
  "msg": null,
  "data": {}
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| code | integer | `0` 成功；非 `0` 为业务错误码（见 [error-code-design.md](./error-code-design.md)） |
| msg | string | 错误或提示信息，成功时通常为 `null` |
| data | T | 业务数据 |

### 1.4 分页结构

分页接口的 `data` 为 MyBatis-Plus `IPage` 结构（前端映射为 `PageResult`）：

```json
{
  "records": [],
  "total": "100",
  "size": "20",
  "current": "1",
  "pages": "5"
}
```

通用分页请求字段（继承 `PmPageRequest` / `PageRequest`）：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| pageNo | integer | 1 | 页码 |
| pageSize | integer | 20 | 每页条数 |

### 1.5 ID 序列化说明

Long 类型 ID 在 JSON 中序列化为 **字符串**（避免 JavaScript 精度丢失），例如 `"id": "2073615378310627330"`。

事项另有 Jira 式展示字段 `itemKey`（如 `DEMO-1`），由 `项目 code + item_no` 组成，响应时组装，非库字段。

### 1.6 接口风格约定

| 约定 | 说明 |
|------|------|
| 前缀 | 业务接口均以 `/pm/` 开头 |
| 创建/更新 | 多数资源使用 `POST .../save`（按 body 中是否有 id 区分） |
| 删除 | `POST .../delete?id={id}`（query 参数） |
| 列表/查询 | 多数使用 `POST` + JSON body |
| 单条查询 | 使用 `GET /{id}` 或 `GET` + query |
| Excel 下载 | 返回 `ResponseEntity<byte[]>`，非 `BaseResult` 包装 |

---

## 2. 健康检查

### GET `/health/check`

检查服务是否存活（无需登录）。

**响应：** 纯文本

```
UP
```

> 该接口不包装 `BaseResult`，直接返回字符串。

---

## 3. 项目管理

Base path: `/pm/projects`

数据按 **当前租户** 隔离（`tenantId` 由登录上下文 / `X-Tenant-Id` 决定）。

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
| id | string | 项目 ID（雪花） |
| tenantId | string | 所属租户 ID |
| code | string | 项目编码（用于 itemKey，如 `demo` → `DEMO-1`） |
| name | string | 项目名称 |
| description | string | 描述 |
| settings | object | 扩展配置 JSON |
| createTime | string | 创建时间 |
| updateTime | string | 更新时间 |

---

### 3.2 创建/更新项目

**POST** `/pm/projects/save`

**请求体：** `PmProject`（`id` 为空则创建并绑定当前租户，否则更新）

**响应 data：** `string` — 项目 ID

**常见错误码：** `20003` 编码/名称为空；`20004` 编码重复

---

### 3.3 项目详情

**GET** `/pm/projects/{id}`

**路径参数：** `id` — 项目 ID

**响应 data：** `PmProject`

**常见错误码：** `20002` 项目不存在或无权访问（租户不匹配）

---

### 3.4 项目访问上下文（深链 / 租户对齐）

**GET** `/pm/projects/{id}/access-context`

解析项目所属租户，**不要求**当前租户上下文与项目租户一致；校验当前用户对该租户的成员资格（或平台管理员）。

**响应 data：** `ProjectAccessContextVO`

| 字段 | 类型 | 说明 |
|------|------|------|
| projectId | string | 项目 ID |
| projectName | string | 项目名称 |
| tenantId | string | 项目所属租户 ID |

**用途：** 登录后通过项目 URL 深链进入时，前端据此自动切换租户。

---

### 3.5 删除项目

**POST** `/pm/projects/delete?id={id}`

**响应 data：** `null`

---

### 3.6 功能模块

Base path: `/pm/project-modules`

项目级功能模块（类似 Jira Component），支持树形层级。事项通过 `moduleId` 归属单一模块。

#### 3.6.1 模块树

**GET** `/pm/project-modules/tree?projectId={projectId}`

**响应 data：** `PmProjectModule[]`（根节点列表，含 `children`）

#### 3.6.2 模块扁平列表（下拉选项）

**GET** `/pm/project-modules/flat?projectId={projectId}`

**响应 data：** `PmProjectModule[]`，每项含 `pathLabel`（如 `订单 / 支付`）

#### 3.6.3 创建/更新模块

**POST** `/pm/project-modules/save`

**请求体：**

```json
{
  "id": null,
  "projectId": "2073615378310627330",
  "parentId": null,
  "name": "用户中心",
  "description": "账号、权限相关"
}
```

**响应 data：** `string` — 模块 ID

#### 3.6.4 删除模块

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
  "projectId": "2073615378310627330",
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
| projectId | string/long | 项目 ID |
| typeCode | string | 事项类型：`requirement` / `task` / `bug` / `test_case` |
| logic | string | 顶层条件逻辑：`AND` / `OR` |
| conditions | array | 查询条件列表 |
| groups | array | 嵌套条件组（可选） |
| sort | array | 排序，`order` 为 `ASC` / `DESC` |

**QueryCondition：**

| 字段 | 类型 | 说明 |
|------|------|------|
| field | string | 系统字段如 `title`、`status`；自定义字段用 `custom.{fieldKey}` |
| operator | string | 见下方运算符 |
| value | any | 条件值 |

**支持运算符：** `EQ` `NE` `GT` `GTE` `LT` `LTE` `LIKE` `IN` `NOT_IN` `BETWEEN` `IS_NULL` `IS_NOT_NULL`

**响应 data：** `IPage<PmWorkItem>`

**PmWorkItem 主要字段：**

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 内部主键 |
| projectId | string | 项目 ID |
| itemNo | integer | 项目内序号 |
| itemKey | string | 展示键，如 `DEMO-1` |
| typeCode | string | 事项类型 |
| title | string | 标题 |
| description | string | 描述（Markdown） |
| status | string | 状态编码 |
| priority | string | 优先级 |
| assigneeId | string | 负责人 ID |
| reporterId | string | 报告人 ID |
| parentId | string | 父事项 ID |
| moduleId | string | 功能模块 ID（可选） |
| customFields | object | 自定义字段 JSON |
| createTime | string | 创建时间 |
| updateTime | string | 更新时间 |

---

### 4.2 创建/更新事项

**POST** `/pm/work-items/save`

**请求体：** `PmWorkItem`（`id` 为空则创建并自动分配 `itemNo`）

**响应 data：** `string` — 事项 ID

---

### 4.3 事项详情

**GET** `/pm/work-items/{id}`

**响应 data：** `PmWorkItem`（含 `itemKey`）

---

### 4.4 删除事项

**POST** `/pm/work-items/delete?id={id}`

**响应 data：** `null`

---

### 4.5 状态流转

**POST** `/pm/work-items/{id}/transition`

**路径参数：** `id` — 事项 ID

**请求体：**

```json
{
  "transitionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fields": {
    "priority": "high"
  }
}
```

按项目 + 类型的工作流配置，以 `transitionId` 定位流转边；若配置了 `REQUIRED_FIELDS` 校验器，则先校验（可附带 `fields` 一并写入）再更新状态、执行后置函数并记录活动日志。

**响应 data：** `null`

**常见错误码：** `21012` 流转不允许；`21013` 状态不存在；业务异常文案如「流转前必须填写「优先级」」

---

### 4.6 添加事项关联

**POST** `/pm/work-items/links/save`

**请求体：**

```json
{
  "sourceId": "1",
  "targetId": "2",
  "linkType": "relates_to"
}
```

| linkType | 说明 |
|----------|------|
| relates_to | 关联 |
| blocks | 阻塞 |
| duplicates | 重复 |

**响应 data：** `string` — 关联记录 ID

---

### 4.7 查询事项关联

**GET** `/pm/work-items/{id}/links`

**响应 data：** `PmWorkItemLink[]`

---

### 4.8 事项活动日志

**GET** `/pm/work-items/{id}/activities`

**响应 data：** `WorkItemActivityVo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 活动 ID |
| workItemId | string | 事项 ID |
| batchId | string | 批次 ID（同一次保存的多字段变更） |
| eventType | string | `CREATE` / `FIELD_CHANGE` / `LINK_ADD` |
| actorId | string | 操作人 ID |
| actorName | string | 操作人显示名 |
| fieldKey | string | 变更字段（FIELD_CHANGE） |
| fieldName | string | 字段名称 |
| oldValue / newValue | string | 原始值 / 新值 |
| oldLabel / newLabel | string | 展示用标签（选项、用户等） |
| createTime | string | 时间 |

---

## 5. 事项评论

Base path: `/pm/work-items`（与事项共用前缀）

### 5.1 评论列表

**GET** `/pm/work-items/{id}/comments`

**响应 data：** `WorkItemCommentVo[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | string | 评论 ID |
| workItemId | string | 事项 ID |
| parentId | string | 回复目标评论 ID |
| content | string | 内容 |
| authorName | string | 作者显示名（当前登录用户） |
| authorId | string | 作者 ID |
| createTime | string | 创建时间 |
| deletable | boolean | 当前用户是否可删除 |

---

### 5.2 单条事项评论数

**GET** `/pm/work-items/{id}/comments/count`

**响应 data：** `number`

---

### 5.3 批量评论数

**POST** `/pm/work-items/comments/counts`

**请求体：** 事项 ID 数组

```json
["1", "2", "3"]
```

**响应 data：**

```json
{
  "1": 3,
  "2": 0,
  "3": 1
}
```

---

### 5.4 发表评论

**POST** `/pm/work-items/comments/save`

**请求体：**

```json
{
  "workItemId": "1",
  "content": "这是一条评论",
  "parentId": null
}
```

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| workItemId | string/long | 是 | 事项 ID |
| content | string | 是 | 评论内容 |
| parentId | string/long | 否 | 回复的评论 ID |

> 作者信息由后端从当前登录用户填充，无需传 `authorName`。

**响应 data：** `string` — 评论 ID

---

### 5.5 删除评论

**POST** `/pm/work-items/comments/delete?id={id}`

仅作者本人可删除。

---

## 6. 事项导入导出

Base path: `/pm/work-items/io`

支持需求 / 任务 / 缺陷 / 测试用例四类事项的 Excel 导入导出。

### 6.1 可导入导出列

**GET** `/pm/work-items/io/columns?projectId={projectId}&typeCode={typeCode}`

**响应 data：** `WorkItemIoColumn[]`

| 字段 | 类型 | 说明 |
|------|------|------|
| fieldKey | string | 字段键 |
| fieldName | string | 列标题 |
| fieldType | string | 字段类型 |
| systemField | boolean | 是否系统字段 |
| exportable | boolean | 可否导出 |
| importable | boolean | 可否导入 |
| defaultSelected | boolean | 默认是否选中 |

---

### 6.2 下载导入模板

**POST** `/pm/work-items/io/import/template`

**请求体：** `WorkItemExportRequest`（仅需 `projectId`、`typeCode`、`fieldKeys`）

**响应：** Excel 文件（`application/vnd.openxmlformats-officedocument.spreadsheetml.sheet`）

---

### 6.3 导出 Excel

**POST** `/pm/work-items/io/export`

**请求体：** `WorkItemExportRequest`

```json
{
  "projectId": "2073615378310627330",
  "typeCode": "bug",
  "ids": ["1", "2"],
  "querySpec": null,
  "fieldKeys": ["itemKey", "title", "status", "priority"]
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| projectId | string/long | 项目 ID |
| typeCode | string | 事项类型 |
| ids | string[] | 选中导出的 ID；**为空则按 querySpec 全量导出** |
| querySpec | object | 与列表查询相同的 `QuerySpec` |
| fieldKeys | string[] | 导出列 |

**响应：** Excel 文件

---

### 6.4 导入预览

**POST** `/pm/work-items/io/import/preview`

**Content-Type：** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| projectId | long | 是 | 项目 ID |
| typeCode | string | 是 | 事项类型 |
| file | file | 是 | Excel 文件 |

**响应 data：** `WorkItemImportPreview`

| 字段 | 说明 |
|------|------|
| totalRows | 总行数 |
| validRows | 有效行数 |
| detectedHeaders | 检测到的表头 |
| sampleRows | 样例行 |
| warnings | 警告信息 |

---

### 6.5 执行导入

**POST** `/pm/work-items/io/import`

**Content-Type：** `multipart/form-data`

| 参数 | 类型 | 必填 | 说明 |
|------|------|------|------|
| projectId | long | 是 | 项目 ID |
| typeCode | string | 是 | 事项类型 |
| mode | string | 否 | `CREATE`（默认）或 `UPSERT`（按 itemKey 匹配更新） |
| fieldKeys | string | 否 | JSON 数组字符串，如 `["title","status"]` |
| file | file | 是 | Excel 文件 |

**响应 data：** `WorkItemImportResult`

| 字段 | 说明 |
|------|------|
| created | 新建数 |
| updated | 更新数 |
| skipped | 跳过数 |
| failed | 失败数 |
| errors | 错误明细 |
| warnings | 警告 |

---

## 7. 字段定义

Base path: `/pm/fields/definitions`

### 7.1 按项目+类型获取字段 Schema

**POST** `/pm/fields/definitions/list`

**请求体：**

```json
{
  "projectId": "2073615378310627330",
  "typeCode": "bug"
}
```

**响应 data：** `FieldDefinition[]`（含布局标记 `showInList` / `searchable` / `showInCreate` / `listOrder`）

---

### 7.2 字段目录（项目下全部字段）

**POST** `/pm/fields/definitions/catalog`

**请求体：** `{ "projectId": "..." }`

**响应 data：** `FieldDefinition[]`

---

### 7.3 可添加到类型的字段

**POST** `/pm/fields/definitions/available`

**请求体：** `{ "projectId": "...", "typeCode": "bug" }`

**响应 data：** 尚未绑定到该类型的项目字段列表

---

### 7.4 字段详情

**GET** `/pm/fields/definitions/{id}`

---

### 7.5 保存字段定义

**POST** `/pm/fields/definitions/save`

**请求体：**

```json
{
  "definition": {
    "projectId": "2073615378310627330",
    "fieldKey": "severity",
    "fieldName": "严重程度",
    "fieldType": "SELECT",
    "applicableTypes": ["bug"]
  },
  "options": [
    { "optionKey": "critical", "optionLabel": "严重" }
  ]
}
```

**响应 data：** `string` — 字段定义 ID

---

### 7.6 删除字段

**POST** `/pm/fields/definitions/delete?id={id}`

系统字段不可删除。

---

### 7.7 绑定 / 解绑事项类型

**POST** `/pm/fields/definitions/add-to-type`

**POST** `/pm/fields/definitions/remove-from-type`

**请求体：**

```json
{
  "projectId": "2073615378310627330",
  "fieldId": "1",
  "typeCode": "bug"
}
```

---

### 7.8 字段静态选项

**GET** `/pm/fields/definitions/options?fieldId={fieldId}`

**响应 data：** `FieldOption[]`

---

### 7.9 解析字段选项（含远程选项）

**GET** `/pm/fields/definitions/options/resolve?fieldId={fieldId}`

**响应 data：** `ResolvedFieldOption[]`（远程 SELECT 会实时拉取）

---

### 7.10 预览远程选项请求

**POST** `/pm/fields/definitions/options/remote/preview`

**请求体：** `FieldRemoteOptionsConfig`

**响应 data：** `RemoteOptionFetchResult`

---

## 8. 字段布局

Base path: `/pm/fields/layout`

### 8.1 获取布局配置

**POST** `/pm/fields/layout/get`

**请求体：** `{ "projectId": "...", "typeCode": "bug" }`

**响应 data：** `TypeFieldLayoutConfig`

```json
{
  "listFields": ["title", "status", "priority", "assignee_id"],
  "searchFields": ["title", "status", "priority"],
  "createFields": ["title", "description"]
}
```

---

### 8.2 保存布局配置

**POST** `/pm/fields/layout/save`

**请求体：**

```json
{
  "projectId": "2073615378310627330",
  "typeCode": "bug",
  "layout": {
    "listFields": ["title", "status"],
    "searchFields": ["title"],
    "createFields": ["title"]
  }
}
```

---

## 9. 状态流转配置

Base path: `/pm/status/workflow`

### 9.1 获取工作流

**POST** `/pm/status/workflow/get`

**请求体：** `{ "projectId": "...", "typeCode": "task" }`

**响应 data：** `StatusWorkflowVO`

| 字段 | 说明 |
|------|------|
| projectId | 项目 ID |
| typeCode | 事项类型 |
| customized | 是否已自定义（非默认模板） |
| statuses | 状态列表 |

**StatusDefinitionVO：**

| 字段 | 说明 |
|------|------|
| statusCode | 状态编码 |
| statusName | 状态名称 |
| sortOrder | 排序 |
| isInitial | 是否初始状态（1/0） |
| isFinal | 是否终态 |
| transitions | `Transition[]`：`id`、`name`、`toStatus`、`validators[]`、`postFunctions[]` |

**Transition：**

| 字段 | 说明 |
|------|------|
| id | 流转 UUID |
| name | 显示名（如「开始处理」） |
| toStatus | 目标状态编码 |
| validators | 校验器列表 |
| postFunctions | 后置函数列表 |

**TransitionValidator：**

| 字段 | 说明 |
|------|------|
| type | 目前仅 `REQUIRED_FIELDS` |
| fieldKeys | 必填字段 key 列表 |

**TransitionPostFunction（后置函数）：**

| 字段 | 说明 |
|------|------|
| type | `SET_FIELD` / `NOTIFY_ASSIGNEE` / `NOTIFY_USER` / `WEBHOOK` |
| fieldKey / value | `SET_FIELD`：目标字段与值 |
| userId | `NOTIFY_USER`：接收人 |
| title / content | 通知标题与正文；支持 `{title}` `{itemKey}` `{fromStatus}` `{toStatus}` |

`WEBHOOK` 推送到租户已启用的钉钉/飞书渠道，不接受独立 URL。

事项 `transition` 或 `save` 导致 status 变更时，按源状态行与 `__any__` 行的规则顺序执行后置函数。

---

### 9.2 状态下拉选项

**POST** `/pm/status/workflow/options`

**请求体：** `{ "projectId": "...", "typeCode": "task" }`

**响应 data：** `StatusDefinitionVO[]`

---

### 9.3 允许的流转目标

**POST** `/pm/status/workflow/allowed`

**请求体：**

```json
{
  "projectId": "2073615378310627330",
  "typeCode": "task",
  "fromStatus": "open"
}
```

**响应 data：** `AllowedTransitionsVO`

| 字段 | 说明 |
|------|------|
| fromStatus | 当前状态 |
| transitions | `TransitionOption[]`：`id`、`name`、`toStatus`、`toStatusName` |

---

### 9.4 后置函数配置元数据

**POST** `/pm/status/workflow/post-function-meta`

**请求体：** `{ "projectId": "...", "typeCode": "task" }`

**响应 data：** `TransitionPostFunctionMetaVO`

| 字段 | 说明 |
|------|------|
| presets | 快捷预设（通知、群通知、优先级模板、字段模板） |
| fields | 可被 `SET_FIELD` 写入的字段元数据（含 options） |
| placeholders | 通知模板占位符列表 |

---

### 9.5 流转校验元数据

**POST** `/pm/status/workflow/transition-meta`

**请求体：**

```json
{
  "projectId": "...",
  "typeCode": "task",
  "transitionId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "fromStatus": "done"
}
```

**响应 data：** `TransitionMetaVO`

| 字段 | 说明 |
|------|------|
| transitionId | 流转 ID |
| name | 流转显示名 |
| fromStatus / toStatus | 源/目标状态 |
| validators | 合并后的校验器列表（含 `__any__`） |
| requiredFields | 需在流转弹窗中填写的字段元数据 |

---

### 9.6 保存工作流

**POST** `/pm/status/workflow/save`

**请求体：**

```json
{
  "projectId": "2073615378310627330",
  "typeCode": "task",
  "statuses": [ /* StatusDefinitionVO[]，含 transitions: Transition[] */ ]
}
```

保存时校验：状态编码唯一、恰有一个初始状态、流转目标存在且非自身；后置函数与校验器 type 合法；`REQUIRED_FIELDS` 需非空 `fieldKeys` 且不得包含 `status`。

---

### 9.7 重置为默认工作流

**POST** `/pm/status/workflow/reset`

**请求体：** `{ "projectId": "...", "typeCode": "task" }`

---

## 10. 事项类型方案（配置导入导出）

Base path: `/pm/issue-type-schemes`

导出/导入某一事项类型的完整配置（字段方案 + 状态工作流等），JSON 格式。

### 10.1 导出单类型方案

**POST** `/pm/issue-type-schemes/export`

**请求体：** `{ "projectId": "...", "typeCode": "bug" }`

**响应 data：** `IssueTypeSchemeExport`

---

### 10.2 导出项目全部类型方案

**POST** `/pm/issue-type-schemes/export-project`

**请求体：** `{ "projectId": "..." }`

**响应 data：** `ProjectIssueTypeSchemeExport`

---

### 10.3 导入预览

**POST** `/pm/issue-type-schemes/preview`

**请求体：** `IssueTypeSchemePreviewDto`（含 `projectId`、`typeCode`、`scheme`）

---

### 10.4 导入单类型方案

**POST** `/pm/issue-type-schemes/import`

**请求体：** `IssueTypeSchemeImportDto`（含 `mode`：`MERGE` / `REPLACE` 等）

---

### 10.5 导入项目全部类型方案

**POST** `/pm/issue-type-schemes/import-project`

**请求体：** `IssueTypeSchemeImportDto`（含 `projectScheme` 或兼容旧格式 `legacyProjectScheme`）

---

## 11. 保存视图

Base path: `/pm/views`

### 11.1 保存视图

**POST** `/pm/views/save`

**请求体：** `PmSavedView`（`id` 为空则创建）

| 字段 | 说明 |
|------|------|
| projectId | 项目 ID |
| name | 视图名称 |
| typeCode | 事项类型 |
| querySpec | 查询条件（同 QuerySpec） |
| columns | 列配置 |
| isDefault | 是否默认 |

**响应 data：** `string` — 视图 ID

---

### 11.2 视图列表

**POST** `/pm/views/list`

**请求体：** `{ "projectId": "...", "typeCode": "bug" }`

---

### 11.3 删除视图

**POST** `/pm/views/delete?id={id}`

---

## 12. 元数据与看板

Base path: `/pm`

### 12.1 事项类型列表

**POST** `/pm/meta/types`

**请求体：** `{}`

**响应 data：** `PmWorkItemType[]`

| code | name |
|------|------|
| requirement | 需求 |
| task | 任务 |
| bug | 缺陷 |
| test_case | 测试用例 |

---

### 12.2 看板数据

**POST** `/pm/board`

**请求体：**

```json
{
  "projectId": "2073615378310627330",
  "typeCode": "task"
}
```

**响应 data：** `Map<string, PmWorkItem[]>`

Key 为 **工作流中的 statusCode**（按 `sortOrder` 排序），Value 为该状态下的事项列表。非固定 `open/in_progress/done` 四列，而是跟随项目类型的状态配置动态生成。

---

## 13. 接口索引

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| 健康 | GET | `/health/check` | 健康检查 |
| 项目 | POST | `/pm/projects/page` | 项目分页 |
| 项目 | POST | `/pm/projects/save` | 创建/更新项目 |
| 项目 | GET | `/pm/projects/{id}` | 项目详情 |
| 项目 | GET | `/pm/projects/{id}/access-context` | 项目租户上下文 |
| 项目 | POST | `/pm/projects/delete` | 删除项目 |
| 模块 | GET | `/pm/project-modules/tree` | 模块树 |
| 模块 | GET | `/pm/project-modules/flat` | 模块扁平列表 |
| 模块 | POST | `/pm/project-modules/save` | 保存模块 |
| 模块 | POST | `/pm/project-modules/delete` | 删除模块 |
| 事项 | POST | `/pm/work-items/page` | 事项分页查询 |
| 事项 | POST | `/pm/work-items/save` | 创建/更新事项 |
| 事项 | GET | `/pm/work-items/{id}` | 事项详情 |
| 事项 | POST | `/pm/work-items/delete` | 删除事项 |
| 事项 | POST | `/pm/work-items/{id}/transition` | 状态流转 |
| 事项 | POST | `/pm/work-items/links/save` | 添加关联 |
| 事项 | GET | `/pm/work-items/{id}/links` | 查询关联 |
| 事项 | GET | `/pm/work-items/{id}/activities` | 活动日志 |
| 评论 | GET | `/pm/work-items/{id}/comments` | 评论列表 |
| 评论 | GET | `/pm/work-items/{id}/comments/count` | 评论数 |
| 评论 | POST | `/pm/work-items/comments/counts` | 批量评论数 |
| 评论 | POST | `/pm/work-items/comments/save` | 发表评论 |
| 评论 | POST | `/pm/work-items/comments/delete` | 删除评论 |
| IO | GET | `/pm/work-items/io/columns` | 导入导出列 |
| IO | POST | `/pm/work-items/io/import/template` | 导入模板下载 |
| IO | POST | `/pm/work-items/io/export` | 导出 Excel |
| IO | POST | `/pm/work-items/io/import/preview` | 导入预览 |
| IO | POST | `/pm/work-items/io/import` | 执行导入 |
| 字段 | POST | `/pm/fields/definitions/list` | 字段 Schema |
| 字段 | POST | `/pm/fields/definitions/catalog` | 字段目录 |
| 字段 | POST | `/pm/fields/definitions/available` | 可添加字段 |
| 字段 | GET | `/pm/fields/definitions/{id}` | 字段详情 |
| 字段 | POST | `/pm/fields/definitions/save` | 保存字段 |
| 字段 | POST | `/pm/fields/definitions/delete` | 删除字段 |
| 字段 | POST | `/pm/fields/definitions/add-to-type` | 绑定类型 |
| 字段 | POST | `/pm/fields/definitions/remove-from-type` | 解绑类型 |
| 字段 | GET | `/pm/fields/definitions/options` | 静态选项 |
| 字段 | GET | `/pm/fields/definitions/options/resolve` | 解析选项 |
| 字段 | POST | `/pm/fields/definitions/options/remote/preview` | 预览远程选项 |
| 布局 | POST | `/pm/fields/layout/get` | 获取布局 |
| 布局 | POST | `/pm/fields/layout/save` | 保存布局 |
| 工作流 | POST | `/pm/status/workflow/get` | 获取工作流 |
| 工作流 | POST | `/pm/status/workflow/options` | 状态选项 |
| 工作流 | POST | `/pm/status/workflow/allowed` | 允许流转 |
| 工作流 | POST | `/pm/status/workflow/post-function-meta` | 后置函数元数据 |
| 工作流 | POST | `/pm/status/workflow/transition-meta` | 流转校验元数据 |
| 工作流 | POST | `/pm/status/workflow/save` | 保存工作流 |
| 工作流 | POST | `/pm/status/workflow/reset` | 重置工作流 |
| 方案 | POST | `/pm/issue-type-schemes/export` | 导出类型方案 |
| 方案 | POST | `/pm/issue-type-schemes/export-project` | 导出项目方案 |
| 方案 | POST | `/pm/issue-type-schemes/preview` | 导入预览 |
| 方案 | POST | `/pm/issue-type-schemes/import` | 导入类型方案 |
| 方案 | POST | `/pm/issue-type-schemes/import-project` | 导入项目方案 |
| 视图 | POST | `/pm/views/save` | 保存视图 |
| 视图 | POST | `/pm/views/list` | 视图列表 |
| 视图 | POST | `/pm/views/delete` | 删除视图 |
| 元数据 | POST | `/pm/meta/types` | 事项类型 |
| 看板 | POST | `/pm/board` | 看板数据 |

---

## 14. 错误处理

业务失败时 HTTP 通常为 `200`（Security 层认证/授权失败为 `401`/`403`），body 示例：

```json
{
  "code": 20002,
  "msg": "项目不存在或无权访问",
  "data": null
}
```

| PM 常见 code | 含义 |
|--------------|------|
| 10002 | 请求参数错误 |
| 11001 | 未登录 |
| 11005 | 无权访问该租户 |
| 20001 | 项目不存在 |
| 20002 | 项目不存在或无权访问 |
| 20004 | 项目编码重复 |
| 21001 | 事项不存在 |
| 21012 | 状态流转不允许 |

完整错误码定义见 [error-code-design.md](./error-code-design.md)。

前端 axios 拦截器在 `code !== 0` 时抛出 `ApiError`（含 `code` 与 `msg`）；`11001` 或 HTTP `401` 时自动跳转登录页。

```typescript
import { errorMessage, isApiError } from '@/shared/errors/apiError'
import { ResultCode } from '@/shared/errors/resultCode'

try {
  await pmProjectApi.getById(id)
} catch (e) {
  message.error(errorMessage(e))
}
```

---

## 15. 相关文档

| 文档 | 说明 |
|------|------|
| [pm-design.md](./pm-design.md) | PM 模块架构与领域设计 |
| [error-code-design.md](./error-code-design.md) | 全局错误码规范 |
