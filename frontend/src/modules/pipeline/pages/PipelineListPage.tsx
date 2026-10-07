import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Input } from '@/components/ui/input'
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
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder="搜索名称"
            aria-label="搜索流水线"
            className="h-8 w-56"
          />
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
                <Link to={`/pipeline/pipelines/${row.id}/runs/${row.lastRunId}`}>
                  <StatusIcon status={row.lastRunStatus} label={runStatusLabel(row.lastRunStatus)} />
                </Link>
              ) : (
                <StatusIcon status={row.lastRunStatus} label={runStatusLabel(row.lastRunStatus)} />
              ),
          },
          {
            id: 'time',
            header: '运行时间',
            cell: (row) => formatDateTime(row.lastRunTime),
          },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={query.isFetching}
        error={query.isError ? '流水线列表加载失败' : undefined}
        empty="还没有流水线"
      />
    </div>
  )
}
