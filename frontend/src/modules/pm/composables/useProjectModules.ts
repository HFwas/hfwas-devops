import { pmModuleApi } from '@/modules/pm/api'
import type { PmProjectModule } from '@/modules/pm/types'
import { asId, type EntityId } from '@/modules/pm/utils/id'

import type { EntityId } from '@/modules/pm/utils/id'

const cache = new Map<string, { flat: PmProjectModule[]; labelMap: Record<string, string> }>()

export function useProjectModules(projectId: Ref<EntityId | undefined>) {
  const loading = ref(false)
  const flatModules = ref<PmProjectModule[]>([])
  const labelMap = ref<Record<string, string>>({})

  async function load(force = false) {
    const id = projectId.value
    if (!id) {
      flatModules.value = []
      labelMap.value = {}
      return
    }
    const cacheKey = asId(id)
    if (!force && cache.has(cacheKey)) {
      const cached = cache.get(cacheKey)!
      flatModules.value = cached.flat
      labelMap.value = cached.labelMap
      return
    }
    loading.value = true
    try {
      const list = await pmModuleApi.flat(id)
      const map: Record<string, string> = {}
      for (const item of list) {
        if (item.id != null) {
          map[asId(item.id)] = item.pathLabel ?? item.name
        }
      }
      flatModules.value = list
      labelMap.value = map
      cache.set(cacheKey, { flat: list, labelMap: map })
    } finally {
      loading.value = false
    }
  }

  function invalidate() {
    const id = projectId.value
    if (id) cache.delete(id)
  }

  const selectOptions = computed(() =>
    flatModules.value
      .filter((m) => m.id != null)
      .map((m) => ({ label: m.pathLabel ?? m.name, value: m.id as number })),
  )

  watch(projectId, () => load(), { immediate: true })

  return { loading, flatModules, labelMap, selectOptions, load, invalidate }
}

export function invalidateProjectModules(projectId: EntityId) {
  cache.delete(asId(projectId))
}
