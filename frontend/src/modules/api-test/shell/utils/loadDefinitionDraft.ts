import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'

export async function loadDefinitionIntoTab(definitionId: number) {
  const detail = await apiDefinitionApi.detail(definitionId)
  const draft = emptyDraft({
    url: detail.path || '',
    method: detail.method,
    contentType: detail.contentType || 'application/json',
    description: detail.description || '',
  })
  for (const p of detail.params || []) {
    if (p.paramType === 'query') draft.queryParams[p.name] = p.defaultValue || ''
    if (p.paramType === 'header') draft.headers[p.name] = p.defaultValue || ''
    if (p.paramType === 'body' && p.defaultValue) draft.body = p.defaultValue
  }
  return { detail, draft }
}
