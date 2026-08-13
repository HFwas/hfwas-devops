# 接口管理模块 — 数据库设计

> 模块：api-management（接口管理）
> 技术栈：MySQL 8.3 + MyBatis Plus + Redisson
> 日期：2026-08-12

---

## 一、核心实体关系

```
api_group (分组)
    │ 1:N
    ├── api_definition (接口定义主表)
    │       │ 1:N
    │       ├── api_definition_param (请求参数)
    │       │       ├── type: path / query / header / body
    │       │
    │       │ 1:N
    │       ├── api_definition_response (响应定义)
    │       │
    │       │ 1:N
    │       └── api_definition_version (版本记录)
    │
    └── (自引用) parent_id → 树形分组
```

---

## 二、枚举定义

### 2.1 ApiStatusEnum — 接口状态

| 编码 | 名称 | 说明 |
|------|------|------|
| `DRAFT` | 草稿 | 新建/编辑中，不可用于调试 |
| `PUBLISHED` | 已发布 | 可用状态，可被引用和调试 |
| `DEPRECATED` | 已废弃 | 不可用，仅保留历史记录 |

**状态流转：**

```
DRAFT ──→ PUBLISHED ──→ DEPRECATED
  ↑            │
  └──── ←──────┘  (可重新发布)
```

### 2.2 HttpMethodEnum — 请求方式

| 编码 | 说明 |
|------|------|
| `GET` | 获取资源 |
| `POST` | 创建资源 |
| `PUT` | 全量更新 |
| `PATCH` | 部分更新 |
| `DELETE` | 删除资源 |
| `HEAD` | 获取响应头 |
| `OPTIONS` | 预检请求 |

### 2.3 ParamTypeEnum — 参数类型

| 编码 | 说明 | 存储位置 |
|------|------|----------|
| `path` | 路径参数 | `/api/users/{id}` |
| `query` | Query 参数 | `?page=1&size=10` |
| `header` | 请求头 | `Authorization: Bearer xxx` |
| `body` | 请求体 | JSON Body |

### 2.4 ParamDataTypeEnum — 参数数据类型

| 编码 | 说明 |
|------|------|
| `string` | 字符串 |
| `integer` | 整数 |
| `number` | 浮点数 |
| `boolean` | 布尔值 |
| `array` | 数组 |
| `object` | 对象 |
| `file` | 文件上传 |

---

## 三、表结构

### 3.1 api_group — 接口分组

```sql
CREATE TABLE `api_group` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `project_id`    BIGINT       NOT NULL COMMENT '所属项目ID',
    `parent_id`     BIGINT       NULL     COMMENT '父分组ID，null为根级',
    `name`          VARCHAR(100) NOT NULL COMMENT '分组名称',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `description`   VARCHAR(500) NULL     COMMENT '分组描述',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`    BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`    BIGINT       NULL     COMMENT '更新人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_sort` (`project_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口分组';
```

### 3.2 api_definition — 接口定义主表

```sql
CREATE TABLE `api_definition` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `project_id`        BIGINT       NOT NULL COMMENT '所属项目ID',
    `group_id`          BIGINT       NULL     COMMENT '所属分组ID',
    `name`              VARCHAR(200) NOT NULL COMMENT '接口名称',
    `path`              VARCHAR(500) NOT NULL COMMENT '请求路径（含路径参数占位符，如 /api/users/{id}）',
    `method`            VARCHAR(10)  NOT NULL DEFAULT 'GET' COMMENT '请求方式 GET/POST/PUT/PATCH/DELETE/HEAD/OPTIONS',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'DRAFT' COMMENT '状态 DRAFT/PUBLISHED/DEPRECATED',
    `version`           VARCHAR(20)  NOT NULL DEFAULT '1.0.0' COMMENT '当前版本号',
    `tags`              JSON         NULL     COMMENT '标签列表 ["auth","user"]',
    `description`       TEXT         NULL     COMMENT '接口描述/说明',
    `protocol`          VARCHAR(20)  NOT NULL DEFAULT 'HTTP' COMMENT '协议 HTTP/HTTPS',
    `host`              VARCHAR(500) NULL     COMMENT '主机地址（可选，用于调试）',
    `content_type`      VARCHAR(100) NULL     COMMENT '请求Content-Type，如 application/json',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_group` (`group_id`),
    KEY `idx_method` (`method`),
    KEY `idx_status` (`status`),
    KEY `idx_path` (`path`(100)),
    KEY `idx_project_status` (`project_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口定义';
```

### 3.3 api_definition_param — 接口参数表

```sql
CREATE TABLE `api_definition_param` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `definition_id`     BIGINT       NOT NULL COMMENT '所属接口定义ID',
    `param_type`        VARCHAR(20)  NOT NULL COMMENT '参数类型 path/query/header/body',
    `name`              VARCHAR(200) NOT NULL COMMENT '参数名称',
    `data_type`         VARCHAR(20)  NOT NULL DEFAULT 'string' COMMENT '数据类型 string/integer/number/boolean/array/object/file',
    `required`          TINYINT      NOT NULL DEFAULT 0 COMMENT '是否必填 0-可选 1-必填',
    `default_value`     VARCHAR(500) NULL     COMMENT '默认值',
    `description`       VARCHAR(500) NULL     COMMENT '参数描述',
    -- body 类型专用字段
    `parent_id`         BIGINT       NULL     COMMENT '父参数ID（嵌套结构时使用，支持多层JSON嵌套）',
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `example`           TEXT         NULL     COMMENT '示例值',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_param_type` (`definition_id`, `param_type`),
    KEY `idx_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口参数';
```

### 3.4 api_definition_response — 接口响应定义表

```sql
CREATE TABLE `api_definition_response` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `definition_id`     BIGINT       NOT NULL COMMENT '所属接口定义ID',
    `status_code`       INT          NOT NULL DEFAULT 200 COMMENT '响应状态码',
    `content_type`      VARCHAR(100) NULL     DEFAULT 'application/json' COMMENT '响应Content-Type',
    `description`       VARCHAR(500) NULL     COMMENT '响应描述',
    -- 响应体结构（JSON Schema 格式存储）
    `body_schema`       JSON         NULL     COMMENT '响应体JSON Schema定义',
    `body_example`      JSON         NULL     COMMENT '响应体示例值',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_status_code` (`definition_id`, `status_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口响应定义';
```

### 3.5 api_definition_version — 接口版本记录表

```sql
CREATE TABLE `api_definition_version` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `definition_id`     BIGINT       NOT NULL COMMENT '所属接口定义ID',
    `version`           VARCHAR(20)  NOT NULL COMMENT '版本号',
    `change_log`        TEXT         NULL     COMMENT '变更说明',
    -- 快照：记录该版本时的完整接口定义
    `snapshot_name`     VARCHAR(200) NOT NULL COMMENT '快照-接口名称',
    `snapshot_path`     VARCHAR(500) NOT NULL COMMENT '快照-请求路径',
    `snapshot_method`   VARCHAR(10)  NOT NULL COMMENT '快照-请求方式',
    `snapshot_params`   JSON         NULL     COMMENT '快照-参数列表',
    `snapshot_responses` JSON        NULL     COMMENT '快照-响应定义列表',
    `snapshot_description` TEXT      NULL     COMMENT '快照-接口描述',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_version` (`definition_id`, `version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口版本记录';
```

---

## 四、核心数据模型示例

### 4.1 接口定义示例

```json
{
  "id": 1234567890,
  "projectId": 1001,
  "groupId": 2001,
  "name": "获取用户列表",
  "path": "/api/users",
  "method": "GET",
  "status": "PUBLISHED",
  "version": "1.0.0",
  "tags": ["user", "list"],
  "description": "分页获取用户列表，支持按用户名搜索",
  "contentType": null
}
```

### 4.2 参数示例

```json
// path 参数
{ "paramType": "path", "name": "id", "dataType": "integer", "required": true, "description": "用户ID" }

// query 参数
{ "paramType": "query", "name": "page", "dataType": "integer", "required": true, "defaultValue": "1", "description": "页码" }
{ "paramType": "query", "name": "size", "dataType": "integer", "required": false, "defaultValue": "10", "description": "每页条数" }
{ "paramType": "query", "name": "keyword", "dataType": "string", "required": false, "description": "搜索关键词" }

// header 参数
{ "paramType": "header", "name": "Authorization", "dataType": "string", "required": true, "description": "Bearer Token" }

// body 参数（嵌套结构）
{ "paramType": "body", "name": "user", "dataType": "object", "required": true, "description": "用户信息" }
{ "paramType": "body", "name": "name", "dataType": "string", "required": true, "parentId": "user-object-id", "description": "用户名" }
{ "paramType": "body", "name": "age", "dataType": "integer", "required": false, "parentId": "user-object-id", "description": "年龄" }
```

### 4.3 响应示例

```json
{
  "statusCode": 200,
  "contentType": "application/json",
  "description": "成功返回用户列表",
  "bodyExample": {
    "code": 0,
    "message": "success",
    "data": {
      "total": 100,
      "list": [
        {
          "id": 1,
          "name": "张三",
          "email": "zhangsan@example.com"
        }
      ]
    }
  }
}
```

---

## 五、字段说明与约束

### 5.1 通用字段

| 字段 | 说明 | 约束 |
|------|------|------|
| `id` | 主键，雪花算法ID | `IdType.ASSIGN_ID` |
| `project_id` | 项目ID，多租户隔离 | 所有业务表必含 |
| `deleted` | 逻辑删除标记 | 所有业务表必含，0-未删 1-已删 |
| `created_by` | 创建人ID | 所有业务表必含 |
| `updated_by` | 更新人ID | 一般业务表含 |
| `create_time` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` |
| `update_time` | 更新时间 | `ON UPDATE CURRENT_TIMESTAMP` |

### 5.2 命名规范

- **表名**：`api_` 前缀，下划线分隔（如 `api_definition`）
- **字段**：小写蛇形（如 `group_id`、`param_type`）
- **主键**：`id`，统一使用雪花算法
- **外键**：`{目标表}_id`（如 `definition_id`）

### 5.3 JSON 字段说明

| 表 | 字段 | 说明 | 处理器 |
|----|------|------|--------|
| `api_definition` | `tags` | 标签列表 | `JacksonTypeHandler` |
| `api_definition_param` | `body_schema` | 预留，后续扩展 | `JacksonTypeHandler` |
| `api_definition_response` | `body_schema` | JSON Schema 定义 | `JacksonTypeHandler` |
| `api_definition_response` | `body_example` | 响应示例 | `JacksonTypeHandler` |
| `api_definition_version` | `snapshot_params` | 版本快照参数 | `JacksonTypeHandler` |
| `api_definition_version` | `snapshot_responses` | 版本快照响应 | `JacksonTypeHandler` |

---

## 六、索引策略

| 表 | 索引 | 说明 |
|----|------|------|
| `api_group` | `(project_id, sort_order)` | 项目内分组排序查询 |
| `api_definition` | `(project_id, status)` | 项目内按状态筛选 |
| `api_definition` | `(path(100))` | 路径搜索前缀匹配 |
| `api_definition_param` | `(definition_id, param_type)` | 按类型查询参数 |
| `api_definition_response` | `(definition_id, status_code)` | 按状态码查询响应 |
| `api_definition_version` | `(definition_id, version)` | 版本查询 |

---

## 七、初始化数据

```sql
-- 默认分组（首次创建项目时自动生成）
INSERT INTO `api_group` (`id`, `project_id`, `parent_id`, `name`, `sort_order`, `description`, `created_by`)
VALUES (?, ?, NULL, '默认分组', 0, '系统默认分组', ?);
```