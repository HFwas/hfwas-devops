import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { DetailShell } from '@/components/console/DetailShell'
import { LogPanel, type ConnectionState } from '@/components/console/LogPanel'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { RunSwimlane } from '@/modules/pipeline/components/RunSwimlane'
import { aggregateRunLog, groupJobsByStage, isTerminalRunStatus, logBody } from '@/modules/pipeline/runView'
import { formatCommit, formatDateTime, formatDuration, formatGitRef, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRunJob } from '@/modules/pipeline/types/pipeline'

const NO_JOBS: PipelineRunJob[] = []

type LogFocus = { type: 'job'; id: string } | { type: 'stage'; name: string }

function stageKey(job: { stageName?: string | null }): string {
  return job.stageName?.trim() || '未命名'
}

function resolveFocus(focus: LogFocus | null, jobs: PipelineRunJob[]): LogFocus | null {
  if (!jobs.length) return null
  if (focus?.type === 'job' && jobs.some((job) => String(job.id) === focus.id)) return focus
  if (focus?.type === 'stage' && jobs.some((job) => stageKey(job) === focus.name)) return focus
  const active = jobs.find((job) => job.status === 'RUNNING' || job.status === 'FAILED') ?? jobs[0]
  return { type: 'job', id: String(active.id) }
}

function runConnection(status: string | undefined, fetching: boolean, hasRun: boolean): ConnectionState {
  if (fetching && !hasRun) return 'connecting'
  if (!status || isTerminalRunStatus(status)) return 'idle'
  if (status === 'RUNNING') return 'connected'
  return 'connecting'
}

function jobsConnection(jobs: PipelineRunJob[], fetching: boolean): ConnectionState {
  if (fetching && jobs.length === 0) return 'connecting'
  if (jobs.some((job) => job.status === 'RUNNING')) return 'connected'
  if (jobs.some((job) => job.status === 'QUEUED')) return 'connecting'
  return 'idle'
}

export function PipelineRunDetailPage() {
  const { pipelineId = '', runId = '' } = useParams()
  const queryClient = useQueryClient()
  const [focus, setFocus] = useState<LogFocus | null>(null)
  const [now, setNow] = useState(() => Date.now())

  const runQuery = useQuery({
    queryKey: ['pipeline-run', pipelineId, runId],
    queryFn: () => pipelineApi.getRun(pipelineId, runId),
    enabled: !!pipelineId && !!runId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (!status || isTerminalRunStatus(status)) return false
      return 3000
    },
  })
  const run = runQuery.data
  const jobs = run?.jobs ?? NO_JOBS
  const stages = useMemo(() => groupJobsByStage(jobs), [jobs])
  const overallLog = useMemo(() => aggregateRunLog(jobs), [jobs])
  const running = jobs.some((job) => job.status === 'RUNNING')

  useEffect(() => {
    if (!running) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [running])

  const resolved = resolveFocus(focus, jobs)
  const focusedJob = resolved?.type === 'job' ? jobs.find((job) => String(job.id) === resolved.id) : undefined
  const focusedStage =
    resolved?.type === 'stage' ? stages.find((stage) => stage.name === resolved.name) : undefined
  const detailJobs = resolved?.type === 'stage' ? (focusedStage?.jobs ?? []) : focusedJob ? [focusedJob] : []
  const detailTitle =
    resolved?.type === 'stage'
      ? `阶段 · ${resolved.name}`
      : focusedJob
        ? `${focusedJob.stageName} / ${focusedJob.jobName}`
        : '日志'
  const detailText =
    resolved?.type === 'stage'
      ? aggregateRunLog(detailJobs)
      : focusedJob
        ? logBody(focusedJob.logText, '')
        : ''
  const detailFollow = detailJobs.some((job) => job.status === 'RUNNING')
  const detailKey = resolved ? `${resolved.type}:${resolved.type === 'job' ? resolved.id : resolved.name}` : 'none'

  const invalidate = async () => {
    await queryClient.invalidateQueries({ queryKey: ['pipeline-run', pipelineId, runId] })
    await queryClient.invalidateQueries({ queryKey: ['pipeline-runs', pipelineId] })
    await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
  }
  const cancel = useMutation({
    mutationFn: () => pipelineApi.cancel(pipelineId, runId),
    onSuccess: async () => {
      toast.success('已取消运行')
      await invalidate()
    },
    onError: (error: Error) => toast.error(error.message || '取消失败'),
  })
  const approve = useMutation({
    mutationFn: () => pipelineApi.approve(pipelineId, runId),
    onSuccess: async () => {
      toast.success('已通过审批')
      await invalidate()
    },
    onError: (error: Error) => toast.error(error.message || '审批失败'),
  })

  const live = !!run && !isTerminalRunStatus(run.status)

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/pipeline/pipelines/${pipelineId}`}>返回</Link>
        </Button>
      }
      title={run ? `${run.pipelineName} #${run.id}` : `运行 #${runId}`}
      description={run?.errorMessage || (run ? `${formatGitRef(run.gitRef)} · ${formatCommit(run.commitSha)}` : undefined)}
      meta={
        run ? (
          <StatusIcon status={run.status} label={runStatusLabel(run.status)} />
        ) : runQuery.isLoading ? (
          '加载中…'
        ) : runQuery.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        live ? (
          <>
            {run.status === 'WAITING_APPROVAL' && (
              <Button size="sm" disabled={approve.isPending} onClick={() => approve.mutate()}>
                通过
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              disabled={cancel.isPending}
              onClick={() => {
                if (window.confirm(`取消运行 #${run.id}？`)) cancel.mutate()
              }}
            >
              取消
            </Button>
          </>
        ) : null
      }
    >
      {runQuery.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {runQuery.isError && <p className="text-sm text-destructive">运行详情加载失败</p>}
      {run && (
        <div className="flex flex-col gap-3">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="触发" value={run.trigger} />
            <Field label="触发人" value={run.triggeredByName} />
            <Field label="开始" value={formatDateTime(run.startedAt)} />
            <Field label="耗时" value={formatDuration(run.startedAt, run.finishedAt, now)} />
          </div>
          <div className="grid items-stretch gap-3 lg:grid-cols-[minmax(0,1fr)_minmax(300px,420px)]">
            <div className="flex min-w-0 flex-col gap-3">
              <RunSwimlane
                stages={stages}
                selectedJobId={resolved?.type === 'job' ? resolved.id : null}
                selectedStageName={resolved?.type === 'stage' ? resolved.name : null}
                now={now}
                onSelectJob={(id) => setFocus({ type: 'job', id })}
                onSelectStage={(name) => setFocus({ type: 'stage', name })}
              />
              <LogPanel
                title={detailTitle}
                connection={jobsConnection(detailJobs, runQuery.isFetching)}
                follow={detailFollow}
                followKey={detailKey}
                className="shrink-0"
              >
                <pre className="p-3 whitespace-pre-wrap break-all">
                  {detailText || (runQuery.isLoading ? '加载中…' : '这条任务还没有日志')}
                </pre>
              </LogPanel>
            </div>
            <LogPanel
              title="总体日志"
              connection={runConnection(run.status, runQuery.isFetching, true)}
              follow={live}
              followKey={String(run.id)}
              className="h-[70vh]"
            >
              <pre className="p-3 whitespace-pre-wrap break-all">
                {overallLog || '这次运行还没有日志'}
              </pre>
            </LogPanel>
          </div>
        </div>
      )}
    </DetailShell>
  )
}

function Field({ label, value }: { label: string; value?: string | null }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value || '—'}</span>
    </div>
  )
}
