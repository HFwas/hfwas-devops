import { pmMetaApi, pmProjectIssueTypeApi } from '@/modules/pm/api'
import type { PmWorkItemType } from '@/modules/pm/types'
import type { EntityId } from '@/modules/pm/utils/id'

const projectCache = new Map<string, { types: PmWorkItemType[]; expiresAt: number }>()
let globalCache: { types: PmWorkItemType[]; expiresAt: number } | null = null

export function invalidateIssueTypeCaches(projectId?: EntityId) {
  if (projectId != null) {
    projectCache.delete(String(projectId))
  } else {
    projectCache.clear()
  }
  globalCache = null
}

export function useProjectIssueTypes(projectId: MaybeRef<EntityId | undefined>) {
  const types = ref<PmWorkItemType[]>([])
  const loading = ref(false)

  async function load(force = false) {
    const pid = unref(projectId)
    if (pid == null || pid === '') {
      types.value = []
      return
    }
    const key = String(pid)
    if (!force) {
      const hit = projectCache.get(key)
      if (hit && hit.expiresAt > Date.now()) {
        types.value = hit.types
        return
      }
    }
    loading.value = true
    try {
      const list = await pmProjectIssueTypeApi.list(pid)
      types.value = list ?? []
      projectCache.set(key, { types: types.value, expiresAt: Date.now() + 60_000 })
    } finally {
      loading.value = false
    }
  }

  watch(() => unref(projectId), () => load(), { immediate: true })

  return { types, loading, load }
}

export function useGlobalIssueTypes(includeDisabled = false) {
  const types = ref<PmWorkItemType[]>([])
  const loading = ref(false)

  async function load(force = false) {
    if (!force && !includeDisabled && globalCache && globalCache.expiresAt > Date.now()) {
      types.value = globalCache.types
      return
    }
    loading.value = true
    try {
      const list = await pmMetaApi.types(includeDisabled)
      types.value = list ?? []
      if (!includeDisabled) {
        globalCache = { types: types.value, expiresAt: Date.now() + 60_000 }
      }
    } finally {
      loading.value = false
    }
  }

  onMounted(() => load())

  return { types, loading, load }
}
