import { pmStatusApi } from '@/modules/pm/api'
import type { StatusDefinition, TransitionOption } from '@/modules/pm/types'
import { STATUS_OPTIONS } from '@/modules/pm/types'

type SelectOption = { label: string; value: string }

const cache = new Map<string, { all: StatusDefinition[]; expiresAt: number }>()

function cacheKey(projectId: number | string, typeCode: string) {
  return `${projectId}:${typeCode}`
}

export function useStatusOptions(
  projectId: MaybeRef<number | string | undefined>,
  typeCode: MaybeRef<string | undefined>,
  fromStatus?: MaybeRef<string | undefined>,
  workItemId?: MaybeRef<number | string | undefined>,
) {
  const loading = ref(false)
  const allStatuses = ref<StatusDefinition[]>([])
  const transitionOptions = ref<TransitionOption[]>([])

  const labelMap = computed(() => {
    const map: Record<string, string> = {}
    for (const item of allStatuses.value.length ? allStatuses.value : STATUS_OPTIONS.map((o) => ({
      statusCode: o.value,
      statusName: o.label,
    }))) {
      map[item.statusCode] = item.statusName
    }
    return map
  })

  const selectOptions = computed<SelectOption[]>(() => {
    const from = unref(fromStatus)
    if (from) {
      const options = transitionOptions.value.map((t) => ({
        label: t.name || t.toStatusName,
        value: t.toStatus,
      }))
      const hasCurrent = options.some((o) => o.value === from)
      if (!hasCurrent) {
        const currentLabel = labelMap.value[from] ?? from
        options.unshift({ label: currentLabel, value: from })
      }
      return options
    }
    if (allStatuses.value.length) {
      return allStatuses.value.map((s) => ({ label: s.statusName, value: s.statusCode }))
    }
    return STATUS_OPTIONS
  })

  function findTransitionByToStatus(toStatus: string): TransitionOption | undefined {
    return transitionOptions.value.find((t) => t.toStatus === toStatus)
  }

  async function load(force = false) {
    const pid = unref(projectId)
    const type = unref(typeCode)
    if (pid == null || !type) {
      allStatuses.value = []
      transitionOptions.value = []
      return
    }
    const key = cacheKey(pid, type)
    if (!force) {
      const hit = cache.get(key)
      if (hit && hit.expiresAt > Date.now()) {
        allStatuses.value = hit.all
      }
    }
    loading.value = true
    try {
      if (force || !allStatuses.value.length) {
        allStatuses.value = await pmStatusApi.options(pid, type)
        cache.set(key, { all: allStatuses.value, expiresAt: Date.now() + 60_000 })
      }
      const from = unref(fromStatus)
      if (from) {
        const allowed = await pmStatusApi.allowed(pid, type, from, unref(workItemId))
        transitionOptions.value = allowed.transitions ?? []
      } else {
        transitionOptions.value = []
      }
    } finally {
      loading.value = false
    }
  }

  watch(
    [() => unref(projectId), () => unref(typeCode), () => unref(fromStatus), () => unref(workItemId)],
    () => load(),
    { immediate: true },
  )

  return {
    loading,
    allStatuses,
    transitionOptions,
    selectOptions,
    labelMap,
    findTransitionByToStatus,
    load,
  }
}

export function invalidateStatusOptionsCache(projectId?: number | string, typeCode?: string) {
  if (projectId != null && typeCode) {
    cache.delete(cacheKey(projectId, typeCode))
    return
  }
  cache.clear()
}
