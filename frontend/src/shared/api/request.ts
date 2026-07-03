import axios from 'axios'
import type { BaseResult } from '@/shared/types/common'
import { AUTH_TOKEN_KEY } from '@/modules/user/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as BaseResult<unknown>
    if (result && typeof result.code === 'number' && result.code !== 0) {
      return Promise.reject(new Error(result.msg || '请求失败'))
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    if (status === 401) {
      localStorage.removeItem(AUTH_TOKEN_KEY)
      const path = window.location.pathname
      if (!path.startsWith('/user/login')) {
        const redirect = encodeURIComponent(path + window.location.search)
        window.location.href = `/user/login?redirect=${redirect}`
      }
    }
    const msg = error.response?.data?.msg
    return Promise.reject(new Error(msg || error.message || '请求失败'))
  },
)

export async function post<T>(url: string, data?: unknown): Promise<T> {
  const res = await request.post<BaseResult<T>>(url, data)
  return res.data.data
}

export async function get<T>(url: string, params?: unknown): Promise<T> {
  const res = await request.get<BaseResult<T>>(url, { params })
  return res.data.data
}

export default request
