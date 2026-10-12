import { useEffect, useMemo, useRef, useState, type ReactNode } from 'react'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ArrowLeft,
  ArrowRight,
  ChevronDown,
  ChevronRight,
  ChevronUp,
  Plus,
  Trash2,
  Workflow,
  X,
} from 'lucide-react'
import { useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from '@/components/ui/collapsible'
import { Input } from '@/components/ui/input'
import { JobKindIcon } from '@/modules/pipeline/components/JobKindIcon'
import { StartRunButton } from '@/modules/pipeline/components/StartRunButton'
import { pipelineApi, pipelineCredentialApi, pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { jobKindMeta } from '@/modules/pipeline/graph/jobCatalog'
import { JOB_KIND_OPTIONS, canAddKindToStage, canChangeJobKind, jobKindLabel, stageOfJob } from '@/modules/pipeline/graph/pipelineGraph'
import {
  addPaletteJob,
  appendEmptyStage,
  changeJobKind,
  commandForKind,
  createJobFromKind,
  draftFromPipeline,
  emptyDraft,
  jobAt,
  locateJob,
  moveJob,
  moveJobBefore,
  moveStage,
  paramChoices,
  removeJob,
  removeStage,
  toSavePayload,
  updateJob,
  validateDraft,
  visibleParams,
  type PipelineDraft,
} from '@/modules/pipeline/pipelineDraft'
import { jobRunDuration, runStatusLabel } from '@/modules/pipeline/status'
import { coerceToolchain, stackLabel, toolsFor, uniqueStacks, runtimesFor } from '@/modules/pipeline/graph/toolchainCascade'
import type { EditorJob, EditorStage, EntityId, JobKind, JobParamBinding, PipelineRunJob, TaskKindVO } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

const controlClass =
  'flex h-8 w-full rounded-md border border-input bg-background px-2 text-sm shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring'

export function PipelineEditorPage() {
  const { pipelineId } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [createdId, setCreatedId] = useState<EntityId | null>(null)
  const effectiveId = pipelineId ?? (createdId == null ? '' : String(createdId))
  const [draft, setDraft] = useState<PipelineDraft>(emptyDraft)
  const [ready, setReady] = useState(!pipelineId)
  const [selectedJobKey, setSelectedJobKey] = useState<string | null>(null)
  const [targetStageKey, setTargetStageKey] = useState<string | null>(null)
  const [panelMode, setPanelMode] = useState<'config' | 'add'>('config')
  const [zoom, setZoom] = useState('100')
  const [busy, setBusy] = useState(false)
  const baseline = useRef(JSON.stringify(emptyDraft()))

  const pipelineQuery = useQuery({
    queryKey: ['pipeline', pipelineId],
    queryFn: () => pipelineApi.get(pipelineId!),
    enabled: !!pipelineId,
  })
  const kindsQuery = useQuery({
    queryKey: ['pipeline-task-kinds'],
    queryFn: () => pipelineTaskKindApi.list(),
  })
  const credentialsQuery = useQuery({
    queryKey: ['pipeline-credentials'],
    queryFn: () => pipelineCredentialApi.list(),
  })
  const toolchainsQuery = useQuery({
    queryKey: ['pipeline-toolchains'],
    queryFn: () => pipelineApi.toolchains(),
  })
  const runQuery = useQuery({
    queryKey: ['pipeline-editor-run', pipelineId, pipelineQuery.data?.lastRunId],
    queryFn: () => pipelineApi.getRun(pipelineId!, pipelineQuery.data!.lastRunId!),
    enabled: !!pipelineId && !!pipelineQuery.data?.lastRunId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      return status && !['SUCCEEDED', 'FAILED', 'CANCELLED'].includes(status) ? 4000 : false
    },
  })

  useEffect(() => {
    if (!pipelineId || !pipelineQuery.data || ready) return
    const next = draftFromPipeline(pipelineQuery.data)
    setDraft(next)
    baseline.current = JSON.stringify(next)
    setSelectedJobKey(next.stages.flatMap((stage) => stage.jobs)[0]?.clientKey ?? null)
    setReady(true)
  }, [pipelineId, pipelineQuery.data, ready])

  const kinds = useMemo(
    () => (kindsQuery.data ?? []).slice().sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label, 'zh')),
    [kindsQuery.data],
  )
  const selected = draft.stages.flatMap((stage) => stage.jobs).find((job) => job.clientKey === selectedJobKey) ?? null
  const runJobs = useMemo(() => {
    const map = new Map<string, PipelineRunJob>()
    for (const job of runQuery.data?.jobs ?? []) {
      if (job.jobId != null) map.set(String(job.jobId), job)
    }
    return map
  }, [runQuery.data])

  function resolveStageKey(current = draft): string | null {
    if (targetStageKey && current.stages.some((stage) => stage.clientKey === targetStageKey)) return targetStageKey
    const selectedStage = stageOfJob(current.stages, selectedJobKey ?? '')
    if (selectedStage) return selectedStage.clientKey
    return current.stages.at(-1)?.clientKey ?? null
  }

  async function persist(): Promise<EntityId | null> {
    const error = validateDraft(draft, kinds)
    if (error) {
      toast.error(error)
      return null
    }
    const payload = toSavePayload(draft)
    try {
      if (!effectiveId) {
        const id = await pipelineApi.create(payload)
        setCreatedId(id)
        await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
        return id
      }
      await pipelineApi.update(effectiveId, payload)
      await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
      await queryClient.invalidateQueries({ queryKey: ['pipeline', String(effectiveId)] })
      return effectiveId
    } catch (saveError) {
      toast.error(saveError instanceof Error ? saveError.message : '保存失败')
      return null
    }
  }

  async function reload(id: EntityId) {
    const freshPipeline = await pipelineApi.get(id)
    const fresh = draftFromPipeline(freshPipeline)
    const loc = locateJob(draft.stages, selectedJobKey)
    const nextKey = loc ? jobAt(fresh.stages, loc.stageIndex, loc.jobIndex)?.clientKey : fresh.stages[0]?.jobs[0]?.clientKey
    setDraft(fresh)
    setSelectedJobKey(nextKey ?? null)
    setTargetStageKey(null)
    setPanelMode('config')
    baseline.current = JSON.stringify(fresh)
  }

  async function onSave() {
    setBusy(true)
    try {
      const id = await persist()
      if (id == null) return
      toast.success('已保存')
      if (!pipelineId) {
        await navigate(`/pipeline/pipelines/${id}/edit`, { replace: true })
        return
      }
      await reload(id)
    } finally {
      setBusy(false)
    }
  }

  function onCancel() {
    if (JSON.stringify(draft) !== baseline.current && !window.confirm('放弃未保存的修改？')) return
    void navigate('/pipeline/pipelines')
  }

  function onAddKind(kind: TaskKindVO) {
    const stageKey = resolveStageKey()
    const job = createJobFromKind(kind, draft.gitRef.trim() || 'main')
    const result = addPaletteJob(draft.stages, stageKey, job)
    if (result.error) {
      toast.error(result.error)
      return
    }
    if (stageKey && result.stages.length > draft.stages.length) {
      const reason = canAddKindToStage(draft.stages, stageKey, kind.kindValue as JobKind)
      if (reason) toast.message(`${reason}，已添加到新阶段`)
    }
    setDraft({ ...draft, stages: result.stages })
    setSelectedJobKey(job.clientKey)
    setPanelMode('config')
    const host = result.stages.find((stage) => stage.jobs.some((item) => item.clientKey === job.clientKey))
    if (host) setTargetStageKey(host.clientKey)
  }

  function onAddStage() {
    setDraft({ ...draft, stages: appendEmptyStage(draft.stages) })
  }

  function onSelectJob(jobKey: string) {
    setSelectedJobKey(jobKey)
    setPanelMode('config')
  }

  function onOpenAddList(stageKey: string) {
    setTargetStageKey(stageKey)
    setPanelMode('add')
  }

  const destinationKey = resolveStageKey()
  const destinationIndex = draft.stages.findIndex((stage) => stage.clientKey === destinationKey)
  const destination = destinationIndex >= 0 ? draft.stages[destinationIndex] : null

  if (pipelineId && pipelineQuery.isLoading) return <p className="text-sm text-muted-foreground">加载中…</p>
  if (pipelineId && pipelineQuery.isError) return <p className="text-sm text-destructive">流水线加载失败</p>
  if (pipelineId && !ready) return <p className="text-sm text-muted-foreground">加载中…</p>

  return (
    <div className="flex h-[calc(100svh-var(--header-height)-2rem)] min-h-[640px] flex-col overflow-hidden rounded-lg border bg-card shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2 border-b px-4 py-2">
        <div className="text-sm font-medium">{pipelineId ? '编辑流水线' : '新建流水线'}</div>
        <div className="flex items-center gap-2">
          <Button type="button" variant="outline" size="sm" onClick={onCancel}>
            取消
          </Button>
          <Button type="button" variant="outline" size="sm" disabled={busy} onClick={() => void onSave()}>
            保存
          </Button>
          <StartRunButton
            label="保存并运行"
            variant="default"
            disabled={busy}
            resolvePipelineId={async () => {
              setBusy(true)
              try {
                return await persist()
              } finally {
                setBusy(false)
              }
            }}
          />
        </div>
      </div>

      <div
        data-slot="pipeline-basic-info"
        className="grid gap-3 border-b px-4 py-3 lg:grid-cols-[auto_minmax(0,0.9fr)_minmax(0,1.5fr)_minmax(0,0.7fr)_minmax(0,0.8fr)] lg:items-end"
      >
        <div className="flex items-center gap-2 pb-2 text-sm font-medium">
          <Workflow className="size-4 text-primary" />
          基本信息
        </div>
        <Field label="名称">
          <Input
            aria-label="名称"
            value={draft.name}
            placeholder="后端发布"
            className="h-8 font-console text-sm"
            onChange={(event) => setDraft({ ...draft, name: event.target.value })}
          />
        </Field>
        <Field label="仓库">
          <Input
            aria-label="仓库"
            value={draft.repoUrl}
            placeholder="https://github.com/org/repo.git"
            className="h-8 font-mono text-xs"
            onChange={(event) => setDraft({ ...draft, repoUrl: event.target.value })}
          />
        </Field>
        <Field label="分支">
          <Input
            aria-label="分支"
            value={draft.gitRef}
            placeholder="main"
            className="h-8 font-mono text-xs"
            onChange={(event) => setDraft({ ...draft, gitRef: event.target.value })}
          />
        </Field>
        <Field label="凭证">
          <select
            aria-label="凭证"
            className={controlClass}
            value={draft.credentialId}
            onChange={(event) => setDraft({ ...draft, credentialId: event.target.value })}
          >
            <option value="">不使用凭证</option>
            {(credentialsQuery.data ?? []).map((item) => (
              <option key={String(item.id)} value={String(item.id)}>
                {item.name}
              </option>
            ))}
            {draft.credentialId && !(credentialsQuery.data ?? []).some((item) => String(item.id) === draft.credentialId) ? (
              <option value={draft.credentialId}>当前凭证</option>
            ) : null}
          </select>
        </Field>
      </div>

      <div className="grid min-h-0 flex-1 lg:grid-cols-[minmax(0,1fr)_22rem]">
        <Lanes
          stages={draft.stages}
          selectedJobKey={selectedJobKey}
          targetStageKey={panelMode === 'add' ? targetStageKey : null}
          zoom={zoom}
          onZoom={setZoom}
          runJobs={runJobs}
          onSelectJob={onSelectJob}
          onTargetStage={onOpenAddList}
          onAddStage={onAddStage}
          onRenameStage={(stageKey, name) =>
            setDraft({
              ...draft,
              stages: draft.stages.map((stage) => (stage.clientKey === stageKey ? { ...stage, name } : stage)),
            })
          }
          onMoveStage={(stageKey, delta) => setDraft({ ...draft, stages: moveStage(draft.stages, stageKey, delta) })}
          onRemoveStage={(stageKey) => {
            const stages = removeStage(draft.stages, stageKey)
            setDraft({ ...draft, stages })
            if (targetStageKey === stageKey) {
              setTargetStageKey(null)
              setPanelMode('config')
            }
            if (selected && !stages.some((stage) => stage.jobs.some((job) => job.clientKey === selected.clientKey))) {
              setSelectedJobKey(null)
            }
          }}
          onMoveJob={(jobKey, delta) => {
            const result = moveJob(draft.stages, jobKey, delta)
            if (result.error) toast.error(result.error)
            else setDraft({ ...draft, stages: result.stages })
          }}
          onMoveJobBefore={(jobKey, stageKey, beforeIndex) => {
            const result = moveJobBefore(draft.stages, jobKey, stageKey, beforeIndex)
            if (result.error) toast.error(result.error)
            else setDraft({ ...draft, stages: result.stages })
          }}
          onRemoveJob={(jobKey) => {
            const job = draft.stages.flatMap((stage) => stage.jobs).find((item) => item.clientKey === jobKey)
            if (!job || !window.confirm(`删除任务「${job.name}」？`)) return
            setDraft({ ...draft, stages: removeJob(draft.stages, jobKey) })
            if (selectedJobKey === jobKey) setSelectedJobKey(null)
          }}
        />
        <aside
          data-slot="pipeline-side-panel"
          data-mode={panelMode === 'add' && destination ? 'add' : 'config'}
          className="flex min-h-0 flex-col border-t lg:border-t-0 lg:border-l"
        >
          {panelMode === 'add' && destination ? (
            <TaskAddList
              stageIndex={destinationIndex}
              stageName={destination.name}
              kinds={kinds}
              loading={kindsQuery.isLoading}
              onClose={() => setPanelMode('config')}
              onAdd={onAddKind}
            />
          ) : (
            <Inspector
              job={selected}
              stages={draft.stages}
              kinds={kinds}
              gitRef={draft.gitRef}
              toolchains={toolchainsQuery.data ?? []}
              onClose={() => setSelectedJobKey(null)}
              onChange={(patch) => {
                if (!selected) return
                setDraft({ ...draft, stages: updateJob(draft.stages, selected.clientKey, patch) })
              }}
              onKind={(kind) => {
                if (!selected) return
                const result = changeJobKind(draft.stages, selected.clientKey, kind, kinds, draft.gitRef.trim() || 'main')
                if (result.error) toast.error(result.error)
                else setDraft({ ...draft, stages: result.stages })
              }}
            />
          )}
        </aside>
      </div>
    </div>
  )
}

function Field({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex min-w-0 flex-col gap-1">
      <span className="text-xs text-muted-foreground">{label}</span>
      {children}
    </label>
  )
}

function TaskAddList({
  stageIndex,
  stageName,
  kinds,
  loading,
  onClose,
  onAdd,
}: {
  stageIndex: number
  stageName: string
  kinds: TaskKindVO[]
  loading: boolean
  onClose: () => void
  onAdd: (kind: TaskKindVO) => void
}) {
  const groups = useMemo(() => {
    const grouped = new Map<string, TaskKindVO[]>()
    for (const kind of kinds) {
      if (!kind.enabled) continue
      const name = kind.taskGroup || '其他'
      grouped.set(name, [...(grouped.get(name) ?? []), kind])
    }
    return [...grouped.entries()]
  }, [kinds])
  const [openGroup, setOpenGroup] = useState<string | null>(null)
  const activeGroup = openGroup === null ? (groups[0]?.[0] ?? null) : openGroup || null

  return (
    <div data-slot="pipeline-task-list" className="flex min-h-0 flex-1 flex-col">
      <div className="flex items-center justify-between gap-2 border-b px-3 py-2">
        <div className="min-w-0 truncate text-sm font-medium">添加任务到「阶段 {stageIndex + 1} · {stageName}」</div>
        <Button type="button" variant="ghost" size="icon" aria-label="关闭添加任务" onClick={onClose}>
          <X />
        </Button>
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-2">
        {loading && <p className="px-2 py-3 text-sm text-muted-foreground">加载中…</p>}
        {!loading && groups.length === 0 && <p className="px-2 py-3 text-sm text-muted-foreground">没有可添加的任务</p>}
        {groups.map(([group, groupKinds]) => {
          const open = activeGroup === group
          return (
            <div key={group} className="mb-1">
              <button
                type="button"
                className={cn(
                  'flex w-full items-center gap-2 rounded-md px-3 py-2 text-sm font-medium',
                  open ? 'bg-primary text-primary-foreground' : 'hover:bg-muted',
                )}
                aria-expanded={open}
                aria-label={`${group} (${groupKinds.length})`}
                onClick={() => setOpenGroup(open ? '' : group)}
              >
                <span className="min-w-0 flex-1 truncate text-left">
                  {group} ({groupKinds.length})
                </span>
                <ChevronDown className={cn('size-4 shrink-0 transition-transform', open && 'rotate-180')} />
              </button>
              {open &&
                groupKinds.map((kind) => (
                  <button
                    key={kind.kindValue}
                    type="button"
                    className="flex w-full items-center gap-2 rounded-md px-2 py-2 text-left hover:bg-muted"
                    aria-label={`添加${kind.label}`}
                    onClick={() => onAdd(kind)}
                  >
                    <JobKindIcon kind={kind.kindValue} className="size-4 shrink-0 text-primary" />
                    <span className="min-w-0 flex-1">
                      <span className="block truncate text-sm font-medium">{kind.label}</span>
                      <span className="block truncate text-xs text-muted-foreground">{kind.description || kind.kindValue}</span>
                    </span>
                  </button>
                ))}
            </div>
          )
        })}
      </div>
      <p className="border-t px-3 py-2.5 text-center text-xs text-muted-foreground">点击任务即可添加</p>
    </div>
  )
}

function Lanes({
  stages,
  selectedJobKey,
  targetStageKey,
  zoom,
  onZoom,
  runJobs,
  onSelectJob,
  onTargetStage,
  onAddStage,
  onRenameStage,
  onMoveStage,
  onRemoveStage,
  onMoveJob,
  onMoveJobBefore,
  onRemoveJob,
}: {
  stages: EditorStage[]
  selectedJobKey: string | null
  targetStageKey: string | null
  zoom: string
  onZoom: (value: string) => void
  runJobs: Map<string, PipelineRunJob>
  onSelectJob: (jobKey: string) => void
  onTargetStage: (stageKey: string) => void
  onAddStage: () => void
  onRenameStage: (stageKey: string, name: string) => void
  onMoveStage: (stageKey: string, delta: -1 | 1) => void
  onRemoveStage: (stageKey: string) => void
  onMoveJob: (jobKey: string, delta: -1 | 1) => void
  onMoveJobBefore: (jobKey: string, stageKey: string, beforeIndex: number | null) => void
  onRemoveJob: (jobKey: string) => void
}) {
  return (
    <section data-slot="pipeline-lanes" className="flex min-h-0 min-w-0 flex-col bg-muted/20">
      <div className="flex items-center justify-end gap-2 border-b px-3 py-1.5">
        <select aria-label="缩放" className="h-7 rounded-md border bg-background px-2 text-xs" value={zoom} onChange={(event) => onZoom(event.target.value)}>
          <option value="75">75%</option>
          <option value="100">100%</option>
          <option value="125">125%</option>
          <option value="150">150%</option>
        </select>
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-4">
        <div className="flex min-h-full items-start gap-2" style={{ zoom: Number(zoom) / 100 }}>
          {stages.length === 0 && (
            <p className="self-center px-2 text-sm text-muted-foreground">添加阶段，再在阶段中添加任务。</p>
          )}
          {stages.map((stage, index) => (
            <div key={stage.clientKey} className="flex items-start gap-2">
              {index > 0 && <ChevronRight className="mt-16 size-4 shrink-0 text-muted-foreground" />}
              <div
                data-slot="pipeline-stage"
                className={cn(
                  'flex w-60 shrink-0 flex-col rounded-lg border bg-card shadow-sm',
                  targetStageKey === stage.clientKey && 'border-primary ring-2 ring-primary/20',
                )}
                onDragOver={(event) => event.preventDefault()}
                onDrop={(event) => {
                  const raw = event.dataTransfer.getData('text/plain')
                  if (!raw.startsWith('job:')) return
                  event.preventDefault()
                  onMoveJobBefore(raw.slice(4), stage.clientKey, null)
                }}
              >
                <div className="flex items-center gap-1 border-b px-2 py-2">
                  <span className="shrink-0 text-xs text-muted-foreground">阶段 {index + 1} ·</span>
                  <input
                    aria-label={`阶段 ${index + 1} 名称`}
                    value={stage.name}
                    className="h-7 min-w-0 flex-1 rounded-md border border-transparent bg-transparent px-1 font-console text-sm font-medium outline-none hover:border-input focus:border-input"
                    onChange={(event) => onRenameStage(stage.clientKey, event.target.value)}
                  />
                  <IconButton label="左移阶段" disabled={index === 0} onClick={() => onMoveStage(stage.clientKey, -1)}>
                    <ArrowLeft />
                  </IconButton>
                  <IconButton label="右移阶段" disabled={index === stages.length - 1} onClick={() => onMoveStage(stage.clientKey, 1)}>
                    <ArrowRight />
                  </IconButton>
                  <IconButton label="删除阶段" onClick={() => onRemoveStage(stage.clientKey)}>
                    <Trash2 />
                  </IconButton>
                </div>
                <div className="flex flex-col gap-2 p-2">
                  {stage.jobs.map((job, jobIndex) => {
                    const runJob = job.id == null ? undefined : runJobs.get(String(job.id))
                    const duration = runJob ? jobRunDuration(runJob) : ''
                    return (
                      <div
                        key={job.clientKey}
                        draggable
                        onDragStart={(event) => {
                          event.dataTransfer.setData('text/plain', `job:${job.clientKey}`)
                          event.dataTransfer.effectAllowed = 'move'
                        }}
                        onDragOver={(event) => event.preventDefault()}
                        onDrop={(event) => {
                          event.preventDefault()
                          event.stopPropagation()
                          const raw = event.dataTransfer.getData('text/plain')
                          if (raw.startsWith('job:')) onMoveJobBefore(raw.slice(4), stage.clientKey, jobIndex)
                        }}
                        className={cn(
                          'rounded-lg border bg-background shadow-sm',
                          selectedJobKey === job.clientKey && 'border-primary ring-2 ring-primary/30',
                        )}
                      >
                        <div className="flex items-start gap-1 p-2">
                          <button
                            type="button"
                            className="flex min-w-0 flex-1 items-start gap-2 text-left"
                            aria-pressed={selectedJobKey === job.clientKey}
                            aria-label={`选择${job.name}`}
                            onClick={() => onSelectJob(job.clientKey)}
                          >
                            <JobKindIcon kind={job.kind} className="mt-0.5 size-4 shrink-0 text-primary" />
                            <span className="min-w-0">
                              <span className="block truncate font-console text-sm font-medium tracking-tight">{job.name}</span>
                              <span className="block font-mono text-xs text-muted-foreground">{job.kind}</span>
                              {runJob && (
                                <span className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                                  <StatusIcon status={runJob.status} showLabel={false} />
                                  <span>{[duration, runStatusLabel(runJob.status)].filter(Boolean).join(' · ')}</span>
                                </span>
                              )}
                            </span>
                          </button>
                          <div className="flex shrink-0 flex-col">
                            <IconButton label={`上移${job.name}`} disabled={jobIndex === 0} onClick={() => onMoveJob(job.clientKey, -1)}>
                              <ChevronUp />
                            </IconButton>
                            <IconButton
                              label={`下移${job.name}`}
                              disabled={jobIndex === stage.jobs.length - 1}
                              onClick={() => onMoveJob(job.clientKey, 1)}
                            >
                              <ChevronDown />
                            </IconButton>
                            <IconButton label={`删除${job.name}`} onClick={() => onRemoveJob(job.clientKey)}>
                              <Trash2 />
                            </IconButton>
                          </div>
                        </div>
                      </div>
                    )
                  })}
                  <button
                    type="button"
                    aria-label={`在阶段 ${index + 1} 添加任务`}
                    className={cn(
                      'flex h-10 items-center justify-center gap-1 rounded-md border border-dashed text-xs text-muted-foreground hover:border-primary hover:text-primary',
                      targetStageKey === stage.clientKey && 'border-solid border-primary bg-primary/10 text-primary',
                    )}
                    onClick={() => onTargetStage(stage.clientKey)}
                  >
                    <Plus className="size-3.5" />
                    添加任务
                  </button>
                </div>
              </div>
            </div>
          ))}
          {stages.length > 0 && <ChevronRight className="mt-16 size-4 shrink-0 text-muted-foreground" />}
          <button
            type="button"
            className="mt-10 flex h-28 w-36 shrink-0 flex-col items-center justify-center gap-1 rounded-lg border border-dashed text-sm text-muted-foreground hover:border-primary hover:text-primary"
            aria-label="添加阶段"
            onClick={onAddStage}
          >
            <Plus className="size-4" />
            添加阶段
          </button>
        </div>
      </div>
    </section>
  )
}

function Inspector({
  job,
  stages,
  kinds,
  gitRef,
  toolchains,
  onClose,
  onChange,
  onKind,
}: {
  job: EditorJob | null
  stages: EditorStage[]
  kinds: TaskKindVO[]
  gitRef: string
  toolchains: Array<{ stack: string; runtimeVersion: string; toolVersion?: string | null; buildCommand: string; testCommand: string; image: string }>
  onClose: () => void
  onChange: (patch: Partial<EditorJob>) => void
  onKind: (kind: string) => void
}) {
  const kindMeta = job ? kinds.find((item) => item.kindValue === job.kind) : undefined
  const params = visibleParams(kindMeta?.params)
  const choices = job ? kindChoices(stages, job, kinds) : []
  const stacks = uniqueStacks(toolchains)
  const runtimes = job?.stack ? runtimesFor(toolchains, job.stack) : []
  const tools = job?.stack && job.runtimeVersion ? toolsFor(toolchains, job.stack, job.runtimeVersion) : []

  return (
    <div data-slot="pipeline-inspector" className="flex min-h-0 flex-1 flex-col">
      <div className="flex items-center justify-between border-b px-3 py-2">
        <div className="text-sm font-medium">任务配置</div>
        {job && (
          <Button type="button" variant="ghost" size="icon" aria-label="关闭任务配置" onClick={onClose}>
            <X />
          </Button>
        )}
      </div>
      {!job && <p className="px-3 py-4 text-sm text-muted-foreground">选择画布中的任务，编辑名称、类型、命令和参数。</p>}
      {job && (
        <div className="flex min-h-0 flex-1 flex-col gap-3 overflow-auto px-3 py-3">
          <Field label="任务名称">
            <Input aria-label="任务名称" className="h-8" value={job.name} onChange={(event) => onChange({ name: event.target.value })} />
          </Field>
          <Field label="任务类型">
            <select aria-label="任务类型" className={controlClass} value={job.kind} onChange={(event) => onKind(event.target.value)}>
              {choices.map((item) => (
                <option key={item.value} value={item.value}>
                  {item.label}
                </option>
              ))}
            </select>
          </Field>
          <label className="flex flex-col gap-1">
            <span className="text-xs text-muted-foreground">命令</span>
            <textarea
              aria-label="命令"
              value={job.command ?? ''}
              rows={8}
              className="min-h-32 w-full rounded-md border border-input bg-background px-2 py-1.5 font-mono text-xs leading-5 shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring"
              onChange={(event) => onChange({ command: event.target.value })}
            />
          </label>
          {kindMeta?.hint ? <p className="text-xs text-muted-foreground">{kindMeta.hint}</p> : null}
          <section className="flex flex-col gap-2">
            <h3 className="text-sm font-medium" title="隐藏参数由平台或凭证注入，不在这里编辑">
              参数绑定
            </h3>
            {params.length === 0 && <p className="text-xs text-muted-foreground">这个任务没有需要填写的参数</p>}
            {params.map((param) => {
              const binding: JobParamBinding = job.paramBindings?.[param.paramKey] ?? {
                mode: 'fixed',
                value: param.paramKey === 'GIT_REF' ? gitRef : (param.defaultValue ?? ''),
              }
              const choicesForParam = paramChoices(param.options)
              return (
                <div key={param.paramKey} className="grid grid-cols-[minmax(0,1fr)_5.25rem_minmax(0,1.1fr)] items-center gap-1.5">
                  <span className="truncate font-mono text-xs" title={param.paramLabel}>
                    {param.paramKey}
                    {param.required ? ' *' : ''}
                  </span>
                  <select
                    aria-label={`${param.paramKey} 绑定方式`}
                    className={controlClass}
                    value={binding.mode}
                    onChange={(event) =>
                      onChange({
                        paramBindings: {
                          ...job.paramBindings,
                          [param.paramKey]: {
                            mode: event.target.value as JobParamBinding['mode'],
                            value: binding.value,
                          },
                        },
                      })
                    }
                  >
                    <option value="fixed">写死</option>
                    <option value="runtime">变量</option>
                  </select>
                  {binding.mode === 'runtime' ? (
                    <span className="text-xs text-muted-foreground">运行时填写</span>
                  ) : param.paramKey === 'GIT_REF' ? (
                    <span className="truncate text-xs text-muted-foreground" title="使用基本信息中的分支">
                      使用分支
                    </span>
                  ) : param.paramType === 'select' && choicesForParam.length > 0 ? (
                    <select
                      aria-label={param.paramLabel || param.paramKey}
                      className={controlClass}
                      value={binding.value ?? ''}
                      onChange={(event) =>
                        onChange({
                          paramBindings: {
                            ...job.paramBindings,
                            [param.paramKey]: { mode: 'fixed', value: event.target.value },
                          },
                        })
                      }
                    >
                      <option value="">请选择</option>
                      {choicesForParam.map((choice) => (
                        <option key={choice.value} value={choice.value}>
                          {choice.label}
                        </option>
                      ))}
                    </select>
                  ) : (
                    <Input
                      aria-label={param.paramLabel || param.paramKey}
                      className="h-8"
                      value={binding.value ?? ''}
                      placeholder={param.placeholder || param.defaultValue || undefined}
                      onChange={(event) =>
                        onChange({
                          paramBindings: {
                            ...job.paramBindings,
                            [param.paramKey]: { mode: 'fixed', value: event.target.value },
                          },
                        })
                      }
                    />
                  )}
                </div>
              )
            })}
          </section>
          <Collapsible>
            <CollapsibleTrigger className="flex w-full items-center justify-between py-1 text-sm font-medium [&[data-state=open]>svg]:rotate-180">
              高级设置
              <ChevronDown className="size-4 transition-transform" />
            </CollapsibleTrigger>
            <CollapsibleContent className="flex flex-col gap-2 pt-2">
              <Field label="技术栈">
                {stacks.length > 0 ? (
                  <select
                    aria-label="技术栈"
                    className={controlClass}
                    value={job.stack ?? ''}
                    onChange={(event) => {
                      const stack = event.target.value
                      const toolchain = coerceToolchain(toolchains, stack, job.runtimeVersion ?? '', job.toolVersion)
                      onChange(toolchainPatch(toolchain?.stack ?? stack, toolchain?.runtimeVersion ?? '', toolchain?.toolVersion ?? ''))
                    }}
                  >
                    <option value="">不指定</option>
                    {stacks.map((stack) => (
                      <option key={stack} value={stack}>
                        {stackLabel(stack)}
                      </option>
                    ))}
                  </select>
                ) : (
                  <Input aria-label="技术栈" className="h-8" value={job.stack ?? ''} onChange={(event) => onChange({ stack: event.target.value })} />
                )}
              </Field>
              <Field label="运行时">
                {runtimes.length > 0 ? (
                  <select
                    aria-label="运行时"
                    className={controlClass}
                    value={job.runtimeVersion ?? ''}
                    onChange={(event) => {
                      const runtime = event.target.value
                      const toolchain = coerceToolchain(toolchains, job.stack ?? '', runtime, null)
                      onChange(toolchainPatch(job.stack ?? '', runtime, toolchain?.toolVersion ?? ''))
                    }}
                  >
                    {runtimes.map((runtime) => (
                      <option key={runtime} value={runtime}>
                        {runtime}
                      </option>
                    ))}
                  </select>
                ) : (
                  <Input
                    aria-label="运行时"
                    className="h-8"
                    value={job.runtimeVersion ?? ''}
                    onChange={(event) => onChange({ runtimeVersion: event.target.value })}
                  />
                )}
              </Field>
              <Field label="工具版本">
                {tools.length > 0 ? (
                  <select
                    aria-label="工具版本"
                    className={controlClass}
                    value={job.toolVersion ?? ''}
                    onChange={(event) => onChange({ toolVersion: event.target.value })}
                  >
                    {tools.map((tool) => (
                      <option key={tool} value={tool}>
                        {tool}
                      </option>
                    ))}
                  </select>
                ) : (
                  <Input
                    aria-label="工具版本"
                    className="h-8"
                    value={job.toolVersion ?? ''}
                    onChange={(event) => onChange({ toolVersion: event.target.value })}
                  />
                )}
              </Field>
              <p className="text-xs text-muted-foreground">
                默认命令：{commandForKind(job.kind, kinds) || '无'}
                {jobKindMeta(job.kind)?.description ? ` · ${jobKindMeta(job.kind)?.description}` : ''}
              </p>
            </CollapsibleContent>
          </Collapsible>
        </div>
      )}
    </div>
  )
}

function toolchainPatch(stack: string, runtime: string, tool: string): Partial<EditorJob> {
  return {
    stack: stack || null,
    runtimeVersion: runtime || null,
    toolVersion: tool || null,
  }
}

function kindChoices(stages: EditorStage[], job: EditorJob, kinds: TaskKindVO[]) {
  const labels = new Map<string, string>()
  for (const item of JOB_KIND_OPTIONS) labels.set(item.value, item.label)
  for (const item of kinds) labels.set(item.kindValue, item.label)
  if (!labels.has(job.kind)) labels.set(job.kind, jobKindLabel(job.kind))
  return [...labels.entries()]
    .filter(([value]) => value === job.kind || !canChangeJobKind(stages, job.clientKey, value as JobKind))
    .map(([value, label]) => ({ value, label }))
}

function IconButton({
  label,
  disabled,
  onClick,
  children,
}: {
  label: string
  disabled?: boolean
  onClick: () => void
  children: ReactNode
}) {
  return (
    <Button type="button" variant="ghost" size="icon" className="size-6" aria-label={label} disabled={disabled} onClick={onClick}>
      {children}
    </Button>
  )
}
