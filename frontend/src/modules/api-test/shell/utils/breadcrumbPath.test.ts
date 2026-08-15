import { describe, expect, it } from 'vitest'
import type { CollectionFolderVO } from '@/modules/api-test/collection/types/collection'
import { buildBreadcrumbSegments, resolveFolderNames } from './breadcrumbPath'

describe('buildBreadcrumbSegments', () => {
  it('builds collection path with folders', () => {
    expect(
      buildBreadcrumbSegments({
        source: 'collection',
        title: 'List',
        collectionName: 'Auth',
        folderNames: ['Apps', 'API'],
      }),
    ).toEqual(['Auth', 'Apps', 'API', 'List'])
  })

  it('scratch and overview', () => {
    expect(buildBreadcrumbSegments({ source: 'scratch', title: 'Untitled' })).toEqual([
      'Scratch',
      'Untitled',
    ])
    expect(buildBreadcrumbSegments({ source: 'collectionOverview', title: 'Auth' })).toEqual([])
  })
})

describe('resolveFolderNames', () => {
  const folders: CollectionFolderVO[] = [
    {
      id: 1,
      collectionId: 1,
      parentId: null,
      name: 'Auth',
      description: '',
      sortOrder: 0,
      children: [
        {
          id: 2,
          collectionId: 1,
          parentId: 1,
          name: 'Apps',
          description: '',
          sortOrder: 0,
          children: [
            {
              id: 3,
              collectionId: 1,
              parentId: 2,
              name: 'API',
              description: '',
              sortOrder: 0,
              children: [],
              items: [],
            },
          ],
          items: [],
        },
      ],
      items: [],
    },
  ]

  it('returns root-to-leaf names for nested folder', () => {
    expect(resolveFolderNames(folders, 3)).toEqual(['Auth', 'Apps', 'API'])
  })

  it('returns empty array when folderId is null or undefined', () => {
    expect(resolveFolderNames(folders, null)).toEqual([])
    expect(resolveFolderNames(folders, undefined)).toEqual([])
  })
})
