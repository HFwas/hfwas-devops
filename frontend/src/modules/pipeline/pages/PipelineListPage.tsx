import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FolderGit2, GitBranch, Pencil, Plus, Trash2 } from 'lucide-react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { resolveStatusTone, STATUS_DOT_CLASS } from '@/components/console/status'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { StartRunButton } from '@/modules/pipeline/components/StartRunButton'
import { repoShortName } from '@/modules/pipeline/graph/pipelineGraph'
import { formatGitRef, formatRelativeTime, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineSummary } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

type ListStatusFilter = 'all' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

const STATUS_FILTERS: Array<{ id: ListStatusFilter; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'RUNNING', label: '运行中' },
  { id: 'SUCCEEDED', label: '成功' },
  { id: 'FAILED', label: '失败' },
]

export function PipelineListPage() {
  const queryClient = useQueryClient()
  const [keyword, setKeyword] = useState('')
  const [statusFilter, setStatusFilter] = useState<ListStatusFilter>('all')
  const query = useQuery({
    queryKey: ['pipelines'],
    queryFn: () => pipelineApi.page({ pageNo: 1, pageSize: 100 }),
  })
  const remove = useMutation({
    mutationFn: (id: PipelineSummary['id']) => pipelineApi.delete(id),
    onSuccess: async () => {
      toast.success('已删除')
      await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  const needle = keyword.trim().toLowerCase()
  const rows = (query.data?.records ?? []).filter((row) => {
    if (statusFilter !== 'all' && row.lastRunStatus !== statusFilter) return false
    if (!needle) return true
    const branch = formatGitRef(row.gitRef).toLowerCase()
    return [row.name, row.repoUrl, repoShortName(row.repoUrl), branch].join(' ').toLowerCase().includes(needle)
  })
  const total = query.data?.total ?? rows.length
  const narrowed = Boolean(needle) || statusFilter !== 'all'

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="流水线"
        description={
          query.isSuccess ? (
            <span className="tabular-nums">共 {narrowed ? rows.length : total} 条流水线</span>
          ) : (
            '查看流水线与最近一次运行'
          )
        }
        actions={
          <>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索流水线、仓库或分支"
              aria-label="搜索流水线、仓库或分支"
              className="h-8 w-64"
            />
            <Button size="sm" asChild>
              <Link to="/pipeline/pipelines/new">
                <Plus />
                新建流水线
              </Link>
            </Button>
          </>
        }
      />
      <div
        role="group"
        aria-label="按最近运行筛选"
        className="flex w-fit flex-wrap gap-1 rounded-lg border bg-card p-1 shadow-sm"
      >
        {STATUS_FILTERS.map((filter) => {
          const selected = statusFilter === filter.id
          return (
            <button
              key={filter.id}
              type="button"
              aria-pressed={selected}
              className={cn(
                'h-7 rounded-md px-2.5 text-xs',
                selected ? 'bg-muted font-medium text-foreground' : 'text-muted-foreground hover:bg-muted/60',
              )}
              onClick={() => setStatusFilter(filter.id)}
            >
              {filter.label}
            </button>
          )
        })}
      </div>
      <DataTable<PipelineSummary>
        fixed
        columns={[
          {
            id: 'name',
            header: '流水线名称',
            className: 'align-top whitespace-normal',
            headClassName: 'w-[28%]',
            cell: (row) => (
              <div className="flex min-w-0 flex-col gap-0.5">
                <Link
                  className="truncate font-console text-sm font-medium tracking-tight text-primary hover:underline"
                  to={`/pipeline/pipelines/${row.id}`}
                >
                  {row.name}
                </Link>
                <span className="line-clamp-2 text-xs text-muted-foreground">{pipelineBlurb(row)}</span>
              </div>
            ),
          },
          {
            id: 'repo',
            header: '仓库 / 分支',
            className: 'align-top whitespace-normal',
            headClassName: 'w-[24%]',
            cell: (row) => (
              <div className="flex flex-col gap-0.5 font-mono text-xs">
                <span className="inline-flex min-w-0 items-center gap-1.5">
                  <FolderGit2 className="size-3.5 shrink-0 text-muted-foreground" />
                  <span className="truncate">{repoShortName(row.repoUrl)}</span>
                </span>
                <span className="inline-flex min-w-0 items-center gap-1.5 text-muted-foreground">
                  <GitBranch className="size-3.5 shrink-0" />
                  <span className="truncate">{formatGitRef(row.gitRef)}</span>
                </span>
              </div>
            ),
          },
          {
            id: 'stages',
            header: '阶段预览',
            className: 'align-middle',
            headClassName: 'w-[16%]',
            cell: (row) => <StagePreview row={row} />,
          },
          {
            id: 'status',
            header: '最近运行',
            className: 'align-top whitespace-normal',
            cell: (row) =>
              row.lastRunId ? (
                <Link className="flex flex-col gap-0.5" to={`/pipeline/pipelines/${row.id}/runs/${row.lastRunId}`}>
                  <StatusIcon status={row.lastRunStatus} label={runStatusLabel(row.lastRunStatus)} />
                  <span className="font-mono text-xs text-muted-foreground tabular-nums">{formatRelativeTime(row.lastRunTime)}</span>
                </Link>
              ) : (
                <div className="flex flex-col gap-0.5">
                  <StatusIcon status={row.lastRunStatus} label={runStatusLabel(row.lastRunStatus)} />
                  <span className="font-mono text-xs text-muted-foreground tabular-nums">{formatRelativeTime(row.lastRunTime)}</span>
                </div>
              ),
          },
          {
            id: 'actions',
            header: '操作',
            className: 'align-middle whitespace-normal',
            headClassName: 'w-[220px]',
            cell: (row) => (
              <div className="flex flex-wrap items-center gap-0.5">
                <StartRunButton pipelineId={row.id} />
                <Button variant="ghost" size="sm" asChild>
                  <Link to={`/pipeline/pipelines/${row.id}/edit`}>
                    <Pencil />
                    编辑
                  </Link>
                </Button>
                <Button variant="ghost" size="sm" asChild>
                  <Link to={`/pipeline/pipelines/${row.id}/history`}>历史</Link>
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  aria-label={`删除${row.name}`}
                  onClick={() => {
                    if (!window.confirm(`删除流水线「${row.name}」？`)) return
                    remove.mutate(row.id)
                  }}
                >
                  <Trash2 />
                  删除
                </Button>
              </div>
            ),
          },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={query.isFetching}
        error={query.isError ? '流水线列表加载失败' : undefined}
        empty={narrowed ? '没有匹配的流水线' : '还没有流水线'}
      />
    </div>
  )
}

function pipelineBlurb(row: PipelineSummary): string {
  const jobs = (row.stages ?? []).flatMap((stage) => stage.jobs.map((job) => job.name).filter(Boolean))
  if (jobs.length > 0) return jobs.join('、')
  return '尚未配置阶段'
}

function StagePreview({ row }: { row: PipelineSummary }) {
  const stages = row.stages ?? []
  const tone = resolveStatusTone(row.lastRunStatus)
  if (stages.length === 0 && !row.lastRunStatus) {
    return <span className="text-xs text-muted-foreground">—</span>
  }
  const dots = stages.length > 0 ? stages : [{ id: 'run', name: '' }]
  return (
    <span className="inline-flex flex-wrap items-center gap-1" title={runStatusLabel(row.lastRunStatus)}>
      {dots.slice(0, 8).map((stage, index) => (
        <span
          key={String(stage.id ?? index)}
          className="inline-flex max-w-28 items-center gap-1 rounded-md border bg-card px-1.5 py-0.5 shadow-sm"
        >
          <span
            data-slot="stage-dot"
            className={cn('size-1.5 shrink-0 rounded-full', STATUS_DOT_CLASS[tone], tone === 'progress' && 'animate-pulse')}
          />
          {stage.name ? <span className="truncate font-console text-xs leading-4">{stage.name}</span> : null}
        </span>
      ))}
    </span>
  )
}
