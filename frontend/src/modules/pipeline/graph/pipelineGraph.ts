import type { EditorJob, EditorStage, JobKind, PipelineStage, ToolchainOption } from '@/modules/pipeline/types/pipeline'

export const JOB_KIND_OPTIONS: Array<{
  value: JobKind
  label: string
  requiresCommand: boolean
  defaultCommand: string
}> = [
  { value: 'CLONE', label: '克隆', requiresCommand: false, defaultCommand: '' },
  {
    value: 'LINT',
    label: '代码检查',
    requiresCommand: true,
    defaultCommand: `export LINT_SEMGREP_ARGS="scan --error --config=auto ."
# export SONAR_HOST_URL=https://sonar.example.com
# export SONAR_TOKEN=
# export SONAR_PROJECT_KEY=app`,
  },
  { value: 'BUILD', label: '构建', requiresCommand: true, defaultCommand: '' },
  { value: 'TEST', label: '测试', requiresCommand: true, defaultCommand: '' },
  {
    value: 'SCAN',
    label: '安全扫描',
    requiresCommand: true,
    defaultCommand: 'trivy fs --exit-code 1 --scanners vuln,secret,misconfig .',
  },
  { value: 'PACKAGE', label: '打包', requiresCommand: true, defaultCommand: '' },
  { value: 'CUSTOM', label: '自定义', requiresCommand: true, defaultCommand: 'echo ok' },
  {
    value: 'IMAGE',
    label: '镜像构建',
    requiresCommand: true,
    defaultCommand: `export DEST=registry.example.com/app:tag
export IMAGE_PLATFORMS=linux/amd64
export DOCKERFILE=Dockerfile`,
  },
  { value: 'PUBLISH', label: '发布制品', requiresCommand: true, defaultCommand: '' },
  {
    value: 'UPLOAD',
    label: '上传对象存储',
    requiresCommand: true,
    defaultCommand:
      'rclone copy ./ :s3:bucket/prefix --s3-provider=Minio --s3-endpoint="${S3_ENDPOINT}" --s3-access-key-id="${S3_ACCESS_KEY}" --s3-secret-access-key="${S3_SECRET_KEY}"',
  },
  { value: 'DEPLOY', label: '部署', requiresCommand: true, defaultCommand: 'kubectl apply -f k8s/' },
  { value: 'APPROVAL', label: '人工卡点', requiresCommand: false, defaultCommand: '' },
  {
    value: 'NOTIFY',
    label: '通知',
    requiresCommand: true,
    defaultCommand:
      `curl -fsS -X POST 'https://example.com/hook' -H 'Content-Type: application/json' -d '{"status":"done"}'`,
  },
]

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
      command: jobItem.kind === 'CLONE' || jobItem.kind === 'APPROVAL' ? '' : jobItem.command,
      sortOrder: jobIndex,
    })),
  }))
}

export function isCloneJob(job: { kind?: string }): boolean {
  return job.kind === 'CLONE'
}

export function hasClone(stages: Array<{ jobs: Array<{ kind?: string }> }>): boolean {
  return stages.some((stage) => stage.jobs.some(isCloneJob))
}

export function jobKindLabel(kind?: string | null): string {
  return JOB_KIND_OPTIONS.find((item) => item.value === kind)?.label ?? kind ?? ''
}

export function requiresCommand(kind?: string | null): boolean {
  return kind !== 'CLONE' && kind !== 'APPROVAL'
}

export function defaultCommandForKind(kind: string, option?: ToolchainOption | null): string {
  if (kind === 'BUILD') return option?.buildCommand ?? ''
  if (kind === 'TEST') return option?.testCommand ?? ''
  return JOB_KIND_OPTIONS.find((item) => item.value === kind)?.defaultCommand ?? ''
}

export function kindSelectOptions(
  stages: Array<{ jobs: Array<{ kind?: string; clientKey?: string }> }>,
  editingClientKey?: string,
): Array<{ label: string; value: JobKind }> {
  const cloneTaken = stages.some((stage) =>
    stage.jobs.some((job) => job.kind === 'CLONE' && job.clientKey !== editingClientKey),
  )
  return JOB_KIND_OPTIONS.filter((item) => item.value !== 'CLONE' || !cloneTaken).map((item) => ({
    label: item.label,
    value: item.value,
  }))
}

export function canDeleteJob(_job?: { kind?: string }): boolean {
  return true
}

export function canDeleteStage(_stage?: { jobs: Array<{ kind?: string }> }): boolean {
  return true
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
