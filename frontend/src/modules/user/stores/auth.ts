import { defineStore } from 'pinia'
import { userAuthApi } from '@/modules/user/api'
import { pmProjectApi } from '@/modules/pm/api'
import type { ProjectAccessContext } from '@/modules/pm/types'
import type { TenantOption, UserProfile } from '@/modules/user/types'
import { TENANT_ID_KEY, TENANT_NAME_KEY } from '@/modules/user/types'
import { getKeycloak, getToken, isAuthenticated, login as keycloakLogin, logout as keycloakLogout } from '@/shared/keycloak'

function readStoredTenantId(): string | null {
  return localStorage.getItem(TENANT_ID_KEY)
}

function readStoredTenantName(): string | null {
  return localStorage.getItem(TENANT_NAME_KEY)
}

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(getKeycloak().token ?? null)
  const user = ref<UserProfile | null>(null)
  const myTenants = ref<TenantOption[]>([])
  const loading = ref(false)
  const switchingTenant = ref(false)
  /** Increments on tenant switch so views can reload tenant-scoped data. */
  const tenantVersion = ref(0)

  const activeTenantId = ref<string | null>(readStoredTenantId())
  const activeTenantName = ref<string | null>(readStoredTenantName())

  const isLoggedIn = computed(() => isAuthenticated() || !!token.value)
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

  async function syncToken() {
    token.value = (await getToken()) ?? null
  }

  async function login() {
    const redirect = window.location.origin + (window.location.pathname.startsWith('/user/login')
      ? '/workbench'
      : window.location.pathname + window.location.search)
    await keycloakLogin(redirect)
  }

  async function fetchMyTenants() {
    if (!isLoggedIn.value) {
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
      const profile = await userAuthApi.switchTenant(tenantId)
      const resolvedId = profile?.tenantId ?? tenantId
      const resolvedName = profile?.tenantName ?? selected?.name
      user.value = profile
        ? { ...profile, tenantId: resolvedId, tenantName: resolvedName ?? profile.tenantName }
        : profile
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

  async function ensureTenantForProject(projectId: number | string): Promise<ProjectAccessContext> {
    try {
      const ctx = await pmProjectApi.accessContext(projectId)
      await ensureTenant(ctx.tenantId)
      return ctx
    } catch {
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
    await syncToken()
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
      user.value = null
      return null
    } finally {
      loading.value = false
    }
  }

  async function logout() {
    clearStoredTenant()
    user.value = null
    myTenants.value = []
    token.value = null
    await keycloakLogout(`${window.location.origin}/`)
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
    syncToken,
  }
})
