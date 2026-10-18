# 用户 / 租户中心功能规格

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架
> 数据来源：`backend/server/.../user/api/controller/`、`backend/user-core/`、`frontend/src/router.tsx`、`docs/backend/keycloak-migration-plan.md`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：认证、租户、用户、日志、站内信、身份集成 |

---

## 1. 概述

用户中心承载平台身份与租户体系：Keycloak 负责认证，后端负责用户落库、租户成员关系、平台/租户角色、登录与操作审计、站内信与通知渠道、外部身份连接器。

## 2. 角色与场景

| 角色 | 场景 |
|------|------|
| 平台管理员 | 管理用户、租户、成员、日志、通知渠道、身份集成 |
| 普通用户 | 登录、切换租户、查看/处理自己的站内信 |
| Keycloak | 通过 SPI 回传登录事件 |

## 3. 功能清单

| 功能项 | 说明 | 页面/路由 | 状态 |
|--------|------|-----------|------|
| 登录 | Keycloak 登录后获取 JWT | 外部 Keycloak | 可用 |
| 当前用户 | 返回当前用户与角色 | — | 可用 |
| 我的租户 | 可访问租户列表 | 顶部租户切换 | 可用 |
| 切换租户 | 更新默认租户/上下文 | 顶部租户切换 | 可用 |
| 用户管理 | 分页、选项、创建/编辑、删除 | `/user/accounts` | 可用 |
| 租户管理 | 分页、选项、创建/编辑、删除 | 管理员 | 可用 |
| 租户成员 | 分页、可加入用户、添加、改角色、移除 | 管理员 | 可用 |
| 登录日志 | 分页查询 | 管理员 | 可用 |
| 操作日志 | 分页查询 | 管理员 | 可用 |
| 站内信 | 未读数、最近、分页、详情、已读、删除 | 顶部消息 | 可用 |
| 管理员发送 | 群发/定向发送站内信 | 管理员 | 可用 |
| 通知渠道 | 渠道列表、保存、测试 | 管理员 | 可用 |
| 身份连接器 | 类型、分页、保存、删除、测试连接、同步 | 管理员 | 可用 |
| Keycloak 事件 | 登录事件回调落库 | — | 可用 |

## 4. 核心流程

### 4.1 首次登录

```text
Keycloak 登录成功，前端携带 Bearer token
  → KeycloakJwtAuthConverter 解析 sub/username/email
  → 按 external_id 查找 sys_user
  → 不存在则创建（首个用户或 admin 用户名为 admin）
  → 确保默认租户成员关系（admin → tenant_admin，否则 member）
  → 返回 AuthUserPrincipal，授予 ROLE_<role>
```

### 4.2 租户切换

```text
前端选择租户
  → POST /user/auth/switch-tenant
  → 校验用户是目标租户活跃成员（平台 admin 除外）
  → 更新默认租户
  → 后续请求携带 X-Tenant-Id
```

### 4.3 Keycloak 登录事件

```text
Keycloak SPI 回调 POST /internal/keycloak/events
  → 校验 webhook secret
  → 按事件类型写入 sys_login_log
```

## 5. 业务规则

| 规则 | 说明 |
|------|------|
| 平台角色 | 仅 `admin` / `user` |
| 租户角色 | 仅 `tenant_admin` / `member` |
| 用户名唯一 | `sys_user.username` 唯一 |
| 不能删除自己 | `CANNOT_DELETE_SELF` |
| 默认租户保护 | 默认租户不可停用/删除 |
| 租户删除前置 | 仍有成员或项目时不可删除 |
| 租户编码格式 | 小写字母开头，仅小写字母、数字、下划线、连字符 |
| 密码 | 本地用户由管理员设置；Keycloak 用户不落明文密码 |
| 逻辑删除 | `del_flag` |

## 6. 异常与边界

| 场景 | 错误码 |
|------|--------|
| 未登录/Token 过期 | 11001 UNAUTHORIZED（HTTP 401） |
| 非管理员访问管理接口 | 11002 FORBIDDEN（HTTP 403） |
| 用户不存在 | 12001 USER_NOT_FOUND |
| 用户名已存在 | 12005 USERNAME_EXISTS |
| 删除当前用户 | 12008 CANNOT_DELETE_SELF |
| 租户不存在/停用 | 13001 / 13002 |
| 租户编码重复 | 13003 TENANT_CODE_EXISTS |
| 默认租户删除 | 13005 DEFAULT_TENANT_DELETE_FORBIDDEN |
| 租户仍有成员/项目 | 13006 / 13007 |
| 租户 ID 无效/缺失 | 11006 / 11007 |

完整错误码见 [docs/backend/error-code-design.md](../../../backend/error-code-design.md)。

## 7. 接口与数据

- 接口：`/user/auth/*`、`/user/users/*`、`/user/tenants/*`、`/user/login-logs/*`、`/user/oper-logs/*`、`/user/messages/*`、`/user/message-notify/*`、`/user/integrations/*`、`/internal/keycloak/events`
- 详见 [04-api/modules/user.md](../../04-api/modules/user.md)
- 数据表：`sys_tenant`、`sys_user`、`sys_tenant_member`、`sys_login_log`、`sys_oper_log`、`sys_identity_connector`、`sys_user_message`、`sys_notify_channel`
- 详见 [03-database/tables/user.md](../../03-database/tables/user.md)

## 8. 验收标准

| 编号 | 验收项 |
|------|--------|
| AC-USER-01 | 新用户首次登录自动落库并获得默认租户成员关系 |
| AC-USER-02 | 普通用户无法访问用户/租户/日志管理接口 |
| AC-USER-03 | 切换租户后接口返回目标租户数据 |
| AC-USER-04 | 默认租户不可停用/删除 |
| AC-USER-05 | 登录事件能写入登录日志 |
| AC-USER-06 | 站内信未读数与已读状态正确 |

## 9. 待确认

1. Keycloak webhook secret 的配置项与环境变量名。
2. 身份连接器支持的类型全集（由 `/user/integrations/types` 返回）。
3. 站内信通知渠道实际可用的通道（邮件/Webhook 等）。
