# 接口测试平台 — 技术方案设计（Web 端）

> 技术路线：Vue 3 + TypeScript + Vite + Naive UI + Java Spring Boot 3.4 + MyBatis Plus + MySQL + Redis
> 定位：DevOps 平台子模块，专注接口测试，后续可扩展接口文档/接口用例
>
> 文档日期：2026-08-12

---

## 一、产品定位与边界

### 1.1 功能范围

```
Phase 1 — 接口管理（本期实现）
├── 接口分组管理（树形结构、CRUD）
├── 接口定义管理（CRUD、分页、筛选）
├── 请求参数管理（Query/Header/Path/Body 参数编辑）
├── 响应定义管理（状态码、Content-Type、Schema、示例）
├── 接口状态流转（草稿 → 已发布 → 已废弃 → 草稿）
├── 版本管理（发布时自动递增版本号，快照记录）
└── 接口详情查看

Phase 2 — 接口测试（后续扩展）
├── 接口调试（HTTP/WebSocket/gRPC）
├── 集合管理
├── 环境变量管理
├── 前置/后置脚本
├── 场景编排
├── 压测（实时 + 定时）
├── 测试报告
└── 团队协作

Phase 3 — 接口文档（后续扩展）
├── OpenAPI 3.x 管理
├── 文档在线预览
├── 版本管理
└── 文档导出
```

### 1.2 技术约束

| 维度 | 选择 |
|------|------|
| 前端框架 | Vue 3 + TypeScript + Vite |
| UI 组件库 | Naive UI |
| 状态管理 | Pinia |
| 后端框架 | Spring Boot 3.4 |
| ORM | MyBatis Plus |
| 数据库 | MySQL 8.3 |
| 缓存 | Redis + Redisson |
| 主键策略 | Snowflake (IdType.ASSIGN_ID) |
| 请求引擎 | Java HttpClient5（后端代理） |
| 压测引擎 | k6（Go 内核） |
| 脚本引擎 | GraalVM JS（沙箱） |

---

## 二、模块架构

### 2.1 后端模块划分

```
api-test-core/
├── apidefine/                    # 接口定义管理
│   ├── controller/               # REST 控制器
│   ├── service/                  # 业务逻辑层
│   ├── mapper/                   # MyBatis Plus 映射
│   ├── entity/                   # 数据实体
│   ├── dto/                      # 数据传输对象
│   ├── vo/                       # 视图对象
│   └── convert/                  # MapStruct 转换器
├── common/                       # 公共模块
│   ├── enums/                    # 枚举定义
│   ├── exception/                # 异常定义
│   └── config/                   # 自动配置
```

### 2.2 前端模块划分

```
api-test/define/
├── api/                          # API 请求封装
├── types/                        # TypeScript 类型定义
├── stores/                       # Pinia 状态管理
├── views/                        # 页面视图
│   ├── ApiDefinitionList.vue     # 接口列表页
│   ├── ApiDefinitionDetail.vue   # 接口详情页
│   └── ApiDefinitionFormDialog.vue # 创建/编辑对话框
├── components/                   # 可复用组件
│   ├── ApiGroupTree.vue          # 分组树组件
│   ├── ParamEditor.vue           # 参数编辑器
│   └── ResponseEditor.vue        # 响应定义编辑器
└── router/                       # 路由定义
```

### 2.3 数据流

```
用户操作 → Vue Component → Pinia Store → API Request → HTTP → Controller
→ Service → MyBatis Mapper → MySQL
                         ↓
                   Redis Cache (后续)
```

---

## 三、数据库设计

### 3.1 表结构

5 张核心表：

| 表名 | 说明 | 核心字段 |
|------|------|---------|
| api_group | 接口分组 | id, project_id, parent_id, name, sort_order, description |
| api_definition | 接口定义 | id, project_id, group_id, name, path, method, status, version, tags(JSON) |
| api_definition_param | 接口参数 | id, definition_id, param_type, name, data_type, required, parent_id |
| api_definition_response | 响应定义 | id, definition_id, status_code, content_type, body_schema(JSON), body_example(JSON) |
| api_definition_version | 版本记录 | id, definition_id, version, change_log, snapshot_* |

### 3.2 接口状态流转

```
DRAFT ──publish──▶ PUBLISHED ──deprecate──▶ DEPRECATED
  ▲                   │                         │
  └── revertDraft ────┘                         │
  └────────────────── revertDraft ──────────────┘
```

### 3.3 版本策略

- 初始版本：`1.0.0`
- 发布时自动递增：`1.0.0 → 1.0.1 → 1.0.2`
- 每次发布创建完整快照

---

## 四、API 设计

### 4.1 接口分组（5 个端点）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/apitest/groups | 创建分组 |
| PUT | /api/apitest/groups/{id} | 更新分组 |
| DELETE | /api/apitest/groups/{id} | 删除分组 |
| GET | /api/apitest/groups/tree | 获取分组树 |
| GET | /api/apitest/groups/{id} | 获取分组详情 |

### 4.2 接口定义（8 个端点）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/apitest/definitions/page | 分页查询 |
| GET | /api/apitest/definitions/{id} | 获取详情 |
| POST | /api/apitest/definitions | 创建接口 |
| PUT | /api/apitest/definitions/{id} | 更新接口 |
| DELETE | /api/apitest/definitions/{id} | 删除接口 |
| POST | /api/apitest/definitions/{id}/publish | 发布 |
| POST | /api/apitest/definitions/{id}/deprecate | 废弃 |
| POST | /api/apitest/definitions/{id}/revert-draft | 恢复草稿 |

### 4.3 通用响应格式

```json
{
  "code": 0,
  "msg": null,
  "data": { ... }
}
```

---

## 五、实现状态

### 5.1 已完成

✅ 后端 5 个实体 + 5 个 Mapper + 5 个 Service + 2 个 Controller
✅ 后端 7 个 DTO + 5 个 VO + 2 个 MapStruct Converter
✅ 后端 4 个枚举 + 1 个异常类 + 1 个自动配置
✅ 后端编译通过，无错误
✅ 前端 13 个 TypeScript 文件
✅ 前端 Pinia Store（group + definition）
✅ 前端 3 个视图页面（列表/详情/表单对话框）
✅ 前端 3 个组件（分组树/参数编辑器/响应编辑器）
✅ 前端路由注册
✅ 前端 TypeScript 编译通过，无错误

### 5.2 待实现

⬜ 接口调试功能（请求引擎）
⬜ 前置/后置脚本（GraalVM）
⬜ 环境变量管理
⬜ 集合管理
⬜ 压测引擎（k6 集成）
⬜ 测试报告
⬜ 接口文档（OpenAPI 3.x）