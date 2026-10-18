# 角色与权限矩阵

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成
> 数据来源：`backend/server/src/main/java/com/hfwas/devops/config/SecurityConfig.java`、`backend/user-core/.../security/`、`AuthService.java`、`TenantMemberService.java`、`SysTenantMember.java`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：平台/租户角色、鉴权规则与模块权限矩阵 |

---

## 1. 认证方式

| 项目 | 说明 |
|------|------|
| 协议 | Spring Security OAuth2 Resource Server，Keycloak 签发 JWT |
| 请求头 | `Authorization: Bearer <access_token>` |
| 用户落库 | `KeycloakJwtAuthConverter` 按 JWT 的 `sub` / `preferred_username` / `email` 自动创建或更新 `sys_user` |
| 登录事件 | Keycloak SPI 回调 `POST /internal/keycloak/events` 写入登录日志 |
| 会话 | 无状态（`SessionCreationPolicy.STATELESS`） |
| CORS | 允许 `http://localhost:*`、`http://127.0.0.1:*`，方法 GET/POST/PUT/DELETE/OPTIONS |

## 2. 平台角色

| 角色 | 代码值 | 来源 | 能力 |
|------|--------|------|------|
| 平台管理员 | `admin` | `sys_user.role` | 用户管理、租户管理、登录/操作日志、身份集成、通知渠道、管理员站内信 |
| 普通用户 | `user` | `sys_user.role` | 使用已认证的业务模块；只能查看自己的消息与所在租户数据 |

首个 Keycloak 登录用户或用户名为 `admin` 的用户会被初始化为 `admin`（`KeycloakUserProvisioningService`）。

## 3. 租户角色

| 角色 | 代码值 | 说明 |
|------|--------|------|
| 租户管理员 | `tenant_admin` | 租户成员管理、租户内业务数据管理 |
| 租户成员 | `member` | 租户内业务数据使用 |

租户角色仅用于租户成员关系（`sys_tenant_member.tenant_role`）。平台 `admin` 可访问全部租户；普通用户必须是目标租户的活跃成员。

## 4. 租户上下文解析

解析优先级（`TenantContextService` / `TenantContextFilter`）：

1. 请求头 `X-Tenant-Id`（需为数字且用户有访问权）；
2. JWT 中的租户声明（如存在）；
3. 用户默认租户；
4. 平台 `admin` 可访问任意租户。

未登录或租户上下文缺失返回 `11010 TENANT_CONTEXT_MISSING`；越权访问返回 `11005 TENANT_FORBIDDEN`。

## 5. 接口鉴权规则

来自 `SecurityConfig.securityFilterChain`：

| 路径 | 规则 |
|------|------|
| `/health/check` | 免认证 |
| `/internal/keycloak/events` | 免认证（由 Keycloak SPI 调用，另有 webhook secret 校验） |
| `POST /pipeline/runs/*/artifacts` | 免认证（Tekton 集群内回传 SBOM） |
| `/ws/exec/**`、`/ws/container/**` | 免认证（WebSocket 由前端携带 token 或集群内访问，待确认具体校验） |
| `/container/**` | 已认证 |
| `/user/users/page`、`/user/users/save`、`/user/users/delete` | `ROLE_admin` |
| `/user/login-logs/**` | `ROLE_admin` |
| `/user/oper-logs/**` | `ROLE_admin` |
| `/user/integrations/**` | `ROLE_admin` |
| `/user/messages/admin/**` | `ROLE_admin` |
| `/user/message-notify/**` | `ROLE_admin` |
| `/user/tenants/**` | `ROLE_admin` |
| 其他 | 已认证 |

> 注意：PM、流水线、API 测试、Docgen、File Parser、Image 的接口目前只要求“已认证”，模块内细粒度权限（如项目成员、流水线所有者）尚未在网关层强制，部分规则由 Service 层按租户/项目校验。

## 6. 模块 × 角色能力矩阵

图例：✅ 可用；⚠️ 仅部分操作可用/受租户或项目成员限制；❌ 不可用；🔒 平台管理员专属。

| 模块 / 能力 | 平台 admin | 普通 user（租户成员） | 说明 |
|-------------|-----------|----------------------|------|
| 用户管理（增删改查） | 🔒 | ❌ | `/user/users/*` 受 `hasRole("admin")` 保护 |
| 租户管理 / 成员管理 | 🔒 | ❌ | `/user/tenants/**` |
| 登录日志 / 操作日志 | 🔒 | ❌ | 仅平台管理员 |
| 身份连接器 / 通知渠道 | 🔒 | ❌ | 仅平台管理员 |
| 站内信（个人） | ✅ | ✅ | 只能操作自己的消息 |
| 站内信（群发/管理） | 🔒 | ❌ | `/user/messages/admin/**` |
| 租户切换 | ✅ | ✅ | 仅可切换到有权限的租户 |
| 项目管理 | ✅ | ⚠️ | 按项目成员/租户隔离，Service 层校验 |
| 流水线 | ✅ | ⚠️ | 按租户隔离；凭证/运行受租户约束 |
| 容器平台 | ✅ | ⚠️ | 接口仅要求认证；集群按 `tenant_id` 过滤 |
| API 测试 | ✅ | ⚠️ | 按 `project_id` 隔离，无 `tenant_id` |
| Docgen / File Parser / Image | ✅ | ✅ | 已认证即可用；图片历史按用户过滤 |
| 制品仓库 / 安全审计 | ❌ | ❌ | 产品占位（comingSoon），未上线 |
| Argo 工作流 | ✅ | ⚠️ | 后端接口存在，前端未注册路由 |

## 7. 数据隔离规则

| 模块 | 隔离键 | 说明 |
|------|--------|------|
| 用户 / 租户 | `tenant_id` | 成员关系表隔离 |
| PM | `tenant_id` + `project_id` | 项目属于租户，事项属于项目 |
| 流水线 | `tenant_id` | 流水线、凭证、任务类型、集群均带租户 |
| 容器 | `tenant_id` | 集群、镜像仓库、Helm 仓库 |
| API 测试 | `project_id` | **没有** `tenant_id`，跨租户隔离依赖项目归属 |
| 图片处理 | `user_id` + `tenant_id` | 历史记录按用户与租户过滤 |
| Docgen / File Parser | 无持久化业务表 | 以请求上下文为准 |

## 8. 待确认

1. WebSocket 终端（`/ws/exec/**`、`/ws/container/**`）的实际鉴权方式与 token 传递机制。
2. 项目成员角色（`pm_project_member.role`）的取值与对应能力尚未在代码中集中定义。
3. 流水线/容器模块是否需要引入更细的 RBAC（当前仅租户级隔离）。
