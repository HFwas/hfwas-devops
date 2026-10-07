import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { LogPanel, type ConnectionState } from '@/components/console/LogPanel'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { formatCommit, formatDateTime, formatDuration, formatGitRef, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRunJob } from '@/modules/pipeline/types/pipeline'

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
const NO_JOBS: PipelineRunJob[] = []

function logConnection(job: PipelineRunJob | undefined, fetching: boolean): ConnectionState {
  if (fetching && !job) return 'connecting'
  if (job?.status === 'RUNNING') return 'connected'
  if (job?.status === 'QUEUED') return 'connecting'
  return 'idle'
}

export function PipelineRunDetailPage() {
  const { pipelineId = '', runId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '任务'
  const [jobId, setJobId] = useState<string | null>(null)

  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '任务') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }

  const runQuery = useQuery({
    queryKey: ['pipeline-run', pipelineId, runId],
    queryFn: () => pipelineApi.getRun(pipelineId, runId),
    enabled: !!pipelineId && !!runId,
    refetchInterval: (query) => {
      const status = query.state.data?.status
      if (!status || TERMINAL.has(status)) return false
      return 3000
    },
  })
  const run = runQuery.data
  const jobs = run?.jobs ?? NO_JOBS

  useEffect(() => {
    if (!jobs.length) return
    if (jobId && jobs.some((job) => String(job.id) === jobId)) return
    const active = jobs.find((job) => job.status === 'RUNNING' || job.status === 'FAILED') ?? jobs[0]
    setJobId(String(active.id))
  }, [jobId, jobs])

  const selected = jobs.find((job) => String(job.id) === jobId)

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

  const openLog = (id: string) => {
    setJobId(id)
    setTab('日志')
  }

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
        run && !TERMINAL.has(run.status) ? (
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
      tabs={[
        { value: '任务', label: '任务' },
        { value: '日志', label: '日志' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '任务' && runQuery.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '任务' && runQuery.isError && <p className="text-sm text-destructive">运行详情加载失败</p>}
      {tab === '任务' && run && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="触发" value={run.trigger} />
            <Field label="触发人" value={run.triggeredByName} />
            <Field label="开始" value={formatDateTime(run.startedAt)} />
            <Field label="耗时" value={formatDuration(run.startedAt, run.finishedAt)} />
          </div>
          <DataTable<PipelineRunJob>
            columns={[
              { id: 'stage', header: '阶段', cell: (job) => job.stageName },
              { id: 'name', header: '任务', cell: (job) => <span className="font-medium">{job.jobName}</span> },
              { id: 'kind', header: '类型', cell: (job) => job.kind },
              {
                id: 'status',
                header: '状态',
                cell: (job) => <StatusIcon status={job.status} label={runStatusLabel(job.status)} />,
              },
              { id: 'duration', header: '耗时', cell: (job) => formatDuration(job.startedAt, job.finishedAt) },
              {
                id: 'log',
                header: <span className="sr-only">日志</span>,
                className: 'text-right',
                cell: (job) => (
                  <Button variant="ghost" size="sm" onClick={() => openLog(String(job.id))}>
                    日志
                  </Button>
                ),
              },
            ]}
            data={jobs}
            getRowId={(job) => String(job.id)}
            empty="这次运行没有任务"
          />
        </div>
      )}
      {tab === '日志' && (
        <div className="flex flex-col gap-3">
          {jobs.length > 0 && (
            <label className="flex items-center gap-2 text-sm">
              <span className="text-muted-foreground">任务</span>
              <select
                className="h-8 rounded-md border border-input bg-background px-2 text-sm"
                value={jobId ?? ''}
                aria-label="选择任务日志"
                onChange={(event) => setJobId(event.target.value)}
              >
                {jobs.map((job) => (
                  <option key={String(job.id)} value={String(job.id)}>
                    {job.stageName} / {job.jobName}
                  </option>
                ))}
              </select>
            </label>
          )}
          <LogPanel
            title={selected ? `${selected.stageName} / ${selected.jobName}` : '日志'}
            connection={logConnection(selected, runQuery.isFetching)}
            toolbar={
              <Button variant="ghost" size="sm" onClick={() => void navigate(`/pipeline/pipelines/${pipelineId}`)}>
                全部运行
              </Button>
            }
          >
            <pre className="p-3 whitespace-pre-wrap break-all">
              {selected?.logText?.trim() || (runQuery.isLoading ? '加载中…' : '这条任务还没有日志')}
            </pre>
          </LogPanel>
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
