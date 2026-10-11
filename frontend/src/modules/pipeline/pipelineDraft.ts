import { normalizeVisibility } from '@/modules/pipeline/taskKindView'
import type {
  JobParamBinding,
  PipelineSavePayload,
  PipelineStage,
  PipelineSummary,
  TaskKindParam,
  TaskKindVO,
} from '@/modules/pipeline/types/pipeline'

export interface DraftJob {
  clientKey: string
  name: string
  kind: string
  command: string
  stack?: string | null
  runtimeVersion?: string | null
  toolVersion?: string | null
  paramBindings: Record<string, JobParamBinding>
}

export interface DraftStage {
  clientKey: string
  name: string
  jobs: DraftJob[]
}

export interface PipelineDraft {
  name: string
  repoUrl: string
  gitRef: string
  credentialId: string
  stages: DraftStage[]
}

export function userVisibleParams(kind?: TaskKindVO | null): TaskKindParam[] {
  return (kind?.params ?? []).filter((param) => normalizeVisibility(param.visibility) === 'user')
}

export function hiddenParamCount(kind?: TaskKindVO | null): number {
  return (kind?.params ?? []).filter((param) => normalizeVisibility(param.visibility) === 'hidden').length
}

export function blankPipeline(kinds: TaskKindVO[]): PipelineDraft {
  const preferred = kinds.find((kind) => kind.enabled && kind.kindValue === 'CLONE')
    ?? kinds.find((kind) => kind.enabled)
  return {
    name: '',
    repoUrl: '',
    gitRef: 'main',
    credentialId: '',
    stages: [
      {
        clientKey: newKey(),
        name: '构建',
        jobs: [preferred ? jobFromKind(preferred) : emptyJob()],
      },
    ],
  }
}

export function draftFromPipeline(pipeline: PipelineSummary): PipelineDraft {
  const stages = (pipeline.stages ?? []).map((stage) => ({
    clientKey: newKey(),
    name: stage.name ?? '',
    jobs: (stage.jobs ?? []).map((job) => ({
      clientKey: newKey(),
      name: job.name ?? '',
      kind: job.kind ?? '',
      command: job.command ?? '',
      stack: job.stack,
      runtimeVersion: job.runtimeVersion,
      toolVersion: job.toolVersion,
      paramBindings: { ...(job.paramBindings ?? {}) },
    })),
  }))
  return {
    name: pipeline.name ?? '',
    repoUrl: pipeline.repoUrl ?? '',
    gitRef: pipeline.gitRef || 'main',
    credentialId: pipeline.credentialId == null ? '' : String(pipeline.credentialId),
    stages: stages.length > 0 ? stages : blankPipeline([]).stages,
  }
}

export function emptyStage(): DraftStage {
  return { clientKey: newKey(), name: '', jobs: [emptyJob()] }
}

export function emptyJob(): DraftJob {
  return { clientKey: newKey(), name: '', kind: '', command: '', paramBindings: {} }
}

export function jobFromKind(kind: TaskKindVO): DraftJob {
  return {
    clientKey: newKey(),
    name: kind.label || kind.kindValue,
    kind: kind.kindValue,
    command: kind.defaultCommand ?? '',
    paramBindings: defaultBindings(kind),
  }
}

export function applyJobKind(job: DraftJob, kind: TaskKindVO, previous?: TaskKindVO): DraftJob {
  const previousDefault = previous?.defaultCommand ?? ''
  const keepCommand = job.command.trim().length > 0 && job.command !== previousDefault
  const bindings = defaultBindings(kind)
  for (const [key, binding] of Object.entries(job.paramBindings)) {
    if (!bindings[key]) continue
    bindings[key] = binding.mode === 'runtime'
      ? { mode: 'runtime' }
      : { mode: 'fixed', value: binding.value ?? bindings[key].value ?? '' }
  }
  return {
    ...job,
    kind: kind.kindValue,
    name: job.name.trim() ? job.name : kind.label || kind.kindValue,
    command: keepCommand ? job.command : kind.defaultCommand ?? '',
    paramBindings: bindings,
  }
}

export function moveItem<T>(items: T[], index: number, direction: -1 | 1): T[] {
  const target = index + direction
  if (target < 0 || target >= items.length) return items
  const copy = items.slice()
  const [item] = copy.splice(index, 1)
  if (!item) return items
  copy.splice(target, 0, item)
  return copy
}

export function validatePipelineDraft(draft: PipelineDraft, kinds: TaskKindVO[]): string | null {
  if (!draft.name.trim()) return '名称不能为空'
  if (draft.stages.length === 0) return '流水线至少需要一个阶段'
  const known = new Map(kinds.map((kind) => [kind.kindValue, kind]))
  let cloneCount = 0
  for (const stage of draft.stages) {
    const stageName = stage.name.trim()
    if (!stageName) return '阶段名称不能为空'
    if (stage.jobs.length === 0) return `阶段「${stageName}」至少需要一个任务`
    const approvals = stage.jobs.filter((job) => job.kind === 'APPROVAL').length
    if (approvals > 0 && stage.jobs.length !== 1) return '审批任务必须独占一个阶段'
    if (stage.jobs.filter((job) => job.kind === 'IMAGE').length > 1) return '同一阶段只能有一个镜像构建'
    for (const job of stage.jobs) {
      const jobName = job.name.trim()
      if (!jobName) return '任务名称不能为空'
      if (!job.kind) return `任务「${jobName}」需要选择类型`
      const meta = known.get(job.kind)
      if (kinds.length > 0 && !meta) return `未知任务类型: ${job.kind}`
      const needsCommand = meta ? meta.requiresCommand : job.kind !== 'CLONE' && job.kind !== 'APPROVAL'
      if (needsCommand && !job.command.trim()) return `任务「${jobName}」需要命令`
      if (job.kind === 'CLONE') cloneCount += 1
    }
  }
  if (cloneCount > 1) return '流水线至多一个代码克隆任务'
  if (cloneCount > 0) {
    const repoError = validateRepoUrl(draft.repoUrl)
    if (repoError) return repoError
  }
  return null
}

export function toPipelineSavePayload(draft: PipelineDraft, kinds: TaskKindVO[]): PipelineSavePayload {
  const known = new Map(kinds.map((kind) => [kind.kindValue, kind]))
  const stages: PipelineStage[] = draft.stages.map((stage, stageIndex) => ({
    name: stage.name.trim(),
    sortOrder: stageIndex,
    jobs: stage.jobs.map((job, jobIndex) => ({
      name: job.name.trim(),
      kind: job.kind,
      command: job.command,
      stack: job.stack,
      runtimeVersion: job.runtimeVersion,
      toolVersion: job.toolVersion,
      sortOrder: jobIndex,
      paramBindings: resolvedBindings(job, known.get(job.kind)),
    })),
  }))
  return {
    name: draft.name.trim(),
    repoUrl: draft.repoUrl.trim(),
    gitRef: draft.gitRef.trim() || 'main',
    credentialId: draft.credentialId ? draft.credentialId : null,
    stages,
  }
}

export function resolvedBindings(job: DraftJob, kind?: TaskKindVO): Record<string, JobParamBinding> {
  const bindings: Record<string, JobParamBinding> = {}
  for (const param of userVisibleParams(kind)) {
    const existing = job.paramBindings[param.paramKey]
    if (existing?.mode === 'runtime') {
      bindings[param.paramKey] = { mode: 'runtime' }
      continue
    }
    bindings[param.paramKey] = {
      mode: 'fixed',
      value: existing?.value ?? param.defaultValue ?? '',
    }
  }
  return bindings
}

function defaultBindings(kind: TaskKindVO): Record<string, JobParamBinding> {
  const bindings: Record<string, JobParamBinding> = {}
  for (const param of userVisibleParams(kind)) {
    bindings[param.paramKey] = { mode: 'fixed', value: param.defaultValue ?? '' }
  }
  return bindings
}

function validateRepoUrl(value: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return '包含代码克隆时仓库地址不能为空'
  let url: URL
  try {
    url = new URL(trimmed)
  } catch {
    return '仓库地址无法解析'
  }
  if (url.protocol !== 'http:' && url.protocol !== 'https:') return '仓库地址需要 http 或 https 协议'
  const path = url.pathname.replace(/^\//, '')
  if (!url.host || !path) return '仓库地址缺少主机或路径'
  return null
}

function newKey(): string {
  return crypto.randomUUID()
}
