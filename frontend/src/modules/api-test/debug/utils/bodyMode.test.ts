import { describe, expect, it } from 'vitest'
import { contentTypeForMode, encodeFormUrlencoded, encodeMultipart, inferBodyMode } from './bodyMode'

describe('bodyMode', () => {
  it('maps modes to content types', () => {
    expect(contentTypeForMode('none')).toBe('')
    expect(contentTypeForMode('json')).toBe('application/json')
    expect(contentTypeForMode('urlencoded')).toBe('application/x-www-form-urlencoded')
    expect(contentTypeForMode('form-data', '----bound')).toBe('multipart/form-data; boundary=----bound')
    expect(contentTypeForMode('raw', undefined, 'text/plain')).toBe('text/plain')
  })

  it('encodes urlencoded from enabled pairs', () => {
    expect(encodeFormUrlencoded([
      { enabled: true, key: 'a', value: '1 2' },
      { enabled: false, key: 'skip', value: 'x' },
    ])).toBe('a=1+2')
  })

  it('encodes multipart text parts and skips file rows', () => {
    const body = encodeMultipart([
      { enabled: true, key: 'name', value: 'Ada', type: 'text' },
      { enabled: true, key: 'avatar', value: 'x.png', type: 'file' },
    ], '----b')
    expect(body).toContain('name="name"')
    expect(body).toContain('Ada')
    expect(body).not.toContain('avatar')
    expect(body.endsWith('------b--\r\n')).toBe(true)
  })

  it('infers none for empty GET-like drafts and json when content type is json', () => {
    expect(inferBodyMode('GET', '', 'application/json')).toBe('none')
    expect(inferBodyMode('POST', '{"a":1}', 'application/json')).toBe('json')
    expect(inferBodyMode('POST', 'a=1', 'application/x-www-form-urlencoded')).toBe('urlencoded')
  })
})
