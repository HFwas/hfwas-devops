import type {
  TransitionFieldMeta,
  TransitionPostFunction,
  TransitionPostFunctionPreset,
  TransitionPostFunctionType,
} from '@/modules/pm/types'

export interface TransitionActionSummaryContext {
  fieldLabelMap?: Record<string, string>
  fieldMetaMap?: Record<string, TransitionFieldMeta>
  userLabelMap?: Record<string, string>
  moduleLabelMap?: Record<string, string>
}

const TYPE_LABELS: Record<TransitionPostFunctionType, string> = {
  SET_FIELD: '改字段',
  NOTIFY_ASSIGNEE: '通知负责人',
  NOTIFY_USER: '通知成员',
  WEBHOOK: '群通知',
}

function fieldLabel(fieldKey: string, ctx: TransitionActionSummaryContext) {
  return ctx.fieldLabelMap?.[fieldKey] ?? ctx.fieldMetaMap?.[fieldKey]?.fieldName ?? fieldKey
}

function optionLabel(fieldKey: string, value: unknown, ctx: TransitionActionSummaryContext) {
  const meta = ctx.fieldMetaMap?.[fieldKey]
  if (meta?.options?.length) {
    const hit = meta.options.find((o) => String(o.value) === String(value))
    if (hit) return hit.label
  }
  if (fieldKey === 'assignee_id' || fieldKey === 'reporter_id') {
    const id = value != null ? String(value) : ''
    return ctx.userLabelMap?.[id] ?? id
  }
  if (fieldKey === 'module_id') {
    const id = value != null ? String(value) : ''
    return ctx.moduleLabelMap?.[id] ?? id
  }
  if (value === true || value === 'true') return '是'
  if (value === false || value === 'false') return '否'
  return value == null || value === '' ? '空' : String(value)
}

export function summarizeTransitionAction(
  action: TransitionPostFunction,
  ctx: TransitionActionSummaryContext = {},
): string {
  switch (action.type) {
    case 'NOTIFY_ASSIGNEE':
      return action.title?.trim()
        ? `通知负责人：${action.title.trim()}`
        : '通知负责人状态变更'
    case 'NOTIFY_USER': {
      const name = action.userId != null ? ctx.userLabelMap?.[String(action.userId)] : ''
      return name
        ? `通知 ${name}${action.title?.trim() ? `：${action.title.trim()}` : ''}`
        : '通知指定成员'
    }
    case 'WEBHOOK':
      return action.title?.trim() ? `群通知：${action.title.trim()}` : '发送钉钉/飞书群通知'
    case 'SET_FIELD':
      if (!action.fieldKey) return '自动修改字段'
      return `将「${fieldLabel(action.fieldKey, ctx)}」设为 ${optionLabel(action.fieldKey, action.value, ctx)}`
    default:
      return '未知动作'
  }
}

export function summarizeTransitionActions(
  actions: TransitionPostFunction[] | undefined,
  ctx: TransitionActionSummaryContext = {},
): string[] {
  return (actions ?? []).map((action) => summarizeTransitionAction(action, ctx))
}

export function presetToAction(preset: TransitionPostFunctionPreset): TransitionPostFunction {
  return {
    type: preset.type,
    fieldKey: preset.fieldKey,
    value: preset.value,
  }
}

/** Short text badge for action type (no emoji). */
export function actionTypeLabel(type: TransitionPostFunction['type']) {
  return TYPE_LABELS[type] ?? type
}

export function blankAction(type: TransitionPostFunctionType = 'SET_FIELD'): TransitionPostFunction {
  return { type }
}
