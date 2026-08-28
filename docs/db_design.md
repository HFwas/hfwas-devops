# 接口测试平台 — 数据库设计

> 模块：api-test-core（接口测试核心）
> 技术栈：SQLite 3 + MyBatis Plus + Redisson (本地开发) / MySQL 8.3 (生产)
> 日期：2026-08-28

---

> **说明：** 本设计文档以 MySQL 为基准描述表结构，实际本地开发使用 SQLite（`backend/server/src/main/resources/db/api-test-schema.sql`）。差异如下：
> - 字段类型：`BIGINT` → `INTEGER`、`VARCHAR(n)` → `TEXT`、`TINYINT` → `INTEGER`、`JSON` → `TEXT`、`DATETIME` → `TEXT`
> - 默认值：`CURRENT_TIMESTAMP` → `datetime('now')`、`ON UPDATE CURRENT_TIMESTAMP` → 由应用层维护 `update_time`
> - 字段名：`created_by` → `create_by`、`updated_by` → `update_by`
> - 生产环境可切换为 MySQL，SQL 需做对应转换。
>
> ---

## Phase1 · 接口管理模块

### 一、核心实体关系

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
    │       ├── api_definition_version (版本记录)
    │       │
    │       │ 1:N
    │       ├── api_definition_script (前置/后置脚本)
    │       │
    │       │ 1:N
    │       ├── api_definition_assertion (响应断言)
    │       │
    │       │ 1:N
    │       └── api_definition_extract (变量提取)
    │
    └── (自引用) parent_id → 树形分组

api_environment (环境)
    │ 1:N
    └── api_environment_variable (环境变量)

api_debug_history (调试历史)
    └── 关联 api_definition、api_environment

api_collection (集合)
    │ 1:N
    ├── api_collection_folder (文件夹，parent_id 自引用)
    │       │ 1:N
    │       └── api_collection_item (集合项，引用 api_definition)
    │
    ├── api_collection_item (根级集合项)
    │
    └── 1:N
        └── api_collection_run (集合执行记录)
                │ 1:N
                └── api_collection_run_item (单条API执行结果)
```

### 二、枚举定义

#### 2.1 ApiStatusEnum — 接口状态

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

#### 2.2 HttpMethodEnum — 请求方式

| 编码 | 说明 |
|------|------|
| `GET` | 获取资源 |
| `POST` | 创建资源 |
| `PUT` | 全量更新 |
| `PATCH` | 部分更新 |
| `DELETE` | 删除资源 |
| `HEAD` | 获取响应头 |
| `OPTIONS` | 预检请求 |

#### 2.3 ParamTypeEnum — 参数类型

| 编码 | 说明 | 存储位置 |
|------|------|----------|
| `path` | 路径参数 | `/api/users/{id}` |
| `query` | Query 参数 | `?page=1&size=10` |
| `header` | 请求头 | `Authorization: Bearer xxx` |
| `body` | 请求体 | JSON Body |

#### 2.4 ParamDataTypeEnum — 参数数据类型

| 编码 | 说明 |
|------|------|
| `string` | 字符串 |
| `integer` | 整数 |
| `number` | 浮点数 |
| `boolean` | 布尔值 |
| `array` | 数组 |
| `object` | 对象 |
| `file` | 文件上传 |

#### 2.5 ScriptTypeEnum — 脚本类型

| 编码 | 说明 |
|------|------|
| `PRE_REQUEST` | 前置脚本（请求发送前执行） |
| `POST_RESPONSE` | 后置脚本（响应接收后执行） |

#### 2.6 AssertionSourceEnum — 断言来源

| 编码 | 说明 |
|------|------|
| `RESPONSE_STATUS` | 响应状态码 |
| `RESPONSE_HEADERS` | 响应头 |
| `RESPONSE_BODY` | 响应体 |
| `RESPONSE_TIME` | 响应耗时 |

#### 2.7 CompareTypeEnum — 断言比较方式

| 编码 | 说明 |
|------|------|
| `EQUALS` | 等于 |
| `NOT_EQUALS` | 不等于 |
| `CONTAINS` | 包含 |
| `NOT_CONTAINS` | 不包含 |
| `REGEX` | 正则匹配 |
| `GT` | 大于 |
| `GTE` | 大于等于 |
| `LT` | 小于 |
| `LTE` | 小于等于 |

#### 2.8 ExtractSourceEnum — 变量提取来源

| 编码 | 说明 |
|------|------|
| `RESPONSE_BODY` | 响应体 |
| `RESPONSE_HEADERS` | 响应头 |
| `RESPONSE_STATUS` | 响应状态码 |

#### 2.9 DebugStatusEnum — 调试状态

| 编码 | 说明 |
|------|------|
| `SUCCESS` | 成功（HTTP 2xx） |
| `FAILURE` | 失败（HTTP 非2xx或无响应） |
| `ERROR` | 执行错误（网络异常、脚本异常等） |

---

### 三、表结构（Phase1 · 接口管理）

#### 3.1 api_group — 接口分组

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

#### 3.2 api_definition — 接口定义主表

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

#### 3.3 api_definition_param — 接口参数表

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

#### 3.4 api_definition_response — 接口响应定义表

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

#### 3.5 api_definition_version — 接口版本记录表

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

## Phase2 · 接口调试与环境管理模块

### 四、新增表结构

#### 4.1 api_definition_script — 前置/后置脚本表

```sql
CREATE TABLE `api_definition_script` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `definition_id`     BIGINT       NOT NULL COMMENT '所属接口定义ID',
    `script_type`       VARCHAR(20)  NOT NULL COMMENT '脚本类型 PRE_REQUEST / POST_RESPONSE',
    `content`           TEXT         NOT NULL COMMENT '脚本内容（JavaScript）',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `description`       VARCHAR(500) NULL     COMMENT '脚本说明',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_script_type` (`definition_id`, `script_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口前置/后置脚本';
```

#### 4.2 api_definition_assertion — 响应断言表

```sql
CREATE TABLE `api_definition_assertion` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `definition_id`     BIGINT       NOT NULL COMMENT '所属接口定义ID',
    `name`              VARCHAR(200) NULL     COMMENT '断言名称（前端显示标签）',
    `source`            VARCHAR(30)  NOT NULL COMMENT '断言来源 RESPONSE_STATUS / RESPONSE_HEADERS / RESPONSE_BODY / RESPONSE_TIME',
    `compare_type`      VARCHAR(20)  NOT NULL COMMENT '比较方式 EQUALS / NOT_EQUALS / CONTAINS / NOT_CONTAINS / REGEX / GT / GTE / LT / LTE',
    `expression`        VARCHAR(500) NULL     COMMENT '表达式（JSONPath 或 Header 名称）',
    `expected_value`    TEXT         NULL     COMMENT '期望值',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `description`       VARCHAR(500) NULL     COMMENT '断言说明',
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口响应断言';
```

#### 4.3 api_definition_extract — 变量提取定义表

```sql
CREATE TABLE `api_definition_extract` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `definition_id`     BIGINT       NOT NULL COMMENT '所属接口定义ID',
    `variable_name`     VARCHAR(100) NOT NULL COMMENT '提取的变量名',
    `expression`        VARCHAR(500) NOT NULL COMMENT '提取表达式（JSONPath 或 Header 名称）',
    `source`            VARCHAR(30)  NOT NULL COMMENT '提取来源 RESPONSE_BODY / RESPONSE_HEADERS / RESPONSE_STATUS',
    `enabled`           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `description`       VARCHAR(500) NULL     COMMENT '提取说明',
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_definition` (`definition_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口变量提取定义';
```

#### 4.4 api_environment — 环境表

```sql
CREATE TABLE `api_environment` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `project_id`        BIGINT       NOT NULL COMMENT '所属项目ID',
    `name`              VARCHAR(100) NOT NULL COMMENT '环境名称',
    `description`       VARCHAR(500) NULL     COMMENT '环境描述',
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_sort` (`project_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境';
```

#### 4.5 api_environment_variable — 环境变量表

```sql
CREATE TABLE `api_environment_variable` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `environment_id`    BIGINT       NOT NULL COMMENT '所属环境ID',
    `name`              VARCHAR(200) NOT NULL COMMENT '变量名',
    `value`             TEXT         NULL     COMMENT '变量值',
    `description`       VARCHAR(500) NULL     COMMENT '变量描述',
    `is_secret`         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否敏感变量 0-否 1-是（前端掩码显示）',
    `sort_order`        INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`        BIGINT       NULL     COMMENT '更新人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_environment` (`environment_id`),
    KEY `idx_name` (`environment_id`, `name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='环境变量';
```

#### 4.6 api_debug_history — 调试历史记录表

```sql
CREATE TABLE `api_debug_history` (
    `id`                BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `project_id`        BIGINT       NOT NULL COMMENT '所属项目ID',
    `definition_id`     BIGINT       NULL     COMMENT '关联接口定义ID（可选，可不关联直接调试）',
    `environment_id`    BIGINT       NULL     COMMENT '使用的环境ID',
    `name`              VARCHAR(200) NULL     COMMENT '调试名称（自动生成或用户指定）',
    -- 请求报文
    `request_url`       VARCHAR(2000) NOT NULL COMMENT '完整请求URL（变量已渲染）',
    `request_method`    VARCHAR(10)  NOT NULL COMMENT '请求方式',
    `request_headers`   JSON         NULL     COMMENT '请求头（已渲染）',
    `request_query`     JSON         NULL     COMMENT '请求Query参数（已渲染）',
    `request_body`      LONGTEXT     NULL     COMMENT '请求体（已渲染）',
    `request_content_type` VARCHAR(100) NULL  COMMENT '请求Content-Type',
    -- 响应报文
    `response_status_code` INT      NULL     COMMENT '响应状态码',
    `response_headers`  JSON         NULL     COMMENT '响应头',
    `response_body`     LONGTEXT    NULL     COMMENT '响应体',
    `response_content_type` VARCHAR(100) NULL COMMENT '响应Content-Type',
    `response_size`     BIGINT       NULL     COMMENT '响应体大小（字节）',
    -- 调试信息
    `duration_ms`       BIGINT       NOT NULL DEFAULT 0 COMMENT '请求耗时（毫秒）',
    `status`            VARCHAR(20)  NOT NULL DEFAULT 'SUCCESS' COMMENT '调试状态 SUCCESS / FAILURE / ERROR',
    `error_message`     TEXT         NULL     COMMENT '错误信息（网络异常/脚本异常时填充）',
    -- 脚本日志
    `pre_request_logs`      TEXT     NULL     COMMENT '前置脚本执行日志（沙箱输出）',
    `post_response_logs`    TEXT     NULL     COMMENT '后置脚本执行日志（沙箱输出）',
    -- 断言结果
    `assertion_results` JSON         NULL     COMMENT '断言结果列表 [{name, passed, actual, expected}]',
    `all_assertions_passed` TINYINT  NULL     COMMENT '断言是否全部通过 0-未通过 1-通过 null-无断言',
    -- 提取变量快照
    `extracted_variables` JSON      NULL     COMMENT '提取的变量快照 {"varName": "value"}',
    -- 审计字段
    `deleted`           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除 0-未删 1-已删',
    `created_by`        BIGINT       NOT NULL COMMENT '创建人ID',
    `create_time`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_environment` (`environment_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`),
    KEY `idx_project_time` (`project_id`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='调试历史记录';
```

---

### 五、核心数据模型示例

#### 5.1 接口定义示例

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

#### 5.2 参数示例

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

#### 5.3 环境变量示例

```json
{
  "environment": {
    "id": 3001,
    "projectId": 1001,
    "name": "测试环境",
    "description": "内网测试环境"
  },
  "variables": [
    { "name": "base_url", "value": "http://test-api.example.com", "isSecret": false },
    { "name": "token", "value": "eyJhbGciOiJIUzI1NiIs...", "isSecret": true },
    { "name": "page_size", "value": "20", "isSecret": false }
  ]
}
```

#### 5.4 调试历史示例

```json
{
  "id": 4001,
  "definitionId": 1234567890,
  "environmentId": 3001,
  "name": "GET /api/users",
  "requestUrl": "http://test-api.example.com/api/users?page=1&size=20",
  "requestMethod": "GET",
  "requestHeaders": {
    "Authorization": "Bearer eyJhbGciOiJIUzI1NiIs...",
    "Content-Type": "application/json"
  },
  "responseStatusCode": 200,
  "responseBody": "{\"code\":0,\"data\":{\"total\":100,\"list\":[...]}}",
  "responseSize": 2048,
  "durationMs": 156,
  "status": "SUCCESS",
  "preRequestLogs": ["[sandbox] 前置脚本执行完成"],
  "postResponseLogs": ["[sandbox] 后置脚本执行完成"],
  "assertionResults": [
    { "name": "状态码为200", "passed": true, "actual": 200, "expected": 200 },
    { "name": "响应包含data字段", "passed": true, "actual": "包含", "expected": "data" }
  ],
  "allAssertionsPassed": true,
  "extractedVariables": {
    "userId": "1"
  }
}
```

---

### 六、字段说明与约束

#### 6.1 通用字段

| 字段 | 说明 | 约束 |
|------|------|------|
| `id` | 主键，雪花算法ID | `IdType.ASSIGN_ID` |
| `project_id` | 项目ID，多租户隔离 | 所有业务表必含 |
| `deleted` | 逻辑删除标记 | 所有业务表必含，0-未删 1-已删 |
| `created_by` | 创建人ID | 所有业务表必含（SQLite 实现为 `create_by`） |
| `updated_by` | 更新人ID | 一般业务表含（SQLite 实现为 `update_by`） |
| `create_time` | 创建时间 | `DEFAULT CURRENT_TIMESTAMP` |
| `update_time` | 更新时间 | `ON UPDATE CURRENT_TIMESTAMP` |

#### 6.2 命名规范

- **表名**：`api_` 前缀，下划线分隔（如 `api_definition`）
- **字段**：小写蛇形（如 `group_id`、`param_type`）
- **主键**：`id`，统一使用雪花算法
- **外键**：`{目标表}_id`（如 `definition_id`）

#### 6.3 JSON 字段说明

| 表 | 字段 | 说明 | 处理器 |
|----|------|------|--------|
| `api_definition` | `tags` | 标签列表 | `JacksonTypeHandler` |
| `api_definition_param` | `body_schema` | 预留，后续扩展 | `JacksonTypeHandler` |
| `api_definition_response` | `body_schema` | JSON Schema 定义 | `JacksonTypeHandler` |
| `api_definition_response` | `body_example` | 响应示例 | `JacksonTypeHandler` |
| `api_definition_version` | `snapshot_params` | 版本快照参数 | `JacksonTypeHandler` |
| `api_definition_version` | `snapshot_responses` | 版本快照响应 | `JacksonTypeHandler` |
| `api_debug_history` | `request_headers` | 请求头 | `JacksonTypeHandler` |
| `api_debug_history` | `request_query` | 请求Query参数 | `JacksonTypeHandler` |
| `api_debug_history` | `response_headers` | 响应头 | `JacksonTypeHandler` |
| `api_debug_history` | `assertion_results` | 断言结果列表 | `JacksonTypeHandler` |
| `api_debug_history` | `extracted_variables` | 提取变量快照 | `JacksonTypeHandler` |

---

### 七、索引策略

| 表 | 索引 | 说明 |
|----|------|------|
| `api_group` | `(project_id, sort_order)` | 项目内分组排序查询 |
| `api_group` | `(project_id, parent_id)` | 按父分组查询子分组 |
| `api_definition` | `(project_id, status)` | 项目内按状态筛选 |
| `api_definition` | `(path, method)` | 路径+方法联合查询 |
| `api_definition` | `(status)` | 按状态筛选 |
| `api_definition` | `(group_id)` | 按分组查询 |
| `api_definition_param` | `(definition_id, param_type)` | 按类型查询参数 |
| `api_definition_response` | `(definition_id, status_code)` | 按状态码查询响应 |
| `api_definition_version` | `(definition_id, version)` | 版本查询 |
| `api_definition_script` | `(definition_id, script_type)` | 按类型查询脚本 |
| `api_definition_assertion` | `(definition_id)` | 按接口查询断言 |
| `api_definition_extract` | `(definition_id)` | 按接口查询变量提取 |
| `api_environment` | `(project_id, sort_order)` | 项目内排序查询 |
| `api_environment_variable` | `(environment_id, name)` | 按环境+变量名查询 |
| `api_debug_history` | `(project_id, create_time)` | 项目内按时间倒序查询 |
| `api_debug_history` | `(definition_id)` | 按接口查询调试历史 |
| `api_debug_history` | `(status)` | 按状态筛选 |

---

### 八、初始化数据

```sql
-- 默认分组（首次创建项目时自动生成）
INSERT INTO `api_group` (`id`, `project_id`, `parent_id`, `name`, `sort_order`, `description`, `created_by`)
VALUES (?, ?, NULL, '默认分组', 0, '系统默认分组', ?);

-- 默认环境（首次创建项目时自动生成）
INSERT INTO `api_environment` (`id`, `project_id`, `name`, `sort_order`, `description`, `created_by`)
VALUES (?, ?, '默认环境', 0, '系统默认环境', ?);
```

---

## Phase3 · 集合管理模块

### 九、新增表结构（Phase3）

#### 9.1 api_collection — 接口集合表

```sql
CREATE TABLE `api_collection` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `project_id`    BIGINT       NOT NULL COMMENT '所属项目ID',
    `name`          VARCHAR(200) NOT NULL COMMENT '集合名称',
    `description`   TEXT         NULL     COMMENT '集合描述',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_by`    BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`    BIGINT       NULL     COMMENT '更新人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_sort` (`project_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接口集合';
```

#### 9.2 api_collection_folder — 集合文件夹表

```sql
CREATE TABLE `api_collection_folder` (
    `id`            BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `collection_id` BIGINT       NOT NULL COMMENT '所属集合ID',
    `parent_id`     BIGINT       NULL     COMMENT '父文件夹ID，null为根级',
    `name`          VARCHAR(200) NOT NULL COMMENT '文件夹名称',
    `description`   VARCHAR(500) NULL     COMMENT '文件夹描述',
    `sort_order`    INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_by`    BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`    BIGINT       NULL     COMMENT '更新人ID',
    `create_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_collection` (`collection_id`),
    KEY `idx_parent` (`parent_id`),
    KEY `idx_sort` (`collection_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集合文件夹';
```

#### 9.3 api_collection_item — 集合项表

```sql
CREATE TABLE `api_collection_item` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `collection_id`   BIGINT       NOT NULL COMMENT '所属集合ID',
    `folder_id`       BIGINT       NULL     COMMENT '所属文件夹ID，null为根级',
    `definition_id`   BIGINT       NOT NULL COMMENT '引用的接口定义ID',
    `name`            VARCHAR(200) NULL     COMMENT '覆盖名称（为空则使用接口定义名称）',
    `description`     VARCHAR(500) NULL     COMMENT '覆盖描述',
    `enabled`         TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用 0-禁用 1-启用',
    `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '排序序号',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人ID',
    `updated_by`      BIGINT       NULL     COMMENT '更新人ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_collection` (`collection_id`),
    KEY `idx_folder` (`folder_id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_sort` (`collection_id`, `sort_order`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集合项';
```

#### 9.4 api_collection_run — 集合执行记录表

```sql
CREATE TABLE `api_collection_run` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `collection_id`   BIGINT       NOT NULL COMMENT '所属集合ID',
    `project_id`      BIGINT       NOT NULL COMMENT '项目ID',
    `environment_id`  BIGINT       NULL     COMMENT '执行时使用的环境ID',
    `name`            VARCHAR(200) NULL     COMMENT '执行名称（自动生成）',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'RUNNING' COMMENT '执行状态 RUNNING / COMPLETED / FAILED',
    `total_count`     INT          NOT NULL DEFAULT 0 COMMENT '总项数',
    `passed_count`    INT          NOT NULL DEFAULT 0 COMMENT '通过数',
    `failed_count`    INT          NOT NULL DEFAULT 0 COMMENT '失败数',
    `error_count`     INT          NOT NULL DEFAULT 0 COMMENT '错误数',
    `duration_ms`     BIGINT       NOT NULL DEFAULT 0 COMMENT '总耗时（毫秒）',
    `trigger_mode`    VARCHAR(20)  NOT NULL DEFAULT 'MANUAL' COMMENT '触发方式 MANUAL / SCHEDULED',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `created_by`      BIGINT       NOT NULL COMMENT '创建人ID',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_collection` (`collection_id`),
    KEY `idx_project` (`project_id`),
    KEY `idx_status` (`status`),
    KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集合执行记录';
```

#### 9.5 api_collection_run_item — 集合执行项结果表

```sql
CREATE TABLE `api_collection_run_item` (
    `id`              BIGINT       NOT NULL COMMENT '主键（雪花ID）',
    `run_id`          BIGINT       NOT NULL COMMENT '所属执行记录ID',
    `collection_item_id` BIGINT    NULL     COMMENT '集合项ID',
    `definition_id`   BIGINT       NULL     COMMENT '接口定义ID',
    `name`            VARCHAR(200) NULL     COMMENT '接口名称（执行时快照）',
    `request_url`     VARCHAR(2000) NULL    COMMENT '请求URL（已渲染）',
    `request_method`  VARCHAR(10)  NULL     COMMENT '请求方式',
    `request_headers` JSON         NULL     COMMENT '请求头（已渲染）',
    `request_body`    LONGTEXT     NULL     COMMENT '请求体（已渲染）',
    `response_status_code` INT     NULL     COMMENT '响应状态码',
    `response_headers` JSON        NULL     COMMENT '响应头',
    `response_body`   LONGTEXT     NULL     COMMENT '响应体',
    `response_size`   BIGINT       NULL     COMMENT '响应大小（字节）',
    `duration_ms`     BIGINT       NOT NULL DEFAULT 0 COMMENT '耗时（毫秒）',
    `status`          VARCHAR(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态 PENDING / SUCCESS / FAILURE / ERROR / SKIPPED',
    `error_message`   TEXT         NULL     COMMENT '错误信息',
    `assertion_results` JSON       NULL     COMMENT '断言结果列表',
    `all_assertions_passed` TINYINT NULL    COMMENT '断言是否全部通过',
    `extracted_variables` JSON     NULL     COMMENT '提取的变量快照',
    `sort_order`      INT          NOT NULL DEFAULT 0 COMMENT '执行顺序',
    `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    `create_time`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    KEY `idx_run` (`run_id`),
    KEY `idx_definition` (`definition_id`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='集合执行项结果';
```

### 十、字段说明与约束

#### 10.1 JSON 字段补充

| 表 | 字段 | 说明 | 处理器 |
|----|------|------|--------|
| `api_collection_run_item` | `request_headers` | 请求头 | `JacksonTypeHandler` |
| `api_collection_run_item` | `response_headers` | 响应头 | `JacksonTypeHandler` |
| `api_collection_run_item` | `assertion_results` | 断言结果 | `JacksonTypeHandler` |
| `api_collection_run_item` | `extracted_variables` | 提取变量 | `JacksonTypeHandler` |

#### 10.2 索引补充

| 表 | 索引 | 说明 |
|----|------|------|
| `api_collection` | `(project_id, sort_order)` | 项目内排序查询 |
| `api_collection_folder` | `(collection_id, sort_order)` | 集合内排序查询 |
| `api_collection_item` | `(collection_id, sort_order)` | 集合内排序查询 |
| `api_collection_item` | `(definition_id)` | 按接口定义查询 |
| `api_collection_run` | `(project_id, create_time)` | 按时间倒序查询 |
| `api_collection_run` | `(collection_id)` | 按集合查询运行记录 |
| `api_collection_run_item` | `(run_id)` | 按运行记录查询项 |