import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { collectionApi } from '@/modules/api-test/collection/api/collection'
import type { HttpMethod } from '@/modules/api-test/define/types/definition'

export interface CreateRequestInCollectionInput {
  projectId: number
  collectionId: number
  userId: number
  folderId?: number | null
  name?: string
  method?: HttpMethod
  path?: string
}

export interface CreateRequestInCollectionResult {
  definitionId: number
  itemId: number
  name: string
  method: string
  path: string
}

export async function createRequestInCollection(
  input: CreateRequestInCollectionInput,
): Promise<CreateRequestInCollectionResult> {
  const name = input.name ?? 'Untitled Request'
  const method = input.method ?? 'POST'
  const path = input.path ?? '/'

  const def = await apiDefinitionApi.create(
    {
      projectId: input.projectId,
      name,
      method,
      path,
    },
    input.userId,
  )

  try {
    const item = await collectionApi.addItem(
      input.collectionId,
      {
        folderId: input.folderId ?? null,
        definitionId: def.id,
        name,
      },
      input.userId,
    )

    return {
      definitionId: def.id,
      itemId: item.id,
      name,
      method: def.method ?? method,
      path: def.path ?? path,
    }
  } catch (e) {
    try {
      await apiDefinitionApi.delete(def.id)
    } catch {
      /* ignore rollback errors */
    }
    throw e
  }
}
