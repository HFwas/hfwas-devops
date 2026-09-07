import type { RequestDraft } from '@/modules/api-test/shell/types/workspace'
import { buildExecutePayload } from './draftExecute'

function shellEscape(value: string): string {
  return `'${value.replace(/'/g, `'\\''`)}'`
}

export function draftToCurl(draft: RequestDraft): string {
  const payload = buildExecutePayload(draft)
  const parts = [`curl --location --request ${payload.method} ${shellEscape(joinUrl(payload.url, payload.queryParams))}`]
  for (const [key, value] of Object.entries(payload.headers ?? {})) {
    parts.push(`  --header ${shellEscape(`${key}: ${value}`)}`)
  }
  if (payload.body) {
    parts.push(`  --data-raw ${shellEscape(payload.body)}`)
  }
  return parts.join(' \\\n')
}

function joinUrl(url: string, query?: Record<string, string>): string {
  if (!query || Object.keys(query).length === 0) return url
  const search = new URLSearchParams(query).toString()
  return `${url}?${search}`
}
