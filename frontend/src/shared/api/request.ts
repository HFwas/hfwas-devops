import axios from 'axios'
import type { BaseResult } from '@/shared/types/common'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as BaseResult<unknown>
    if (result && typeof result.code === 'number' && result.code !== 0) {
      return Promise.reject(new Error(result.msg || '请求失败'))
    }
    return response
  },
  (error) => Promise.reject(error),
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
