import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { pmProjectApi } from '@/modules/pm/api'
import type { PmProject } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'
import { useAuthStore } from '@/stores/auth'

export function ProjectMonitorPage() {
  const tenantVersion = useAuthStore((s) => s.tenantVersion)
  const activeTenantId = useAuthStore((s) => s.activeTenantId)
  const query = useQuery({
    queryKey: ['pm-projects', 'monitor', activeTenantId, tenantVersion],
    queryFn: () => pmProjectApi.page({ pageNo: 1, pageSize: 100 }),
  })
  const rows = query.data?.records ?? []

  return (
    <div className="flex w-full flex-col gap-4 md:gap-6">
      <PageHeader
        title="项目监控"
        description={query.isSuccess ? `共 ${query.data?.total ?? rows.length} 个项目` : '从项目进入看板。趋势图留到后续。'}
      />
      <DataTable<PmProject>
        columns={[
          {
            id: 'name',
            header: '项目',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={`/pm/projects/${asId(row.id)}/board/task`}>
                {row.name}
              </Link>
            ),
          },
          { id: 'code', header: '编码', className: 'font-mono text-xs', cell: (row) => row.code },
          { id: 'desc', header: '说明', className: 'max-w-sm truncate', cell: (row) => row.description || '—' },
        ]}
        data={rows}
        getRowId={(row) => asId(row.id) || row.code}
        loading={query.isFetching}
        error={query.isError ? '项目列表加载失败' : undefined}
        empty="还没有项目"
      />
    </div>
  )
}
