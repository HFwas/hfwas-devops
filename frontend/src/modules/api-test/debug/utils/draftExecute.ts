import type { RequestDraft } from '@/modules/api-test/shell/types/workspace'
import type { ApiDebugExecuteDTO } from '@/modules/api-test/debug/types/debug'
import { applyAuth } from './auth'
import {
  contentTypeForMode,
  encodeFormUrlencoded,
  encodeMultipart,
  newMultipartBoundary,
} from './bodyMode'
import { pairsToRecord } from '@/modules/api-test/shared/utils/keyValue'
import { applyPathParams, replaceQuery } from './urlParams'

export function resolveDraftBody(draft: RequestDraft): { body?: string; contentType?: string } {
  if (draft.bodyMode === 'none') return { body: undefined, contentType: undefined }
  if (draft.bodyMode === 'urlencoded') {
    return {
      body: encodeFormUrlencoded(draft.formFields),
      contentType: contentTypeForMode('urlencoded'),
    }
  }
  if (draft.bodyMode === 'form-data') {
    const boundary = newMultipartBoundary()
    return {
      body: encodeMultipart(draft.formFields, boundary),
      contentType: contentTypeForMode('form-data', boundary),
    }
  }
  if (draft.bodyMode === 'json') {
    return { body: draft.body || undefined, contentType: contentTypeForMode('json') }
  }
  return {
    body: draft.body || undefined,
    contentType: contentTypeForMode('raw', undefined, draft.contentType || 'text/plain'),
  }
}

export function buildExecutePayload(
  draft: RequestDraft,
  extra?: { projectId?: number; definitionId?: number; environmentId?: number },
): ApiDebugExecuteDTO {
  const withPath = applyPathParams(draft.url, draft.pathParams)
  const withQuery = replaceQuery(withPath, draft.queryParams)
  const { body, contentType } = resolveDraftBody(draft)
  const authed = applyAuth(draft.auth, pairsToRecord(draft.headers), [])
  const queryFromUrl = new URLSearchParams(withQuery.split('?')[1]?.split('#')[0] ?? '')
  const queryParams: Record<string, string> = {}
  queryFromUrl.forEach((value, key) => {
    queryParams[key] = value
  })
  if (authed.query.length) {
    for (const row of authed.query) queryParams[row.key] = row.value
  }
  const headers = { ...authed.headers }
  if (contentType) headers['Content-Type'] = contentType

  return {
    projectId: extra?.projectId,
    definitionId: extra?.definitionId,
    environmentId: extra?.environmentId,
    url: withQuery.split('?')[0].split('#')[0] || withPath.split('?')[0],
    method: draft.method,
    headers,
    queryParams,
    body,
    contentType,
    timeoutMs: draft.timeoutMs ?? 30000,
    followRedirects: draft.followRedirects,
    preRequestScript: draft.preRequestScript || undefined,
    postResponseScript: draft.postResponseScript || undefined,
    assertions: draft.assertions,
    extracts: draft.extracts,
  }
}
