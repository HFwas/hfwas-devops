import type { ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'

/** 调试历史查询条件 */
export interface DebugHistoryQueryDTO {
  projectId: number
  definitionId?: number
  status?: string
  keyword?: string
  startTime?: string
  endTime?: string
  pageNo?: number
  pageSize?: number
}

/** 调试历史详情VO */
export interface DebugHistoryDetailVO {
  id: number
  projectId: number
  definitionId: number | null
  environmentId: number | null
  name: string
  requestUrl: string
  requestMethod: string
  requestHeaders?: Record<string, string>
  requestQuery?: Record<string, string>
  requestBody?: string
  requestContentType?: string
  responseStatusCode: number | null
  responseHeaders?: Record<string, string>
  responseBody?: string
  responseContentType?: string
  responseSize: number | null
  durationMs: number
  status: string
  errorMessage?: string
  assertionResults?: any[]
  allAssertionsPassed?: boolean
  extractedVariables?: Record<string, string>
  createBy: number
  createTime: string
}

/** 调试历史列表VO（复用 debug.ts 中的 ApiDebugHistoryVO） */
export type { ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'