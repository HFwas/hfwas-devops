import { describe, expect, it } from 'vitest'
import { emptyPair } from '@/modules/api-test/shared/types/keyValue'
import {
  applyPathParams,
  extractPathKeys,
  parseQuery,
  replaceQuery,
  syncQueryPairsFromUrl,
} from './urlParams'

describe('urlParams', () => {
  it('replaceQuery writes enabled pairs onto urls that contain {{placeholders}}', () => {
    const url = replaceQuery('https://{{baseUrl}}/users?old=1', [
      { enabled: true, key: 'page', value: '2' },
      { enabled: false, key: 'debug', value: '1' },
      { enabled: true, key: 'q', value: 'ada' },
      emptyPair(),
    ])
    expect(url).toBe('https://{{baseUrl}}/users?page=2&q=ada')
  })

  it('parseQuery reads duplicate keys in order', () => {
    expect(parseQuery('/x?a=1&a=2&b=')).toEqual([
      { key: 'a', value: '1' },
      { key: 'a', value: '2' },
      { key: 'b', value: '' },
    ])
  })

  it('syncQueryPairsFromUrl updates enabled rows from the URL and keeps disabled rows', () => {
    const next = syncQueryPairsFromUrl('/users?page=9', [
      { enabled: true, key: 'page', value: '1' },
      { enabled: false, key: 'debug', value: '1' },
      emptyPair(),
    ])
    expect(next).toEqual([
      { enabled: true, key: 'page', value: '9' },
      { enabled: false, key: 'debug', value: '1' },
      emptyPair(),
    ])
  })

  it('extractPathKeys finds :id and {id} but not {{env}}', () => {
    expect(extractPathKeys('https://{{host}}/users/:id/posts/{postId}')).toEqual(['id', 'postId'])
  })

  it('applyPathParams replaces :id and {id}', () => {
    const url = applyPathParams('/users/:id/posts/{postId}', [
      { enabled: true, key: 'id', value: '42' },
      { enabled: true, key: 'postId', value: '9' },
    ])
    expect(url).toBe('/users/42/posts/9')
  })
})
