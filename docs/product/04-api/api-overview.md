# 接口总览与通用约定

> 日期：2026-10-18
> 版本：v0.2
> 状态：已完成
> 数据来源：`backend/server/src/main/resources/application.yml`、`SecurityConfig.java`、`BaseResult.java`、`ResultCode.java`、`RequestIdFilter.java`、`RequestIdResponseAdvice.java`、`ExceptionAdvice.java`、`frontend/vite.config.ts`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：基础地址、认证、统一响应、错误码、分页、鉴权白名单与文档缺口 |
| v0.2 | 2026-10-18 | P0 权限错误 HTTP 映射：11010/11006/12012/20002/30005/30202；补 401/403/404 约定 |

---

## 1. 基础地址

| 环境 | 地址 | 说明 |
|------|------|------|
| 本地后端直连 | `http://localhost:8089` | `server.port=8089`，无 context-path |
| 本地前端 | `http://localhost:5173` | 前端请求统一加 `/api` 前缀，Vite 代理去前缀后转发 |
| Kong | 见 `docs/devops/kong-local-dev-guide.md` | 生产/本地网关，上游为 `devops-backend` / `devops-frontend` / `keycloak` |

所有路径均为后端实际路径，例如前端调用 `/api/pm/projects/page`，代理后为 `/pm/projects/page`。

## 2. 认证与租户

| Header | 必填 | 说明 |
|--------|------|------|
| `Authorization` | 是（白名单除外） | `Bearer <Keycloak JWT>` |
| `X-Tenant-Id` | 推荐 | 当前租户 ID；缺省时依次取 JWT 租户声明、用户默认租户 |
| `X-Request-Id` | 否 | 客户端可传入；不传由后端生成 |
| `Content-Type` | 按接口 | JSON 接口 `application/json`；上传接口 `multipart/form-data` |

> 认证由 Keycloak 完成，后端**没有** `/user/auth/login` 接口。登录、登出、刷新令牌均走 Keycloak；后端 `/user/auth/*` 只提供当前用户、我的租户与切换租户。`README.md` 中“除 `/health/check`、`/user/auth/login` 外”的表述已过时。

鉴权失败：

| 场景 | HTTP | body.code |
|------|------|-----------|
| 未携带/无效 Token | 401 | 11001 `UNAUTHORIZED` |
| 已登录但无权限（非 admin） | 403 | 11002 `FORBIDDEN` |

## 3. 统一响应

```json
{
  "code": 0,
  "msg": "成功",
  "data": {},
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| `code` | Integer | 0 成功；非 0 为业务错误码 |
| `msg` | String | 提示信息 |
| `data` | T | 业务数据，可为对象、数组或 null |
| `requestId` | String | 请求追踪 ID，由 `RequestIdResponseAdvice` 注入 |

约定：

- 业务失败通常返回 HTTP 200 + `code != 0`；认证/权限失败返回 401/403。
- `Long` 类型 ID 序列化为字符串，避免 JavaScript 精度丢失。
- 文件下载接口（Docgen、图片结果、产物下载）直接返回二进制流，不使用 `BaseResult`。

完整错误码规范与清单：[docs/backend/error-code-design.md](../../backend/error-code-design.md)。错误码分段：

| 区间 | 领域 |
|------|------|
| 0 | 成功 |
| 10000–10999 | 通用 |
| 11000–11999 | 认证与权限 |
| 12000–12999 | 用户 |
| 13000–13999 | 租户 |
| 14000–14999 | 会话管理 |
| 15000–15999 | 消息与通知 |
| 16000–16999 | 身份集成 |
| 20000–20999 | 项目 |
| 21000–21999 | 事项 |
| 22000–22999 | 字段与方案 |
| 23000–23999 | 模块 |

> 流水线、容器、API 测试、Docgen、File Parser、Image 模块的错误码多复用通用段或使用模块内枚举（如 `ContainerErrorCode`），需按模块补充。

### 3.1 P0 权限错误 HTTP 状态（2026-10-18）

`ExceptionAdvice` 与 `TenantContextFilter` 已按 `ApiErrorWriter.resolveHttpStatus` 输出：

| 场景 | code | HTTP |
|------|------|------|
| 未登录 | 11001 | 401 |
| 租户上下文缺失 | 11010 | 401 |
| 无效租户 ID / 角色 | 11006 / 12012 | 400 |
| 缺少 projectId / typeCode | 20005 / 20006 | 400 |
| 非租户成员 | 11005 | 403 |
| 项目无权访问 | 20002 | 403 |
| 集群/镜像仓库无权访问 | 30005 / 30202 | 403 |
| 项目/事项/字段不存在 | 20001 / 21001 / 22003 | 404 |
| 其它业务错误 | 原 code | 200 |

---

## 4. 请求追踪

```text
客户端 X-Request-Id
  → RequestIdFilter 读取或生成
  → 写入 MDC 与响应头 X-Request-Id
  → RequestIdResponseAdvice 注入 BaseResult.requestId
  → 日志可按 requestId 检索完整链路
```

## 5. 分页约定

分页接口命名通常为 `/page`（GET 或 POST），请求体/参数一般包含：

| 字段 | 说明 |
|------|------|
| `pageNum` / `pageSize` | 页码与每页数量（具体字段名以各接口为准） |
| `keyword` | 模糊搜索 |
| `tenantId` / `projectId` | 过滤条件 |
| 其他业务过滤字段 | 按模块定义 |

返回结构通常为 MyBatis-Plus `Page<T>` 序列化结果（`records`、`total`、`size`、`current` 等，具体以接口为准）。各接口的准确字段名待逐接口补文档时确认。

## 6. 鉴权白名单

来自 `SecurityConfig`：

| 路径 | 方法 | 说明 |
|------|------|------|
| `/health/check` | GET | 健康检查 |
| `/internal/keycloak/events` | POST | Keycloak SPI 回调（另有 webhook secret 校验） |
| `/pipeline/runs/*/artifacts` | POST | Tekton 集群内回传产物 |
| `/ws/exec/**` | WS | 流水线 Pod 终端 |
| `/ws/container/**` | WS | 容器平台 watch/logs/shell |

管理接口（`/user/users/*`、`/user/tenants/**`、`/user/login-logs/**`、`/user/oper-logs/**`、`/user/integrations/**`、`/user/message-notify/**`、`/user/messages/admin/**`）要求平台 `admin`。

## 7. WebSocket

| 路径 | 用途 |
|------|------|
| `/ws/exec/{pipelineId}/{runId}/{jobId}` | 流水线运行任务 Pod 终端 |
| `/ws/container/watch/**` | 容器资源 watch |
| `/ws/container/logs/**` | 容器日志流 |
| `/ws/container/shell/**` | 容器 Web Shell |

鉴权与消息协议待补充（见 [roles-permissions.md](../01-overview/roles-permissions.md) 待确认项）。

## 8. 接口文档完成度

| 项目 | 状态 |
|------|------|
| Controller/路径清单 | 已完成（67 个 Controller） |
| 请求参数与响应报文 | 待按模块补充 |
| 正常响应示例 | 待补充 |
| 失败响应示例（参数/业务/鉴权） | 待补充 |
| Postman collection | **仓库当前没有任何 `*.postman_collection.json`**，需按 AGENTS.md 分模块补齐 |

模块覆盖矩阵见 [api-module-matrix.md](api-module-matrix.md)。
