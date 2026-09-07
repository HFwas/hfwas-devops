import { describe, expect, it } from 'vitest'
import { emptyPair } from '../types/keyValue'
import {
  ensureTrailingEmpty,
  pairsToRecord,
  recordToPairs,
  enabledNamedPairs,
} from './keyValue'

describe('keyValue helpers', () => {
  it('pairsToRecord skips disabled and empty keys; last duplicate wins', () => {
    const record = pairsToRecord([
      { enabled: true, key: 'Accept', value: 'text/plain' },
      { enabled: false, key: 'X-Debug', value: '1' },
      { enabled: true, key: '', value: 'ignored' },
      { enabled: true, key: 'Accept', value: 'application/json' },
    ])
    expect(record).toEqual({ Accept: 'application/json' })
  })

  it('recordToPairs keeps insertion order and appends a trailing empty row', () => {
    const pairs = recordToPairs({ page: '1', q: 'ada' })
    expect(pairs).toEqual([
      { enabled: true, key: 'page', value: '1' },
      { enabled: true, key: 'q', value: 'ada' },
      emptyPair(),
    ])
  })

  it('ensureTrailingEmpty adds a blank row only when the last row is used', () => {
    expect(ensureTrailingEmpty([])).toEqual([emptyPair()])
    expect(ensureTrailingEmpty([emptyPair()])).toEqual([emptyPair()])
    expect(ensureTrailingEmpty([{ enabled: true, key: 'a', value: '1' }])).toEqual([
      { enabled: true, key: 'a', value: '1' },
      emptyPair(),
    ])
  })

  it('enabledNamedPairs drops blank keys and disabled rows', () => {
    expect(enabledNamedPairs([
      { enabled: true, key: 'a', value: '1' },
      { enabled: false, key: 'b', value: '2' },
      { enabled: true, key: '  ', value: '3' },
    ])).toEqual([{ enabled: true, key: 'a', value: '1' }])
  })
})
