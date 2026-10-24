import { resolveErrorMessage, isUnauthorizedCode } from '@/shared/errors/resultCode'

export class ApiError extends Error {
  readonly code: number
  /** 服务端返回的请求编号，便于失败态展示与日志关联。 */
  readonly requestId?: string

  constructor(code: number, message: string, requestId?: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
    this.requestId = requestId
  }

  static fromPayload(code: number, serverMsg?: string | null, requestId?: string): ApiError {
    return new ApiError(code, resolveErrorMessage(code, serverMsg), requestId)
  }

  get isUnauthorized(): boolean {
    return isUnauthorizedCode(this.code)
  }
}

export function toApiError(code: number, serverMsg?: string | null, requestId?: string): ApiError {
  return ApiError.fromPayload(code, serverMsg, requestId)
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError
}

/** Normalize unknown thrown values for UI display. */
export function errorMessage(error: unknown, fallback = '请求失败'): string {
  if (isApiError(error)) return error.message
  if (error instanceof Error) return error.message || fallback
  return fallback
}
