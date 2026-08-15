import type { ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'
import type { DebugHistoryDetailVO } from '@/modules/api-test/debug/types/debugHistory'
import type { RequestDraft } from '@/modules/api-test/shell/types/workspace'

export function mapHistoryDetailToTab(detail: DebugHistoryDetailVO): {
  title: string
  method: string
  draftPatch: Partial<RequestDraft>
  result: ApiDebugResultVO
} {
  return {
    title: detail.name,
    method: detail.requestMethod || 'GET',
    draftPatch: {
      url: detail.requestUrl,
      method: detail.requestMethod || 'GET',
      headers: detail.requestHeaders ?? {},
      queryParams: detail.requestQuery ?? {},
      body: detail.requestBody ?? '',
      contentType: detail.requestContentType ?? 'application/json',
    },
    result: {
      historyId: detail.id,
      requestUrl: detail.requestUrl,
      requestMethod: detail.requestMethod,
      requestHeaders: detail.requestHeaders,
      requestQuery: detail.requestQuery,
      requestBody: detail.requestBody,
      requestContentType: detail.requestContentType,
      responseStatusCode: detail.responseStatusCode ?? undefined,
      responseHeaders: detail.responseHeaders,
      responseBody: detail.responseBody,
      responseContentType: detail.responseContentType,
      responseSize: detail.responseSize ?? undefined,
      durationMs: detail.durationMs,
      status: detail.status as ApiDebugResultVO['status'],
      errorMessage: detail.errorMessage,
      assertionResults: detail.assertionResults,
      allAssertionsPassed: detail.allAssertionsPassed,
      extractedVariables: detail.extractedVariables,
    },
  }
}
