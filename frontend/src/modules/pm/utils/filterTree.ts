import type {
  FilterCondition,
  FilterFieldType,
  FilterGroup,
  FilterOperator,
  FilterTree,
  QueryLogic,
} from '@/modules/pm/types'

let counter = 0

/** 纯函数与测试环境都可用的稳定本地 id（不依赖 crypto）。 */
export function filterNodeId(prefix = 'node') {
  counter += 1
  return `${prefix}-${Date.now().toString(36)}-${counter}`
}

export function emptyFilterTree(): FilterTree {
  return { logic: 'AND', conditions: [], groups: [] }
}

export function createCondition(field: string, operator: FilterOperator = 'EQ', value: unknown = ''): FilterCondition {
  return { id: filterNodeId('cond'), field, operator, value }
}

export function createGroup(logic: QueryLogic = 'AND'): FilterGroup {
  return { id: filterNodeId('group'), logic, conditions: [] }
}

export const OPERATORS_BY_TYPE: Record<FilterFieldType, { value: FilterOperator; label: string }[]> = {
  text: [
    { value: 'CONTAINS', label: '包含' },
    { value: 'NOT_CONTAINS', label: '不包含' },
    { value: 'EQ', label: '等于' },
    { value: 'NE', label: '不等于' },
    { value: 'EMPTY', label: '为空' },
    { value: 'NOT_EMPTY', label: '不为空' },
  ],
  enum: [
    { value: 'IN', label: '属于' },
    { value: 'NOT_IN', label: '不属于' },
    { value: 'EQ', label: '是' },
    { value: 'NE', label: '不是' },
    { value: 'EMPTY', label: '为空' },
    { value: 'NOT_EMPTY', label: '不为空' },
  ],
  user: [
    { value: 'EQ', label: '是' },
    { value: 'NE', label: '不是' },
    { value: 'IN', label: '属于' },
    { value: 'NOT_IN', label: '不属于' },
    { value: 'EMPTY', label: '为空' },
    { value: 'NOT_EMPTY', label: '不为空' },
  ],
  date: [
    { value: 'ON', label: '等于' },
    { value: 'BEFORE', label: '早于' },
    { value: 'AFTER', label: '晚于' },
    { value: 'BETWEEN', label: '在区间' },
    { value: 'TODAY', label: '今天' },
    { value: 'THIS_WEEK', label: '本周' },
    { value: 'EMPTY', label: '为空' },
    { value: 'NOT_EMPTY', label: '不为空' },
  ],
  number: [
    { value: 'EQ', label: '等于' },
    { value: 'GT', label: '大于' },
    { value: 'LT', label: '小于' },
    { value: 'GTE', label: '大于等于' },
    { value: 'LTE', label: '小于等于' },
    { value: 'BETWEEN', label: '在区间' },
    { value: 'EMPTY', label: '为空' },
    { value: 'NOT_EMPTY', label: '不为空' },
  ],
}

export function operatorsFor(fieldType: FilterFieldType) {
  return OPERATORS_BY_TYPE[fieldType] ?? OPERATORS_BY_TYPE.text
}

export function operatorLabel(fieldType: FilterFieldType, operator: FilterOperator) {
  return operatorsFor(fieldType).find((item) => item.value === operator)?.label ?? operator
}

export function isValueLessOperator(operator: FilterOperator) {
  return ['EMPTY', 'NOT_EMPTY', 'TODAY', 'THIS_WEEK'].includes(operator)
}

/** 多值操作符需要数组，区间操作符需要两个值。 */
export function acceptsMultipleValues(operator: FilterOperator) {
  return operator === 'IN' || operator === 'NOT_IN'
}

export function acceptsRange(operator: FilterOperator) {
  return operator === 'BETWEEN'
}

function blank(value: unknown) {
  if (value === null || value === undefined) return true
  if (typeof value === 'string') return value.trim() === ''
  if (Array.isArray(value)) return value.length === 0
  return false
}

export function missingValueMessage(
  condition: FilterCondition,
  fieldType: FilterFieldType,
  fieldLabel: string,
): string | null {
  const allowed = operatorsFor(fieldType)
  if (!allowed.some((item) => item.value === condition.operator)) {
    return `「${fieldLabel}」不支持操作符「${condition.operator}」`
  }
  if (isValueLessOperator(condition.operator)) return null
  if (acceptsRange(condition.operator)) {
    const values = Array.isArray(condition.value) ? condition.value : [condition.value]
    if (values.filter((item) => !blank(item)).length < 2) {
      return `「${fieldLabel}」需要完整的起止值`
    }
    return null
  }
  if (acceptsMultipleValues(condition.operator)) {
    return Array.isArray(condition.value) && condition.value.length > 0
      ? null
      : `「${fieldLabel}」缺少值`
  }
  return blank(condition.value) ? `「${fieldLabel}」缺少值` : null
}

export interface FilterValidationContext {
  fieldTypes: Map<string, FilterFieldType>
  fieldLabels: Map<string, string>
}

/** 返回所有阻断应用的错误文案；空数组表示可应用。 */
export function validateFilterTree(tree: FilterTree, ctx: FilterValidationContext): string[] {
  const errors: string[] = []
  const visit = (condition: FilterCondition) => {
    const fieldType = ctx.fieldTypes.get(condition.field)
    if (!fieldType) {
      errors.push(`未知筛选字段：${condition.field}`)
      return
    }
    const message = missingValueMessage(
      condition,
      fieldType,
      ctx.fieldLabels.get(condition.field) ?? condition.field,
    )
    if (message) errors.push(message)
  }
  tree.conditions.forEach(visit)
  tree.groups.forEach((group) => group.conditions.forEach(visit))
  return errors
}

export function cropFilterTree(tree: FilterTree, allowedFields: Set<string>): FilterTree {
  return {
    logic: tree.logic,
    conditions: tree.conditions.filter((condition) => allowedFields.has(condition.field)),
    groups: tree.groups
      .map((group) => ({
        ...group,
        conditions: group.conditions.filter((condition) => allowedFields.has(condition.field)),
      }))
      .filter((group) => group.conditions.length > 0),
  }
}

export function countFilterConditions(tree: FilterTree) {
  return tree.conditions.length + tree.groups.reduce((total, group) => total + group.conditions.length, 0)
}

export function isEmptyFilterTree(tree: FilterTree) {
  return countFilterConditions(tree) === 0
}

function displayValue(value: unknown) {
  if (Array.isArray(value)) return value.join('、')
  if (value === null || value === undefined || value === '') return '—'
  return String(value)
}

/** 可读表达式，例如：状态 属于 待处理、进行中 且 优先级 是 高。 */
export function filterTreeToExpression(
  tree: FilterTree,
  ctx: {
    fieldLabels: Map<string, string>
    fieldTypes: Map<string, FilterFieldType>
    optionLabels?: Map<string, Map<string, string>>
  },
): string {
  const render = (condition: FilterCondition) => {
    const fieldLabel = ctx.fieldLabels.get(condition.field) ?? condition.field
    const fieldType = ctx.fieldTypes.get(condition.field) ?? 'text'
    const label = operatorLabel(fieldType, condition.operator)
    if (isValueLessOperator(condition.operator)) return `${fieldLabel} ${label}`
    const labels = ctx.optionLabels?.get(condition.field)
    const values = Array.isArray(condition.value) ? condition.value : [condition.value]
    const renderedValues = values.map((value) => labels?.get(String(value)) ?? displayValue(value))
    return `${fieldLabel} ${label} ${renderedValues.join('、')}`
  }
  const join = (logic: QueryLogic) => (logic === 'OR' ? ' 或 ' : ' 且 ')
  const parts: string[] = []
  tree.conditions.forEach((condition) => parts.push(render(condition)))
  tree.groups.forEach((group) => {
    const inner = group.conditions.map(render).join(join(group.logic))
    if (inner) parts.push(group.conditions.length > 1 ? `(${inner})` : inner)
  })
  return parts.join(join(tree.logic))
}

export function filterTreeToChips(
  tree: FilterTree,
  ctx: {
    fieldLabels: Map<string, string>
    fieldTypes: Map<string, FilterFieldType>
    optionLabels?: Map<string, Map<string, string>>
  },
): { key: string; label: string }[] {
  const chips: { key: string; label: string }[] = []
  const push = (condition: FilterCondition, prefix = '') => {
    const fieldLabel = ctx.fieldLabels.get(condition.field) ?? condition.field
    const fieldType = ctx.fieldTypes.get(condition.field) ?? 'text'
    const label = operatorLabel(fieldType, condition.operator)
    if (isValueLessOperator(condition.operator)) {
      chips.push({ key: condition.id, label: `${prefix}${fieldLabel} ${label}` })
      return
    }
    const labels = ctx.optionLabels?.get(condition.field)
    const values = Array.isArray(condition.value) ? condition.value : [condition.value]
    const rendered = values.map((value) => labels?.get(String(value)) ?? displayValue(value)).join('、')
    chips.push({ key: condition.id, label: `${prefix}${fieldLabel} ${label} ${rendered}` })
  }
  tree.conditions.forEach((condition) => push(condition))
  tree.groups.forEach((group) => group.conditions.forEach((condition) => push(condition, group.logic === 'OR' ? '或 ' : '')))
  return chips
}

/** 清理 URL / 远端数据中的非法条件，丢弃未知字段与不合法操作符。 */
export function sanitizeFilterTree(
  input: unknown,
  ctx: { fieldTypes: Map<string, FilterFieldType> },
): FilterTree {
  const tree = emptyFilterTree()
  if (!input || typeof input !== 'object') return tree
  const source = input as Partial<FilterTree>
  if (source.logic === 'OR') tree.logic = 'OR'
  const accept = (condition: unknown): FilterCondition | null => {
    if (!condition || typeof condition !== 'object') return null
    const item = condition as Partial<FilterCondition>
    if (typeof item.field !== 'string' || typeof item.operator !== 'string') return null
    const fieldType = ctx.fieldTypes.get(item.field)
    if (!fieldType) return null
    if (!operatorsFor(fieldType).some((operator) => operator.value === item.operator)) return null
    return {
      id: item.id || filterNodeId('cond'),
      field: item.field,
      operator: item.operator as FilterOperator,
      value: item.value ?? '',
    }
  }
  if (Array.isArray(source.conditions)) {
    tree.conditions = source.conditions.map(accept).filter((item): item is FilterCondition => Boolean(item))
  }
  if (Array.isArray(source.groups)) {
    tree.groups = source.groups.slice(0, 20).map((group) => {
      const created = createGroup()
      const raw = group as Partial<FilterGroup>
      created.logic = raw.logic === 'OR' ? 'OR' : 'AND'
      created.conditions = Array.isArray(raw.conditions)
        ? raw.conditions.map(accept).filter((item): item is FilterCondition => Boolean(item))
        : []
      return created
    })
  }
  return tree
}

export type QuickFilter = 'mine' | 'week' | 'blocked' | 'unassigned'

export function buildQuickFilter(
  preset: QuickFilter,
  ctx: { currentUserId?: string; riskHighValue?: string },
): FilterTree {
  const tree = emptyFilterTree()
  if (preset === 'mine') {
    if (!ctx.currentUserId) return tree
    tree.conditions.push(createCondition('assignee_id', 'EQ', ctx.currentUserId))
  } else if (preset === 'unassigned') {
    tree.conditions.push(createCondition('assignee_id', 'EMPTY', ''))
  } else if (preset === 'week') {
    tree.conditions.push(createCondition('end_time', 'THIS_WEEK', ''))
  } else if (preset === 'blocked') {
    const group = createGroup('OR')
    if (ctx.riskHighValue) {
      group.conditions.push(createCondition('risk', 'EQ', ctx.riskHighValue))
    }
    group.conditions.push(createCondition('status', 'EQ', 'blocked'))
    tree.groups.push(group)
  }
  return tree
}
