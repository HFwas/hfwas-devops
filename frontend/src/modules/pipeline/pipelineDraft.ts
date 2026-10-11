import { jobKindLabel, jobKindMeta } from '@/modules/pipeline/graph/jobCatalog'
import {
  addParallelJob,
  canAddKindToStage,
  canChangeJobKind,
  createEditorJob,
  defaultCommandForKind,
  findEditorJob,
  hasClone,
  insertStageAt,
  nextClientKey,
  toEditorStages,
  toSaveStages,
} from '@/modules/pipeline/graph/pipelineGraph'
import type {
  EditorJob,
  EditorStage,
  JobKind,
  JobParamBinding,
  PipelineSavePayload,
  PipelineSummary,
  TaskKindParam,
  TaskKindVO,
} from '@/modules/pipeline/types/pipeline'

/** 由凭证或平台注入，不在任务检查器里编辑。 */
const PLATFORM_HIDDEN_PARAM_KEYS = new Set([
  'GIT_USERNAME',
  'GIT_PASSWORD',
  'GIT_EMBEDDED_AUTH',
  'GIT_HTTP_PROXY',
])

const STAGE_TITLES: Record<string, string> = {
  代码: '拉取',
  构建: '构建',
  质量控制: '检查',
  制品: '制品',
  部署: '部署',
  测试: '测试',
  命令: '命令',
  流程: '流程',
}

export interface PipelineDraft {
  name: string
  repoUrl: string
  gitRef: string
  credentialId: string
  stages: EditorStage[]
}

export function emptyDraft(): PipelineDraft {
  return { name: '', repoUrl: '', gitRef: 'main', credentialId: '', stages: [] }
}

export function draftFromPipeline(pipeline: PipelineSummary): PipelineDraft {
  const ref = (pipeline.gitRef ?? '').replace(/^refs\/heads\//, '').replace(/^refs\/tags\//, '').trim()
  return {
    name: pipeline.name ?? '',
    repoUrl: pipeline.repoUrl ?? '',
    gitRef: ref || 'main',
    credentialId: pipeline.credentialId == null || pipeline.credentialId === '' ? '' : String(pipeline.credentialId),
    stages: toEditorStages(pipeline.stages),
  }
}

export function isUserVisibleParam(param: {
  paramKey?: string | null
  hidden?: boolean | null
  visibility?: string | null
  paramType?: string | null
}): boolean {
  if (!param.paramKey) return false
  if (param.hidden || param.visibility === 'hidden') return false
  if (param.paramType === 'hidden') return false
  return !PLATFORM_HIDDEN_PARAM_KEYS.has(param.paramKey)
}

export function visibleParams(params?: TaskKindParam[] | null): TaskKindParam[] {
  return (params ?? [])
    .filter((param) => isUserVisibleParam(param))
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
}

export function paramChoices(options?: TaskKindParam['options']): Array<{ value: string; label: string }> {
  const raw = (options ?? []) as Array<string | { value?: string; label?: string }>
  return raw.flatMap((item) => {
    if (typeof item === 'string') {
      const value = item.trim()
      return value ? [{ value, label: value }] : []
    }
    if (item && typeof item === 'object') {
      const value = String(item.value ?? '').trim()
      if (!value) return []
      return [{ value, label: item.label?.trim() || value }]
    }
    return []
  })
}

export function commandForKind(kind: string, kinds: TaskKindVO[] = []): string {
  const remote = kinds.find((item) => item.kindValue === kind)
  if (remote?.commandTemplate?.trim()) return remote.commandTemplate
  if (remote?.defaultCommand?.trim()) return remote.defaultCommand
  return defaultCommandForKind(kind)
}

export function kindNeedsCommand(kind: string, kinds: TaskKindVO[] = []): boolean {
  const catalog = jobKindMeta(kind)
  if (catalog) return catalog.requiresCommand
  const remote = kinds.find((item) => item.kindValue === kind)
  if (remote) return remote.requiresCommand
  return kind !== 'CLONE' && kind !== 'APPROVAL'
}

export function defaultStageName(kind: string, label?: string): string {
  const group = jobKindMeta(kind)?.group
  if (group && STAGE_TITLES[group]) return STAGE_TITLES[group]
  return label?.trim() || jobKindLabel(kind) || '未命名'
}

export function bindingsForKind(params: TaskKindParam[] | null | undefined, gitRef: string): Record<string, JobParamBinding> {
  const bindings: Record<string, JobParamBinding> = {}
  for (const param of visibleParams(params)) {
    bindings[param.paramKey] = {
      mode: 'fixed',
      value: param.paramKey === 'GIT_REF' ? gitRef : (param.defaultValue ?? ''),
    }
  }
  return bindings
}

export function createJobFromKind(kind: TaskKindVO, gitRef: string): EditorJob {
  return createEditorJob(kind.kindValue as JobKind, {
    name: kind.label || jobKindLabel(kind.kindValue),
    command: commandForKind(kind.kindValue, [kind]),
    paramBindings: bindingsForKind(kind.params, gitRef),
  })
}

function reindex(stages: EditorStage[]): EditorStage[] {
  return stages.map((stage, index) => ({
    ...stage,
    sortOrder: index,
    jobs: stage.jobs.map((job, jobIndex) => ({ ...job, sortOrder: jobIndex })),
  }))
}

function nameStageOfJob(stages: EditorStage[], jobKey: string, name: string): EditorStage[] {
  return stages.map((stage) =>
    stage.jobs.length === 1 && stage.jobs[0]?.clientKey === jobKey ? { ...stage, name } : stage,
  )
}

export function appendEmptyStage(stages: EditorStage[]): EditorStage[] {
  return reindex([
    ...stages,
    { clientKey: nextClientKey('stage'), name: '未命名', sortOrder: stages.length, jobs: [] },
  ])
}

export function addPaletteJob(
  stages: EditorStage[],
  stageKey: string | null,
  job: EditorJob,
): { stages: EditorStage[]; error: string | null } {
  const globalError = canAddKindToStage(stages, null, job.kind as JobKind)
  const stageName = defaultStageName(job.kind, job.name)
  if (!stageKey) {
    if (globalError) return { stages, error: globalError }
    const inserted = insertStageAt(stages, stages.length - 1, job)
    return { stages: nameStageOfJob(inserted, job.clientKey, stageName), error: null }
  }
  const stage = stages.find((item) => item.clientKey === stageKey)
  if (!stage) return { stages, error: '阶段不存在' }
  if (stage.jobs.length === 0) {
    if (globalError) return { stages, error: globalError }
    const name = stage.name === '未命名' ? stageName : stage.name
    return {
      stages: stages.map((item) =>
        item.clientKey === stageKey ? { ...item, name, jobs: [{ ...job, sortOrder: 0 }] } : item,
      ),
      error: null,
    }
  }
  const localError = canAddKindToStage(stages, stageKey, job.kind as JobKind)
  if (!localError) {
    return { stages: addParallelJob(stages, stageKey, job), error: null }
  }
  if (globalError) return { stages, error: localError }
  const index = stages.findIndex((item) => item.clientKey === stageKey)
  const inserted = insertStageAt(stages, index, job)
  return { stages: nameStageOfJob(inserted, job.clientKey, stageName), error: null }
}

export function updateJob(stages: EditorStage[], jobKey: string, patch: Partial<EditorJob>): EditorStage[] {
  return stages.map((stage) => ({
    ...stage,
    jobs: stage.jobs.map((job) => (job.clientKey === jobKey ? { ...job, ...patch } : job)),
  }))
}

export function changeJobKind(
  stages: EditorStage[],
  jobKey: string,
  kind: string,
  kinds: TaskKindVO[],
  gitRef: string,
): { stages: EditorStage[]; error: string | null } {
  const error = canChangeJobKind(stages, jobKey, kind as JobKind)
  if (error) return { stages, error }
  const current = findEditorJob(stages, jobKey)
  if (!current) return { stages, error: '任务不存在' }
  const prevLabel = kinds.find((item) => item.kindValue === current.kind)?.label ?? jobKindLabel(current.kind)
  const nextLabel = kinds.find((item) => item.kindValue === kind)?.label ?? jobKindLabel(kind)
  const prevCommand = commandForKind(current.kind, kinds)
  const nextCommand = commandForKind(kind, kinds)
  const name = !current.name.trim() || current.name === prevLabel ? nextLabel : current.name
  const command = !current.command || current.command === prevCommand ? nextCommand : current.command
  const params = kinds.find((item) => item.kindValue === kind)?.params
  return {
    stages: updateJob(stages, jobKey, {
      kind,
      name,
      command,
      paramBindings: bindingsForKind(params, gitRef),
    }),
    error: null,
  }
}

export function removeStage(stages: EditorStage[], stageKey: string): EditorStage[] {
  return reindex(stages.filter((stage) => stage.clientKey !== stageKey))
}

export function removeJob(stages: EditorStage[], jobKey: string): EditorStage[] {
  return reindex(
    stages.map((stage) => ({
      ...stage,
      jobs: stage.jobs.filter((job) => job.clientKey !== jobKey),
    })),
  )
}

export function moveStage(stages: EditorStage[], stageKey: string, delta: -1 | 1): EditorStage[] {
  const index = stages.findIndex((stage) => stage.clientKey === stageKey)
  const next = index + delta
  if (index < 0 || next < 0 || next >= stages.length) return stages
  const copy = [...stages]
  const [item] = copy.splice(index, 1)
  copy.splice(next, 0, item)
  return reindex(copy)
}

/**
 * 把任务插到目标阶段。`beforeIndex` 是移动前目标阶段里的下标；`null` 表示放到末尾。
 */
export function moveJobBefore(
  stages: EditorStage[],
  jobKey: string,
  toStageKey: string,
  beforeIndex: number | null,
): { stages: EditorStage[]; error: string | null } {
  const source = stages.find((stage) => stage.jobs.some((job) => job.clientKey === jobKey))
  const job = source?.jobs.find((item) => item.clientKey === jobKey)
  const target = stages.find((stage) => stage.clientKey === toStageKey)
  if (!source || !job || !target) return { stages, error: '任务不存在' }
  const fromIndex = source.jobs.findIndex((item) => item.clientKey === jobKey)
  const same = source.clientKey === toStageKey
  const remaining = target.jobs.filter((item) => item.clientKey !== jobKey)
  let index = beforeIndex == null ? remaining.length : beforeIndex
  if (same && beforeIndex != null && fromIndex < beforeIndex) index -= 1
  index = Math.max(0, Math.min(index, remaining.length))
  const next = reindex(
    stages.map((stage) => {
      const jobs = stage.jobs.filter((item) => item.clientKey !== jobKey)
      if (stage.clientKey !== toStageKey) return { ...stage, jobs }
      const copy = [...jobs]
      copy.splice(index, 0, job)
      return { ...stage, jobs: copy }
    }),
  )
  const dest = next.find((stage) => stage.clientKey === toStageKey)
  if (!dest) return { stages, error: '阶段不存在' }
  const approvals = dest.jobs.filter((item) => item.kind === 'APPROVAL').length
  if (approvals > 0 && dest.jobs.length !== 1) return { stages, error: '审批任务必须独占一列' }
  const images = dest.jobs.filter((item) => item.kind === 'IMAGE').length
  if (images > 1) return { stages, error: '镜像构建不能与其它镜像构建并行' }
  return { stages: next, error: null }
}

export function moveJob(
  stages: EditorStage[],
  jobKey: string,
  delta: -1 | 1,
): { stages: EditorStage[]; error: string | null } {
  const stage = stages.find((item) => item.jobs.some((job) => job.clientKey === jobKey))
  if (!stage) return { stages, error: '任务不存在' }
  const index = stage.jobs.findIndex((job) => job.clientKey === jobKey)
  const next = index + delta
  if (next < 0 || next >= stage.jobs.length) return { stages, error: null }
  const beforeIndex = delta < 0 ? next : next + 1
  return moveJobBefore(stages, jobKey, stage.clientKey, beforeIndex)
}

export function locateJob(
  stages: EditorStage[],
  jobKey: string | null | undefined,
): { stageIndex: number; jobIndex: number } | null {
  if (!jobKey) return null
  for (let stageIndex = 0; stageIndex < stages.length; stageIndex += 1) {
    const jobIndex = stages[stageIndex].jobs.findIndex((job) => job.clientKey === jobKey)
    if (jobIndex >= 0) return { stageIndex, jobIndex }
  }
  return null
}

export function jobAt(stages: EditorStage[], stageIndex: number, jobIndex: number): EditorJob | null {
  return stages[stageIndex]?.jobs[jobIndex] ?? null
}

function validateRepoUrl(repoUrl: string): string | null {
  const trimmed = repoUrl.trim()
  if (!trimmed) return '仓库地址不能为空'
  let uri: URL
  try {
    uri = new URL(trimmed)
  } catch {
    return `仓库地址无法解析: ${trimmed}`
  }
  if (uri.protocol !== 'http:' && uri.protocol !== 'https:') {
    return `仓库地址需要 http 或 https 协议: ${trimmed}`
  }
  const path = uri.pathname.replace(/^\//, '')
  if (!uri.hostname || !path) return `仓库地址缺少主机或路径: ${trimmed}`
  return null
}

export function validateDraft(draft: PipelineDraft, kinds: TaskKindVO[] = []): string | null {
  if (!draft.name.trim()) return '名称不能为空'
  if (draft.stages.length === 0) return '流水线至少需要一个阶段'
  const jobs = draft.stages.flatMap((stage) => stage.jobs)
  if (jobs.filter((job) => job.kind === 'CLONE').length > 1) return '流水线至多一个克隆任务'
  if (hasClone(draft.stages)) {
    const repoError = validateRepoUrl(draft.repoUrl)
    if (repoError) return repoError
  }
  for (const stage of draft.stages) {
    if (!stage.name.trim()) return '阶段名称不能为空'
    if (stage.jobs.length === 0) return `阶段「${stage.name}」至少需要一个任务`
    const approvals = stage.jobs.filter((job) => job.kind === 'APPROVAL').length
    if (approvals > 0 && stage.jobs.length !== 1) return '审批任务必须独占一列'
    if (stage.jobs.filter((job) => job.kind === 'IMAGE').length > 1) {
      return '镜像构建不能与其它镜像构建并行'
    }
    for (const job of stage.jobs) {
      if (!job.name.trim()) return '任务名称不能为空'
      if (kindNeedsCommand(job.kind, kinds) && !job.command?.trim()) {
        return `任务「${job.name}」需要命令`
      }
      const params = kinds.find((item) => item.kindValue === job.kind)?.params
      for (const param of visibleParams(params)) {
        if (!param.required) continue
        const binding = job.paramBindings?.[param.paramKey]
        if ((binding?.mode ?? 'fixed') === 'runtime') continue
        if (param.paramKey === 'GIT_REF') continue
        const value = (binding?.value ?? param.defaultValue ?? '').trim()
        if (!value) return `任务「${job.name}」的参数 ${param.paramLabel || param.paramKey} 不能为空`
      }
    }
  }
  return null
}

function withFixedGitRef(stages: EditorStage[], gitRef: string): EditorStage[] {
  return stages.map((stage) => ({
    ...stage,
    jobs: stage.jobs.map((job) => {
      const binding = job.paramBindings?.GIT_REF
      if (!binding || binding.mode !== 'fixed') return job
      return {
        ...job,
        paramBindings: { ...job.paramBindings, GIT_REF: { mode: 'fixed', value: gitRef } },
      }
    }),
  }))
}

export function toSavePayload(draft: PipelineDraft): PipelineSavePayload {
  const gitRef = draft.gitRef.trim() || 'main'
  return {
    name: draft.name.trim(),
    repoUrl: draft.repoUrl.trim(),
    gitRef,
    credentialId: draft.credentialId ? draft.credentialId : null,
    stages: toSaveStages(withFixedGitRef(draft.stages, gitRef)),
  }
}
