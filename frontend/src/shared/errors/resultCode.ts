/** Business error codes – keep in sync with backend {@code ResultCode}. */
export const ResultCode = {
  SUCCESS: 0,

  INTERNAL_ERROR: 10001,
  BAD_REQUEST: 10002,
  NOT_FOUND: 10003,
  DUPLICATE: 10004,
  OPERATION_FAILED: 10005,
  IMPORT_EMPTY: 10006,
  SCHEMA_UNSUPPORTED: 10007,
  FILE_INVALID: 10008,
  FILE_READ_FAILED: 10009,
  FIELD_KEYS_INVALID: 10010,

  UNAUTHORIZED: 11001,
  FORBIDDEN: 11002,
  ADMIN_REQUIRED: 11003,
  PLATFORM_ADMIN_REQUIRED: 11004,
  TENANT_FORBIDDEN: 11005,
  TENANT_ID_INVALID: 11006,
  TENANT_ID_REQUIRED: 11007,
  NOT_TENANT_MEMBER: 11008,
  NOT_TENANT_MEMBER_CONTACT: 11009,
  TENANT_CONTEXT_MISSING: 11010,

  USER_NOT_FOUND: 12001,
  USER_PASSWORD_WRONG: 12002,
  USERNAME_PASSWORD_REQUIRED: 12003,
  USERNAME_DISPLAY_NAME_REQUIRED: 12004,
  USERNAME_EXISTS: 12005,
  INVALID_ROLE: 12006,
  PASSWORD_REQUIRED: 12007,
  CANNOT_DELETE_SELF: 12008,

  TENANT_NOT_FOUND: 13001,
  TENANT_DISABLED: 13002,
  TENANT_CODE_EXISTS: 13003,

  PROJECT_NOT_FOUND: 20001,
  PROJECT_ACCESS_DENIED: 20002,
  PROJECT_CODE_DUPLICATE: 20004,

  WORK_ITEM_NOT_FOUND: 21001,
  TITLE_REQUIRED: 21002,
  EXCEL_FILE_REQUIRED: 21007,
} as const

export type ResultCodeValue = (typeof ResultCode)[keyof typeof ResultCode]

/** Default user-facing messages keyed by error code. */
export const ERROR_MESSAGES: Record<number, string> = {
  [ResultCode.SUCCESS]: '成功',
  [ResultCode.INTERNAL_ERROR]: '服务器内部错误',
  [ResultCode.BAD_REQUEST]: '请求参数错误',
  [ResultCode.NOT_FOUND]: '资源不存在',
  [ResultCode.UNAUTHORIZED]: '未登录或登录已过期',
  [ResultCode.FORBIDDEN]: '无权访问',
  [ResultCode.ADMIN_REQUIRED]: '需要管理员权限',
  [ResultCode.TENANT_FORBIDDEN]: '无权访问该租户',
  [ResultCode.USER_PASSWORD_WRONG]: '用户名或密码错误',
  [ResultCode.USERNAME_PASSWORD_REQUIRED]: '用户名和密码不能为空',
  [ResultCode.TENANT_NOT_FOUND]: '租户不存在',
  [ResultCode.TENANT_DISABLED]: '租户已停用',
  [ResultCode.PROJECT_ACCESS_DENIED]: '项目不存在或无权访问',
  [ResultCode.PROJECT_CODE_DUPLICATE]: '当前租户下项目编码已存在',
  [ResultCode.WORK_ITEM_NOT_FOUND]: '事项不存在',
  [ResultCode.EXCEL_FILE_REQUIRED]: '请上传 Excel 文件',
}

export function resolveErrorMessage(code: number, serverMsg?: string | null): string {
  if (serverMsg) return serverMsg
  return ERROR_MESSAGES[code] ?? '请求失败'
}

export function isUnauthorizedCode(code: number): boolean {
  return code === ResultCode.UNAUTHORIZED
}

export function isForbiddenCode(code: number): boolean {
  return code === ResultCode.FORBIDDEN
    || code === ResultCode.TENANT_FORBIDDEN
    || code === ResultCode.ADMIN_REQUIRED
    || code === ResultCode.PLATFORM_ADMIN_REQUIRED
}
