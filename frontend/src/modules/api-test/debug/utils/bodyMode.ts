import type { KeyValuePair } from '@/modules/api-test/shared/types/keyValue'
import { enabledNamedPairs } from '@/modules/api-test/shared/utils/keyValue'

export type BodyMode = 'none' | 'json' | 'raw' | 'form-data' | 'urlencoded'

export function contentTypeForMode(
  mode: BodyMode,
  boundary?: string,
  rawType?: string,
): string {
  if (mode === 'none') return ''
  if (mode === 'json') return 'application/json'
  if (mode === 'urlencoded') return 'application/x-www-form-urlencoded'
  if (mode === 'form-data') return `multipart/form-data; boundary=${boundary ?? '----hfwBoundary'}`
  return rawType || 'text/plain'
}

export function inferBodyMode(method: string, body: string, contentType: string | null | undefined): BodyMode {
  const ct = (contentType || '').toLowerCase()
  if (ct.includes('urlencoded')) return 'urlencoded'
  if (ct.includes('multipart')) return 'form-data'
  if ((method === 'GET' || method === 'HEAD') && !body) return 'none'
  if (ct.includes('json') || (!ct && body.trim().startsWith('{'))) return 'json'
  if (body) return 'raw'
  return method === 'GET' || method === 'HEAD' ? 'none' : 'json'
}

export function encodeFormUrlencoded(pairs: KeyValuePair[]): string {
  const search = new URLSearchParams()
  for (const pair of enabledNamedPairs(pairs)) {
    search.append(pair.key, pair.value)
  }
  return search.toString()
}

export function encodeMultipart(pairs: KeyValuePair[], boundary: string): string {
  const chunks: string[] = []
  for (const pair of enabledNamedPairs(pairs)) {
    if (pair.type === 'file') continue
    chunks.push(`--${boundary}\r\nContent-Disposition: form-data; name="${pair.key}"\r\n\r\n${pair.value}\r\n`)
  }
  chunks.push(`--${boundary}--\r\n`)
  return chunks.join('')
}

export function newMultipartBoundary(): string {
  return `----hfwBoundary${Math.random().toString(36).slice(2, 12)}`
}
