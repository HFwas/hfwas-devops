# 用户 / 租户接口骨架

> 日期：2026-10-17
> 版本：v0.1
> 状态：骨架（路径与用途已核对；请求/响应/失败示例待补）
> 数据来源：`backend/server/src/main/java/com/hfwas/devops/user/api/controller/`、`SecurityConfig.java`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：10 个 Controller 的方法、路径与鉴权 |

---

基础路径：`/user`。认证由 Keycloak 完成，**无** `/user/auth/login` 接口。

## 认证与当前用户

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| GET | `/user/auth/me` | 当前用户信息与角色 | 已认证 |
| GET | `/user/auth/my-tenants` | 我可访问的租户 | 已认证 |
| POST | `/user/auth/switch-tenant` | 切换默认租户 | 已认证 |

## 用户管理

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| GET | `/user/users/options` | 用户下拉选项 | 已认证 |
| POST | `/user/users/page` | 用户分页 | `admin` |
| POST | `/user/users/save` | 创建/更新用户 | `admin` |
| POST | `/user/users/delete` | 删除用户 | `admin` |

## 租户与成员

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| POST | `/user/tenants/page` | 租户分页 | `admin` |
| GET | `/user/tenants/options` | 租户选项 | `admin` |
| GET | `/user/tenants/{id}` | 租户详情 | `admin` |
| POST | `/user/tenants/save` | 创建/更新租户 | `admin` |
| POST | `/user/tenants/delete` | 删除租户 | `admin` |
| POST | `/user/tenants/{tenantId}/members/page` | 成员分页 | `admin` |
| GET | `/user/tenants/{tenantId}/members/available` | 可加入用户 | `admin` |
| POST | `/user/tenants/{tenantId}/members/add` | 添加成员 | `admin` |
| POST | `/user/tenants/{tenantId}/members/save` | 修改成员角色 | `admin` |
| POST | `/user/tenants/{tenantId}/members/remove` | 移除成员 | `admin` |

## 日志

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| POST | `/user/login-logs/page` | 登录日志分页 | `admin` |
| POST | `/user/oper-logs/page` | 操作日志分页 | `admin` |

## 站内信

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| GET | `/user/messages/unread-count` | 未读数 | 已认证 |
| GET | `/user/messages/recent` | 最近消息 | 已认证 |
| POST | `/user/messages/page` | 我的消息分页 | 已认证 |
| GET | `/user/messages/{id}` | 消息详情 | 已认证 |
| POST | `/user/messages/mark-read` | 标记单条已读 | 已认证 |
| POST | `/user/messages/mark-read-batch` | 批量标记已读 | 已认证 |
| POST | `/user/messages/mark-all-read` | 全部标记已读 | 已认证 |
| POST | `/user/messages/delete` | 删除消息 | 已认证 |
| POST | `/user/messages/admin/page` | 管理端消息分页 | `admin` |
| POST | `/user/messages/admin/send` | 管理员发送消息 | `admin` |

## 通知渠道

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| GET | `/user/message-notify/channels` | 渠道列表 | `admin` |
| POST | `/user/message-notify/save` | 保存渠道配置 | `admin` |
| POST | `/user/message-notify/test` | 测试渠道 | `admin` |

## 身份连接器

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| GET | `/user/integrations/types` | 支持的连接器类型 | `admin` |
| POST | `/user/integrations/page` | 连接器分页 | `admin` |
| GET | `/user/integrations/{id}` | 连接器详情 | `admin` |
| POST | `/user/integrations/save` | 保存连接器 | `admin` |
| POST | `/user/integrations/delete` | 删除连接器 | `admin` |
| POST | `/user/integrations/test-connection` | 测试连接 | `admin` |
| POST | `/user/integrations/sync` | 同步用户 | `admin` |

## 内部回调

| 方法 | 路径 | 用途 | 鉴权 |
|------|------|------|------|
| POST | `/internal/keycloak/events` | Keycloak 登录事件回调 | 白名单（webhook secret） |

## 待补

1. 每个接口的请求参数与响应结构。
2. 失败示例：401/403、用户/租户不存在、重复、默认租户保护等。
3. Postman collection：`docs/api/user/user-api.postman_collection.json`。
4. `/internal/keycloak/events` 的 webhook secret 头名称与校验逻辑。
