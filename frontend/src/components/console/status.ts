/**
 * 全站状态字典。PM、流水线、容器、API 测试把领域状态映射到同一套色与图标，
 * 不要在各模块再写一套绿/红/黄。
 *
 * 未知编码落在 neutral。领域自己的工作流码（例如 PM 自定义状态）用 StatusIcon 的 tone 显式指定。
 */

export const STATUS_TONES = [
  'success',
  'failed',
  'warning',
  'progress',
  'terminating',
  'paused',
  'neutral',
] as const

export type StatusTone = (typeof STATUS_TONES)[number]

const TONE_LABEL: Record<StatusTone, string> = {
  success: '成功',
  failed: '失败',
  warning: '警告',
  progress: '进行中',
  terminating: '终止中',
  paused: '已暂停',
  neutral: '空闲',
}

/** Tailwind 色，与样式规范 4.4 一致。只允许出现在这个字典里。 */
export const STATUS_TONE_CLASS: Record<StatusTone, string> = {
  success: 'text-green-500 dark:text-green-400',
  failed: 'text-red-500 dark:text-red-400',
  warning: 'text-yellow-500 dark:text-yellow-400',
  progress: 'text-blue-500 dark:text-blue-400',
  terminating: 'text-orange-500 dark:text-orange-400',
  paused: 'text-purple-500 dark:text-purple-400',
  neutral: 'text-muted-foreground',
}

/** 圆点与图标共用同一套语义色。 */
export const STATUS_DOT_CLASS: Record<StatusTone, string> = {
  success: 'bg-green-500 dark:bg-green-400',
  failed: 'bg-red-500 dark:bg-red-400',
  warning: 'bg-yellow-500 dark:bg-yellow-400',
  progress: 'bg-blue-500 dark:bg-blue-400',
  terminating: 'bg-orange-500 dark:bg-orange-400',
  paused: 'bg-purple-500 dark:bg-purple-400',
  neutral: 'bg-muted-foreground',
}

const ALIAS: Record<string, StatusTone> = {
  ready: 'success',
  succeeded: 'success',
  success: 'success',
  successful: 'success',
  passed: 'success',
  pass: 'success',
  resolved: 'success',
  connected: 'success',
  bound: 'success',
  active: 'success',
  available: 'success',
  healthy: 'success',
  complete: 'success',
  completed: 'success',
  ok: 'success',
  deployed: 'success',
  published: 'success',
  enabled: 'success',
  done: 'success',
  closed: 'success',
  成功: 'success',
  通过: 'success',
  已解决: 'success',
  就绪: 'success',
  已发布: 'success',
  已完成: 'success',
  已关闭: 'success',

  failed: 'failed',
  failure: 'failed',
  error: 'failed',
  critical: 'failed',
  high: 'failed',
  errored: 'failed',
  crashloopbackoff: 'failed',
  imagepullbackoff: 'failed',
  errimagepull: 'failed',
  disconnected: 'failed',
  blocked: 'failed',
  rejected: 'failed',
  失败: 'failed',
  错误: 'failed',
  阻断: 'failed',

  warning: 'warning',
  medium: 'warning',
  low: 'warning',
  warn: 'warning',
  degraded: 'warning',
  waiting_approval: 'warning',
  waiting: 'warning',
  unconfirmed: 'warning',
  notready: 'warning',
  警告: 'warning',
  降级: 'warning',
  待确认: 'warning',
  待审批: 'warning',
  deprecated: 'warning',
  已废弃: 'warning',

  pending: 'progress',
  pending_install: 'progress',
  pending_upgrade: 'progress',
  pending_rollback: 'progress',
  running: 'progress',
  queued: 'progress',
  building: 'progress',
  progressing: 'progress',
  containercreating: 'progress',
  podinitializing: 'progress',
  in_progress: 'progress',
  open: 'progress',
  todo: 'progress',
  to_do: 'progress',
  进行中: 'progress',
  待处理: 'progress',
  排队中: 'progress',
  运行中: 'progress',
  构建中: 'progress',

  uninstalling: 'terminating',
  terminating: 'terminating',
  cancelling: 'terminating',
  canceling: 'terminating',
  deleting: 'terminating',
  终止中: 'terminating',
  取消中: 'terminating',

  paused: 'paused',
  pause: 'paused',
  hold: 'paused',
  suspended: 'paused',
  已暂停: 'paused',
  暂停: 'paused',

  cancelled: 'neutral',
  canceled: 'neutral',
  unknown: 'neutral',
  draft: 'neutral',
  idle: 'neutral',
  scaled_to_zero: 'neutral',
  scaledtozero: 'neutral',
  disabled: 'neutral',
  superseded: 'neutral',
  uninstalled: 'neutral',
  inactive: 'neutral',
  not_running: 'neutral',
  none: 'neutral',
  草稿: 'neutral',
  未启用: 'neutral',
  未运行: 'neutral',
  已取消: 'neutral',
}

export function normalizeStatusKey(status?: string | null): string {
  return (status ?? '')
    .trim()
    .toLowerCase()
    .replace(/[\s-]+/g, '_')
}

export function resolveStatusTone(status?: string | null): StatusTone {
  const key = normalizeStatusKey(status)
  if (!key) return 'neutral'
  return ALIAS[key] ?? 'neutral'
}

export function statusToneLabel(tone: StatusTone): string {
  return TONE_LABEL[tone]
}

const SPINNING = new Set(['running', 'building', 'progressing', 'containercreating', 'podinitializing', '运行中', '构建中'])

export function statusIconSpins(status: string | null | undefined, tone: StatusTone): boolean {
  if (tone !== 'progress') return false
  return SPINNING.has(normalizeStatusKey(status))
}
