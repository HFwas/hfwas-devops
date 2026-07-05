import { resolveErrorMessage, isUnauthorizedCode } from '@/shared/errors/resultCode'

export class ApiError extends Error {
  readonly code: number

  constructor(code: number, message: string) {
    super(message)
    this.name = 'ApiError'
    this.code = code
  }

  static fromPayload(code: number, serverMsg?: string | null): ApiError {
    return new ApiError(code, resolveErrorMessage(code, serverMsg))
  }

  get isUnauthorized(): boolean {
    return isUnauthorizedCode(this.code)
  }
}

export function toApiError(code: number, serverMsg?: string | null): ApiError {
  return ApiError.fromPayload(code, serverMsg)
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
