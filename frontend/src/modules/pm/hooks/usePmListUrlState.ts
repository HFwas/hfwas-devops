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
      setSearchParams(serializePmListUrl(next, { fallbackConfig }), { replace: true })
    },
    [fallbackConfig, setSearchParams],
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
