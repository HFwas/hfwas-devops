import { useEffect, useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft, Clock, Hand, Pencil, Webhook } from 'lucide-react'
import { Link, useMatch, useNavigate, useParams, useSearchParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { OverviewMetricCards } from '@/components/console/ResourceOverview'
import { Pagination } from '@/components/console/Pagination'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { pipelineApi, pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { PipelineDefinitionCanvas } from '@/modules/pipeline/components/PipelineDefinitionCanvas'
import { StartRunButton } from '@/modules/pipeline/components/StartRunButton'
import { repoShortName } from '@/modules/pipeline/graph/pipelineGraph'
import {
  filterPipelineRuns,
  rangeLabel,
  recentSuccessRate,
  triggerLabel,
  type RunStatusFilter,
  type RunTimeRange,
} from '@/modules/pipeline/runHistory'
import { formatCompactDuration, formatCommit, formatDateTime, formatGitRef, formatRelativeTime, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRun } from '@/modules/pipeline/types/pipeline'
import { DEFAULT_PAGE_SIZE } from '@/shared/types/pagination'

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])
/** 运行历史一次拉取的上限；超过则只能看到最近这些。 */
const HISTORY_FETCH_LIMIT = 200
const PAGE_SIZE = DEFAULT_PAGE_SIZE

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

const RUN_STATUS_VALUES = new Set(STATUS_OPTIONS.map((option) => option.value))
const RUN_RANGE_VALUES = new Set(RANGE_OPTIONS.map((option) => option.value))

function readStatus(value: string | null): RunStatusFilter {
  return value && RUN_STATUS_VALUES.has(value as RunStatusFilter) ? (value as RunStatusFilter) : 'all'
}

function readRange(value: string | null): RunTimeRange {
  return value && RUN_RANGE_VALUES.has(value as RunTimeRange) ? (value as RunTimeRange) : '7d'
}

function pageParam(value: string | null): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
}

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
    queryFn: () => pipelineApi.pageRuns(pipelineId, { pageNo: 1, pageSize: HISTORY_FETCH_LIMIT }),
    enabled: !!pipelineId,
    refetchInterval: (query) => {
      const records = query.state.data?.records ?? []
      return records.some((run) => run.status && !TERMINAL.has(run.status)) ? 4000 : false
    },
  })

  const pipeline = pipelineQuery.data
  const records = runsQuery.data?.records ?? []
  const latestRun = records[0]
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
            { label: 'Commit', value: <MonoValue>{latestRun?.commitSha ? formatCommit(latestRun.commitSha) : '—'}</MonoValue> },
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
  loading,
  error,
  onOpen,
}: {
  pipelineId: string
  records: PipelineRun[]
  loading: boolean
  error: boolean
  onOpen: (runId: string) => void
}) {
  const [searchParams, setSearchParams] = useSearchParams()
  const status = readStatus(searchParams.get('status'))
  const range = readRange(searchParams.get('range'))
  const keyword = searchParams.get('q') ?? ''
  const page = pageParam(searchParams.get('page'))
  const [draftKeyword, setDraftKeyword] = useState(keyword)

  function patchParams(patch: Record<string, string | null>, replace = false) {
    const next = new URLSearchParams(searchParams)
    for (const [key, value] of Object.entries(patch)) {
      if (value == null || value === '') next.delete(key)
      else next.set(key, value)
    }
    setSearchParams(next, { replace })
  }

  useEffect(() => {
    const timer = window.setTimeout(() => {
      const next = draftKeyword.trim()
      if (next === keyword) return
      patchParams({ q: next || null, page: null }, true)
    }, 300)
    return () => window.clearTimeout(timer)
  }, [draftKeyword, keyword, searchParams, setSearchParams])

  const filtered = filterPipelineRuns(records, { status, range, keyword })
  const pageCount = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE))
  const safePage = Math.min(page, pageCount)
  const visible = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE)
  const rate = recentSuccessRate(records, range)
  const rateText = rate == null ? '—' : `${rate}%`

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
            <Select
              value={status}
              onValueChange={(value) => patchParams({ status: value === 'all' ? null : value, page: null })}
            >
              <SelectTrigger aria-label="状态" size="sm" className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {STATUS_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Select
              value={range}
              onValueChange={(value) => patchParams({ range: value === '7d' ? null : value, page: null })}
            >
              <SelectTrigger aria-label="时间" size="sm" className="w-32">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                {RANGE_OPTIONS.map((option) => (
                  <SelectItem key={option.value} value={option.value}>
                    {option.label}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
            <Input
              value={draftKeyword}
              onChange={(event) => setDraftKeyword(event.target.value)}
              placeholder="搜索运行编号、分支"
              aria-label="搜索运行编号、分支"
              className="h-8 w-56"
            />
            <span className="ml-auto text-sm text-muted-foreground tabular-nums">
              最近 {records.length} 次中 {filtered.length} 条 · {rangeLabel(range)}成功率 {rateText}
            </span>
          </>
        }
      />
      <Pagination
        page={safePage}
        pageCount={pageCount}
        onPageChange={(next) => patchParams({ page: next > 1 ? String(next) : null })}
      />
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