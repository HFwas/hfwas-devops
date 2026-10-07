import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { pmMetaApi, pmProjectApi, pmProjectIssueTypeApi, pmStatusApi } from '@/modules/pm/api'
import { workflowStatusTone } from '@/modules/pm/statusTone'
import { typeLabel, type PmWorkItem, type StatusDefinition } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'
import { resolveRouteProjectId } from '@/modules/pm/utils/projectRoute'

export function BoardPage() {
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
  const statusQuery = useQuery({
    queryKey: ['pm-status-options', projectId, typeCode],
    queryFn: () => pmStatusApi.options(projectId!, typeCode),
    enabled: !!projectId && !!typeCode,
  })
  const boardQuery = useQuery({
    queryKey: ['pm-board', projectId, typeCode],
    queryFn: () => pmMetaApi.board(projectId!, typeCode),
    enabled: !!projectId && !!typeCode,
  })

  if (!projectId) return <p className="text-sm text-destructive">项目地址无效</p>

  const types = (typesQuery.data ?? []).filter((item) => item.enabled !== 0)
  const statuses = [...(statusQuery.data ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
  const board = boardQuery.data ?? {}
  const known = new Set(statuses.map((item) => item.statusCode))
  const extra = Object.keys(board).filter((code) => !known.has(code))
  const columns = [
    ...statuses.map((status) => ({ status, items: board[status.statusCode] ?? [] })),
    ...extra.map((code) => ({
      status: { statusCode: code, statusName: code } satisfies StatusDefinition,
      items: board[code] ?? [],
    })),
  ]
  const total = columns.reduce((sum, column) => sum + column.items.length, 0)

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title={projectQuery.data?.name ? `${projectQuery.data.name} · 看板` : '看板'}
        description={boardQuery.isSuccess ? `${typeLabel(typeCode, types)} · ${total} 条事项` : '按状态分列查看事项'}
        actions={
          <Button variant="outline" size="sm" asChild>
            <Link to={`/pm/projects/${projectId}/items/${typeCode}`}>列表</Link>
          </Button>
        }
      />
      {types.length > 0 && (
        <div className="flex flex-wrap gap-1">
          {types.map((type) => (
            <Button key={type.code} variant={type.code === typeCode ? 'secondary' : 'ghost'} size="sm" asChild>
              <Link to={`/pm/projects/${projectId}/board/${type.code}`}>{type.name || type.code}</Link>
            </Button>
          ))}
        </div>
      )}
      {boardQuery.isError && <p className="text-sm text-destructive">看板加载失败</p>}
      {boardQuery.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      <div className="flex gap-3 overflow-x-auto pb-2">
        {columns.map((column) => (
          <section key={column.status.statusCode} className="flex w-72 shrink-0 flex-col rounded-lg border bg-background">
            <header className="flex h-10 items-center justify-between gap-2 border-b bg-muted px-2">
              <StatusIcon
                status={column.status.statusCode}
                tone={workflowStatusTone(column.status)}
                label={column.status.statusName || column.status.statusCode}
              />
              <span className="text-xs text-muted-foreground">{column.items.length}</span>
            </header>
            <div className="flex min-h-40 flex-col gap-2 p-2">
              {column.items.length === 0 && <p className="px-1 py-6 text-center text-xs text-muted-foreground">空列</p>}
              {column.items.map((item) => (
                <BoardCard key={asId(item.id) || item.title} projectId={projectId} typeCode={typeCode} item={item} />
              ))}
            </div>
          </section>
        ))}
        {!boardQuery.isLoading && columns.length === 0 && (
          <p className="text-sm text-muted-foreground">还没有状态列</p>
        )}
      </div>
    </div>
  )
}

function BoardCard({
  projectId,
  typeCode,
  item,
}: {
  projectId: string
  typeCode: string
  item: PmWorkItem
}) {
  return (
    <Link
      to={`/pm/projects/${projectId}/items/${typeCode}/${asId(item.id)}`}
      className="block rounded-md border bg-card p-3 hover:bg-muted/50"
    >
      <div className="font-mono text-xs text-muted-foreground">{item.itemKey || '—'}</div>
      <div className="mt-1 text-sm font-medium">{item.title}</div>
    </Link>
  )
}
