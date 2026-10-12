import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Clock, Hand, Pencil, Webhook } from 'lucide-react'
import { Link, useMatch, useNavigate, useParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { OverviewMetricCards } from '@/components/console/ResourceOverview'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { pipelineApi, pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { PipelineDefinitionCanvas } from '@/modules/pipeline/components/PipelineDefinitionCanvas'
import { StartRunButton } from '@/modules/pipeline/components/StartRunButton'
import { repoShortName } from '@/modules/pipeline/graph/pipelineGraph'
import {
  filterPipelineRuns,
  recentSuccessRate,
  triggerLabel,
  type RunStatusFilter,
  type RunTimeRange,
} from '@/modules/pipeline/runHistory'
import { formatCompactDuration, formatCommit, formatDateTime, formatGitRef, formatRelativeTime, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRun } from '@/modules/pipeline/types/pipeline'

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
const PAGE_SIZE = 10

const STATUS_OPTIONS: Array<{ value: RunStatusFilter; label: string }> = [
  { value: 'all', label: '全部' },
  { value: 'RUNNING', label: '运行中' },
  { value: 'SUCCEEDED', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'QUEUED', label: '排队中' },
  { value: 'WAITING_APPROVAL', label: '待审批' },
  { value: 'CANCELLED', label: '已取消' },
]

const RANGE_OPTIONS: Array<{ value: RunTimeRange; label: string }> = [
  { value: '24h', label: '近 24 小时' },
  { value: '7d', label: '近 7 天' },
  { value: '30d', label: '近 30 天' },
  { value: 'all', label: '全部时间' },
]

const controlClass =
  'flex h-8 rounded-md border border-input bg-background px-2 text-sm shadow-xs outline-none focus-visible:ring-2 focus-visible:ring-ring'

/**
 * 流水线详情壳。垂直顺序固定为：返回与名称、编辑/运行、页签、仓库元信息、页签正文。
 * 页签在元信息上方。
 */
export function PipelineDetailPage() {
  const { pipelineId = '' } = useParams()
  const navigate = useNavigate()
  const onHistory = useMatch('/pipeline/pipelines/:pipelineId/history')
  const tab = onHistory ? 'history' : 'definition'

  const pipelineQuery = useQuery({
    queryKey: ['pipeline', pipelineId],
    queryFn: () => pipelineApi.get(pipelineId),
    enabled: !!pipelineId,
  })
  const credentialsQuery = useQuery({
    queryKey: ['pipeline-credentials'],
    queryFn: () => pipelineCredentialApi.list(),
  })
  const runsQuery = useQuery({
    queryKey: ['pipeline-runs', pipelineId],
    queryFn: () => pipelineApi.pageRuns(pipelineId, { pageNo: 1, pageSize: 100 }),
    enabled: !!pipelineId,
    refetchInterval: (query) => {
      const records = query.state.data?.records ?? []
      return records.some((run) => run.status && !TERMINAL.has(run.status)) ? 4000 : false
    },
  })

  const pipeline = pipelineQuery.data
  const records = runsQuery.data?.records ?? []
  const total = Number(runsQuery.data?.total ?? records.length)
  const credentialName = pipeline?.credentialId
    ? (credentialsQuery.data ?? []).find((item) => String(item.id) === String(pipeline.credentialId))?.name || '当前凭证'
    : '未配置'
  const stageNames = (pipeline?.stages ?? [])
    .slice()
    .sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
    .map((stage) => stage.name)
    .filter(Boolean)

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to="/pipeline/pipelines" data-slot="pipeline-detail-back">
            <ArrowLeft />
            返回
          </Link>
        </Button>
      }
      title={
        <span className="font-console tracking-tight">{pipeline?.name || (pipelineQuery.isLoading ? '加载中…' : '流水线')}</span>
      }
      actions={
        <>
          <Button variant="outline" size="sm" asChild>
            <Link to={`/pipeline/pipelines/${pipelineId}/edit`}>
              <Pencil />
              编辑
            </Link>
          </Button>
          <StartRunButton pipelineId={pipelineId} variant="default" />
        </>
      }
      tabs={[
        { value: 'definition', label: '详情' },
        { value: 'history', label: '运行历史' },
      ]}
      value={tab}
      onValueChange={(value) => {
        navigate(value === 'history' ? `/pipeline/pipelines/${pipelineId}/history` : `/pipeline/pipelines/${pipelineId}`)
      }}
    >
      <div data-slot="pipeline-detail-meta" className="mb-4">
        <OverviewMetricCards
          items={[
            { label: '仓库', value: <MonoValue>{repoShortName(pipeline?.repoUrl)}</MonoValue> },
            { label: '分支', value: <MonoValue>{formatGitRef(pipeline?.gitRef)}</MonoValue> },
            { label: '阶段', value: <MonoValue>{stageNames.length > 0 ? stageNames.join(' · ') : '尚未配置'}</MonoValue> },
            {
              label: '最近运行',
              value: (
                <span className="flex min-w-0 flex-col gap-1">
                  <StatusIcon status={pipeline?.lastRunStatus} label={runStatusLabel(pipeline?.lastRunStatus)} />
                  {pipeline?.lastRunTime ? (
                    <span className="truncate font-mono text-xs text-muted-foreground tabular-nums">
                      {formatRelativeTime(pipeline.lastRunTime)}
                    </span>
                  ) : null}
                </span>
              ),
            },
            { label: '凭证', value: <MonoValue>{credentialName}</MonoValue> },
          ]}
        />
      </div>
      <div data-slot="pipeline-detail-body">
        {pipelineQuery.isError ? <p className="text-sm text-destructive">流水线加载失败</p> : null}
        {tab === 'definition' ? (
          pipelineQuery.isLoading ? (
            <p className="text-sm text-muted-foreground">加载中…</p>
          ) : (
            <PipelineDefinitionCanvas stages={pipeline?.stages} />
          )
        ) : (
          <RunHistory
            pipelineId={pipelineId}
            records={records}
            total={total}
            loading={runsQuery.isFetching}
            error={runsQuery.isError}
            onOpen={(runId) => navigate(`/pipeline/pipelines/${pipelineId}/runs/${runId}`)}
          />
        )}
      </div>
    </DetailShell>
  )
}

function MonoValue({ children }: { children: ReactNode }) {
  return <span className="block truncate font-console text-sm font-medium tracking-tight">{children}</span>
}

function RunHistory({
  pipelineId,
  records,
  total,
  loading,
  error,
  onOpen,
}: {
  pipelineId: string
  records: PipelineRun[]
  total: number
  loading: boolean
  error: boolean
  onOpen: (runId: string) => void
}) {
  const [status, setStatus] = useState<RunStatusFilter>('all')
  const [range, setRange] = useState<RunTimeRange>('7d')
  const [keyword, setKeyword] = useState('')
  const [page, setPage] = useState(1)
  const filtered = filterPipelineRuns(records, { status, range, keyword })
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, pageCount)
  const visible = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)
  const rate = recentSuccessRate(records)
  const rateText = rate == null ? '—' : `${rate}%`

  function resetPage() {
    setPage(1)
  }

  return (
    <div className="flex flex-col gap-3">
      <DataTable<PipelineRun>
        columns={[
          {
            id: 'id',
            header: '运行编号',
            cell: (run) => (
              <div className="flex min-w-0 flex-col">
                <Link
                  className="font-mono text-sm font-medium tracking-tight text-primary hover:underline"
                  to={`/pipeline/pipelines/${pipelineId}/runs/${run.id}`}
                >
                  #{run.id}
                </Link>
                <span className="font-mono text-xs text-muted-foreground">
                  {run.commitSha ? formatCommit(run.commitSha) : run.triggeredByName || '—'}
                </span>
              </div>
            ),
          },
          {
            id: 'trigger',
            header: '触发方式',
            cell: (run) => <TriggerCell trigger={run.trigger} />,
          },
          {
            id: 'ref',
            header: '分支',
            className: 'font-mono text-xs',
            cell: (run) => formatGitRef(run.gitRef),
          },
          {
            id: 'status',
            header: '状态',
            cell: (run) => <StatusIcon status={run.status} label={runStatusLabel(run.status)} />,
          },
          {
            id: 'started',
            header: '开始时间',
            className: 'font-mono text-xs tabular-nums',
            cell: (run) => formatDateTime(run.startedAt),
          },
          {
            id: 'duration',
            header: '耗时',
            className: 'font-mono text-xs tabular-nums',
            cell: (run) => formatCompactDuration(run.startedAt, run.finishedAt),
          },
          {
            id: 'actions',
            header: '操作',
            cell: (run) => (
              <Button variant="outline" size="sm" asChild>
                <Link to={`/pipeline/pipelines/${pipelineId}/runs/${run.id}`}>查看详情</Link>
              </Button>
            ),
          },
        ]}
        data={visible}
        getRowId={(run) => String(run.id)}
        loading={loading}
        error={error ? '运行记录加载失败' : undefined}
        empty={records.length === 0 ? '还没有运行。点击「运行」后会打开这次运行的详情。' : '没有符合筛选的运行'}
        onRowClick={(run) => onOpen(String(run.id))}
        toolbar={
          <>
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              状态
              <select
                aria-label="状态"
                className={controlClass}
                value={status}
                onChange={(event) => {
                  setStatus(event.target.value as RunStatusFilter)
                  resetPage()
                }}
              >
                {STATUS_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <label className="flex items-center gap-2 text-sm text-muted-foreground">
              时间
              <select
                aria-label="时间"
                className={controlClass}
                value={range}
                onChange={(event) => {
                  setRange(event.target.value as RunTimeRange)
                  resetPage()
                }}
              >
                {RANGE_OPTIONS.map((option) => (
                  <option key={option.value} value={option.value}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
            <Input
              value={keyword}
              onChange={(event) => {
                setKeyword(event.target.value)
                resetPage()
              }}
              placeholder="搜索运行编号、分支"
              aria-label="搜索运行编号、分支"
              className="h-8 w-56"
            />
            <span className="ml-auto text-sm text-muted-foreground">
              共 {total} 次运行 · 近 7 天成功率 {rateText}
            </span>
          </>
        }
      />
      <div className="flex items-center justify-between text-sm text-muted-foreground">
        <span>共 {filtered.length} 条</span>
        {pageCount > 1 ? (
          <div className="flex items-center gap-1">
            {Array.from({ length: pageCount }, (_, index) => index + 1).map((item) => (
              <button
                key={item}
                type="button"
                aria-label={`第 ${item} 页`}
                aria-current={item === safePage ? 'page' : undefined}
                className={
                  item === safePage
                    ? 'flex size-8 items-center justify-center rounded-md bg-primary text-primary-foreground'
                    : 'flex size-8 items-center justify-center rounded-md hover:bg-muted'
                }
                onClick={() => setPage(item)}
              >
                {item}
              </button>
            ))}
          </div>
        ) : null}
      </div>
    </div>
  )
}

function TriggerCell({ trigger }: { trigger?: string | null }) {
  const label = triggerLabel(trigger)
  const Icon = label === '定时' ? Clock : label === 'Webhook' ? Webhook : Hand
  return (
    <span className="inline-flex items-center gap-1.5">
      <Icon className="size-3.5 text-muted-foreground" aria-hidden />
      {label}
    </span>
  )
}
