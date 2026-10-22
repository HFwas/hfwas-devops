import { describe, expect, it } from 'vitest'
import {
  buildQuickFilter,
  createCondition,
  createGroup,
  emptyFilterTree,
  filterTreeToChips,
  filterTreeToExpression,
  sanitizeFilterTree,
  validateFilterTree,
} from '@/modules/pm/utils/filterTree'
import type { FilterFieldType } from '@/modules/pm/types'

const fieldTypes = new Map<string, FilterFieldType>([
  ['title', 'text'],
  ['status', 'enum'],
  ['assignee_id', 'user'],
  ['end_time', 'date'],
  ['score', 'number'],
])
const fieldLabels = new Map([
  ['title', '标题'],
  ['status', '状态'],
  ['assignee_id', '负责人'],
  ['end_time', '截止时间'],
  ['score', '故事点'],
])
const ctx = { fieldTypes, fieldLabels }

describe('filter tree', () => {
  it('validatesMissingValues', () => {
    const tree = emptyFilterTree()
    tree.conditions.push(createCondition('title', 'CONTAINS', ''))
    tree.conditions.push(createCondition('status', 'IN', []))
    tree.conditions.push(createCondition('end_time', 'BETWEEN', ['2026-10-01']))
    const errors = validateFilterTree(tree, ctx)
    expect(errors).toHaveLength(3)
    expect(errors.join(' ')).toContain('标题')

    tree.conditions[0].value = '登录'
    tree.conditions[1].value = ['open']
    tree.conditions[2].value = ['2026-10-01', '2026-10-31']
    expect(validateFilterTree(tree, ctx)).toEqual([])
  })

  it('enforcesTwoLevels', () => {
    const tree = emptyFilterTree()
    const group = createGroup('OR')
    group.conditions.push(createCondition('status', 'EQ', 'open'))
    const raw = {
      logic: 'AND',
      conditions: [],
      groups: [{
        id: group.id,
        logic: 'OR',
        conditions: [{ id: 'c1', field: 'status', operator: 'EQ', value: 'open' }],
        groups: [{ id: 'nested', logic: 'AND', conditions: [{ id: 'c2', field: 'title', operator: 'CONTAINS', value: 'x' }] }],
      }],
    }
    const sanitized = sanitizeFilterTree(raw, ctx)
    expect(sanitized.groups).toHaveLength(1)
    expect((sanitized.groups[0] as unknown as { groups?: unknown[] }).groups).toBeUndefined()
  })

  it('buildsReadableExpression', () => {
    const tree = emptyFilterTree()
    tree.conditions.push(createCondition('status', 'IN', ['open', 'doing']))
    tree.conditions.push(createCondition('score', 'GT', 5))
    const expression = filterTreeToExpression(tree, {
      ...ctx,
      optionLabels: new Map([['status', new Map([['open', '待处理'], ['doing', '进行中']])]]),
    })
    expect(expression).toContain('状态 属于 待处理、进行中')
    expect(expression).toContain('故事点 大于 5')
    expect(expression).toContain('且')

    const chips = filterTreeToChips(tree, ctx)
    expect(chips).toHaveLength(2)
  })

  it('buildsQuickFilters', () => {
    expect(buildQuickFilter('unassigned', {}).conditions[0]).toMatchObject({
      field: 'assignee_id',
      operator: 'EMPTY',
    })
    expect(buildQuickFilter('week', {}).conditions[0]).toMatchObject({
      field: 'end_time',
      operator: 'THIS_WEEK',
    })
    const blocked = buildQuickFilter('blocked', { riskHighValue: 'high' })
    expect(blocked.groups[0].conditions.map((condition) => condition.field)).toEqual(['risk', 'status'])
    const mine = buildQuickFilter('mine', { currentUserId: '2' })
    expect(mine.conditions[0]).toMatchObject({ field: 'assignee_id', operator: 'EQ', value: '2' })
  })

  it('sanitizesUnknown', () => {
    const sanitized = sanitizeFilterTree({
      logic: 'OR',
      conditions: [
        { field: 'unknown', operator: 'EQ', value: 'x' },
        { field: 'title', operator: 'BETWEEN', value: 'x' },
      ],
      groups: [],
    }, ctx)
    expect(sanitized.logic).toBe('OR')
    expect(sanitized.conditions).toEqual([])
  })
})
