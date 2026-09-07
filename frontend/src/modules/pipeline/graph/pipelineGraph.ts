import type { EditorJob, EditorStage, JobKind, PipelineStage, ToolchainOption } from '@/modules/pipeline/types/pipeline'

let seq = 0

export function nextClientKey(prefix = 'k'): string {
  seq += 1
  return `${prefix}-${seq}-${Math.random().toString(36).slice(2, 8)}`
}

export function createDefaultGraph(option: ToolchainOption): EditorStage[] {
  return [
    stage('clone', 0, [job('clone', 'CLONE', '', 0)]),
    stage('build', 1, [job('build', 'BUILD', option.buildCommand, 0)]),
    stage('test', 2, [job('test', 'TEST', option.testCommand, 0)]),
  ]
}

export function toEditorStages(stages: PipelineStage[] | undefined, option?: ToolchainOption): EditorStage[] {
  if (!stages?.length) {
    return option ? createDefaultGraph(option) : []
  }
  return stages.map((item, index) => ({
    id: item.id,
    clientKey: nextClientKey('stage'),
    name: item.name,
    sortOrder: item.sortOrder ?? index,
    jobs: (item.jobs ?? []).map((jobItem, jobIndex) => ({
      id: jobItem.id,
      clientKey: nextClientKey('job'),
      name: jobItem.name,
      kind: jobItem.kind,
      command: jobItem.command ?? '',
      sortOrder: jobItem.sortOrder ?? jobIndex,
    })),
  }))
}

export function toSaveStages(stages: EditorStage[]): PipelineStage[] {
  return stages.map((stage, index) => ({
    id: stage.id,
    name: stage.name,
    sortOrder: index,
    jobs: stage.jobs.map((jobItem, jobIndex) => ({
      id: jobItem.id,
      name: jobItem.name,
      kind: jobItem.kind,
      command: jobItem.kind === 'CLONE' ? '' : jobItem.command,
      sortOrder: jobIndex,
    })),
  }))
}

export function isCloneJob(job: { kind?: string }): boolean {
  return job.kind === 'CLONE'
}

export function canDeleteJob(job: { kind?: string }): boolean {
  return !isCloneJob(job)
}

export function canDeleteStage(stage: { jobs: Array<{ kind?: string }> }): boolean {
  return !stage.jobs.some(isCloneJob)
}

export function addStage(stages: EditorStage[], name?: string): EditorStage[] {
  const next = [...stages]
  next.push(stage(name ?? `阶段 ${next.length + 1}`, next.length, [job('任务 1', 'CUSTOM', 'echo ok', 0)]))
  return next
}

export function addJob(stages: EditorStage[], stageKey: string): EditorStage[] {
  return stages.map((item) => {
    if (item.clientKey !== stageKey) return item
    const jobs = [...item.jobs, job(`任务 ${item.jobs.length + 1}`, 'CUSTOM', 'echo ok', item.jobs.length)]
    return { ...item, jobs }
  })
}

export function removeJob(stages: EditorStage[], stageKey: string, jobKey: string): EditorStage[] {
  return stages.map((item) => {
    if (item.clientKey !== stageKey) return item
    const target = item.jobs.find((jobItem) => jobItem.clientKey === jobKey)
    if (!target || !canDeleteJob(target)) return item
    return { ...item, jobs: item.jobs.filter((jobItem) => jobItem.clientKey !== jobKey) }
  })
}

export function removeStage(stages: EditorStage[], stageKey: string): EditorStage[] {
  const target = stages.find((item) => item.clientKey === stageKey)
  if (!target || !canDeleteStage(target)) return stages
  return stages.filter((item) => item.clientKey !== stageKey)
}

/** 换栈时，若 build/test 命令仍是旧默认值，则换成新栈默认命令。 */
export function refreshDefaultCommands(
  stages: EditorStage[],
  previous: ToolchainOption | null,
  next: ToolchainOption,
): EditorStage[] {
  return stages.map((item) => ({
    ...item,
    jobs: item.jobs.map((jobItem) => {
      if (jobItem.kind === 'BUILD' && (!previous || jobItem.command === previous.buildCommand)) {
        return { ...jobItem, command: next.buildCommand }
      }
      if (jobItem.kind === 'TEST' && (!previous || jobItem.command === previous.testCommand)) {
        return { ...jobItem, command: next.testCommand }
      }
      return jobItem
    }),
  }))
}

export function repoShortName(url: string | undefined | null): string {
  if (!url) return '—'
  const trimmed = url.trim()
  try {
    const parsed = new URL(trimmed)
    const path = parsed.pathname.replace(/\/+$/, '').replace(/\.git$/i, '').replace(/^\//, '')
    if (path) return path
    return parsed.host || trimmed
  } catch {
    const fallback = trimmed.replace(/\/+$/, '').replace(/\.git$/i, '')
    const parts = fallback.split(/[/:]/).filter(Boolean)
    return parts[parts.length - 1] || fallback
  }
}

export function stackSummary(stack: string, runtime: string, tool?: string | null): string {
  if (stack === 'JAVA_MAVEN') return `Java ${runtime} / Maven ${tool ?? ''}`.trim()
  if (stack === 'NODE') return `Node ${runtime} / ${tool ?? ''}`.trim()
  if (stack === 'GO') return `Go ${runtime}`
  if (stack === 'PYTHON') return `Python ${runtime}`
  return [stack, runtime, tool].filter(Boolean).join(' ')
}

function stage(name: string, sortOrder: number, jobs: EditorJob[]): EditorStage {
  return { clientKey: nextClientKey('stage'), name, sortOrder, jobs }
}

function job(name: string, kind: JobKind, command: string, sortOrder: number): EditorJob {
  return { clientKey: nextClientKey('job'), name, kind, command, sortOrder }
}
