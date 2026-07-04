import { get, post } from '@/shared/api/request'
import type { LoginLog, LoginResponse, OperLog, PlatformUserOption, Tenant, TenantMember, TenantOption, UserProfile, UserSession, UserSessionStats, IdentityConnector, IdentityConnectorType, ConnectorTestResult, ConnectorSyncResult, UserMessage, NotifyChannel, NotifyTestResult } from '@/modules/user/types'
import type { PageResult } from '@/shared/types/common'
import { asId } from '@/modules/pm/utils/id'

export const userAuthApi = {
  login: (username: string, password: string) =>
    post<LoginResponse>('/user/auth/login', { username, password }),
  me: () => get<UserProfile>('/user/auth/me'),
  myTenants: () => get<TenantOption[]>('/user/auth/my-tenants'),
  switchTenant: (tenantId: number | string) =>
    post<LoginResponse>('/user/auth/switch-tenant', { tenantId }),
  logout: () => post<void>('/user/auth/logout', {}),
  userOptions: () => get<UserProfile[]>('/user/users/options'),
}

export const userSessionApi = {
  stats: () => get<UserSessionStats>('/user/sessions/stats'),
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string; status?: string }) =>
    post<PageResult<UserSession>>('/user/sessions/page', data),
  revoke: (id: number | string) => post<void>(`/user/sessions/revoke?id=${asId(id)}`, {}),
}

export const loginLogApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string; action?: string }) =>
    post<PageResult<LoginLog>>('/user/login-logs/page', data),
}

export const operLogApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string; module?: string; action?: string }) =>
    post<PageResult<OperLog>>('/user/oper-logs/page', data),
}

export const userManageApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string; tenantId?: number | string }) =>
    post<PageResult<UserProfile>>('/user/users/page', data),
  save: (data: Partial<UserProfile> & { password?: string }) =>
    post<number>('/user/users/save', data),
  delete: (id: number | string) => post<void>(`/user/users/delete?id=${asId(id)}`, {}),
}

export const tenantManageApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string; status?: string }) =>
    post<PageResult<Tenant>>('/user/tenants/page', data),
  options: () => get<Tenant[]>('/user/tenants/options'),
  getById: (id: number | string) => get<Tenant>(`/user/tenants/${asId(id)}`),
  save: (data: Partial<Tenant>) => post<number>('/user/tenants/save', data),
  delete: (id: number | string) => post<void>(`/user/tenants/delete?id=${asId(id)}`, {}),
}

export const tenantMemberApi = {
  page: (tenantId: number | string, data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<TenantMember>>(`/user/tenants/${asId(tenantId)}/members/page`, data),
  available: (tenantId: number | string, keyword?: string) =>
    get<PlatformUserOption[]>(`/user/tenants/${asId(tenantId)}/members/available`, keyword ? { keyword } : undefined),
  add: (tenantId: number | string, data: { userIds: (number | string)[]; tenantRole?: string }) =>
    post<void>(`/user/tenants/${asId(tenantId)}/members/add`, data),
  save: (tenantId: number | string, data: { userId: number | string; tenantRole?: string; status?: number }) =>
    post<void>(`/user/tenants/${asId(tenantId)}/members/save`, data),
  remove: (tenantId: number | string, userId: number | string) =>
    post<void>(`/user/tenants/${asId(tenantId)}/members/remove?userId=${asId(userId)}`, {}),
}

export const identityConnectorApi = {
  types: () => get<IdentityConnectorType[]>('/user/integrations/types'),
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string; type?: string }) =>
    post<PageResult<IdentityConnector>>('/user/integrations/page', data),
  getById: (id: number | string) => get<IdentityConnector>(`/user/integrations/${asId(id)}`),
  save: (data: Partial<IdentityConnector> & { configJson?: string }) =>
    post<number>('/user/integrations/save', data),
  delete: (id: number | string) => post<void>(`/user/integrations/delete?id=${asId(id)}`, {}),
  testConnection: (data: Partial<IdentityConnector> & { configJson?: string }) =>
    post<ConnectorTestResult>('/user/integrations/test-connection', data),
  sync: (id: number | string) => post<ConnectorSyncResult>(`/user/integrations/sync?id=${asId(id)}`, {}),
}

export const messageApi = {
  unreadCount: () => get<number>('/user/messages/unread-count'),
  recent: (limit = 5) => get<UserMessage[]>('/user/messages/recent', { limit }),
  page: (data: { pageNo?: number; pageSize?: number; readFlag?: string; category?: string; keyword?: string }) =>
    post<PageResult<UserMessage>>('/user/messages/page', data),
  detail: (id: number | string) => get<UserMessage>(`/user/messages/${asId(id)}`),
  markRead: (id: number | string) => post<void>(`/user/messages/mark-read?id=${asId(id)}`, {}),
  markAllRead: () => post<void>('/user/messages/mark-all-read', {}),
  markReadBatch: (ids: (number | string)[]) => post<void>('/user/messages/mark-read-batch', ids),
  delete: (id: number | string) => post<void>(`/user/messages/delete?id=${asId(id)}`, {}),
  adminPage: (data: { pageNo?: number; pageSize?: number; userId?: number | string; category?: string; keyword?: string }) =>
    post<PageResult<UserMessage>>('/user/messages/admin/page', data),
  adminSend: (data: {
    targetType: 'all' | 'tenant' | 'users'
    tenantId?: number | string
    userIds?: (number | string)[]
    category?: string
    title: string
    content?: string
    linkUrl?: string
  }) => post<void>('/user/messages/admin/send', data),
}

export const messageNotifyApi = {
  channels: () => get<NotifyChannel[]>('/user/message-notify/channels'),
  save: (data: { channel: string; enabled?: number; configJson?: string; remark?: string }) =>
    post<void>('/user/message-notify/save', data),
  test: (data: { channel: string; enabled?: number; configJson?: string }) =>
    post<NotifyTestResult>('/user/message-notify/test', data),
}
