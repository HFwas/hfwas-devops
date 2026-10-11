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

/** 近 7 天已结束运行的成功率。没有已结束运行时返回 null。 */
export function recentSuccessRate(runs: PipelineRun[], now = Date.now()): number | null {
  const windowMs = RANGE_MS['7d']
  let success = 0
  let finished = 0
  for (const run of runs) {
    if (!run.startedAt) continue
    const started = parseInstant(run.startedAt)
    if (Number.isNaN(started) || now - started > windowMs) continue
    if (run.status !== 'SUCCEEDED' && run.status !== 'FAILED' && run.status !== 'CANCELLED') continue
    finished += 1
    if (run.status === 'SUCCEEDED') success += 1
  }
  if (finished === 0) return null
  return Math.round((success / finished) * 100)
}
