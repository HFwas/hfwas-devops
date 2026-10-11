import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { DetailShell } from '@/components/console/DetailShell'
import { LogPanel, type ConnectionState } from '@/components/console/LogPanel'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from '@/components/ui/sheet'
import { Button } from '@/components/ui/button'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { RunSwimlane } from '@/modules/pipeline/components/RunSwimlane'
import { aggregateRunLog, groupJobsByStage, isTerminalRunStatus, logBody } from '@/modules/pipeline/runView'
import { formatCommit, formatDateTime, formatDuration, formatGitRef, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRunJob } from '@/modules/pipeline/types/pipeline'

const NO_JOBS: PipelineRunJob[] = []

type LogFocus = { type: 'job'; id: string } | { type: 'stage'; name: string } | { type: 'overall' }

function stageKey(job: { stageName?: string | null }): string {
  return job.stageName?.trim() || '未命名'
}

function focusValid(focus: LogFocus | null, jobs: PipelineRunJob[]): focus is LogFocus {
  if (!focus) return false
  if (focus.type === 'overall') return true
  if (focus.type === 'job') return jobs.some((job) => String(job.id) === focus.id)
  return jobs.some((job) => stageKey(job) === focus.name)
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

function TerminalLines({ text }: { text: string }) {
  const lines = text.split('\n')
  return (
    <pre className="p-3 whitespace-pre-wrap break-all">
      {lines.map((line, index) => (
        <span key={index} className="flex gap-3">
          <span className="w-8 shrink-0 text-right opacity-45 select-none">{index + 1}</span>
          <span className="min-w-0 flex-1">{line || ' '}</span>
        </span>
      ))}
    </pre>
  )
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
  const open = focusValid(focus, jobs)

  useEffect(() => {
    if (!running) return
    const timer = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(timer)
  }, [running])

  useEffect(() => {
    if (focus && !focusValid(focus, jobs)) setFocus(null)
  }, [focus, jobs])

  const focusedJob = focus?.type === 'job' ? jobs.find((job) => String(job.id) === focus.id) : undefined
  const focusedStage = focus?.type === 'stage' ? stages.find((stage) => stage.name === focus.name) : undefined
  const detailJobs =
    focus?.type === 'overall'
      ? jobs
      : focus?.type === 'stage'
        ? (focusedStage?.jobs ?? [])
        : focusedJob
          ? [focusedJob]
          : []
  const detailTitle =
    focus?.type === 'overall'
      ? '全程日志'
      : focus?.type === 'stage'
        ? `阶段 · ${focus.name}`
        : focusedJob
          ? `${focusedJob.stageName} / ${focusedJob.jobName}`
          : '日志'
  const detailText =
    focus?.type === 'overall'
      ? overallLog
      : focus?.type === 'stage'
        ? aggregateRunLog(detailJobs)
        : focusedJob
          ? logBody(focusedJob.logText, '')
          : ''
  const detailFollow =
    focus?.type === 'overall' ? !!run && !isTerminalRunStatus(run.status) : detailJobs.some((job) => job.status === 'RUNNING')
  const detailKey = focus ? `${focus.type}:${focus.type === 'job' ? focus.id : focus.type === 'stage' ? focus.name : 'all'}` : 'none'
  const live = !!run && !isTerminalRunStatus(run.status)

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
          <RunSwimlane
            stages={stages}
            runStatus={run.status}
            selectedJobId={focus?.type === 'job' ? focus.id : null}
            selectedStageName={focus?.type === 'stage' ? focus.name : null}
            overallOpen={focus?.type === 'overall'}
            now={now}
            onSelectJob={(id) => setFocus({ type: 'job', id })}
            onSelectStage={(name) => setFocus({ type: 'stage', name })}
            onSelectOverall={() => setFocus({ type: 'overall' })}
          />
        </div>
      )}
      <Sheet open={open} onOpenChange={(next) => { if (!next) setFocus(null) }}>
        <SheetContent
          side="right"
          className="w-[min(720px,92vw)] gap-0 bg-(--term-bg) p-0 text-(--term-fg) sm:max-w-none"
        >
          <SheetHeader className="sr-only">
            <SheetTitle>{detailTitle}</SheetTitle>
            <SheetDescription>运行日志</SheetDescription>
          </SheetHeader>
          <LogPanel
            title={detailTitle}
            connection={
              focus?.type === 'overall'
                ? runConnection(run?.status, runQuery.isFetching, !!run)
                : jobsConnection(detailJobs, runQuery.isFetching)
            }
            follow={detailFollow}
            followKey={detailKey}
            variant="terminal"
            resizable={false}
            className="h-full rounded-none border-0"
            bodyClassName="bg-(--term-bg) text-(--term-fg)"
          >
            <TerminalLines text={detailText || (runQuery.isLoading ? '加载中…' : '这条任务还没有日志')} />
          </LogPanel>
        </SheetContent>
      </Sheet>
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
