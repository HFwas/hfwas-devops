export interface UserProfile {
  id?: number | string
  username: string
  displayName: string
  email?: string
  phone?: string
  role: 'admin' | 'user' | string
  enabled?: number
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

export const AUTH_TOKEN_KEY = 'hfwas.auth.token'
