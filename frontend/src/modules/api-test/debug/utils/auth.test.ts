import { describe, expect, it } from 'vitest'
import { applyAuth, emptyAuth } from './auth'

describe('applyAuth', () => {
  it('leaves headers untouched when type is none', () => {
    const headers = { Accept: 'application/json' }
    expect(applyAuth(emptyAuth(), headers, [])).toEqual({
      headers,
      query: [],
    })
  })

  it('writes Bearer Authorization', () => {
    const { headers } = applyAuth(
      { ...emptyAuth(), type: 'bearer', token: '{{accessToken}}' },
      {},
      [],
    )
    expect(headers.Authorization).toBe('Bearer {{accessToken}}')
  })

  it('writes Basic from username/password', () => {
    const { headers } = applyAuth(
      { ...emptyAuth(), type: 'basic', username: 'ada', password: 'pw' },
      {},
      [],
    )
    expect(headers.Authorization).toBe(`Basic ${btoa('ada:pw')}`)
  })

  it('adds API key to header or query', () => {
    const asHeader = applyAuth(
      { ...emptyAuth(), type: 'apikey', apiKey: 'X-API-Key', apiValue: 'k', addTo: 'header' },
      {},
      [],
    )
    expect(asHeader.headers['X-API-Key']).toBe('k')

    const asQuery = applyAuth(
      { ...emptyAuth(), type: 'apikey', apiKey: 'key', apiValue: 'k', addTo: 'query' },
      {},
      [],
    )
    expect(asQuery.query).toEqual([{ enabled: true, key: 'key', value: 'k' }])
  })
})
