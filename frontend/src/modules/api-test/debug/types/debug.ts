/** 调试状态枚举 */
export type DebugStatus = 'SUCCESS' | 'FAILURE' | 'ERROR'

/** 调试状态配置 */
export const DEBUG_STATUS_OPTIONS = [
  { label: '成功', value: 'SUCCESS', color: '#67C23A' },
  { label: '失败', value: 'FAILURE', color: '#F56C6C' },
  { label: '错误', value: 'ERROR', color: '#E6A23C' },
] as const

/** 断言来源 */
export type AssertionSource = 'RESPONSE_STATUS' | 'RESPONSE_HEADERS' | 'RESPONSE_BODY' | 'RESPONSE_TIME'

/** 断言来源配置 */
export const ASSERTION_SOURCE_OPTIONS = [
  { label: '响应状态码', value: 'RESPONSE_STATUS' },
  { label: '响应头', value: 'RESPONSE_HEADERS' },
  { label: '响应体', value: 'RESPONSE_BODY' },
  { label: '响应耗时', value: 'RESPONSE_TIME' },
] as const

/** 比较方式 */
export type CompareType = 'EQUALS' | 'NOT_EQUALS' | 'CONTAINS' | 'NOT_CONTAINS' | 'REGEX' | 'GT' | 'GTE' | 'LT' | 'LTE'

/** 比较方式配置 */
export const COMPARE_TYPE_OPTIONS = [
  { label: '等于', value: 'EQUALS' },
  { label: '不等于', value: 'NOT_EQUALS' },
  { label: '包含', value: 'CONTAINS' },
  { label: '不包含', value: 'NOT_CONTAINS' },
  { label: '正则匹配', value: 'REGEX' },
  { label: '大于', value: 'GT' },
  { label: '大于等于', value: 'GTE' },
  { label: '小于', value: 'LT' },
  { label: '小于等于', value: 'LTE' },
] as const

/** 变量提取来源 */
export type ExtractSource = 'RESPONSE_BODY' | 'RESPONSE_HEADERS' | 'RESPONSE_STATUS'

/** 变量提取来源配置 */
export const EXTRACT_SOURCE_OPTIONS = [
  { label: '响应体', value: 'RESPONSE_BODY' },
  { label: '响应头', value: 'RESPONSE_HEADERS' },
  { label: '响应状态码', value: 'RESPONSE_STATUS' },
] as const

/** 脚本类型 */
export type ScriptType = 'PRE_REQUEST' | 'POST_RESPONSE'

/** 脚本类型配置 */
export const SCRIPT_TYPE_OPTIONS = [
  { label: '前置脚本', value: 'PRE_REQUEST' },
  { label: '后置脚本', value: 'POST_RESPONSE' },
] as const

/** 调试断言 */
export interface ApiDebugAssertionDTO {
  name?: string
  source: AssertionSource
  compareType: CompareType
  expression?: string
  expectedValue?: string
}

/** 调试变量提取 */
export interface ApiDebugExtractDTO {
  variableName: string
  expression: string
  source: ExtractSource
}

/** 调试执行请求 */
export interface ApiDebugExecuteDTO {
  projectId?: number
  definitionId?: number
  environmentId?: number
  url: string
  method: string
  headers?: Record<string, string>
  queryParams?: Record<string, string>
  body?: string
  contentType?: string
  timeoutMs?: number
  followRedirects?: boolean
  preRequestScript?: string
  postResponseScript?: string
  assertions?: ApiDebugAssertionDTO[]
  extracts?: ApiDebugExtractDTO[]
}

/** 调试结果 */
export interface ApiDebugResultVO {
  historyId?: number
  requestUrl: string
  requestMethod: string
  requestHeaders?: Record<string, string>
  requestQuery?: Record<string, string>
  requestBody?: string
  requestContentType?: string
  responseStatusCode?: number
  responseHeaders?: Record<string, string>
  responseBody?: string
  responseContentType?: string
  responseSize?: number
  durationMs: number
  status: DebugStatus
  errorMessage?: string
  preRequestLogs?: string[]
  postResponseLogs?: string[]
  assertionResults?: Array<{
    name: string
    source: string
    compareType: string
    expression: string | null
    expected: string
    actual: string
    passed: boolean
  }>
  allAssertionsPassed?: boolean
  extractedVariables?: Record<string, string>
}

/** 调试历史列表VO */
export interface ApiDebugHistoryVO {
  id: number
  definitionId: number | null
  environmentId: number | null
  name: string
  requestUrl: string
  requestMethod: string
  responseStatusCode: number | null
  responseSize: number | null
  durationMs: number
  status: DebugStatus
  allAssertionsPassed: boolean | null
  createTime: string
}