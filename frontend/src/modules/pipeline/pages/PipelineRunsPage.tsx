import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { formatDateTime, formatDuration, formatGitRef, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRun } from '@/modules/pipeline/types/pipeline'

const TERMINAL = new Set(['SUCCEEDED', 'FAILED', 'CANCELLED'])

export function PipelineRunsPage() {
  const { pipelineId = '' } = useParams()
  const pipelineQuery = useQuery({
    queryKey: ['pipeline', pipelineId],
    queryFn: () => pipelineApi.get(pipelineId),
    enabled: !!pipelineId,
  })
  const runsQuery = useQuery({
    queryKey: ['pipeline-runs', pipelineId],
    queryFn: () => pipelineApi.pageRuns(pipelineId, { pageNo: 1, pageSize: 50 }),
    enabled: !!pipelineId,
    refetchInterval: (query) => {
      const records = query.state.data?.records ?? []
      return records.some((run) => run.status && !TERMINAL.has(run.status)) ? 4000 : false
    },
  })
  const rows = runsQuery.data?.records ?? []
  const total = runsQuery.data?.total ?? rows.length
  const pipeline = pipelineQuery.data

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={pipeline?.name || '运行记录'}
        description={
          runsQuery.isSuccess
            ? `${pipeline?.repoUrl || '流水线'} · 共 ${total} 次运行`
            : pipeline?.repoUrl || '查看这条流水线的运行'
        }
        actions={
          <Button variant="outline" size="sm" asChild>
            <Link to="/pipeline/pipelines">返回列表</Link>
          </Button>
        }
      />
      <DataTable<PipelineRun>
        columns={[
          {
            id: 'id',
            header: '运行',
            cell: (run) => (
              <Link
                className="font-medium text-primary hover:underline"
                to={`/pipeline/pipelines/${pipelineId}/runs/${run.id}`}
              >
                #{run.id}
              </Link>
            ),
          },
          {
            id: 'status',
            header: '状态',
            cell: (run) => <StatusIcon status={run.status} label={runStatusLabel(run.status)} />,
          },
          {
            id: 'trigger',
            header: '触发',
            cell: (run) => run.trigger || '—',
          },
          {
            id: 'ref',
            header: '分支',
            cell: (run) => formatGitRef(run.gitRef),
          },
          {
            id: 'who',
            header: '触发人',
            cell: (run) => run.triggeredByName || '—',
          },
          {
            id: 'started',
            header: '开始时间',
            cell: (run) => formatDateTime(run.startedAt),
          },
          {
            id: 'duration',
            header: '耗时',
            cell: (run) => formatDuration(run.startedAt, run.finishedAt),
          },
        ]}
        data={rows}
        getRowId={(run) => String(run.id)}
        loading={runsQuery.isFetching}
        error={runsQuery.isError ? '运行记录加载失败' : undefined}
        empty="还没有运行"
      />
    </div>
  )
}
