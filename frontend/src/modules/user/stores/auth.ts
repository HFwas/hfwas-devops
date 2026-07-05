import { defineStore } from 'pinia'
import { userAuthApi } from '@/modules/user/api'
import { pmProjectApi } from '@/modules/pm/api'
import type { ProjectAccessContext } from '@/modules/pm/types'
import type { TenantOption, UserProfile } from '@/modules/user/types'
import { AUTH_TOKEN_KEY, TENANT_ID_KEY, TENANT_NAME_KEY } from '@/modules/user/types'

function readStoredTenantId(): string | null {
  return localStorage.getItem(TENANT_ID_KEY)
}

function readStoredTenantName(): string | null {
  return localStorage.getItem(TENANT_NAME_KEY)
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(AUTH_TOKEN_KEY))
  const user = ref<UserProfile | null>(null)
  const myTenants = ref<TenantOption[]>([])
  const loading = ref(false)
  const switchingTenant = ref(false)
  /** Increments on tenant switch so views can reload tenant-scoped data. */
  const tenantVersion = ref(0)

  /** Reactive tenant context for header display and request header. */
  const activeTenantId = ref<string | null>(readStoredTenantId())
  const activeTenantName = ref<string | null>(readStoredTenantName())

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')

  function resolveTenantName(tenantId: number | string, fallback?: string | null) {
    const fromList = myTenants.value.find((t) => String(t.id) === String(tenantId))
    return fromList?.name ?? fallback ?? null
  }

  function applyActiveTenant(tenantId: number | string, tenantName?: string | null) {
    const id = String(tenantId)
    activeTenantId.value = id
    localStorage.setItem(TENANT_ID_KEY, id)
    const name = tenantName ?? resolveTenantName(tenantId)
    if (name) {
      activeTenantName.value = name
      localStorage.setItem(TENANT_NAME_KEY, name)
    }
  }

  function persistTenant(profile: UserProfile | null | undefined) {
    if (profile?.tenantId != null) {
      applyActiveTenant(profile.tenantId, profile.tenantName)
    }
  }

  function refreshActiveTenantName() {
    const id = activeTenantId.value
    if (!id) return
    const name = resolveTenantName(id, activeTenantName.value ?? user.value?.tenantName)
    if (name) {
      activeTenantName.value = name
      localStorage.setItem(TENANT_NAME_KEY, name)
    }
  }

  function clearStoredTenant() {
    activeTenantId.value = null
    activeTenantName.value = null
    localStorage.removeItem(TENANT_ID_KEY)
    localStorage.removeItem(TENANT_NAME_KEY)
  }

  function setToken(value: string | null) {
    token.value = value
    if (value) {
      localStorage.setItem(AUTH_TOKEN_KEY, value)
    } else {
      localStorage.removeItem(AUTH_TOKEN_KEY)
    }
  }

  async function login(username: string, password: string) {
    const res = await userAuthApi.login(username, password)
    setToken(res.token)
    user.value = res.user
    persistTenant(res.user)
    await fetchMyTenants()
    refreshActiveTenantName()
    return res.user
  }

  async function fetchMyTenants() {
    if (!token.value) {
      myTenants.value = []
      return []
    }
    try {
      myTenants.value = await userAuthApi.myTenants()
      refreshActiveTenantName()
      return myTenants.value
    } catch {
      myTenants.value = []
      return []
    }
  }

  async function switchTenant(tenantId: number | string) {
    if (String(tenantId) === String(activeTenantId.value)) {
      refreshActiveTenantName()
      return user.value
    }
    switchingTenant.value = true
    try {
      const selected = myTenants.value.find((t) => String(t.id) === String(tenantId))
      const res = await userAuthApi.switchTenant(tenantId)
      setToken(res.token)
      const resolvedId = res.user?.tenantId ?? tenantId
      const resolvedName = res.user?.tenantName ?? selected?.name
      user.value = res.user
        ? { ...res.user, tenantId: resolvedId, tenantName: resolvedName ?? res.user.tenantName }
        : res.user
      applyActiveTenant(resolvedId, resolvedName)
      tenantVersion.value += 1
      return user.value
    } finally {
      switchingTenant.value = false
    }
  }

  async function ensureTenant(tenantId: number | string) {
    if (String(tenantId) === String(activeTenantId.value)) {
      refreshActiveTenantName()
      return false
    }
    await switchTenant(tenantId)
    return true
  }

  /** Align tenant context with a project (deep link / post-login redirect). */
  async function ensureTenantForProject(projectId: number | string): Promise<ProjectAccessContext> {
    try {
      const ctx = await pmProjectApi.accessContext(projectId)
      await ensureTenant(ctx.tenantId)
      return ctx
    } catch {
      // Fallback when access-context unavailable: project list/detail under current tenant
      const project = await pmProjectApi.getById(projectId)
      if (project?.tenantId == null) {
        throw new Error('项目不存在或无权访问')
      }
      await ensureTenant(project.tenantId)
      return {
        projectId: project.id ?? projectId,
        projectName: project.name,
        tenantId: project.tenantId,
      }
    }
  }

  async function fetchMe() {
    if (!token.value) {
      user.value = null
      myTenants.value = []
      return null
    }
    loading.value = true
    try {
      user.value = await userAuthApi.me()
      persistTenant(user.value)
      await fetchMyTenants()
      return user.value
    } catch {
      logout()
      return null
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    try {
      if (token.value) {
        await userAuthApi.logout()
      }
    } catch {
      // ignore network errors on logout
    } finally {
      setToken(null)
      clearStoredTenant()
      user.value = null
      myTenants.value = []
    }
  }

  return {
    token,
    user,
    myTenants,
    loading,
    switchingTenant,
    tenantVersion,
    activeTenantId,
    activeTenantName,
    isLoggedIn,
    isAdmin,
    login,
    fetchMyTenants,
    switchTenant,
    ensureTenant,
    ensureTenantForProject,
    fetchMe,
    logout,
    setToken,
  }
})
