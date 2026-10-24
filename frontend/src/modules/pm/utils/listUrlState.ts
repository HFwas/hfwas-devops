import type { FilterFieldType, FilterTree, PmViewConfig, SortSpec } from '@/modules/pm/types'
import type { ColumnCatalog } from '@/modules/pm/utils/columns'
import { DEFAULT_COLUMNS, normalizeColumnKeys, sameSort, sameStringArray } from '@/modules/pm/utils/viewConfig'
import { emptyFilterTree, isEmptyFilterTree, sanitizeFilterTree } from '@/modules/pm/utils/filterTree'

export type PmListOverlay = 'view-config' | 'filter' | 'save-view' | 'bulk-edit' | 'transition' | 'delete'
export type PmConfigTab = 'columns' | 'search' | 'filter'

export interface PmListUrlState {
  view: string
  q: string
  cols?: string[]
  search?: string[]
  ff?: string[]
  density?: PmViewConfig['density']
  sort?: SortSpec[]
  filterTree: FilterTree
  page: number
  size: number
  selectedIds: string[]
  overlay?: PmListOverlay
  configTab: PmConfigTab
}

export interface ListUrlContext {
  fallbackConfig: PmViewConfig
  catalog: ColumnCatalog
  fieldTypes: Map<string, FilterFieldType>
  /** 字段元数据是否已加载；未加载时保留原始筛选/排序，避免刷新丢配置。 */
  fieldsReady?: boolean
}

export const PAGE_SIZES = [10, 20, 50, 100] as const
export const DEFAULT_PAGE_SIZE = 10

function listParam(params: URLSearchParams, key: string): string[] | undefined {
  const raw = params.get(key)
  if (!raw) return undefined
  const values = raw.split(',').map((item) => item.trim()).filter(Boolean)
  return values.length ? values : undefined
}

function validKey(key: string) {
  return /^[A-Za-z_][A-Za-z0-9_]{0,63}$/.test(key)
}

function knownColumn(key: string, ctx: ListUrlContext) {
  // 字段元数据加载前也要保留自定义列，避免刷新时丢 URL 配置。
  return ctx.catalog.byKey.has(key) || DEFAULT_COLUMNS.includes(key) || validKey(key)
}

function sortParam(params: URLSearchParams, ctx: ListUrlContext): SortSpec[] | undefined {
  const raw = params.get('sort')
  if (!raw) return undefined
  const sorts = raw
    .split(',')
    .map((part) => {
      const [field, order] = part.split(':')
      if (!field) return null
      if (ctx.fieldsReady !== false && !ctx.catalog.byKey.get(field)?.sortable) return null
      if (ctx.fieldsReady === false && !validKey(field)) return null
      return { field, order: order?.toUpperCase() === 'ASC' ? 'ASC' : 'DESC' } as SortSpec
    })
    .filter((item): item is SortSpec => Boolean(item))
  return sorts.length ? sorts : undefined
}

function pageParam(params: URLSearchParams) {
  const parsed = Number(params.get('page'))
  return Number.isInteger(parsed) && parsed >= 1 ? parsed : 1
}

function sizeParam(params: URLSearchParams) {
  const parsed = Number(params.get('size'))
  return PAGE_SIZES.includes(parsed as (typeof PAGE_SIZES)[number]) ? parsed : DEFAULT_PAGE_SIZE
}

function normalizeRawTree(input: unknown): FilterTree {
  if (!input || typeof input !== 'object') return emptyFilterTree()
  const raw = input as Partial<FilterTree>
  return {
    logic: raw.logic === 'OR' ? 'OR' : 'AND',
    conditions: Array.isArray(raw.conditions) ? raw.conditions as FilterTree['conditions'] : [],
    groups: Array.isArray(raw.groups) ? raw.groups as FilterTree['groups'] : [],
  }
}

export function parsePmListUrl(params: URLSearchParams, ctx: ListUrlContext): PmListUrlState {
  const cols = normalizeColumnKeys(listParam(params, 'cols'))?.filter((key) => knownColumn(key, ctx))
  const search = normalizeColumnKeys(listParam(params, 'search'))?.filter((key) =>
    ctx.catalog.byKey.get(key)?.searchable || ['title', 'description', 'itemKey'].includes(key) || validKey(key))
  const ff = normalizeColumnKeys(listParam(params, 'ff'))?.filter((key) =>
    ctx.catalog.byKey.get(key)?.filterable || (ctx.catalog.candidates.length <= 10 && validKey(key)))
  const density = params.get('density')
  const overlay = params.get('overlay')
  const allowedOverlays: PmListOverlay[] = ['view-config', 'filter', 'save-view', 'bulk-edit', 'transition', 'delete']
  const tab = params.get('tab')
  let filterTree = emptyFilterTree()
  const rawFilter = params.get('f')
  if (rawFilter) {
    try {
      const parsed = JSON.parse(rawFilter)
      // 字段元数据未加载时先保留原始树，等 fieldTypes 就绪后再清洗。
      filterTree = ctx.fieldsReady === false
        ? normalizeRawTree(parsed)
        : sanitizeFilterTree(parsed, { fieldTypes: ctx.fieldTypes })
    } catch {
      filterTree = emptyFilterTree()
    }
  }
  return {
    view: params.get('view') || 'dev',
    q: params.get('q')?.trim() ?? '',
    ...(cols?.length ? { cols } : {}),
    ...(search?.length ? { search } : {}),
    ...(ff?.length ? { ff } : {}),
    ...(density === 'compact' || density === 'standard' ? { density } : {}),
    ...(sortParam(params, ctx) ? { sort: sortParam(params, ctx) } : {}),
    filterTree,
    page: pageParam(params),
    size: sizeParam(params),
    selectedIds: listParam(params, 'sel') ?? [],
    ...(overlay && allowedOverlays.includes(overlay as PmListOverlay) ? { overlay: overlay as PmListOverlay } : {}),
    configTab: tab === 'search' || tab === 'filter' ? tab : 'columns',
  }
}

export function effectiveConfig(state: PmListUrlState, fallback: PmViewConfig): PmViewConfig {
  return {
    listFields: state.cols ?? fallback.listFields,
    searchFields: state.search ?? fallback.searchFields,
    filterFields: state.ff ?? fallback.filterFields,
    density: state.density ?? fallback.density,
    sort: state.sort ?? fallback.sort,
  }
}

export function isListConfigDirty(state: PmListUrlState, fallback: PmViewConfig, baselineTree: FilterTree = emptyFilterTree()) {
  const config = effectiveConfig(state, fallback)
  return (
    !sameStringArray(config.listFields, fallback.listFields) ||
    !sameStringArray(config.searchFields, fallback.searchFields) ||
    !sameStringArray(config.filterFields, fallback.filterFields) ||
    config.density !== fallback.density ||
    !sameSort(config.sort, fallback.sort) ||
    JSON.stringify(state.filterTree) !== JSON.stringify(baselineTree)
  )
}

export function serializePmListUrl(
  state: PmListUrlState,
  ctx: { fallbackConfig: PmViewConfig },
): URLSearchParams {
  const params = new URLSearchParams()
  if (state.view && state.view !== 'dev') params.set('view', state.view)
  if (state.q) params.set('q', state.q)
  if (state.cols && !sameStringArray(state.cols, ctx.fallbackConfig.listFields)) {
    params.set('cols', state.cols.join(','))
  }
  if (state.search && !sameStringArray(state.search, ctx.fallbackConfig.searchFields)) {
    params.set('search', state.search.join(','))
  }
  if (state.ff && !sameStringArray(state.ff, ctx.fallbackConfig.filterFields)) {
    params.set('ff', state.ff.join(','))
  }
  if (state.density && state.density !== ctx.fallbackConfig.density) {
    params.set('density', state.density)
  }
  if (state.sort && !sameSort(state.sort, ctx.fallbackConfig.sort)) {
    params.set('sort', state.sort.map((sort) => `${sort.field}:${sort.order}`).join(','))
  }
  if (!isEmptyFilterTree(state.filterTree)) params.set('f', JSON.stringify(state.filterTree))
  if (state.page > 1) params.set('page', String(state.page))
  if (state.size !== DEFAULT_PAGE_SIZE) params.set('size', String(state.size))
  if (state.selectedIds.length) params.set('sel', state.selectedIds.join(','))
  if (state.overlay) {
    params.set('overlay', state.overlay)
    if (state.overlay === 'view-config' && state.configTab !== 'columns') params.set('tab', state.configTab)
  }
  return params
}

export function pruneSelection(selectedIds: string[], pageIds: string[]) {
  const pageSet = new Set(pageIds)
  return selectedIds.filter((id) => pageSet.has(id))
}
