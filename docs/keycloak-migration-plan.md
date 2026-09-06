# 迁移计划：自研 JWT 认证 → Keycloak OIDC 统一认证

## 背景

当前项目使用自研 JWT（`JwtTokenService` + `JwtAuthFilter` + `UserAuthController`）。`docker-compose.kong.yml` 已引入 Keycloak 26，代码尚未对接。本计划把**认证 / 会话 / 密码**全部交给 Keycloak，后端只做 OAuth2 Resource Server；本地库只保留用户资料、租户成员，以及从 Keycloak 事件写入的登录日志。

**绿野、本地开发、无存量用户。** 直接改 schema / API / 前端类型；本地库可删可重建。不写旧 JWT 兼容，不迁旧登录日志行，不保留 `AuthService.login()` 写入路径。

## 架构概览

```
浏览器 ↔ keycloak-js ↔ Keycloak (OIDC, 经 Kong /auth)
              ↓ Bearer access token
Spring Security oauth2ResourceServer 验签
              ↓
KeycloakUserProvisioningService（sub → SysUser.externalId，必要时建档）
              ↓
AuthUserPrincipal（角色来自 SysUser.role）
              ↓
TenantContextFilter（X-Tenant-Id 覆盖当前租户）
              ↓
UserContextHolder（从 SecurityContext 读取）

登录审计（独立通道，不走用户 JWT）:
Keycloak LOGIN / LOGIN_ERROR / LOGOUT
        ↓ Event Listener SPI（HTTP webhook）
POST http://host.docker.internal:8089/internal/keycloak/events
        ↓ 共享密钥校验
LoginLogService.ingest → sys_login_log → 现有 LoginLogView
```

对外统一入口：`http://localhost:8000`（Kong）。Keycloak 的 issuer、keycloak-js `url`、redirect 均走 `http://localhost:8000/auth`。直连 `http://localhost:8081` 只给 admin console。

## 职责划分（权威源）

| 职责 | 权威系统 | 本地还留什么 |
|------|----------|--------------|
| 认证 / 会话 / 密码 / MFA | Keycloak | 不签发 JWT，删除 `SysUserSession` |
| 平台角色 `admin` \| `user` | `SysUser.role` | `SecurityConfig.hasRole` 读 `AuthUserPrincipal`；不映射 realm roles |
| 当前租户 | 请求头 `X-Tenant-Id` + `TenantContextFilter` | 不写入 access token；禁止改 `UserContextHolder` |
| 用户资料与成员 | `SysUser` + `sys_tenant_member` | `sub` → `externalId`；首次出现时建档并加入 default 租户 |
| 登录审计 | Keycloak 用户事件 | `sys_login_log` 只收 webhook，不再由业务接口写入 |
| LDAP / 企业目录 | 本轮不接 | 连接器仍可同步资料，不能登录 |

## 关键设计决策

1. **用户映射**：`jwt.sub` → `SysUser.externalId`，`authSource = "keycloak"`。不用 `preferred_username` 当主键。建档放 `KeycloakUserProvisioningService`，JWT converter 只查不写。
2. **租户**：JWT 不带 `tenantId`。前端继续带 `X-Tenant-Id`。`switchTenant()` 只校验成员并返回 `UserProfile`，不重签 token，**不记登录日志**。
3. **角色**：只看 `SysUser.role`。
4. **会话**：交给 Keycloak。删除在线会话表 / API / 管理页。
5. **登录日志（本轮做完）**：Keycloak Event Listener → 内部 webhook → `LoginLogService`。管理页和查询 API 保留。不在 `/me`、路由守卫、`logout()` 前端回调里假造记录。
6. **PasswordEncoder**：保留。`sys_user.password` 直接改为可空；Keycloak 用户不写密码。
7. **本地用户管理**：不创建可登录账号。新建登录用户去 Keycloak。
8. **单一 issuer**：`http://localhost:8000/auth/realms/hfwas-devops`。
9. **前端登录只留一条路径**：`init({ onLoad: 'check-sso' })` + 守卫未登录时 `keycloak.login()`。`LoginView` 只做过渡页。

## 登录日志：从 0 实现

密码错误、锁定、登出都发生在 Keycloak，请求到不了 Spring。因此写入源只能是 Keycloak 事件。

### 事件映射

| Keycloak `type` | `sys_login_log.action` | 说明 |
|-----------------|------------------------|------|
| `LOGIN` | `login_success` | 按 `userId`（即 `sub`）查 `externalId`；尚未建档则 `userId` 为空，只记 username |
| `LOGIN_ERROR` | `login_fail` | 本地往往没有用户；`failReason` 用 `error`（如 `invalid_user_credentials`） |
| `LOGOUT` | `logout` | 按 `sub` 对齐本地用户 |
| `REFRESH_TOKEN` / `CODE_TO_TOKEN` / 其它 | **丢弃** | 刷新不是一次登录 |

`ip` ← `event.ipAddress`；UA / `clientInfo` ← `details.user_agent`（没有则记 `clientId`）。

同一 `event.id` 只落一行（表上 `kc_event_id` 唯一）。Listener 重试不会双写。

### 表结构（直接定义，不迁旧列）

`sys_login_log` 按下面重建（`UserSchemaMigration` / `user-schema.sql` 写最终形态；本地删库即可）：

| 列 | 说明 |
|----|------|
| `id` | 本地主键 |
| `kc_event_id` | Keycloak event id，UNIQUE |
| `user_id` | 本地 `SysUser.id`，可空 |
| `kc_user_id` | Keycloak `sub`，可空（失败时可能没有） |
| `username` / `display_name` | 展示用 |
| `action` | `login_success` \| `login_fail` \| `logout` |
| `login_ip` / `user_agent` / `client_info` | 客户端 |
| `fail_reason` | 仅失败 |
| `create_time` | 事件时间（用 Keycloak `time`，不是入库墙钟） |

删掉只为旧 JWT 登录服务的写入方法：`recordLoginSuccess` / `recordLoginFail` / `recordLogout`。查询 `page()` 与 `LoginLogView` 字段对齐后保留。

### 内部接入

1. **SPI JAR**（`keycloak/http-event-listener/`，独立小模块，不进 Spring 应用）：实现 `EventListenerProvider`，只转发 `LOGIN` / `LOGIN_ERROR` / `LOGOUT`。POST JSON 到 `KEYCLOAK_HTTP_LISTENER_URL`，头带 `X-Webhook-Secret`。
2. **挂载**：compose 把 JAR 放到 `/opt/keycloak/providers/`，realm 的 `eventsListeners` 含该 id（例如 `hfwas-http`），打开 User Events。
3. **后端**：`POST /internal/keycloak/events`，`SecurityConfig` permitAll，Controller 校验密钥（`keycloak.webhook-secret`）。Keycloak 容器走 `http://host.docker.internal:8089`，不经 Kong。
4. **ingest**：校验 type → 幂等插入 → 能匹配 `externalId` 则填 `user_id`。LOGIN 事件**可以**顺便调用 provisioning（用户尚未访问 `/me` 时管理页也能看到 userId）；converter 仍然只查不写。

不要让 Listener 写 Keycloak 自带 H2，也不要轮询 Admin Events API。

## 修改步骤

### Step 0: Keycloak 主机名与 realm

**修改:** `docker-compose.kong.yml`

- 删除 `KC_PROXY: edge`
- 使用：

```yaml
KC_HTTP_RELATIVE_PATH: /auth
KC_HOSTNAME: http://localhost:8000
KC_HOSTNAME_ADMIN: http://localhost:8081
KC_HOSTNAME_STRICT: "false"
KC_PROXY_HEADERS: xforwarded
KEYCLOAK_HTTP_LISTENER_URL: http://host.docker.internal:8089/internal/keycloak/events
KEYCLOAK_HTTP_LISTENER_SECRET: ${KEYCLOAK_WEBHOOK_SECRET:-dev-keycloak-webhook}
```

- `command: start-dev --import-realm`
- volume：`./keycloak/hfwas-devops-realm.json` → `/opt/keycloak/data/import/`
- volume：SPI JAR → `/opt/keycloak/providers/`
- Kong 对 `/auth` 转发 `X-Forwarded-Proto` / `Host`（缺则 issuer 会漂）

**新增:** `keycloak/hfwas-devops-realm.json`

- realm `hfwas-devops`
- client `hfwas-devops-web`：public + Standard flow + PKCE S256，关闭 Direct Access Grants
- redirect / web origin / post-logout：`http://localhost:8000`、`http://localhost:5173`
- 测试用户 `admin`（仅本地开发密码）
- `eventsEnabled: true`，保存 `LOGIN`、`LOGIN_ERROR`、`LOGOUT`
- `eventsListeners`: `["jboss-logging", "hfwas-http"]`
- 无 `tenantId` / `role` mapper

禁止验证步骤里手点创建 realm。

### Step 1: 后端依赖 & 配置

- `backend/server/pom.xml` — `spring-boot-starter-oauth2-resource-server`
- `backend/user-core/pom.xml` — 删除 `jjwt-*`
- `application.yml`：

```yaml
spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://localhost:8000/auth/realms/hfwas-devops

keycloak:
  webhook-secret: ${KEYCLOAK_WEBHOOK_SECRET:dev-keycloak-webhook}
```

删除 `user.jwt.*`。converter 放 `server` 模块。

### Step 2: SecurityConfig

- 去掉 `JwtAuthFilter`；**保留** `TenantContextFilter`
- 顺序：`RequestIdFilter` → OAuth2 JWT → `TenantContextFilter`
- 删除 `/user/auth/login` permitAll、删除 `/user/sessions/**`
- **保留** `/user/login-logs/**` 的 `hasRole("admin")`
- `/internal/keycloak/events` permitAll（密钥在 Controller）
- 其余规则、CORS、`/health/check` 不变

### Step 3: 用户查找与建档

**新增 server:** `KeycloakJwtAuthConverter` — `sub` 查本地用户，组装 `AuthUserPrincipal(user, loginTenantId=null)`。只查不写；没有用户则 401（正常情况 LOGIN webhook 已建档；竞态下 `/me` 可再触发一次 provisioning）。

**新增 user-core:** `KeycloakUserProvisioningService`

- 按 `externalId = sub` 查找
- 没有则插入：`authSource=keycloak`，`password=null`，username 用 `preferred_username`
- 角色：username 为 `admin` 或库中尚无用户 → `admin`，否则 `user`
- 加入 default 租户
- `externalId` 唯一索引；先查后插，撞唯一键再查

**修改:** `AuthUserDetailsService` 去掉 `UserDetailsService` / `loadUserByUsername()`，保留 `loadById()`。`UserContextHolder`、`AuthUserPrincipal`、`TenantContextFilter` 不改。

### Step 4: 删除自研 JWT 与会话

删除：`JwtTokenService`、`JwtAuthFilter`、`IssuedToken`、`UserJwtProperties`、`SysUserSession*`、`UserSessionService`、会话 model、`UserSessionController`。

### Step 5: UserAuthController / AuthService

- 删除 `login()` / `logout()` 及 JWT、session、**旧** `loginLogService.record*` 依赖
- 保留 `me()` / `myTenants()`
- `switchTenant()` 返回 `UserProfile`，不写登录日志
- `save()` 不再要求密码
- 删除 `LoginRequest`；`LoginResponse` 无引用则删

### Step 6: 登录日志接入（替换旧写入，不是删功能）

**新增 `keycloak/http-event-listener/`**

- Keycloak 26 SPI，打包 `hfwas-keycloak-http-listener.jar`
- 环境变量读 URL / secret
- 只 POST 三种事件，body 含 `id, type, time, userId, ipAddress, details, error`

**新增 server:** `KeycloakEventController`（`/internal/keycloak/events`）

**修改 user-core:** `LoginLogService`

- 新增 `ingest(KeycloakAuthEvent)`：过滤、幂等、映射、插入
- 删除 `recordLoginSuccess` / `recordLoginFail` / `recordLogout`
- `page()` 保留，VO 可带上 `kcUserId`（可选，前端暂不展示也行）

**前端登录日志页保留。** 不改交互，除非 VO 字段改名。

### Step 7: AutoConfiguration 与 schema

- 去掉 `UserJwtProperties`
- **保留** `PasswordEncoder`
- 删除 `sys_user_session` / `ensureSessionTable()`
- **重写** `sys_login_log` 为上一节最终形态（不要 `ALTER` 旧表；本地重建）
- `sys_user.password` 可空；`external_id` UNIQUE
- `seedAdminUser()`：不插入 `admin/admin123`。default 租户仍 seed。平台管理员由 Keycloak `admin` 首次事件 / 首次登录建档

### Step 8: 前端 keycloak-js

- 依赖 `keycloak-js`；`frontend/src/shared/keycloak/index.ts`：`url=http://localhost:8000/auth`，`realm=hfwas-devops`，`clientId=hfwas-devops-web`
- `init({ onLoad: 'check-sso', pkceMethod: 'S256' })`；`getToken()` 先 `updateToken(30)`
- `main.ts` 先 init 再 mount
- `auth.ts`：token 来自 keycloak-js；`switchTenant` 不 `setToken`
- `request.ts`：Authorization 用 `getToken()`；401 → `keycloak.login()`；`X-Tenant-Id` 不变

直连 `:5173` 时 keycloak-js 仍打 `:8000/auth`。开发同时起 Kong。不必给 Vite 加 `/auth` 代理。

### Step 9: 登录页与路由守卫

- `LoginView` 去掉账密，只做跳转过渡，`meta.public`
- 守卫：未登录 `keycloak.login({ redirectUri })`；已登录无 user 则 `fetchMe()`
- 不要 `login-required` 再叠守卫 / 登录页各 `login()` 一次

### Step 10: 只删前端会话，保留登录日志

- 删除 `/user/sessions`、`userSessionApi`、`UserSessionView.vue`、相关 types
- `UserLayout` 去掉「在线会话」，**保留「登录日志」**
- 保留 `loginLogApi`、`LoginLogView`、`/user/login-logs`

### Step 11: 用户管理展示

- 新建用户不必填密码；`authSource=keycloak` 禁用改密
- 账号来源文案：「统一认证」
- 身份连接器：只同步，不能登录

## 明确会改到、但行为保持的模块

| 模块 | 说明 |
|------|------|
| `TenantContextFilter` | 留在链上 |
| 租户管理 | 切换改为校验 + profile |
| `pm-core` SPI | 不改 |
| 操作日志 | `@OperLog` 不改；登录/登出两条随接口删除 |
| 站内信 / 通知渠道 | 不改 |
| 登录日志查询页 | 保留；数据改由 webhook 写入 |
| 身份连接器 | 实现不改；登录语义降级为只同步 |

## 验证步骤

1. compose 起来后，`issuer` 为 `http://localhost:8000/auth/realms/hfwas-devops`
2. 只访问 `http://localhost:8000`，未登录跳 Keycloak，回来落在原路径
3. 首次 `admin` 登录后本地有 `SysUser`（`externalId=sub`，`role=admin`，default 成员）
4. 管理端「登录日志」出现一条 `login_success`（有 IP；能对上 userId）
5. Keycloak 用错误密码登录，出现 `login_fail`，应用进程没有任何 `/user/auth/login`
6. 登出后再进，多一条 `logout` 和一条新的 `login_success`；同一操作刷新页面不双写
7. 切换租户不新增登录日志，token 不变，请求带新 `X-Tenant-Id`
8. `updateToken` 续期不写登录日志、不整页 401
9. 无「在线会话」菜单；业务 `@OperLog` 仍能记
10. webhook 密钥错误时不入库（401/403）

---

> 本文档对应计划：`sleepy-sniffing-stearns`
> 修订：绿野从 0 实现；登录日志改为 Keycloak Event webhook，不再删除管理页。
