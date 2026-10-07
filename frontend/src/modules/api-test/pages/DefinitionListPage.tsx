import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Input } from '@/components/ui/input'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'
import { apiStatusLabel } from '@/modules/api-test/define/statusLabel'
import type { ApiDefinitionVO } from '@/modules/api-test/define/types/definition'
import { pmProjectApi } from '@/modules/pm/api'
import { asId } from '@/modules/pm/utils/id'
import { useAuthStore } from '@/stores/auth'

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}

export function DefinitionListPage() {
  const [keyword, setKeyword] = useState('')
  const [projectId, setProjectId] = useState('')
  const tenantVersion = useAuthStore((s) => s.tenantVersion)
  const activeTenantId = useAuthStore((s) => s.activeTenantId)
  const projectsQuery = useQuery({
    queryKey: ['pm-projects', 'api-test-filter', activeTenantId, tenantVersion],
    queryFn: () => pmProjectApi.page({ pageNo: 1, pageSize: 100 }),
  })
  const query = useQuery({
    queryKey: ['api-definitions', projectId, keyword],
    queryFn: () =>
      apiDefinitionApi.page({
        ...(projectId ? { projectId } : {}),
        keyword: keyword.trim() || undefined,
        pageNo: 1,
        pageSize: 50,
      }),
  })
  const rows = query.data?.records ?? []
  const total = query.data?.total ?? rows.length
  const projects = projectsQuery.data?.records ?? []

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="接口"
        description={query.isSuccess ? `共 ${total} 个接口定义` : '查看接口定义、状态与路径'}
        actions={
          <>
            <select
              className="h-8 max-w-56 rounded-md border border-input bg-background px-2 text-sm"
              value={projectId}
              aria-label="按项目筛选"
              onChange={(event) => setProjectId(event.target.value)}
            >
              <option value="">全部项目</option>
              {projects.map((project) => (
                <option key={asId(project.id)} value={asId(project.id)}>
                  {project.name}
                </option>
              ))}
            </select>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索名称或路径"
              aria-label="搜索接口"
              className="h-8 w-56"
            />
          </>
        }
      />
      <DataTable<ApiDefinitionVO>
        columns={[
          {
            id: 'method',
            header: '方法',
            cell: (row) => <span className="font-mono text-xs">{row.method}</span>,
          },
          {
            id: 'name',
            header: '名称',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={`/api-test/definitions/${row.id}`}>
                {row.name}
              </Link>
            ),
          },
          {
            id: 'path',
            header: '路径',
            className: 'max-w-sm truncate font-mono text-xs',
            cell: (row) => row.path,
          },
          {
            id: 'status',
            header: '状态',
            cell: (row) => <StatusIcon status={row.status} label={apiStatusLabel(row.status)} />,
          },
          {
            id: 'group',
            header: '分组',
            cell: (row) => row.groupName || '—',
          },
          {
            id: 'updated',
            header: '更新时间',
            cell: (row) => formatTime(row.updateTime),
          },
        ]}
        data={rows}
        getRowId={(row) => String(row.id)}
        loading={query.isFetching}
        error={query.isError ? '接口列表加载失败' : undefined}
        empty="还没有接口定义"
      />
    </div>
  )
}
