import { pmFieldApi } from '@/modules/pm/api'
import type { FieldDefinition, FieldOptionSource } from '@/modules/pm/types'
import { PRIORITY_OPTIONS, STATUS_OPTIONS } from '@/modules/pm/types'

type SelectOption = { label: string; value: string }

const resolveCache = new Map<string, { options: SelectOption[]; expiresAt: number }>()

function isSelectField(field: FieldDefinition | undefined) {
  return field?.fieldType === 'SELECT' || field?.fieldType === 'MULTI_SELECT'
}

function staticOptionsFromConfig(field: FieldDefinition): SelectOption[] {
  const cfg = field.config?.options as Array<{ label: string; value: string }> | undefined
  if (!cfg?.length) return []
  return cfg.map((o) => ({ label: o.label, value: o.value }))
}

function optionSource(field: FieldDefinition): FieldOptionSource {
  const source = field.config?.optionSource
  return source === 'remote' ? 'remote' : 'static'
}

export function useFieldOptions(field: MaybeRef<FieldDefinition | undefined>) {
  const loading = ref(false)
  const error = ref<string | null>(null)
  const loadedOptions = ref<SelectOption[]>([])

  const resolvedField = computed(() => unref(field))

  const options = computed<SelectOption[]>(() => {
    const f = resolvedField.value
    if (!f) return []
    if (f.fieldKey === 'status' || f.fieldType === 'STATUS') return STATUS_OPTIONS
    if (f.fieldKey === 'priority' || f.fieldType === 'PRIORITY') return PRIORITY_OPTIONS
    if (!isSelectField(f)) return []
    if (f.id) return loadedOptions.value
    return staticOptionsFromConfig(f)
  })

  const labelMap = computed(() => {
    const map: Record<string, string> = {}
    for (const opt of options.value) {
      map[opt.value] = opt.label
    }
    return map
  })

  async function load(force = false) {
    const f = resolvedField.value
    if (!f || !isSelectField(f)) {
      loadedOptions.value = []
      error.value = null
      return
    }
    if (!f.id) {
      loadedOptions.value = staticOptionsFromConfig(f)
      error.value = null
      return
    }
    const cacheKey = String(f.id)
    const cacheSeconds = optionSource(f) === 'remote'
      ? ((f.config?.remoteOptions as { cacheSeconds?: number } | undefined)?.cacheSeconds ?? 300)
      : 60
    if (!force) {
      const cached = resolveCache.get(cacheKey)
      if (cached && cached.expiresAt > Date.now()) {
        loadedOptions.value = cached.options
        return
      }
    }
    loading.value = true
    error.value = null
    try {
      const list = await pmFieldApi.resolveOptions(f.id)
      const mapped = list.map((o) => ({ label: o.label, value: o.value }))
      loadedOptions.value = mapped
      resolveCache.set(cacheKey, { options: mapped, expiresAt: Date.now() + cacheSeconds * 1000 })
    } catch (e) {
      error.value = e instanceof Error ? e.message : '选项加载失败'
      loadedOptions.value = staticOptionsFromConfig(f)
    } finally {
      loading.value = false
    }
  }

  watch(resolvedField, () => load(), { immediate: true, deep: true })

  return { loading, error, options, labelMap, load }
}

export function invalidateFieldOptionsCache(fieldId?: number | string) {
  if (fieldId != null) {
    resolveCache.delete(String(fieldId))
    return
  }
  resolveCache.clear()
}
