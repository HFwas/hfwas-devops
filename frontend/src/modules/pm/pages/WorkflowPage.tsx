import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pmProjectApi, pmProjectIssueTypeApi, pmStatusApi } from '@/modules/pm/api'
import { workflowStatusTone } from '@/modules/pm/statusTone'
import { typeLabel, type StatusDefinition } from '@/modules/pm/types'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'

interface TransitionRow {
  id: string
  from: string
  fromCode: string
  name: string
  to: string
  toCode: string
}

export function WorkflowPage() {
  const { projectId: projectParam = '', typeCode = 'task' } = useParams()
  const projectId = resolveRouteProjectId(projectParam)
  const projectQuery = useQuery({
    queryKey: ['pm-project', projectId],
    queryFn: () => pmProjectApi.getById(projectId!),
    enabled: !!projectId,
  })
  const typesQuery = useQuery({
    queryKey: ['pm-issue-types', projectId],
    queryFn: () => pmProjectIssueTypeApi.list(projectId!),
    enabled: !!projectId,
  })
  const workflowQuery = useQuery({
    queryKey: ['pm-workflow', projectId, typeCode],
    queryFn: () => pmStatusApi.get(projectId!, typeCode),
    enabled: !!projectId && !!typeCode,
  })

  if (!projectId) return <p className="text-sm text-destructive">项目地址无效</p>

  const types = (typesQuery.data ?? []).filter((item) => item.enabled !== 0)
  const statuses = [...(workflowQuery.data?.statuses ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  const names = new Map(statuses.map((item) => [item.statusCode, item.statusName || item.statusCode]))
  const transitions: TransitionRow[] = statuses.flatMap((status) =>
    (status.transitions ?? []).map((transition) => ({
      id: `${status.statusCode}:${transition.id}`,
      from: status.statusName || status.statusCode,
      fromCode: status.statusCode,
      name: transition.name,
      to: names.get(transition.toStatus) || transition.toStatus,
      toCode: transition.toStatus,
    })),
  )

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title={projectQuery.data?.name ? `${projectQuery.data.name} · 工作流` : '工作流'}
        description={`${typeLabel(typeCode, types)} 的状态与流转。画布编辑留到后续，这里只列表。`}
        actions={
          <>
            <Button variant="outline" size="sm" asChild>
              <Link to={`/pm/projects/${projectId}/items/${typeCode}`}>事项</Link>
            </Button>
            <Button variant="ghost" size="sm" asChild>
              <Link to={`/pm/projects/${projectId}/board/${typeCode}`}>看板</Link>
            </Button>
          </>
        }
      />
      {types.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {types.map((type) => (
            <Button key={type.code} variant={type.code === typeCode ? 'secondary' : 'ghost'} size="sm" asChild>
              <Link to={`/pm/projects/${projectId}/settings/workflow/${type.code}`}>{type.name || type.code}</Link>
            </Button>
          ))}
        </div>
      )}
      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-medium">状态</h2>
        <DataTable<StatusDefinition>
          columns={[
            {
              id: 'name',
              header: '名称',
              cell: (row) => (
                <StatusIcon
                  status={row.statusCode}
                  tone={workflowStatusTone(row)}
                  label={row.statusName || row.statusCode}
                />
              ),
            },
            { id: 'code', header: '编码', className: 'font-mono text-xs', cell: (row) => row.statusCode },
            { id: 'initial', header: '起始', cell: (row) => (row.isInitial ? '是' : '—') },
            { id: 'final', header: '终态', cell: (row) => (row.isFinal ? '是' : '—') },
            { id: 'order', header: '顺序', cell: (row) => String(row.sortOrder ?? '—') },
          ]}
          data={statuses}
          getRowId={(row) => row.statusCode}
          loading={workflowQuery.isFetching}
          error={workflowQuery.isError ? '工作流加载失败' : undefined}
          empty="还没有状态"
        />
      </section>
      <section className="flex flex-col gap-2">
        <h2 className="text-sm font-medium">流转</h2>
        <DataTable<TransitionRow>
          columns={[
            { id: 'from', header: '从', cell: (row) => row.from },
            { id: 'name', header: '动作', cell: (row) => row.name },
            { id: 'to', header: '到', cell: (row) => row.to },
          ]}
          data={transitions}
          getRowId={(row) => row.id}
          loading={workflowQuery.isFetching && statuses.length === 0}
          empty="还没有流转规则"
        />
      </section>
    </div>
  )
}
