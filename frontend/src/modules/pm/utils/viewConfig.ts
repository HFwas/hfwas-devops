import type { FilterTree, PmSavedView, PmViewConfig, SortSpec } from '@/modules/pm/types'
import { emptyFilterTree } from '@/modules/pm/utils/filterTree'

export type BuiltInViewId = 'dev' | 'mine' | 'week' | 'blocked'

export interface BuiltInView {
  id: BuiltInViewId
  label: string
  config: PmViewConfig
}

export const DEFAULT_COLUMNS = [
  'itemKey',
  'type_code',
  'title',
  'status',
  'priority',
  'assignee_id',
  'end_time',
  'storyPoints',
]

export const DEFAULT_SEARCH_FIELDS = ['title', 'itemKey', 'assignee_id', 'description']
export const DEFAULT_FILTER_FIELDS = ['status', 'priority', 'assignee_id', 'end_time', 'risk']
export const DEFAULT_DENSITY: PmViewConfig['density'] = 'standard'
export const DEFAULT_SORT: SortSpec[] = [{ field: 'update_time', order: 'DESC' }]

export function defaultViewConfig(): PmViewConfig {
  return {
    listFields: [...DEFAULT_COLUMNS],
    searchFields: [...DEFAULT_SEARCH_FIELDS],
    filterFields: [...DEFAULT_FILTER_FIELDS],
    density: DEFAULT_DENSITY,
    sort: DEFAULT_SORT.map((sort) => ({ ...sort })),
  }
}

export const BUILT_IN_VIEWS: BuiltInView[] = [
  { id: 'dev', label: '研发待办', config: defaultViewConfig() },
  {
    id: 'mine',
    label: '我的事项',
    config: {
      ...defaultViewConfig(),
      sort: [{ field: 'priority', order: 'ASC' }],
    },
  },
  {
    id: 'week',
    label: '本周到期',
    config: {
      listFields: ['itemKey', 'type_code', 'title', 'status', 'assignee_id', 'end_time', 'storyPoints'],
      searchFields: [...DEFAULT_SEARCH_FIELDS],
      filterFields: [...DEFAULT_FILTER_FIELDS],
      density: DEFAULT_DENSITY,
      sort: [{ field: 'end_time', order: 'ASC' }],
    },
  },
  {
    id: 'blocked',
    label: '阻塞风险',
    config: {
      listFields: ['itemKey', 'type_code', 'title', 'status', 'priority', 'assignee_id', 'risk', 'update_time'],
      searchFields: [...DEFAULT_SEARCH_FIELDS],
      filterFields: [...DEFAULT_FILTER_FIELDS],
      density: DEFAULT_DENSITY,
      sort: [{ field: 'priority', order: 'ASC' }],
    },
  },
]

export function builtInView(id: string): BuiltInView | undefined {
  return BUILT_IN_VIEWS.find((view) => view.id === id)
}

export function isBuiltInView(id: string): id is BuiltInViewId {
  return BUILT_IN_VIEWS.some((view) => view.id === id)
}

const COLUMN_ALIASES: Record<string, string> = {
  type: 'type_code',
  assignee: 'assignee_id',
  updated: 'update_time',
  updated_at: 'update_time',
  created: 'create_time',
}

/** URL/旧配置中的列别名统一为后端字段 key。 */
export function normalizeColumnKey(key: string) {
  return COLUMN_ALIASES[key] ?? key
}

export function normalizeColumnKeys(keys: string[] | undefined): string[] | undefined {
  if (!keys) return undefined
  return [...new Set(keys.map(normalizeColumnKey).filter(Boolean))]
}

function stringArray(value: unknown): string[] | undefined {
  if (!Array.isArray(value)) return undefined
  return value.filter((item): item is string => typeof item === 'string')
}

function sortArray(value: unknown): SortSpec[] | undefined {
  if (!Array.isArray(value)) return undefined
  const sorts: SortSpec[] = value
    .filter((item): item is SortSpec => Boolean(item) && typeof item === 'object')
    .map((item) => ({
      field: String(item.field),
      order: (item.order === 'ASC' ? 'ASC' : 'DESC') as SortSpec['order'],
    }))
    .filter((item) => item.field)
  return sorts.length ? sorts : undefined
}

export interface NormalizedSavedView {
  config: PmViewConfig
  filterTree: FilterTree
}

/** 兼容新形状 `{version,listFields,...}` 与旧形状 `[{fieldKey}]`。 */
export function normalizeSavedView(view: PmSavedView): NormalizedSavedView {
  const base = defaultViewConfig()
  const first = Array.isArray(view.columns) ? view.columns[0] : undefined
  let config = base
  let filterTree = emptyFilterTree()
  if (first && typeof first === 'object') {
    const raw = first as Record<string, unknown>
    if (Array.isArray(raw.listFields)) {
      config = {
        listFields: normalizeColumnKeys(stringArray(raw.listFields)) ?? base.listFields,
        searchFields: normalizeColumnKeys(stringArray(raw.searchFields)) ?? base.searchFields,
        filterFields: normalizeColumnKeys(stringArray(raw.filterFields)) ?? base.filterFields,
        density: raw.density === 'compact' ? 'compact' : 'standard',
        sort: sortArray(raw.sort) ?? base.sort,
      }
      if (raw.filterTree && typeof raw.filterTree === 'object') {
        filterTree = raw.filterTree as FilterTree
      }
    } else if (Array.isArray(view.columns)) {
      const legacyKeys = view.columns
        .map((column) => (column && typeof column === 'object' ? column.fieldKey : undefined))
        .filter((key): key is string => typeof key === 'string')
      if (legacyKeys.length) {
        config = { ...base, listFields: normalizeColumnKeys(legacyKeys) ?? base.listFields }
      }
    }
  }
  return { config, filterTree }
}

export function configToSavedColumns(config: PmViewConfig, filterTree: FilterTree): Array<Record<string, unknown>> {
  return [
    {
      version: 1,
      listFields: config.listFields,
      searchFields: config.searchFields,
      filterFields: config.filterFields,
      density: config.density,
      sort: config.sort,
      filterTree,
    },
  ]
}

export function sameStringArray(a: string[] = [], b: string[] = []) {
  return a.length === b.length && a.every((value, index) => value === b[index])
}

export function sameSort(a: SortSpec[] = [], b: SortSpec[] = []) {
  return a.length === b.length && a.every((value, index) =>
    value.field === b[index]?.field && value.order === b[index]?.order)
}

export function sameViewConfig(a: PmViewConfig, b: PmViewConfig) {
  return (
    sameStringArray(a.listFields, b.listFields) &&
    sameStringArray(a.searchFields, b.searchFields) &&
    sameStringArray(a.filterFields, b.filterFields) &&
    a.density === b.density &&
    sameSort(a.sort, b.sort)
  )
}

export function sameFilterTree(a: FilterTree, b: FilterTree) {
  return JSON.stringify(a) === JSON.stringify(b)
}

export function isViewDirty(
  current: { config: PmViewConfig; filterTree: FilterTree },
  baseline: { config: PmViewConfig; filterTree: FilterTree },
) {
  return !sameViewConfig(current.config, baseline.config) || !sameFilterTree(current.filterTree, baseline.filterTree)
}
