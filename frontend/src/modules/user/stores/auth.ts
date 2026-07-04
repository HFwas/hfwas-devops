import { defineStore } from 'pinia'
import { userAuthApi } from '@/modules/user/api'
import type { TenantOption, UserProfile } from '@/modules/user/types'
import { AUTH_TOKEN_KEY } from '@/modules/user/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(AUTH_TOKEN_KEY))
  const user = ref<UserProfile | null>(null)
  const myTenants = ref<TenantOption[]>([])
  const loading = ref(false)
  const switchingTenant = ref(false)
  /** Increments on tenant switch so views can reload tenant-scoped data. */
  const tenantVersion = ref(0)

  const isLoggedIn = computed(() => !!token.value)
  const isAdmin = computed(() => user.value?.role === 'admin')

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
    await fetchMyTenants()
    return res.user
  }

  async function fetchMyTenants() {
    if (!token.value) {
      myTenants.value = []
      return []
    }
    try {
      myTenants.value = await userAuthApi.myTenants()
      return myTenants.value
    } catch {
      myTenants.value = []
      return []
    }
  }

  async function switchTenant(tenantId: number | string) {
    if (String(tenantId) === String(user.value?.tenantId)) {
      return user.value
    }
    switchingTenant.value = true
    try {
      const res = await userAuthApi.switchTenant(tenantId)
      setToken(res.token)
      user.value = res.user
      tenantVersion.value += 1
      return res.user
    } finally {
      switchingTenant.value = false
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
    isLoggedIn,
    isAdmin,
    login,
    fetchMyTenants,
    switchTenant,
    fetchMe,
    logout,
    setToken,
  }
})
