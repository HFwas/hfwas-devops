import { pmModuleApi } from '@/modules/pm/api'
import type { PmProjectModule } from '@/modules/pm/types'

const cache = new Map<number, { flat: PmProjectModule[]; labelMap: Record<number, string> }>()

export function useProjectModules(projectId: Ref<number | undefined>) {
  const loading = ref(false)
  const flatModules = ref<PmProjectModule[]>([])
  const labelMap = ref<Record<number, string>>({})

  async function load(force = false) {
    const id = projectId.value
    if (!id) {
      flatModules.value = []
      labelMap.value = {}
      return
    }
    if (!force && cache.has(id)) {
      const cached = cache.get(id)!
      flatModules.value = cached.flat
      labelMap.value = cached.labelMap
      return
    }
    loading.value = true
    try {
      const list = await pmModuleApi.flat(id)
      const map: Record<number, string> = {}
      for (const item of list) {
        if (item.id != null) {
          map[item.id] = item.pathLabel ?? item.name
        }
      }
      flatModules.value = list
      labelMap.value = map
      cache.set(id, { flat: list, labelMap: map })
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

export function invalidateProjectModules(projectId: number) {
  cache.delete(projectId)
}
