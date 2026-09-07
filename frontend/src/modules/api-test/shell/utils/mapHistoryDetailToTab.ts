import type { ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'
import type { DebugHistoryDetailVO } from '@/modules/api-test/debug/types/debugHistory'
import { inferBodyMode } from '@/modules/api-test/debug/utils/bodyMode'
import { mergePathParams } from '@/modules/api-test/debug/utils/urlParams'
import { emptyPair } from '@/modules/api-test/shared/types/keyValue'
import { recordToPairs } from '@/modules/api-test/shared/utils/keyValue'
import type { RequestDraft } from '@/modules/api-test/shell/types/workspace'

export function mapHistoryDetailToTab(detail: DebugHistoryDetailVO): {
  title: string
  method: string
  draftPatch: Partial<RequestDraft>
  result: ApiDebugResultVO
} {
  const method = detail.requestMethod || 'GET'
  const body = detail.requestBody ?? ''
  const contentType = detail.requestContentType ?? 'application/json'
  return {
    title: detail.name,
    method,
    draftPatch: {
      url: detail.requestUrl,
      method,
      headers: recordToPairs(detail.requestHeaders ?? {}),
      queryParams: recordToPairs(detail.requestQuery ?? {}),
      pathParams: mergePathParams(detail.requestUrl, [emptyPair()]),
      body,
      contentType,
      bodyMode: inferBodyMode(method, body, contentType),
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
