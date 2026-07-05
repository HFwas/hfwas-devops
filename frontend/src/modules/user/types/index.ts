export interface UserProfile {
  id?: number | string
  username: string
  displayName: string
  email?: string
  phone?: string
  role: 'admin' | 'user' | string
  enabled?: number
  tenantId?: number | string
  tenantCode?: string
  tenantName?: string
  tenantNames?: string[]
  authSource?: 'local' | 'ldap' | string
  connectorName?: string
}

export interface TenantMember {
  id?: number | string
  tenantId?: number | string
  userId?: number | string
  username: string
  displayName: string
  email?: string
  tenantRole: 'tenant_admin' | 'member' | string
  status?: number
  joinTime?: string
}

export interface PlatformUserOption {
  id: number | string
  username: string
  displayName: string
  email?: string
}

export interface Tenant {
  id?: number | string
  code: string
  name: string
  contactName?: string
  contactPhone?: string
  status?: number
  remark?: string
  userCount?: number
  projectCount?: number
  createTime?: string
  updateTime?: string
}

export interface TenantOption {
  id: number | string
  code: string
  name: string
}

export interface LoginResponse {
  token: string
  user: UserProfile
}

export interface UserSessionStats {
  onlineCount: number
  idleCount: number
  totalActive: number
}

export interface UserSession {
  id: number | string
  userId: number | string
  username: string
  displayName: string
  role: string
  loginIp: string
  clientInfo: string
  userAgent?: string
  loginTime: string
  lastActiveTime: string
  expireTime: string
  onlineStatus: 'online' | 'idle' | string
  current?: boolean
}

export interface LoginLog {
  id: number | string
  userId?: number | string
  username: string
  displayName?: string
  action: 'login_success' | 'login_fail' | 'logout' | string
  loginIp: string
  clientInfo: string
  userAgent?: string
  failReason?: string
  createTime: string
}

export interface OperLog {
  id: number | string
  userId?: number | string
  username?: string
  displayName?: string
  module: string
  action: string
  bizType?: string
  bizId?: string
  summary: string
  status: string
  failReason?: string
  requestIp: string
  clientInfo: string
  userAgent?: string
  extraJson?: string
  createTime: string
}

export const AUTH_TOKEN_KEY = 'hfwas.auth.token'
export const TENANT_ID_KEY = 'hfwas.auth.tenantId'
export const TENANT_NAME_KEY = 'hfwas.auth.tenantName'

export interface IdentityConnectorType {
  type: string
  label: string
  description?: string
}

export interface LdapConnectorConfig {
  url: string
  baseDn: string
  bindDn: string
  bindPassword: string
  userFilter?: string
  usernameAttribute?: string
  displayNameAttribute?: string
  emailAttribute?: string
  phoneAttribute?: string
  externalIdAttribute?: string
}

export interface IdentityConnector {
  id?: number | string
  name: string
  type: string
  typeLabel?: string
  configJson?: string
  enabled?: number
  defaultTenantId?: number | string
  defaultTenantName?: string
  autoCreateMember?: number
  lastSyncTime?: string
  lastSyncStatus?: string
  lastSyncMessage?: string
  lastSyncCount?: number
  createTime?: string
}

export interface ConnectorTestResult {
  success: boolean
  message: string
  sampleCount?: number
}

export interface ConnectorSyncResult {
  success: boolean
  message: string
  fetched: number
  created: number
  updated: number
  skipped: number
  disabled: number
}

export interface NotifyChannel {
  id?: number | string
  channel: 'site' | 'dingtalk' | 'feishu' | string
  channelLabel?: string
  enabled?: number
  configJson?: string
  remark?: string
  updateTime?: string
}

export interface WebhookChannelConfig {
  webhookUrl: string
  secret?: string
}

export interface NotifyTestResult {
  success: boolean
  message: string
}

export interface UserMessage {
  id?: number | string
  userId?: number | string
  username?: string
  displayName?: string
  tenantId?: number | string
  tenantName?: string
  category: 'system' | 'operation' | 'announcement' | string
  categoryLabel?: string
  title: string
  content?: string
  readFlag?: number
  senderId?: number | string
  senderName?: string
  bizType?: string
  bizId?: string
  linkUrl?: string
  createTime?: string
  readTime?: string
}
