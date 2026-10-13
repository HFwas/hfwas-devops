import { useEffect, useState } from 'react'
import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { FolderGit2, GitBranch, Pencil, Plus, Trash2 } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { ConfirmDialog } from '@/components/console/ConfirmDialog'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Pagination } from '@/components/console/Pagination'
import { StatusIcon } from '@/components/console/StatusIcon'
import { resolveStatusTone, STATUS_DOT_CLASS } from '@/components/console/status'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { StartRunButton } from '@/modules/pipeline/components/StartRunButton'
import { repoShortName } from '@/modules/pipeline/graph/pipelineGraph'
import { formatGitRef, formatRelativeTime, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineSummary } from '@/modules/pipeline/types/pipeline'
import { DEFAULT_PAGE_SIZE, pageCountOf } from '@/shared/types/pagination'
import { cn } from '@/lib/utils'

const PAGE_SIZE = DEFAULT_PAGE_SIZE

type ListStatusFilter = 'all' | 'RUNNING' | 'SUCCEEDED' | 'FAILED'

const STATUS_FILTERS: Array<{ id: ListStatusFilter; label: string }> = [
  { id: 'all', label: '全部' },
  { id: 'RUNNING', label: '运行中' },
  { id: 'SUCCEEDED', label: '成功' },
  { id: 'FAILED', label: '失败' },
]

function pageParam(value: string | null): number {
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 1 ? Math.floor(parsed) : 1
}

export function PipelineListPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const q = searchParams.get('q')?.trim() ?? ''
  const page = pageParam(searchParams.get('page'))
  const [keyword, setKeyword] = useState(q)
  const [statusFilter, setStatusFilter] = useState<ListStatusFilter>('all')

  const query = useQuery({
    queryKey: ['pipelines', page, q],
    queryFn: () => pipelineApi.page({ pageNo: page, pageSize: PAGE_SIZE, keyword: q || undefined }),
    placeholderData: keepPreviousData,
  })
  const remove = useMutation({
    mutationFn: (id: PipelineSummary['id']) => pipelineApi.delete(id),
    onSuccess: async () => {
      toast.success('已删除')
      await queryClient.invalidateQueries({ queryKey: ['pipelines'] })
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })

  // 输入防抖后写进 URL；关键词交给服务端过滤，避免逐键 refetch。
  useEffect(() => {
    const timer = window.setTimeout(() => {
      const next = keyword.trim()
      if (next === q) return
      const params = new URLSearchParams(searchParams)
      if (next) params.set('q', next)
      else params.delete('q')
      params.delete('page')
      setSearchParams(params, { replace: true })
    }, 300)
    return () => window.clearTimeout(timer)
  }, [keyword, q, searchParams, setSearchParams])

  const pageRows = query.data?.records ?? []
  // 后端 /page 只支持 keyword，不支持按状态筛选；状态快捷筛选仅作用于当前页。
  const rows = statusFilter === 'all' ? pageRows : pageRows.filter((row) => row.lastRunStatus === statusFilter)
  const total = Number(query.data?.total ?? pageRows.length)
  const pageCount = pageCountOf(query.data, PAGE_SIZE)
  const safePage = Math.min(page, pageCount)
  const narrowed = Boolean(q) || statusFilter !== 'all'

  function goToPage(next: number) {
    const target = Math.min(Math.max(1, next), pageCount)
    const params = new URLSearchParams(searchParams)
    if (target > 1) params.set('page', String(target))
    else params.delete('page')
    setSearchParams(params)
  }

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="流水线"
        description={
          query.isSuccess ? (
            <span className="tabular-nums">共 {total} 条流水线</span>
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
      <div className="flex w-fit flex-wrap items-center gap-2">
        <div
          role="group"
          aria-label="按最近运行筛选（仅当前页）"
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
        <span className="text-xs text-muted-foreground">状态筛选仅作用于当前页</span>
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
                <ConfirmDialog
                  title={`删除流水线「${row.name}」？`}
                  description="删除后无法恢复，运行历史会一并移除。"
                  confirmText="删除"
                  destructive
                  confirmDisabled={remove.isPending}
                  onConfirm={() => remove.mutate(row.id)}
                >
                  <Button variant="ghost" size="sm" aria-label={`删除${row.name}`}>
                    <Trash2 />
                    删除
                  </Button>
                </ConfirmDialog>
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
      <Pagination
        page={safePage}
        pageCount={pageCount}
        onPageChange={goToPage}
        summary={
          <span className="tabular-nums">
            第 {safePage} / {pageCount} 页
            {statusFilter !== 'all' ? ` · 当前页 ${rows.length} / ${pageRows.length} 条符合状态` : null}
          </span>
        }
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