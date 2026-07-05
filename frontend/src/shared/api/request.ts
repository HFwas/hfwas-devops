import axios from 'axios'
import type { BaseResult } from '@/shared/types/common'
import { ApiError, isApiError, toApiError } from '@/shared/errors/apiError'
import { ResultCode } from '@/shared/errors/resultCode'
import { AUTH_TOKEN_KEY, TENANT_ID_KEY, TENANT_NAME_KEY } from '@/modules/user/types'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000,
})

function rejectResult(result: BaseResult<unknown>): Promise<never> {
  const code = typeof result.code === 'number' ? result.code : ResultCode.BAD_REQUEST
  return Promise.reject(toApiError(code, result.msg))
}

function handleUnauthorized() {
  localStorage.removeItem(AUTH_TOKEN_KEY)
  localStorage.removeItem(TENANT_ID_KEY)
  localStorage.removeItem(TENANT_NAME_KEY)
  const path = window.location.pathname
  if (!path.startsWith('/user/login')) {
    const redirect = encodeURIComponent(path + window.location.search)
    window.location.href = `/user/login?redirect=${redirect}`
  }
}

request.interceptors.request.use((config) => {
  const token = localStorage.getItem(AUTH_TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  const tenantId = localStorage.getItem(TENANT_ID_KEY)
  if (tenantId) {
    config.headers['X-Tenant-Id'] = tenantId
  }
  return config
})

request.interceptors.response.use(
  (response) => {
    const result = response.data as BaseResult<unknown>
    if (result && typeof result.code === 'number' && result.code !== ResultCode.SUCCESS) {
      return rejectResult(result)
    }
    return response
  },
  (error) => {
    const status = error.response?.status
    const payload = error.response?.data as BaseResult<unknown> | undefined
    const code = typeof payload?.code === 'number' ? payload.code : undefined

    if (status === 401 || code === ResultCode.UNAUTHORIZED) {
      handleUnauthorized()
    }

    if (code != null) {
      return Promise.reject(toApiError(code, payload?.msg))
    }

    const msg = payload?.msg
    return Promise.reject(new ApiError(ResultCode.INTERNAL_ERROR, msg || error.message || '请求失败'))
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

/** POST and download binary response (e.g. Excel export). */
export async function postBlob(url: string, data?: unknown, defaultFilename = 'export.xlsx'): Promise<{ blob: Blob; filename: string }> {
  const res = await request.post(url, data, { responseType: 'blob' })
  const blob = res.data as Blob
  if (blob.type?.includes('json')) {
    const text = await blob.text()
    try {
      const err = JSON.parse(text) as BaseResult<unknown>
      throw toApiError(typeof err.code === 'number' ? err.code : ResultCode.BAD_REQUEST, err.msg)
    } catch (e) {
      if (isApiError(e)) throw e
      throw new ApiError(ResultCode.BAD_REQUEST, '下载失败')
    }
  }
  const disposition = res.headers['content-disposition'] as string | undefined
  let filename = defaultFilename
  if (disposition) {
    const match = disposition.match(/filename\*=UTF-8''([^;]+)/i)
    if (match?.[1]) {
      filename = decodeURIComponent(match[1])
    }
  }
  return { blob, filename }
}

/** POST multipart form for file upload APIs. */
export async function postFormData<T>(url: string, form: FormData): Promise<T> {
  const res = await request.post<BaseResult<T>>(url, form, {
    headers: { 'Content-Type': 'multipart/form-data' },
  })
  return res.data.data
}

export default request
