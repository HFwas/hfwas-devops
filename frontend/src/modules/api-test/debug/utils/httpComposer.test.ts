import { describe, expect, it } from 'vitest'
import { parseSetCookieHeaders } from './cookies'
import { interpolate } from './interpolate'
import { draftToCurl } from './curlExport'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'
import { emptyPair } from '@/modules/api-test/shared/types/keyValue'
import { emptyAuth } from './auth'
import { buildExecutePayload } from './draftExecute'

describe('cookies', () => {
  it('parses Set-Cookie from response headers case-insensitively', () => {
    expect(parseSetCookieHeaders({
      'Set-Cookie': 'session=abc; Path=/; HttpOnly, theme=dark; Path=/',
      'Content-Type': 'application/json',
    })).toEqual([
      { name: 'session', value: 'abc', extra: 'Path=/; HttpOnly' },
      { name: 'theme', value: 'dark', extra: 'Path=/' },
    ])
  })
})

describe('interpolate', () => {
  it('replaces {{ var }} and masks secrets in preview mode', () => {
    expect(interpolate('https://{{ host }}/{{id}}', { host: 'api.dev', id: '1' })).toBe('https://api.dev/1')
    expect(interpolate('Bearer {{token}}', { token: 'secret' }, { token: true })).toBe('Bearer ******')
  })
})

describe('curlExport + draftExecute', () => {
  it('buildExecutePayload applies auth, path, query, json body and settings', () => {
    const payload = buildExecutePayload(emptyDraft({
      url: 'https://{{base}}/users/:id',
      method: 'POST',
      pathParams: [{ enabled: true, key: 'id', value: '9' }, emptyPair()],
      queryParams: [{ enabled: true, key: 'dry', value: '1' }, emptyPair()],
      headers: [{ enabled: true, key: 'Accept', value: 'application/json' }, emptyPair()],
      auth: { ...emptyAuth(), type: 'bearer', token: 't' },
      bodyMode: 'json',
      body: '{"ok":true}',
      timeoutMs: 5000,
      followRedirects: false,
    }))
    expect(payload.url).toBe('https://{{base}}/users/9')
    expect(payload.queryParams).toEqual({ dry: '1' })
    expect(payload.headers).toMatchObject({
      Accept: 'application/json',
      Authorization: 'Bearer t',
      'Content-Type': 'application/json',
    })
    expect(payload.body).toBe('{"ok":true}')
    expect(payload.timeoutMs).toBe(5000)
    expect(payload.followRedirects).toBe(false)
  })

  it('draftToCurl emits method, headers and data', () => {
    const curl = draftToCurl(emptyDraft({
      url: 'https://api.example.com/users',
      method: 'POST',
      headers: [{ enabled: true, key: 'Accept', value: 'application/json' }, emptyPair()],
      bodyMode: 'json',
      body: '{"n":1}',
    }))
    expect(curl).toContain("curl --location --request POST 'https://api.example.com/users'")
    expect(curl).toContain("Accept: application/json")
    expect(curl).toContain('{"n":1}')
  })
})
