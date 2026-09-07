import { emptyPair, type KeyValuePair } from '@/modules/api-test/shared/types/keyValue'
import { ensureTrailingEmpty } from '@/modules/api-test/shared/utils/keyValue'

export function splitUrl(url: string): { stem: string; query: string; hash: string } {
  const hashIdx = url.indexOf('#')
  const withoutHash = hashIdx >= 0 ? url.slice(0, hashIdx) : url
  const hash = hashIdx >= 0 ? url.slice(hashIdx) : ''
  const qIdx = withoutHash.indexOf('?')
  if (qIdx < 0) return { stem: withoutHash, query: '', hash }
  return {
    stem: withoutHash.slice(0, qIdx),
    query: withoutHash.slice(qIdx + 1),
    hash,
  }
}

export function parseQuery(url: string): Array<{ key: string; value: string }> {
  const { query } = splitUrl(url)
  if (!query) return []
  const params = new URLSearchParams(query)
  const rows: Array<{ key: string; value: string }> = []
  params.forEach((value, key) => {
    rows.push({ key, value })
  })
  return rows
}

export function replaceQuery(url: string, pairs: KeyValuePair[]): string {
  const { stem, hash } = splitUrl(url)
  const enabled = pairs.filter((p) => p.enabled && p.key.trim() !== '')
  if (enabled.length === 0) return `${stem}${hash}`
  const search = new URLSearchParams()
  for (const pair of enabled) {
    search.append(pair.key, pair.value)
  }
  return `${stem}?${search.toString()}${hash}`
}

export function syncQueryPairsFromUrl(url: string, previous: KeyValuePair[]): KeyValuePair[] {
  const fromUrl = parseQuery(url).map((row) => ({
    enabled: true,
    key: row.key,
    value: row.value,
  }))
  const disabled = previous.filter((p) => !p.enabled && p.key.trim() !== '')
  return ensureTrailingEmpty([...fromUrl, ...disabled])
}

const PATH_COLON = /:([A-Za-z_][A-Za-z0-9_]*)/g
const PATH_BRACE = /\{([A-Za-z_][A-Za-z0-9_]*)\}/g

export function extractPathKeys(url: string): string[] {
  const { stem } = splitUrl(url)
  const pathname = stem.replace(/^[a-z][a-z0-9+.-]*:\/\//i, '').replace(/^[^/]*/, '')
  const keys: string[] = []
  const seen = new Set<string>()
  const stripped = pathname.replace(/\{\{[^}]*\}\}/g, '')
  for (const re of [PATH_COLON, PATH_BRACE]) {
    re.lastIndex = 0
    let m: RegExpExecArray | null
    while ((m = re.exec(stripped)) != null) {
      if (!seen.has(m[1])) {
        seen.add(m[1])
        keys.push(m[1])
      }
    }
  }
  return keys
}

export function mergePathParams(url: string, previous: KeyValuePair[]): KeyValuePair[] {
  const keys = extractPathKeys(url)
  const prevByKey = new Map(previous.filter((p) => p.key).map((p) => [p.key, p]))
  const next = keys.map((key) => prevByKey.get(key) ?? { enabled: true, key, value: '' })
  return ensureTrailingEmpty(next.length ? next : previous.filter((p) => !p.enabled && p.key))
}

export function applyPathParams(url: string, pairs: KeyValuePair[]): string {
  let result = url
  for (const pair of pairs) {
    if (!pair.enabled || !pair.key.trim()) continue
    const key = pair.key.trim()
    result = result.split(`:${key}`).join(encodeURIComponent(pair.value))
    result = result.split(`{${key}}`).join(encodeURIComponent(pair.value))
  }
  return result
}
