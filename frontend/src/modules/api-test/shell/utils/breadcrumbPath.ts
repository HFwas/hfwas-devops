import type { CollectionFolderVO } from '@/modules/api-test/collection/types/collection'
import type { TabSource } from '@/modules/api-test/shell/types/workspace'

export function buildBreadcrumbSegments(input: {
  source: TabSource
  title: string
  collectionName?: string | null
  folderNames?: string[]
}): string[] {
  const { source, title, collectionName, folderNames = [] } = input

  switch (source) {
    case 'collection': {
      const segments = [collectionName, ...folderNames, title].filter(
        (s): s is string => s != null && s !== '',
      )
      return segments
    }
    case 'scratch':
      return ['Scratch', title]
    case 'definition':
      return ['Definition', title]
    case 'collectionOverview':
      return []
    default:
      return []
  }
}

function flattenFolders(folders: CollectionFolderVO[]): Map<number, CollectionFolderVO> {
  const map = new Map<number, CollectionFolderVO>()

  function walk(nodes: CollectionFolderVO[]) {
    for (const node of nodes) {
      map.set(node.id, node)
      if (node.children.length > 0) {
        walk(node.children)
      }
    }
  }

  walk(folders)
  return map
}

export function resolveFolderNames(
  folders: CollectionFolderVO[],
  folderId: number | null | undefined,
): string[] {
  if (folderId == null) {
    return []
  }

  const byId = flattenFolders(folders)
  const names: string[] = []
  let current = byId.get(folderId)

  while (current) {
    names.unshift(current.name)
    current = current.parentId != null ? byId.get(current.parentId) : undefined
  }

  return names
}
