# 用户 / 租户数据字典

> 日期：2026-10-17
> 版本：v0.1
> 状态：已完成（表级）
> 数据来源：`backend/user-core/src/main/resources/db/user-schema.sql`、`UserSchemaMigration.java`、`backend/server/.../user/`

### 变更记录

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| v0.1 | 2026-10-17 | 初版：8 张 sys_* 表字段与索引 |

---

共 8 张表，权威 schema 为 `user-schema.sql`；embedded 模式下由 `UserSchemaMigration` 用 JdbcTemplate 建表（**不读取该 SQL 文件**），external 模式读取 `01-user-schema.sql`。

## sys_tenant

租户主表。

| 字段 | 说明 |
|------|------|
| `id` | 主键，自增 |
| `code` | 租户编码，唯一，小写字母开头 |
| `name` | 租户名称 |
| `contact_name` / `contact_phone` | 联系人/电话 |
| `status` | 状态（启用/停用） |
| `remark` | 备注 |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_sys_tenant_code`、`idx_sys_tenant_status`。

## sys_user

平台用户。

| 字段 | 说明 |
|------|------|
| `id` | 主键，自增 |
| `username` | 用户名，唯一 |
| `password` | 本地密码（Keycloak 用户不使用） |
| `display_name` | 显示名 |
| `email` / `phone` | 联系方式 |
| `role` | 平台角色：`admin` / `user` |
| `enabled` | 是否启用 |
| `auth_source` | 认证来源（本地/Keycloak/LDAP 等） |
| `external_id` | 外部身份 ID（Keycloak `sub`） |
| `connector_id` | 身份连接器 ID |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_sys_user_external_id`；`pm-schema.sql` 中另有 `idx_sys_user_username`（重复表定义，见总览）。

## sys_tenant_member

租户成员关系。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `tenant_id` | 租户 |
| `user_id` | 用户 |
| `tenant_role` | `tenant_admin` / `member` |
| `status` | 成员状态（活跃/禁用） |
| `join_time` | 加入时间 |
| `del_flag` | 逻辑删除 |

索引：`idx_sys_tenant_member`、`idx_sys_tenant_member_user`。

## sys_login_log

登录日志，由 Keycloak 事件回调写入。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `kc_event_id` | Keycloak 事件 ID |
| `user_id` / `kc_user_id` / `username` / `display_name` | 用户标识 |
| `action` | 登录/登出等 |
| `login_ip` / `user_agent` / `client_info` | 来源信息 |
| `fail_reason` | 失败原因 |
| `create_time` | 时间 |

索引：`idx_sys_login_log_time`、`idx_sys_login_log_user`、`idx_sys_login_log_action`。

## sys_oper_log

操作日志，由 `@OperLog` 切面写入。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `user_id` / `username` / `display_name` | 操作人 |
| `module` / `action` | 模块与动作 |
| `biz_type` / `biz_id` | 业务对象 |
| `summary` | 摘要 |
| `status` / `fail_reason` | 结果 |
| `request_ip` / `user_agent` / `client_info` | 来源 |
| `extra_json` | 扩展信息 |
| `create_time` | 时间 |

索引：`idx_sys_oper_log_time`、`idx_sys_oper_log_module`、`idx_sys_oper_log_user`、`idx_sys_oper_log_action`。

## sys_identity_connector

外部身份连接器（LDAP 等）。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `name` / `type` | 名称与类型 |
| `config_json` | 连接配置 |
| `enabled` | 是否启用 |
| `default_tenant_id` | 新用户默认租户 |
| `auto_create_member` | 是否自动建立成员关系 |
| `last_sync_time` / `last_sync_status` / `last_sync_message` / `last_sync_count` | 最近同步状态 |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_sys_identity_connector_type`。

## sys_user_message

站内信。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `user_id` / `tenant_id` | 接收人与租户 |
| `category` | 消息分类 |
| `title` / `content` | 标题与正文 |
| `read_flag` / `read_time` | 已读状态 |
| `sender_id` / `sender_name` | 发送人 |
| `biz_type` / `biz_id` / `link_url` | 业务关联与跳转 |
| `create_time` / `del_flag` | 审计与逻辑删除 |

索引：`idx_sys_user_message_user`、`idx_sys_user_message_time`。

## sys_notify_channel

通知渠道配置。

| 字段 | 说明 |
|------|------|
| `id` | 主键 |
| `channel` | 渠道类型（邮件/Webhook 等） |
| `enabled` | 是否启用 |
| `config_json` | 渠道配置 |
| `remark` | 备注 |
| `create_time` / `update_time` / `del_flag` | 审计与逻辑删除 |

## 写入方

| 表 | 主要归属 |
|----|----------|
| `sys_tenant` / `sys_tenant_member` | `TenantService`、`TenantMemberService`、`KeycloakUserProvisioningService` |
| `sys_user` | `UserManageService`、`KeycloakUserProvisioningService`、`IdentityConnectorSyncService` |
| `sys_login_log` | `LoginLogService`（Keycloak 事件） |
| `sys_oper_log` | `OperLogService` / `@OperLog` 切面 |
| `sys_identity_connector` | `IdentityConnectorService` |
| `sys_user_message` | `SiteMessageService` |
| `sys_notify_channel` | `NotifyChannelService` |
