import dayjs from 'dayjs'
import type { UserProfile } from '@/modules/user/types'
import type {
  FilterCondition,
  FilterGroup,
  FilterTree,
  QueryCondition,
  QueryConditionGroup,
  QuerySpec,
  SortSpec,
} from '@/modules/pm/types'
import type { ColumnCatalog, PmColumnMeta } from '@/modules/pm/utils/columns'

export interface QueryBuildContext {
  projectId: string
  /** 全部工作项时为空，查询绝不能传 "all"。 */
  typeCode?: string
  viewId: string
  filterTree: FilterTree
  q: string
  searchFields: string[]
  sort: SortSpec[]
  page: number
  size: number
  currentUserId?: string
  users: UserProfile[]
  catalog: ColumnCatalog
  /** 当前项目 risk 字段“高风险”选项的 key；不存在则不生成风险条件。 */
  riskHighValue?: string
  /** 全部工作项时限定为项目启用的类型，保证与类型计数合计一致。 */
  enabledTypeCodes?: string[]
  now?: Date | string
}

function dateTime(value: dayjs.Dayjs) {
  return value.format('YYYY-MM-DD HH:mm:ss')
}

function dateOnly(value: dayjs.Dayjs) {
  return value.format('YYYY-MM-DD')
}

function startOfIsoWeek(value: dayjs.Dayjs) {
  const day = value.day()
  const offset = day === 0 ? -6 : 1 - day
  return value.add(offset, 'day').startOf('day')
}

function nextMonday(value: dayjs.Dayjs) {
  const day = value.day()
  const offset = day === 0 ? 1 : 8 - day
  return value.add(offset, 'day').startOf('day')
}

function numeric(value: unknown): number | string {
  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : String(value ?? '')
}

function stringValue(value: unknown): string {
  return value == null ? '' : String(value)
}

function userValues(value: unknown): number[] {
  const list = Array.isArray(value) ? value : [value]
  return list.map((item) => Number(item)).filter((item) => Number.isFinite(item))
}

export function toQueryField(meta: PmColumnMeta): string {
  return meta.queryField
}

/** 搜索组：配置字段内 OR；无法映射的字段跳过。 */
export function buildSearchGroup(ctx: QueryBuildContext): QueryConditionGroup | null {
  const q = ctx.q.trim()
  if (!q) return null
  const conditions: QueryCondition[] = []
  for (const key of ctx.searchFields) {
    const meta = ctx.catalog.byKey.get(key) ?? ctx.catalog.missing(key)
    if (meta.missing) continue
    if (key === 'itemKey') {
      const match = /^(?:[A-Za-z][A-Za-z0-9_]*-)?(\d+)$/.exec(q)
      if (match) conditions.push({ field: 'item_no', operator: 'EQ', value: Number(match[1]) })
      continue
    }
    if (key === 'assignee_id') {
      const matched = ctx.users
        .filter((user) =>
          user.displayName?.toLowerCase() === q.toLowerCase() ||
          user.username?.toLowerCase() === q.toLowerCase())
        .map((user) => Number(user.id))
        .filter((id) => Number.isFinite(id))
      if (matched.length) conditions.push({ field: 'assignee_id', operator: 'IN', value: matched })
      continue
    }
    if (meta.filterType === 'text') {
      conditions.push({ field: meta.queryField, operator: 'LIKE', value: q })
      continue
    }
    if ((key === 'status' || key === 'priority') && meta.queryField === key) {
      conditions.push({ field: meta.queryField, operator: 'EQ', value: q })
    }
  }
  return conditions.length ? { logic: 'OR', conditions } : null
}

function mapDateCondition(condition: FilterCondition, field: string): QueryCondition | null {
  const raw = condition.value
  if (condition.operator === 'TODAY') {
    const start = dayjs().startOf('day')
    return { field, operator: 'BETWEEN', value: [dateTime(start), dateTime(start.endOf('day'))] }
  }
  if (condition.operator === 'THIS_WEEK') {
    const start = startOfIsoWeek(dayjs())
    return { field, operator: 'BETWEEN', value: [dateTime(start), dateTime(start.add(1, 'week').subtract(1, 'second'))] }
  }
  if (condition.operator === 'BETWEEN') {
    const values = Array.isArray(raw) ? raw : []
    if (values.length < 2 || !values[0] || !values[1]) return null
    return {
      field,
      operator: 'BETWEEN',
      value: [dateTime(dayjs(String(values[0])).startOf('day')), dateTime(dayjs(String(values[1])).endOf('day'))],
    }
  }
  if (!raw) return null
  const value = dayjs(String(raw))
  if (!value.isValid()) return null
  if (condition.operator === 'BEFORE') {
    return { field, operator: 'LT', value: dateTime(value.startOf('day')) }
  }
  if (condition.operator === 'AFTER') {
    return { field, operator: 'GT', value: dateTime(value.endOf('day')) }
  }
  return {
    field,
    operator: 'BETWEEN',
    value: [dateTime(value.startOf('day')), dateTime(value.endOf('day'))],
  }
}

function mapCondition(condition: FilterCondition, meta: PmColumnMeta): QueryCondition | null {
  if (meta.missing) return null
  const raw = condition.value
  const field = meta.queryField
  if (meta.filterType === 'date') {
    if (condition.operator === 'EMPTY') return { field, operator: 'IS_NULL', value: null }
    if (condition.operator === 'NOT_EMPTY') return { field, operator: 'IS_NOT_NULL', value: null }
    return mapDateCondition(condition, field)
  }
  switch (condition.operator) {
    case 'CONTAINS':
      return { field, operator: 'LIKE', value: stringValue(raw) }
    case 'NOT_CONTAINS':
      return { field, operator: 'NOT_LIKE', value: stringValue(raw) }
    case 'IN':
      return Array.isArray(raw) && raw.length
        ? { field, operator: 'IN', value: meta.filterType === 'user' ? userValues(raw) : raw }
        : null
    case 'NOT_IN':
      return Array.isArray(raw) && raw.length
        ? { field, operator: 'NOT_IN', value: meta.filterType === 'user' ? userValues(raw) : raw }
        : null
    case 'EMPTY':
      return { field, operator: 'IS_NULL', value: null }
    case 'NOT_EMPTY':
      return { field, operator: 'IS_NOT_NULL', value: null }
    case 'EQ':
    case 'NE':
      if (raw == null || raw === '') return null
      return { field, operator: condition.operator, value: meta.filterType === 'user' ? numeric(raw) : raw }
    case 'GT':
    case 'LT':
    case 'GTE':
    case 'LTE':
      if (raw == null || raw === '') return null
      return { field, operator: condition.operator, value: meta.filterType === 'number' ? numeric(raw) : raw }
    case 'BETWEEN': {
      const values = Array.isArray(raw) ? raw : []
      if (values.length < 2 || values.some((item) => item == null || item === '')) return null
      return {
        field,
        operator: 'BETWEEN',
        value: meta.filterType === 'number' ? values.map(numeric) : values,
      }
    }
    default:
      return null
  }
}

function mapGroup(group: FilterGroup, catalog: ColumnCatalog): QueryConditionGroup | null {
  const conditions = group.conditions
    .map((condition) => {
      const meta = catalog.byKey.get(condition.field) ?? catalog.missing(condition.field)
      return mapCondition(condition, meta)
    })
    .filter((condition): condition is QueryCondition => Boolean(condition))
  return conditions.length ? { logic: group.logic, conditions } : null
}

export function buildFilterGroup(tree: FilterTree, catalog: ColumnCatalog): QueryConditionGroup | null {
  const conditions = tree.conditions
    .map((condition) => {
      const meta = catalog.byKey.get(condition.field) ?? catalog.missing(condition.field)
      return mapCondition(condition, meta)
    })
    .filter((condition): condition is QueryCondition => Boolean(condition))
  const groups = tree.groups
    .map((group) => mapGroup(group, catalog))
    .filter((group): group is QueryConditionGroup => Boolean(group))
  if (!conditions.length && !groups.length) return null
  return { logic: tree.logic, conditions, groups }
}

export function buildViewPredicate(ctx: QueryBuildContext): QueryConditionGroup | null {
  if (ctx.viewId === 'mine') {
    if (!ctx.currentUserId) return null
    return {
      logic: 'AND',
      conditions: [{ field: 'assignee_id', operator: 'EQ', value: numeric(ctx.currentUserId) }],
    }
  }
  if (ctx.viewId === 'week') {
    const now = dayjs(ctx.now ?? undefined)
    const start = now.startOf('day')
    return {
      logic: 'AND',
      conditions: [
        { field: 'end_time', operator: 'GTE', value: dateTime(start) },
        { field: 'end_time', operator: 'LT', value: dateTime(nextMonday(start)) },
      ],
    }
  }
  if (ctx.viewId === 'blocked') {
    if (ctx.riskHighValue) {
      return {
        logic: 'AND',
        conditions: [{ field: 'custom.risk', operator: 'EQ', value: ctx.riskHighValue }],
      }
    }
    return {
      logic: 'AND',
      conditions: [{ field: 'status', operator: 'EQ', value: 'blocked' }],
    }
  }
  return null
}

/** 视图/搜索/筛选各占一个 group，避免顶层 OR 把它们混在一起。 */
export function buildQuerySpec(ctx: QueryBuildContext): QuerySpec {
  const baseConditions: QueryCondition[] = []
  if (!ctx.typeCode && ctx.enabledTypeCodes?.length) {
    baseConditions.push({ field: 'type_code', operator: 'IN', value: ctx.enabledTypeCodes })
  }
  const groups = [
    buildViewPredicate(ctx),
    buildSearchGroup(ctx),
    buildFilterGroup(ctx.filterTree, ctx.catalog),
  ].filter((group): group is QueryConditionGroup => Boolean(group))
  return {
    projectId: ctx.projectId,
    ...(ctx.typeCode ? { typeCode: ctx.typeCode } : {}),
    logic: 'AND',
    conditions: baseConditions,
    groups,
    sort: ctx.sort,
    pageNo: ctx.page,
    pageSize: ctx.size,
  }
}

export function estimateCountSpec(spec: QuerySpec): QuerySpec {
  return { ...spec, pageNo: 1, pageSize: 1 }
}
