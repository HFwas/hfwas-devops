import { pmStatusApi } from '@/modules/pm/api'
import type { TransitionPostFunctionMeta } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'

const cache = new Map<string, TransitionPostFunctionMeta>()

export function useTransitionPostFunctionMeta(projectId: Ref<string | number>, typeCode: Ref<string>) {
  const loading = ref(false)
  const meta = ref<TransitionPostFunctionMeta | null>(null)

  const cacheKey = computed(() => `${routeId(projectId.value)}:${typeCode.value}`)

  const presetMap = computed(() => {
    const map: Record<string, TransitionPostFunctionMeta['presets'][number]> = {}
    for (const preset of meta.value?.presets ?? []) {
      map[preset.id] = preset
    }
    return map
  })

  const fieldMetaMap = computed(() => {
    const map: Record<string, TransitionPostFunctionMeta['fields'][number]> = {}
    for (const field of meta.value?.fields ?? []) {
      map[field.fieldKey] = field
    }
    return map
  })

  const fieldLabelMap = computed(() => {
    const map: Record<string, string> = {}
    for (const field of meta.value?.fields ?? []) {
      map[field.fieldKey] = field.fieldName
    }
    return map
  })

  async function load(force = false) {
    const key = cacheKey.value
    if (!key || key.startsWith(':') || key.endsWith(':')) return
    if (!force && cache.has(key)) {
      meta.value = cache.get(key) ?? null
      return
    }
    loading.value = true
    try {
      const data = await pmStatusApi.postFunctionMeta(routeId(projectId.value), typeCode.value)
      meta.value = data
      cache.set(key, data)
    } finally {
      loading.value = false
    }
  }

  watch([projectId, typeCode], () => load(), { immediate: true })

  return { loading, meta, presetMap, fieldMetaMap, fieldLabelMap, load }
}

export function invalidateTransitionPostFunctionMetaCache(projectId?: string | number, typeCode?: string) {
  if (projectId != null && typeCode) {
    cache.delete(`${routeId(projectId)}:${typeCode}`)
    return
  }
  cache.clear()
}
