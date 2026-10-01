import { create } from 'zustand'
import { pmProjectApi } from '@/modules/pm/api'
import type { ProjectAccessContext } from '@/modules/pm/types'
import { userAuthApi } from '@/modules/user/api'
import type { TenantOption, UserProfile } from '@/modules/user/types'
import { TENANT_ID_KEY, TENANT_NAME_KEY } from '@/modules/user/types'
import {
  appRedirectUri,
  getKeycloak,
  getToken,
  isAuthenticated,
  login as keycloakLogin,
  logout as keycloakLogout,
} from '@/shared/keycloak'

function readStoredTenantId(): string | null {
  return localStorage.getItem(TENANT_ID_KEY)
}

function readStoredTenantName(): string | null {
  return localStorage.getItem(TENANT_NAME_KEY)
}

interface AuthState {
  token: string | null
  user: UserProfile | null
  myTenants: TenantOption[]
  loading: boolean
  switchingTenant: boolean
  tenantVersion: number
  activeTenantId: string | null
  activeTenantName: string | null
  isLoggedIn: () => boolean
  isAdmin: () => boolean
  syncToken: () => Promise<void>
  login: () => Promise<void>
  fetchMyTenants: () => Promise<TenantOption[]>
  switchTenant: (tenantId: number | string) => Promise<UserProfile | null>
  ensureTenant: (tenantId: number | string) => Promise<boolean>
  ensureTenantForProject: (projectId: number | string) => Promise<ProjectAccessContext>
  fetchMe: () => Promise<UserProfile | null>
  logout: () => Promise<void>
}

export const useAuthStore = create<AuthState>((set, get) => {
  function resolveTenantName(tenantId: number | string, fallback?: string | null) {
    const fromList = get().myTenants.find((t) => String(t.id) === String(tenantId))
    return fromList?.name ?? fallback ?? null
  }

  function applyActiveTenant(tenantId: number | string, tenantName?: string | null) {
    const id = String(tenantId)
    const name = tenantName ?? resolveTenantName(tenantId)
    if (name) {
      localStorage.setItem(TENANT_NAME_KEY, name)
    }
    localStorage.setItem(TENANT_ID_KEY, id)
    set({ activeTenantId: id, activeTenantName: name ?? get().activeTenantName })
  }

  function refreshActiveTenantName() {
    const id = get().activeTenantId
    if (!id) return
    const name = resolveTenantName(id, get().activeTenantName ?? get().user?.tenantName)
    if (name) {
      localStorage.setItem(TENANT_NAME_KEY, name)
      set({ activeTenantName: name })
    }
  }

  function clearStoredTenant() {
    localStorage.removeItem(TENANT_ID_KEY)
    localStorage.removeItem(TENANT_NAME_KEY)
    set({ activeTenantId: null, activeTenantName: null })
  }

  function ensureStoredTenant(tenants: TenantOption[]) {
    if (!tenants.length) return
    const current = get().activeTenantId
    if (current && tenants.some((t) => String(t.id) === String(current))) {
      refreshActiveTenantName()
      return
    }
    const pick = tenants.find((t) => String(t.id) === '1') ?? tenants[0]
    applyActiveTenant(pick.id, pick.name)
    set((state) => ({ tenantVersion: state.tenantVersion + 1 }))
  }

  return {
    token: getKeycloak().token ?? null,
    user: null,
    myTenants: [],
    loading: false,
    switchingTenant: false,
    tenantVersion: 0,
    activeTenantId: readStoredTenantId(),
    activeTenantName: readStoredTenantName(),
    isLoggedIn: () => isAuthenticated() || !!get().token,
    isAdmin: () => get().user?.role === 'admin',
    async syncToken() {
      set({ token: (await getToken()) ?? null })
    },
    async login() {
      const path = window.location.pathname.startsWith('/user/login')
        ? '/workbench'
        : window.location.pathname
      await keycloakLogin(appRedirectUri(path))
    },
    async fetchMyTenants() {
      if (!get().isLoggedIn()) {
        set({ myTenants: [] })
        return []
      }
      try {
        const myTenants = await userAuthApi.myTenants()
        set({ myTenants })
        ensureStoredTenant(myTenants)
        return myTenants
      } catch {
        set({ myTenants: [] })
        return []
      }
    },
    async switchTenant(tenantId) {
      if (String(tenantId) === String(get().activeTenantId)) {
        refreshActiveTenantName()
        return get().user
      }
      set({ switchingTenant: true })
      try {
        const selected = get().myTenants.find((t) => String(t.id) === String(tenantId))
        const profile = await userAuthApi.switchTenant(tenantId)
        const resolvedId = profile?.tenantId ?? tenantId
        const resolvedName = profile?.tenantName ?? selected?.name
        const user = profile
          ? { ...profile, tenantId: resolvedId, tenantName: resolvedName ?? profile.tenantName }
          : profile
        set({ user })
        applyActiveTenant(resolvedId, resolvedName)
        set((state) => ({ tenantVersion: state.tenantVersion + 1 }))
        return user
      } finally {
        set({ switchingTenant: false })
      }
    },
    async ensureTenant(tenantId) {
      if (String(tenantId) === String(get().activeTenantId)) {
        refreshActiveTenantName()
        return false
      }
      await get().switchTenant(tenantId)
      return true
    },
    async ensureTenantForProject(projectId) {
      try {
        const ctx = await pmProjectApi.accessContext(projectId)
        await get().ensureTenant(ctx.tenantId)
        return ctx
      } catch {
        const project = await pmProjectApi.getById(projectId)
        if (project?.tenantId == null) {
          throw new Error('项目不存在或无权访问')
        }
        await get().ensureTenant(project.tenantId)
        return {
          projectId: project.id ?? projectId,
          projectName: project.name,
          tenantId: project.tenantId,
        }
      }
    },
    async fetchMe() {
      await get().syncToken()
      if (!get().token) {
        set({ user: null, myTenants: [] })
        return null
      }
      set({ loading: true })
      try {
        const user = await userAuthApi.me()
        set({ user })
        if (user?.tenantId != null) {
          applyActiveTenant(user.tenantId, user.tenantName)
        }
        await get().fetchMyTenants()
        return user
      } catch {
        set({ user: null })
        return null
      } finally {
        set({ loading: false })
      }
    },
    async logout() {
      clearStoredTenant()
      set({ user: null, myTenants: [], token: null })
      await keycloakLogout(appRedirectUri('/workbench'))
    },
  }
})
