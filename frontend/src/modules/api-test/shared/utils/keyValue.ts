import { emptyPair, type KeyValuePair } from '../types/keyValue'

export function enabledNamedPairs(pairs: KeyValuePair[]): KeyValuePair[] {
  return pairs.filter((p) => p.enabled && p.key.trim() !== '')
}

export function pairsToRecord(pairs: KeyValuePair[]): Record<string, string> {
  const record: Record<string, string> = {}
  for (const pair of enabledNamedPairs(pairs)) {
    record[pair.key] = pair.value
  }
  return record
}

export function recordToPairs(record: Record<string, string> | null | undefined): KeyValuePair[] {
  const pairs = Object.entries(record ?? {}).map(([key, value]) => ({
    enabled: true,
    key,
    value: value ?? '',
  }))
  return ensureTrailingEmpty(pairs)
}

export function ensureTrailingEmpty(pairs: KeyValuePair[]): KeyValuePair[] {
  if (pairs.length === 0) return [emptyPair()]
  const last = pairs[pairs.length - 1]
  if (last.key === '' && last.value === '') return pairs
  return [...pairs, emptyPair()]
}

export function updatePairAt(
  pairs: KeyValuePair[],
  index: number,
  patch: Partial<KeyValuePair>,
): KeyValuePair[] {
  const next = pairs.map((row, i) => (i === index ? { ...row, ...patch } : row))
  return ensureTrailingEmpty(next)
}

export function removePairAt(pairs: KeyValuePair[], index: number): KeyValuePair[] {
  return ensureTrailingEmpty(pairs.filter((_, i) => i !== index))
}
