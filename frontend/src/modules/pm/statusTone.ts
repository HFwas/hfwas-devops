import type { StatusTone } from '@/components/console/status'
import type { StatusDefinition } from '@/modules/pm/types'

/**
 * 自定义工作流不在全站字典里时：起始态视为进行中，终态视为成功。
 * 中间态返回 undefined，交给 StatusIcon 按状态码查字典。
 */
export function workflowStatusTone(definition?: StatusDefinition | null): StatusTone | undefined {
  if (!definition) return undefined
  if (definition.isFinal) return 'success'
  if (definition.isInitial) return 'progress'
  return undefined
}

const PRIORITY_LABEL: Record<string, string> = {
  critical: '紧急',
  high: '高',
  medium: '中',
  low: '低',
}

export function priorityLabel(priority?: string | null): string {
  if (!priority?.trim()) return '—'
  return PRIORITY_LABEL[priority.trim().toLowerCase()] ?? priority
}
