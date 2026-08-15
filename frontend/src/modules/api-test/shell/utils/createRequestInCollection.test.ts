import { describe, it, expect, vi, beforeEach } from 'vitest'
import { createRequestInCollection } from './createRequestInCollection'

vi.mock('@/modules/api-test/define/api/definition', () => ({
  apiDefinitionApi: {
    create: vi.fn(),
    delete: vi.fn(),
  },
}))
vi.mock('@/modules/api-test/collection/api/collection', () => ({
  collectionApi: {
    addItem: vi.fn(),
  },
}))

import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { collectionApi } from '@/modules/api-test/collection/api/collection'

beforeEach(() => vi.clearAllMocks())

it('creates definition then item', async () => {
  vi.mocked(apiDefinitionApi.create).mockResolvedValue({ id: 101, name: 'Untitled Request', method: 'POST', path: '/' } as any)
  vi.mocked(collectionApi.addItem).mockResolvedValue({ id: 55, definitionId: 101 } as any)
  const result = await createRequestInCollection({
    projectId: 1, collectionId: 7, userId: 1,
  })
  expect(apiDefinitionApi.create).toHaveBeenCalledWith(
    expect.objectContaining({ projectId: 1, name: 'Untitled Request', method: 'POST', path: '/' }),
    1,
  )
  expect(collectionApi.addItem).toHaveBeenCalledWith(
    7,
    expect.objectContaining({ definitionId: 101, folderId: null }),
    1,
  )
  expect(result).toEqual({
    definitionId: 101, itemId: 55, name: 'Untitled Request', method: 'POST', path: '/',
  })
})

it('deletes definition if addItem fails', async () => {
  vi.mocked(apiDefinitionApi.create).mockResolvedValue({ id: 101 } as any)
  vi.mocked(collectionApi.addItem).mockRejectedValue(new Error('boom'))
  await expect(createRequestInCollection({
    projectId: 1, collectionId: 7, userId: 1,
  })).rejects.toThrow('boom')
  expect(apiDefinitionApi.delete).toHaveBeenCalledWith(101)
})
