import { JOB_KIND_OPTIONS, jobKindLabel, jobKindMeta, requiresCommand } from '@/modules/pipeline/graph/jobCatalog'
import { nextClientKey } from '@/modules/pipeline/graph/ids'
import type {
  EditorJob,
  EditorStage,
  JobKind,
  PipelineRunJob,
  PipelineStage,
} from '@/modules/pipeline/types/pipeline'

export { JOB_KIND_OPTIONS, jobKindLabel, requiresCommand, nextClientKey }

export type StageInsertTarget =
  | { type: 'stage'; afterIndex: number }
  | { type: 'parallel'; stageKey: string }

export function defaultCommandForKind(kind: string): string {
  if (kind === 'BUILD') return ''
  if (kind === 'TEST') return ''
  return jobKindMeta(kind)?.defaultCommand ?? ''
}

export function isCloneJob(job: { kind?: string }): boolean {
  return job.kind === 'CLONE'
}

export function hasClone(stages: Array<{ jobs: Array<{ kind?: string }> }>): boolean {
  return stages.some((stage) => stage.jobs.some(isCloneJob))
}

export function allEditorJobs(stages: EditorStage[]): EditorJob[] {
  return stages.flatMap((stage) => stage.jobs)
}

export function kindSelectOptions(
  jobs: Array<{ kind?: string; clientKey?: string; id?: string | number }>,
  editingKey?: string,
): Array<{ label: string; value: JobKind }> {
  const cloneTaken = jobs.some((job) => job.kind === 'CLONE' && (job.clientKey ?? job.id) !== editingKey)
  return JOB_KIND_OPTIONS.filter((item) => item.value !== 'CLONE' || !cloneTaken).map((item) => ({
    label: item.label,
    value: item.value,
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

/** 仅用于「插入常用模板」；新建流水线默认是空画布。 */
export function createTemplateStages(): PipelineStage[] {
  return [
    { name: '代码克隆', sortOrder: 0, jobs: [{ name: '代码克隆', kind: 'CLONE', command: '', sortOrder: 0 }] },
    { name: '构建', sortOrder: 1, jobs: [{ name: '构建', kind: 'BUILD', command: '', sortOrder: 0 }] },
    { name: '测试', sortOrder: 2, jobs: [{ name: '测试', kind: 'TEST', command: '', sortOrder: 0 }] },
  ]
}

export function createEditorJob(
  kind: JobKind,
  extras?: Partial<EditorJob>,
): EditorJob {
  return {
    clientKey: extras?.clientKey ?? nextClientKey('job'),
    id: extras?.id,
    name: extras?.name ?? jobKindLabel(kind),
    kind,
    command: extras?.command ?? defaultCommandForKind(kind),
    stack: extras?.stack ?? null,
    runtimeVersion: extras?.runtimeVersion ?? null,
    toolVersion: extras?.toolVersion ?? null,
    sortOrder: extras?.sortOrder ?? 0,
    paramBindings: extras?.paramBindings ?? {},
    status: extras?.status,
    runJobId: extras?.runJobId,
  }
}

export function toEditorStages(stages?: PipelineStage[] | null): EditorStage[] {
  return (stages ?? []).map((stage, index) => ({
    id: stage.id,
    clientKey: nextClientKey('stage'),
    name: stage.name,
    sortOrder: index,
    jobs: (stage.jobs ?? []).map((job, jobIndex) => ({
      ...job,
      clientKey: nextClientKey('job'),
      command: job.command ?? '',
      stack: job.stack ?? null,
      runtimeVersion: job.runtimeVersion ?? null,
      toolVersion: job.toolVersion ?? null,
      paramBindings: job.paramBindings ?? {},
      sortOrder: jobIndex,
    })),
  }))
}

export function toSaveStages(stages: EditorStage[]): PipelineStage[] {
  return stages.map((stage, index) => ({
    id: stage.id,
    name: stage.name.trim() || stage.jobs[0]?.name || `阶段 ${index + 1}`,
    sortOrder: index,
    jobs: stage.jobs.map((job, jobIndex) => ({
      id: job.id,
      name: job.name,
      kind: job.kind,
      command: job.command ?? '',
      stack: job.stack ?? null,
      runtimeVersion: job.runtimeVersion ?? null,
      toolVersion: job.toolVersion ?? null,
      sortOrder: jobIndex,
      paramBindings: job.paramBindings ?? {},
    })),
  }))
}

export function insertStageAt(stages: EditorStage[], afterIndex: number, job: EditorJob): EditorStage[] {
  const stage: EditorStage = {
    clientKey: nextClientKey('stage'),
    name: job.name,
    sortOrder: 0,
    jobs: [{ ...job, sortOrder: 0 }],
  }
  const next = [...stages]
  next.splice(afterIndex + 1, 0, stage)
  return reindexStages(next)
}

export function addParallelJob(stages: EditorStage[], stageKey: string, job: EditorJob): EditorStage[] {
  return stages.map((stage) => {
    if (stage.clientKey !== stageKey) return stage
    return {
      ...stage,
      jobs: [...stage.jobs, { ...job, sortOrder: stage.jobs.length }],
    }
  })
}

export function patchEditorJob(stages: EditorStage[], jobKey: string, patch: Partial<EditorJob>): EditorStage[] {
  return stages.map((stage) => {
    const jobs = stage.jobs.map((job) => (job.clientKey === jobKey ? { ...job, ...patch } : job))
    const renamed = jobs.length === 1 && jobs[0]?.clientKey === jobKey && patch.name != null
    return {
      ...stage,
      name: renamed ? String(patch.name) : stage.name,
      jobs,
    }
  })
}

export function removeEditorJob(stages: EditorStage[], jobKey: string): EditorStage[] {
  return reindexStages(
    stages
      .map((stage) => ({
        ...stage,
        jobs: stage.jobs.filter((job) => job.clientKey !== jobKey).map((job, index) => ({ ...job, sortOrder: index })),
      }))
      .filter((stage) => stage.jobs.length > 0),
  )
}

export function findEditorJob(stages: EditorStage[], jobKey: string | null | undefined): EditorJob | null {
  if (!jobKey) return null
  return allEditorJobs(stages).find((job) => job.clientKey === jobKey) ?? null
}

export function stageOfJob(stages: EditorStage[], jobKey: string): EditorStage | undefined {
  return stages.find((stage) => stage.jobs.some((job) => job.clientKey === jobKey))
}

/**
 * 新增任务约束：clone 全局唯一；审批独占阶段；同一阶段不能两个镜像构建。
 * `stageKey` 为空表示插入新的顺序阶段。
 */
export function canAddKindToStage(stages: EditorStage[], stageKey: string | null, kind: JobKind): string | null {
  if (kind === 'CLONE' && hasClone(stages)) return '流水线至多一个克隆任务'
  if (!stageKey) return null
  const stage = stages.find((item) => item.clientKey === stageKey)
  if (!stage) return '阶段不存在'
  if (kind === 'APPROVAL' || stage.jobs.some((job) => job.kind === 'APPROVAL')) {
    return '审批任务必须独占一列'
  }
  if (kind === 'IMAGE' && stage.jobs.some((job) => job.kind === 'IMAGE')) {
    return '镜像构建不能与其它镜像构建并行'
  }
  return null
}

export function canChangeJobKind(stages: EditorStage[], jobKey: string, kind: JobKind): string | null {
  const stage = stageOfJob(stages, jobKey)
  if (!stage) return '任务不存在'
  const others = stage.jobs.filter((job) => job.clientKey !== jobKey)
  if (kind === 'CLONE' && stages.some((item) => item.jobs.some((job) => job.kind === 'CLONE' && job.clientKey !== jobKey))) {
    return '流水线至多一个克隆任务'
  }
  if (kind === 'APPROVAL' && others.length > 0) return '审批任务必须独占一列'
  if (others.some((job) => job.kind === 'APPROVAL')) return '审批任务必须独占一列'
  if (kind === 'IMAGE' && others.some((job) => job.kind === 'IMAGE')) {
    return '镜像构建不能与其它镜像构建并行'
  }
  return null
}

export function groupRunJobs(jobs: PipelineRunJob[]): EditorStage[] {
  const names: string[] = []
  const map = new Map<string, PipelineRunJob[]>()
  for (const job of jobs) {
    const key = job.stageName || '未命名'
    if (!map.has(key)) {
      names.push(key)
      map.set(key, [])
    }
    map.get(key)!.push(job)
  }
  return names.map((name, index) => ({
    clientKey: `stage-${index}`,
    name,
    sortOrder: index,
    jobs: (map.get(name) ?? []).map((job, jobIndex) => ({
      id: job.jobId ?? job.id,
      clientKey: `job-${job.id}`,
      name: job.jobName,
      kind: job.kind,
      command: job.command,
      sortOrder: jobIndex,
      status: job.status,
      runJobId: job.id,
      startedAt: job.startedAt,
      finishedAt: job.finishedAt,
    })),
  }))
}

function reindexStages(stages: EditorStage[]): EditorStage[] {
  return stages.map((stage, index) => ({ ...stage, sortOrder: index }))
}
