import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { inferBodyMode } from '@/modules/api-test/debug/utils/bodyMode'
import { extractPathKeys } from '@/modules/api-test/debug/utils/urlParams'
import { emptyPair } from '@/modules/api-test/shared/types/keyValue'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'
import { ensureTrailingEmpty, recordToPairs } from '@/modules/api-test/shared/utils/keyValue'

export async function loadDefinitionIntoTab(definitionId: number) {
  const detail = await apiDefinitionApi.detail(definitionId)
  const url = detail.path || ''
  const query: Record<string, string> = {}
  const headers: Record<string, string> = {}
  const pathFromDef: Array<{ key: string; value: string }> = []
  let body = ''

  for (const p of detail.params || []) {
    if (p.paramType === 'query') query[p.name] = p.defaultValue || ''
    if (p.paramType === 'header') headers[p.name] = p.defaultValue || ''
    if (p.paramType === 'body' && p.defaultValue) body = p.defaultValue
    if (p.paramType === 'path') pathFromDef.push({ key: p.name, value: p.defaultValue || '' })
  }

  const contentType = detail.contentType || 'application/json'
  const method = detail.method
  const bodyMode = inferBodyMode(method, body, contentType)
  const urlKeys = extractPathKeys(url)
  const pathParams = ensureTrailingEmpty(
    (urlKeys.length ? urlKeys : pathFromDef.map((p) => p.key)).map((key) => ({
      enabled: true,
      key,
      value: pathFromDef.find((p) => p.key === key)?.value || '',
    })),
  )

  const draft = emptyDraft({
    url,
    method,
    contentType,
    description: detail.description || '',
    body,
    bodyMode,
    queryParams: recordToPairs(query),
    headers: recordToPairs(headers),
    pathParams,
    formFields: bodyMode === 'urlencoded' || bodyMode === 'form-data'
      ? recordToPairs(parseFormBody(body))
      : [emptyPair()],
  })
  return { detail, draft }
}

function parseFormBody(body: string): Record<string, string> {
  const record: Record<string, string> = {}
  if (!body) return record
  new URLSearchParams(body).forEach((value, key) => {
    record[key] = value
  })
  return record
}
