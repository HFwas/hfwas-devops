import { describe, expect, it } from 'vitest'
import type { FieldDefinition, FilterTree } from '@/modules/pm/types'
import type { UserProfile } from '@/modules/user/types'
import { buildColumnCatalog } from '@/modules/pm/utils/columns'
import {
  buildQuerySpec,
  estimateCountSpec,
} from '@/modules/pm/utils/querySpec'
import { createCondition, createGroup, emptyFilterTree } from '@/modules/pm/utils/filterTree'

const fields: FieldDefinition[] = [
  { fieldKey: 'score', fieldName: '故事点', fieldType: 'NUMBER', systemFlag: 0 },
  { fieldKey: 'risk', fieldName: '风险', fieldType: 'SELECT', systemFlag: 0 },
  { fieldKey: 'note', fieldName: '备注', fieldType: 'TEXT', systemFlag: 0 },
]
const catalog = buildColumnCatalog(fields)
const users = [
  { id: '2', username: 'zhangsan', displayName: '张三', role: 'user' },
  { id: '3', username: 'lisi', displayName: '李四', role: 'user' },
] as UserProfile[]

function base(overrides: Partial<Parameters<typeof buildQuerySpec>[0]> = {}) {
  return {
    projectId: '10',
    typeCode: 'task',
    viewId: 'dev',
    filterTree: emptyFilterTree(),
    q: '',
    searchFields: ['title', 'itemKey', 'assignee_id', 'description'],
    sort: [{ field: 'update_time', order: 'DESC' as const }],
    page: 1,
    size: 10,
    currentUserId: '2',
    users,
    catalog,
    riskHighValue: 'high',
    ...overrides,
  }
}

function groups(spec: ReturnType<typeof buildQuerySpec>) {
  return spec.groups ?? []
}

describe('query spec builder', () => {
  it('omitsTypeCodeForAll', () => {
    const spec = buildQuerySpec(base({ typeCode: undefined }))
    expect(spec.typeCode).toBeUndefined()
    expect(spec.projectId).toBe('10')
  })

  it('buildsMinePredicate', () => {
    const spec = buildQuerySpec(base({ viewId: 'mine' }))
    expect(groups(spec)[0].conditions[0]).toEqual({
      field: 'assignee_id',
      operator: 'EQ',
      value: 2,
    })
  })

  it('mapsSearchFields', () => {
    const spec = buildQuerySpec(base({ q: 'DEMO-23' }))
    const search = groups(spec)[0]
    expect(search.logic).toBe('OR')
    expect(search.conditions).toEqual(
      expect.arrayContaining([{ field: 'item_no', operator: 'EQ', value: 23 }]),
    )

    const byName = buildQuerySpec(base({ q: '张三' }))
    expect(groups(byName)[0].conditions).toEqual(
      expect.arrayContaining([{ field: 'assignee_id', operator: 'IN', value: [2] }]),
    )
  })

  it('mapsNotContainsToNotLike', () => {
    const tree: FilterTree = emptyFilterTree()
    tree.conditions.push(createCondition('note', 'NOT_CONTAINS', '草稿'))
    const spec = buildQuerySpec(base({ filterTree: tree }))
    const filter = groups(spec)[0]
    expect(filter.conditions[0]).toMatchObject({
      field: 'custom.note',
      operator: 'NOT_LIKE',
    })
  })

  it('mapsDateOperators', () => {
    const tree = emptyFilterTree()
    tree.conditions.push(createCondition('end_time', 'BEFORE', '2026-11-01'))
    const spec = buildQuerySpec(base({ filterTree: tree }))
    expect(groups(spec)[0].conditions[0]).toMatchObject({ field: 'end_time', operator: 'LT' })

    const week = emptyFilterTree()
    week.conditions.push(createCondition('end_time', 'THIS_WEEK', ''))
    const weekSpec = buildQuerySpec(base({ filterTree: week }))
    expect(groups(weekSpec)[0].conditions[0].operator).toBe('BETWEEN')
  })

  it('combinesGroupsWithAnd', () => {
    const tree = emptyFilterTree()
    tree.conditions.push(createCondition('score', 'GT', 5))
    const group = createGroup('OR')
    group.conditions.push(createCondition('risk', 'EQ', 'high'))
    group.conditions.push(createCondition('status', 'EQ', 'blocked'))
    tree.groups.push(group)
    const spec = buildQuerySpec(base({ viewId: 'mine', q: '登录', filterTree: tree }))
    expect(spec.logic).toBe('AND')
    expect(groups(spec)).toHaveLength(3)
    expect(groups(spec)[2].groups?.[0].logic).toBe('OR')
  })

  it('mapsDateEmptyOperators', () => {
    const tree = emptyFilterTree()
    tree.conditions.push(createCondition('end_time', 'EMPTY', ''))
    tree.conditions.push(createCondition('end_time', 'NOT_EMPTY', ''))
    const spec = buildQuerySpec(base({ filterTree: tree }))
    const conditions = groups(spec)[0].conditions
    expect(conditions).toEqual(expect.arrayContaining([
      { field: 'end_time', operator: 'IS_NULL', value: null },
      { field: 'end_time', operator: 'IS_NOT_NULL', value: null },
    ]))
  })

  it('mapsTextSearchToLike', () => {
    const spec = buildQuerySpec(base({ q: '登录' }))
    const conditions = groups(spec)[0].conditions
    expect(conditions).toEqual(expect.arrayContaining([
      { field: 'title', operator: 'LIKE', value: '登录' },
      { field: 'description', operator: 'LIKE', value: '登录' },
    ]))
  })

  it('buildsBlockedPredicates', () => {
    const withRisk = buildQuerySpec(base({ viewId: 'blocked' }))
    expect(groups(withRisk)[0].conditions).toEqual([
      { field: 'custom.risk', operator: 'EQ', value: 'high' },
    ])
    const withoutRisk = buildQuerySpec(base({ viewId: 'blocked', riskHighValue: undefined }))
    expect(groups(withoutRisk)[0].conditions).toEqual([
      { field: 'status', operator: 'EQ', value: 'blocked' },
    ])
  })

  it('usesNextMondayAsWeekUpperBound', () => {
    const spec = buildQuerySpec(base({ viewId: 'week', now: '2026-10-22' }))
    const conditions = groups(spec)[0].conditions
    expect(conditions[0].value).toBe('2026-10-22 00:00:00')
    expect(conditions[1].value).toBe('2026-10-26 00:00:00')
  })

  it('limitsAllItemsToEnabledTypes', () => {
    const spec = buildQuerySpec(base({ typeCode: undefined, enabledTypeCodes: ['task', 'bug'] }))
    expect(spec.conditions).toEqual([
      { field: 'type_code', operator: 'IN', value: ['task', 'bug'] },
    ])
  })

  it('estimatesCountWithPageSizeOne', () => {
    const spec = buildQuerySpec(base())
    expect(estimateCountSpec(spec)).toMatchObject({ pageNo: 1, pageSize: 1 })
  })
})
