import { formatGitRef, parseInstant } from '@/modules/pipeline/status'
import type { PipelineRun } from '@/modules/pipeline/types/pipeline'

export type RunStatusFilter = 'all' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'QUEUED' | 'WAITING_APPROVAL' | 'CANCELLED'
export type RunTimeRange = '24h' | '7d' | '30d' | 'all'

const RANGE_MS: Record<Exclude<RunTimeRange, 'all'>, number> = {
  '24h': 24 * 60 * 60 * 1000,
  '7d': 7 * 24 * 60 * 60 * 1000,
  '30d': 30 * 24 * 60 * 60 * 1000,
}

export function triggerLabel(trigger?: string | null): string {
  const key = (trigger ?? '').trim().toUpperCase()
  if (!key) return '—'
  if (key === 'MANUAL') return '手动'
  if (key === 'WEBHOOK') return 'Webhook'
  if (key === 'SCHEDULE' || key === 'SCHEDULED' || key === 'CRON' || key === 'TIMER') return '定时'
  return trigger?.trim() || '—'
}

export function filterPipelineRuns(
  runs: PipelineRun[],
  input: { status: RunStatusFilter; range: RunTimeRange; keyword: string; now?: number },
): PipelineRun[] {
  const now = input.now ?? Date.now()
  const needle = input.keyword.trim().toLowerCase()
  const windowMs = input.range === 'all' ? null : RANGE_MS[input.range]
  return runs.filter((run) => {
    if (input.status !== 'all' && run.status !== input.status) return false
    if (windowMs != null && run.startedAt) {
      const started = parseInstant(run.startedAt)
      if (!Number.isNaN(started) && now - started > windowMs) return false
    }
    if (!needle) return true
    const hay = [String(run.id), formatGitRef(run.gitRef), run.gitRef ?? ''].join(' ').toLowerCase()
    return hay.includes(needle)
  })
}

/** 指定时间窗口内已结束运行的成功率；`all` 表示不限时间。没有已结束运行时返回 null。 */
export function recentSuccessRate(runs: PipelineRun[], range: RunTimeRange = '7d', now = Date.now()): number | null {
  const windowMs = range === 'all' ? null : RANGE_MS[range]
  let success = 0
  let finished = 0
  for (const run of runs) {
    if (!run.startedAt) continue
    const started = parseInstant(run.startedAt)
    if (Number.isNaN(started)) continue
    if (windowMs != null && now - started > windowMs) continue
    if (run.status !== 'SUCCEEDED' && run.status !== 'FAILED' && run.status !== 'CANCELLED') continue
    finished += 1
    if (run.status === 'SUCCEEDED') success += 1
  }
  if (finished === 0) return null
  return Math.round((success / finished) * 100)
}

/** 时间窗口的中文标签，供成功率文案使用。 */
export function rangeLabel(range: RunTimeRange): string {
  if (range === '24h') return '近 24 小时'
  if (range === '7d') return '近 7 天'
  if (range === '30d') return '近 30 天'
  return '全部时间'
}
