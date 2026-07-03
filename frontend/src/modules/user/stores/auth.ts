import { defineStore } from 'pinia'
import { userAuthApi } from '@/modules/user/api'
import type { UserProfile } from '@/modules/user/types'
import { AUTH_TOKEN_KEY } from '@/modules/user/types'

export const useAuthStore = defineStore('auth', () => {
  const token = ref<string | null>(localStorage.getItem(AUTH_TOKEN_KEY))
  const user = ref<UserProfile | null>(null)
  const loading = ref(false)

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

  async function login(username: string, password: string, tenantCode?: string) {
    const res = await userAuthApi.login(username, password, tenantCode)
    setToken(res.token)
    user.value = res.user
    return res.user
  }

  async function fetchMe() {
    if (!token.value) {
      user.value = null
      return null
    }
    loading.value = true
    try {
      user.value = await userAuthApi.me()
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
    }
  }

  return { token, user, loading, isLoggedIn, isAdmin, login, fetchMe, logout, setToken }
})
