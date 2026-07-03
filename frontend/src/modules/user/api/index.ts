import { get, post } from '@/shared/api/request'
import type { LoginLog, LoginResponse, UserProfile, UserSession, UserSessionStats } from '@/modules/user/types'
import type { PageResult } from '@/shared/types/common'
import { asId } from '@/modules/pm/utils/id'

export const userAuthApi = {
  login: (username: string, password: string) =>
    post<LoginResponse>('/user/auth/login', { username, password }),
  me: () => get<UserProfile>('/user/auth/me'),
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

export const userManageApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<UserProfile>>('/user/users/page', data),
  save: (data: Partial<UserProfile> & { password?: string }) =>
    post<number>('/user/users/save', data),
  delete: (id: number | string) => post<void>(`/user/users/delete?id=${asId(id)}`, {}),
}
