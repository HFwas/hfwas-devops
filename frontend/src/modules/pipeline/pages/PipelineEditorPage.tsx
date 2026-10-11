import { useEffect, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { PageHeader } from '@/components/console/PageHeader'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { pipelineApi, pipelineCredentialApi, pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import {
  applyJobKind,
  blankPipeline,
  draftFromPipeline,
  emptyJob,
  emptyStage,
  hiddenParamCount,
  jobFromKind,
  moveItem,
  toPipelineSavePayload,
  userVisibleParams,
  validatePipelineDraft,
  type DraftJob,
  type DraftStage,
  type PipelineDraft,
} from '@/modules/pipeline/pipelineDraft'
import type { JobParamBinding, TaskKindVO } from '@/modules/pipeline/types/pipeline'

const controlClass =
  'flex h-8 w-full rounded-md border border-input bg-background px-2 text-sm outline-none focus-visible:ring-2 focus-visible:ring-ring'
const textAreaClass =
  'min-h-20 w-full rounded-md border border-input bg-background px-3 py-2 font-mono text-xs outline-none focus-visible:ring-2 focus-visible:ring-ring'

export function PipelineEditorPage() {
  const { pipelineId } = useParams()
  const isNew = !pipelineId
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [draft, setDraft] = useState<PipelineDraft | null>(null)
  const kindsQuery = useQuery({
    queryKey: ['pipeline-task-kinds'],
    queryFn: () => pipelineTaskKindApi.list(),
  })
  const credentialsQuery = useQuery({
    queryKey: ['pipeline-credentials'],
    queryFn: () => pipelineCredentialApi.list(),
  })
  const pipelineQuery = useQuery({
    queryKey: ['pipeline', pipelineId],
    queryFn: () => pipelineApi.get(pipelineId!),
    enabled: !!pipelineId,
  })

  useEffect(() => {
    if (draft || !kindsQuery.isSuccess) return
    if (isNew) {
      setDraft(blankPipeline(kindsQuery.data))
      return
    }
    if (pipelineQuery.data) setDraft(draftFromPipeline(pipelineQuery.data))
  }, [draft, isNew, kindsQuery.data, kindsQuery.isSuccess, pipelineQuery.data])

  const save = useMutation({
    mutationFn: async (next: PipelineDraft) => {
      const kinds = kindsQuery.data ?? []
      const error = validatePipelineDraft(next, kinds)
      if (error) throw new Error(error)
      const payload = toPipelineSavePayload(next, kinds)
      if (isNew) return pipelineApi.create(payload)
      return pipelineApi.update(pipelineId!, payload)
    },
    onSuccess: async (id) => {
      const savedId = String(id)
      toast.success(isNew ? '流水线已创建' : '流水线已保存')
      await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
      await queryClient.invalidateQueries({ queryKey: ['pipeline', savedId] })
      void navigate(`/pipeline/pipelines/${savedId}`)
    },
    onError: (error: Error) => toast.error(error.message || '保存失败'),
  })

  const kinds = kindsQuery.data ?? []
  const back = isNew ? '/pipeline/pipelines' : `/pipeline/pipelines/${pipelineId}`

  if (kindsQuery.isError || pipelineQuery.isError) {
    return <p className="text-sm text-destructive">流水线加载失败</p>
  }
  if (!draft) {
    return <p className="text-sm text-muted-foreground">加载中…</p>
  }

  function update(patch: Partial<PipelineDraft>) {
    setDraft((current) => (current ? { ...current, ...patch } : current))
  }

  function updateStage(index: number, patch: Partial<DraftStage>) {
    setDraft((current) => {
      if (!current) return current
      return {
        ...current,
        stages: current.stages.map((stage, stageIndex) => (stageIndex === index ? { ...stage, ...patch } : stage)),
      }
    })
  }

  function updateJob(stageIndex: number, jobIndex: number, patch: Partial<DraftJob> | ((job: DraftJob) => DraftJob)) {
    setDraft((current) => {
      if (!current) return current
      return {
        ...current,
        stages: current.stages.map((stage, index) => {
          if (index !== stageIndex) return stage
          return {
            ...stage,
            jobs: stage.jobs.map((job, inner) => {
              if (inner !== jobIndex) return job
              return typeof patch === 'function' ? patch(job) : { ...job, ...patch }
            }),
          }
        }),
      }
    })
  }

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={isNew ? '新建流水线' : '编辑流水线'}
        description="填写仓库和阶段任务。保存后可从运行记录启动，并打开运行详情。"
        actions={
          <>
            <Button variant="outline" size="sm" asChild>
              <Link to={back}>取消</Link>
            </Button>
            <Button size="sm" disabled={save.isPending} onClick={() => save.mutate(draft)}>
              保存
            </Button>
          </>
        }
      />
      <section className="grid gap-3 rounded-lg border p-4 sm:grid-cols-2">
        <Field id="pipeline-name" label="名称">
          <Input id="pipeline-name" className="h-8" value={draft.name} onChange={(event) => update({ name: event.target.value })} />
        </Field>
        <Field id="pipeline-ref" label="分支">
          <Input id="pipeline-ref" className="h-8" value={draft.gitRef} onChange={(event) => update({ gitRef: event.target.value })} />
        </Field>
        <Field id="pipeline-repo" label="仓库">
          <Input
            id="pipeline-repo"
            className="h-8 font-mono text-xs"
            placeholder="https://github.com/org/repo.git"
            value={draft.repoUrl}
            onChange={(event) => update({ repoUrl: event.target.value })}
          />
        </Field>
        <Field id="pipeline-credential" label="凭证">
          <select
            id="pipeline-credential"
            className={controlClass}
            value={draft.credentialId}
            onChange={(event) => update({ credentialId: event.target.value })}
          >
            <option value="">不使用凭证</option>
            {(credentialsQuery.data ?? []).map((credential) => (
              <option key={String(credential.id)} value={String(credential.id)}>
                {credential.name}
              </option>
            ))}
          </select>
        </Field>
      </section>
      <div className="flex items-center justify-between gap-2">
        <h2 className="text-sm font-medium">阶段与任务</h2>
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={() => update({ stages: [...draft.stages, emptyStage()] })}
        >
          添加阶段
        </Button>
      </div>
      {draft.stages.map((stage, stageIndex) => (
        <section key={stage.clientKey} className="flex flex-col gap-3 rounded-lg border p-4">
          <div className="flex flex-wrap items-end justify-between gap-2">
            <Field id={`stage-name-${stageIndex}`} label={`阶段 ${stageIndex + 1}`}>
              <Input
                id={`stage-name-${stageIndex}`}
                className="h-8"
                value={stage.name}
                onChange={(event) => updateStage(stageIndex, { name: event.target.value })}
              />
            </Field>
            <div className="flex gap-1">
              <Button type="button" variant="ghost" size="sm" disabled={stageIndex === 0} onClick={() => update({ stages: moveItem(draft.stages, stageIndex, -1) })}>
                上移
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                disabled={stageIndex === draft.stages.length - 1}
                onClick={() => update({ stages: moveItem(draft.stages, stageIndex, 1) })}
              >
                下移
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="text-destructive"
                onClick={() => update({ stages: draft.stages.filter((_, index) => index !== stageIndex) })}
              >
                删除阶段
              </Button>
            </div>
          </div>
          {stage.jobs.map((job, jobIndex) => {
            const kind = kinds.find((item) => item.kindValue === job.kind)
            const options = kindOptions(kinds, job.kind)
            const params = userVisibleParams(kind)
            const hidden = hiddenParamCount(kind)
            return (
              <div key={job.clientKey} className="flex flex-col gap-3 rounded-md bg-muted/30 p-3">
                <div className="grid gap-3 sm:grid-cols-2">
                  <Field id={`job-name-${stageIndex}-${jobIndex}`} label="任务名称">
                    <Input
                      id={`job-name-${stageIndex}-${jobIndex}`}
                      className="h-8"
                      value={job.name}
                      onChange={(event) => updateJob(stageIndex, jobIndex, { name: event.target.value })}
                    />
                  </Field>
                  <Field id={`job-kind-${stageIndex}-${jobIndex}`} label="任务类型">
                    <select
                      id={`job-kind-${stageIndex}-${jobIndex}`}
                      className={controlClass}
                      value={job.kind}
                      onChange={(event) => {
                        const next = kinds.find((item) => item.kindValue === event.target.value)
                        if (!next) return
                        updateJob(stageIndex, jobIndex, (current) => applyJobKind(current, next, kind))
                      }}
                    >
                      <option value="">选择类型</option>
                      {options.map((item) => (
                        <option key={item.kindValue} value={item.kindValue}>
                          {item.label} ({item.kindValue}){item.enabled ? '' : ' · 已停用'}
                        </option>
                      ))}
                    </select>
                  </Field>
                </div>
                <Field id={`job-command-${stageIndex}-${jobIndex}`} label={kind?.requiresCommand === false ? '命令（可选）' : '命令'}>
                  <textarea
                    id={`job-command-${stageIndex}-${jobIndex}`}
                    className={textAreaClass}
                    value={job.command}
                    onChange={(event) => updateJob(stageIndex, jobIndex, { command: event.target.value })}
                  />
                </Field>
                {params.length > 0 ? (
                  <div className="flex flex-col gap-2">
                    <p className="text-xs text-muted-foreground">对用户开放的参数。设为运行时填写后，点「运行」会先弹出参数框。</p>
                    {params.map((param) => {
                      const binding = job.paramBindings[param.paramKey] ?? { mode: 'fixed' as const, value: param.defaultValue ?? '' }
                      return (
                        <div key={param.paramKey} className="grid gap-2 sm:grid-cols-[10rem_8rem_1fr] sm:items-center">
                          <span className="text-sm">
                            {param.paramLabel}
                            <span className="ml-1 font-mono text-xs text-muted-foreground">{param.paramKey}</span>
                          </span>
                          <select
                            aria-label={`${param.paramLabel}绑定方式`}
                            className={controlClass}
                            value={binding.mode}
                            onChange={(event) => {
                              const mode = event.target.value === 'runtime' ? 'runtime' : 'fixed'
                              const next: JobParamBinding = mode === 'runtime'
                                ? { mode }
                                : { mode, value: binding.value ?? param.defaultValue ?? '' }
                              updateJob(stageIndex, jobIndex, {
                                paramBindings: { ...job.paramBindings, [param.paramKey]: next },
                              })
                            }}
                          >
                            <option value="fixed">写死</option>
                            <option value="runtime">运行时填写</option>
                          </select>
                          {binding.mode === 'runtime' ? (
                            <span className="text-xs text-muted-foreground">启动运行时填写</span>
                          ) : param.paramKey === 'GIT_REF' ? (
                            <span className="text-xs text-muted-foreground">使用上方分支</span>
                          ) : (
                            <Input
                              aria-label={`${param.paramLabel}的值`}
                              className="h-8"
                              value={binding.value ?? ''}
                              onChange={(event) =>
                                updateJob(stageIndex, jobIndex, {
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
                  </div>
                ) : null}
                {hidden > 0 ? (
                  <p className="text-xs text-muted-foreground">{hidden} 个仅管理员参数不在这里绑定，运行时使用任务市场默认值。</p>
                ) : null}
                <div className="flex flex-wrap gap-1">
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={jobIndex === 0}
                    onClick={() => updateStage(stageIndex, { jobs: moveItem(stage.jobs, jobIndex, -1) })}
                  >
                    上移任务
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    disabled={jobIndex === stage.jobs.length - 1}
                    onClick={() => updateStage(stageIndex, { jobs: moveItem(stage.jobs, jobIndex, 1) })}
                  >
                    下移任务
                  </Button>
                  <Button
                    type="button"
                    variant="ghost"
                    size="sm"
                    className="text-destructive"
                    onClick={() => updateStage(stageIndex, { jobs: stage.jobs.filter((_, index) => index !== jobIndex) })}
                  >
                    删除任务
                  </Button>
                </div>
              </div>
            )
          })}
          <Button
            type="button"
            variant="outline"
            size="sm"
            className="w-fit"
            onClick={() => {
              const fallback = kinds.find((item) => item.enabled && item.kindValue !== 'CLONE') ?? kinds.find((item) => item.enabled)
              updateStage(stageIndex, { jobs: [...stage.jobs, fallback ? jobFromKind(fallback) : emptyJob()] })
            }}
          >
            添加任务
          </Button>
        </section>
      ))}
    </div>
  )
}

function kindOptions(kinds: TaskKindVO[], current: string): TaskKindVO[] {
  return kinds
    .filter((kind) => kind.enabled || kind.kindValue === current)
    .slice()
    .sort((a, b) => a.sortOrder - b.sortOrder || a.label.localeCompare(b.label, 'zh'))
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div className="flex min-w-0 flex-col gap-1.5">
      <Label htmlFor={id}>{label}</Label>
      {children}
    </div>
  )
}
