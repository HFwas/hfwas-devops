import { beforeEach, describe, expect, it, vi } from 'vitest'

const detailMock = vi.fn()

vi.mock('@/modules/api-test/define/api/definition', () => ({
  apiDefinitionApi: {
    detail: (...args: unknown[]) => detailMock(...args),
  },
}))

import { loadDefinitionIntoTab } from './loadDefinitionDraft'

describe('loadDefinitionIntoTab', () => {
  beforeEach(() => {
    detailMock.mockReset()
  })

  it('maps path, method, contentType and query/header/body params into a draft', async () => {
    detailMock.mockResolvedValue({
      id: 7,
      name: 'Create User',
      path: '/users',
      method: 'POST',
      contentType: 'application/json',
      params: [
        { paramType: 'query', name: 'page', defaultValue: '1' },
        { paramType: 'header', name: 'X-Token', defaultValue: 'abc' },
        { paramType: 'body', name: 'payload', defaultValue: '{"ok":true}' },
        { paramType: 'path', name: 'id', defaultValue: '9' },
      ],
    })

    const { detail, draft } = await loadDefinitionIntoTab(7)

    expect(detailMock).toHaveBeenCalledWith(7)
    expect(detail.name).toBe('Create User')
    expect(draft.url).toBe('/users')
    expect(draft.method).toBe('POST')
    expect(draft.contentType).toBe('application/json')
    expect(draft.queryParams).toEqual({ page: '1' })
    expect(draft.headers).toEqual({ 'X-Token': 'abc' })
    expect(draft.body).toBe('{"ok":true}')
  })

  it('defaults empty path and application/json contentType when missing', async () => {
    detailMock.mockResolvedValue({
      id: 1,
      name: 'Ping',
      path: null,
      method: 'GET',
      contentType: null,
      params: null,
    })

    const { draft } = await loadDefinitionIntoTab(1)
    expect(draft.url).toBe('')
    expect(draft.contentType).toBe('application/json')
    expect(draft.queryParams).toEqual({})
    expect(draft.headers).toEqual({})
    expect(draft.body).toBe('')
  })

  it('uses empty string when query or header defaultValue is missing', async () => {
    detailMock.mockResolvedValue({
      id: 2,
      name: 'List',
      path: '/items',
      method: 'GET',
      contentType: 'application/json',
      params: [
        { paramType: 'query', name: 'q', defaultValue: null },
        { paramType: 'header', name: 'Accept', defaultValue: undefined },
        { paramType: 'body', name: 'payload', defaultValue: '' },
      ],
    })

    const { draft } = await loadDefinitionIntoTab(2)
    expect(draft.queryParams).toEqual({ q: '' })
    expect(draft.headers).toEqual({ Accept: '' })
    expect(draft.body).toBe('')
  })
})
