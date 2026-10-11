import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Plus } from 'lucide-react'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { StartRunButton } from '@/modules/pipeline/components/StartRunButton'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { formatDateTime, formatGitRef, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineSummary } from '@/modules/pipeline/types/pipeline'

export function PipelineListPage() {
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['pipelines', keyword],
    queryFn: () => pipelineApi.page({ pageNo: 1, pageSize: 50, keyword: keyword.trim() || undefined }),
  })
  const rows = query.data?.records ?? []
  const total = query.data?.total ?? rows.length

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="流水线"
        description={query.isSuccess ? `共 ${total} 条流水线` : '查看流水线与最近一次运行'}
        actions={
          <>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索名称"
              aria-label="搜索流水线"
              className="h-8 w-56"
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
      <DataTable<PipelineSummary>
        columns={[
          {
            id: 'name',
            header: '名称',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={`/pipeline/pipelines/${row.id}`}>
                {row.name}
              </Link>
            ),
          },
          {
            id: 'repo',
            header: '仓库',
            className: 'max-w-sm truncate',
            cell: (row) => row.repoUrl,
          },
          {
            id: 'ref',
            header: '分支',
            cell: (row) => formatGitRef(row.gitRef),
          },
          {
            id: 'status',
            header: '最近运行',
            cell: (row) =>
              row.lastRunId ? (
                <span className="inline-flex items-center gap-2">
                  <Link to={`/pipeline/pipelines/${row.id}/runs/${row.lastRunId}`}>
                    <StatusIcon status={row.lastRunStatus} label={runStatusLabel(row.lastRunStatus)} />
                  </Link>
                  <Link className="text-primary hover:underline" to={`/pipeline/pipelines/${row.id}/runs/${row.lastRunId}`}>
                    查看详情
                  </Link>
                </span>
              ) : (
                <StatusIcon status={row.lastRunStatus} label={runStatusLabel(row.lastRunStatus)} />
              ),
          },
          {
            id: 'time',
            header: '运行时间',
            cell: (row) => formatDateTime(row.lastRunTime),
          },
          {
            id: 'actions',
            header: '',
            className: 'text-right',
            cell: (row) => (
              <div className="flex justify-end gap-1">
                <Button variant="ghost" size="sm" asChild>
                  <Link to={`/pipeline/pipelines/${row.id}/edit`}>编辑</Link>
                </Button>
                <StartRunButton pipelineId={String(row.id)} variant="outline" />
              </div>
            ),
          },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={query.isFetching}
        error={query.isError ? '流水线列表加载失败' : undefined}
        empty="还没有流水线。使用右上角「新建流水线」添加阶段和任务。"
      />
    </div>
  )
}
