import { runStatusLabel } from '@/modules/pipeline/status'

/**
 * 运行详情的阶段状态与日志拼接。
 * 总体日志是前端按阶段、任务顺序拼接各任务的 `logText`，没有单独的服务端日志流。
 */

export interface RunJobLike {
  id?: string | number
  stageName?: string | null
  jobName: string
  status?: string | null
  logText?: string | null
}

export interface RunStageGroup<T> {
  name: string
  status: string
  jobs: T[]
}

const TERMINAL_RUN_STATUSES = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])

export function isTerminalRunStatus(status?: string | null): boolean {
  return !!status && TERMINAL_RUN_STATUSES.has(status)
}

/**
 * 阶段色：失败优先，其次取消、待审批、运行中。
 * 已有成功任务且仍有排队任务时，阶段视为运行中。
 * 未识别的状态压过排队和成功，避免被静默成已完成。
 */
function statusRank(status: string): number {
  switch (status) {
    case 'FAILED':
      return 0
    case 'CANCELLED':
      return 1
    case 'WAITING_APPROVAL':
      return 2
    case 'RUNNING':
      return 3
    case 'QUEUED':
      return 5
    case 'SUCCEEDED':
      return 6
    default:
      return 4
  }
}

export function aggregateJobStatus(statuses: readonly (string | null | undefined)[]): string {
  const present = statuses.map((status) => status?.trim() ?? '').filter(Boolean)
  if (present.length === 0) return 'QUEUED'
  let winner = present[0]
  let winnerRank = statusRank(winner)
  for (const status of present.slice(1)) {
    const rank = statusRank(status)
    if (rank < winnerRank) {
      winner = status
      winnerRank = rank
    }
  }
  if (winner === 'QUEUED' && present.includes('SUCCEEDED')) return 'RUNNING'
  return winner
}

/** 按阶段首次出现的顺序分组，阶段内保持任务原顺序。同名阶段合并。 */
export function groupJobsByStage<T extends { stageName?: string | null; status?: string | null }>(
  jobs: readonly T[],
): RunStageGroup<T>[] {
  const names: string[] = []
  const map = new Map<string, T[]>()
  for (const job of jobs) {
    const name = job.stageName?.trim() || '未命名'
    let bucket = map.get(name)
    if (!bucket) {
      bucket = []
      map.set(name, bucket)
      names.push(name)
    }
    bucket.push(job)
  }
  return names.map((name) => {
    const stageJobs = map.get(name) ?? []
    return {
      name,
      jobs: stageJobs,
      status: aggregateJobStatus(stageJobs.map((job) => job.status)),
    }
  })
}

export function logBody(text?: string | null, empty = '（暂无日志）'): string {
  if (!text || !text.trim()) return empty
  return text.replace(/\s+$/, '')
}

export function jobLogHeading(job: { stageName?: string | null; jobName: string; status?: string | null }): string {
  const stage = job.stageName?.trim() || '未命名'
  return `──── ${stage} / ${job.jobName} · ${runStatusLabel(job.status)} ────`
}

export function aggregateRunLog(jobs: readonly RunJobLike[]): string {
  const ordered = groupJobsByStage(jobs).flatMap((stage) => stage.jobs)
  if (ordered.length === 0) return ''
  return ordered.map((job) => `${jobLogHeading(job)}\n${logBody(job.logText)}`).join('\n\n')
}
