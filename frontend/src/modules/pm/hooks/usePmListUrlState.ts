import { useCallback, useMemo } from 'react'
import { useSearchParams } from 'react-router'
import type { FilterFieldType, FilterTree, PmViewConfig } from '@/modules/pm/types'
import type { ColumnCatalog } from '@/modules/pm/utils/columns'
import {
  effectiveConfig,
  isListConfigDirty,
  parsePmListUrl,
  serializePmListUrl,
  type PmConfigTab,
  type PmListOverlay,
  type PmListUrlState,
} from '@/modules/pm/utils/listUrlState'

/**
 * 列表 URL 状态 Hook：URL 是唯一来源，写入统一 replace，避免筛选产生大量历史记录。
 */
export function usePmListUrlState(input: {
  fallbackConfig: PmViewConfig
  catalog: ColumnCatalog
  fieldTypes: Map<string, FilterFieldType>
  fieldsReady?: boolean
}) {
  const { fallbackConfig, catalog, fieldTypes, fieldsReady } = input
  const [searchParams, setSearchParams] = useSearchParams()
  const context = useMemo(
    () => ({ fallbackConfig, catalog, fieldTypes, fieldsReady }),
    [fallbackConfig, catalog, fieldTypes, fieldsReady],
  )
  const state = useMemo(() => parsePmListUrl(searchParams, context), [searchParams, context])
  const config = useMemo(() => effectiveConfig(state, fallbackConfig), [state, fallbackConfig])
  const isDirty = useMemo(
    () => isListConfigDirty(state, fallbackConfig),
    [state, fallbackConfig],
  )

  const commit = useCallback(
    (next: PmListUrlState) => {
      const params = serializePmListUrl(next, { fallbackConfig })
      // 详情弹窗参数不在列表状态模型里：列表侧任何写入都不能静默关闭详情。
      for (const key of ['detail', 'dtab'] as const) {
        const value = searchParams.get(key)
        if (value) params.set(key, value)
      }
      setSearchParams(params, { replace: true })
    },
    [fallbackConfig, setSearchParams, searchParams],
  )

  const patch = useCallback(
    (next: Partial<PmListUrlState>) => {
      commit({ ...state, ...next })
    },
    [commit, state],
  )

  const patchConfig = useCallback(
    (next: Partial<PmViewConfig>) => {
      const merged = { ...config, ...next }
      commit({
        ...state,
        cols: merged.listFields,
        search: merged.searchFields,
        ff: merged.filterFields,
        density: merged.density,
        sort: merged.sort,
        page: 1,
        selectedIds: [],
      })
    },
    [commit, config, state],
  )

  const resetConfig = useCallback(() => {
    const next = {
      ...state,
      cols: undefined,
      search: undefined,
      ff: undefined,
      density: undefined,
      sort: undefined,
      page: 1,
      selectedIds: [],
    }
    commit(next)
  }, [commit, state])

  const switchView = useCallback(
    (view: string) => {
      commit({
        ...state,
        view,
        cols: undefined,
        search: undefined,
        ff: undefined,
        density: undefined,
        sort: undefined,
        filterTree: { logic: 'AND', conditions: [], groups: [] } as FilterTree,
        page: 1,
        selectedIds: [],
        overlay: undefined,
      })
    },
    [commit, state],
  )

  const clearSelection = useCallback(() => {
    commit({ ...state, selectedIds: [] })
  }, [commit, state])

  const setOverlay = useCallback(
    (overlay?: PmListOverlay, configTab: PmConfigTab = 'columns') => {
      commit({ ...state, overlay, configTab })
    },
    [commit, state],
  )

  return {
    state,
    config,
    isDirty,
    patch,
    patchConfig,
    resetConfig,
    switchView,
    clearSelection,
    setOverlay,
    commit,
  }
}
