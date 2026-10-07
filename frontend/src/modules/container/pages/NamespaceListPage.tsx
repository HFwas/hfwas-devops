import { useQuery } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusBadge } from '@/modules/container/components/StatusBadge'
import { namespaceApi } from '@/modules/container/api/namespace'

export function NamespaceListPage() {
  const { clusterId = '' } = useParams()
  const query = useQuery({
    queryKey: ['container-namespaces', clusterId],
    queryFn: () => namespaceApi.list(clusterId),
    enabled: !!clusterId,
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="命名空间" description="当前集群中的 Namespace" />
      <DataTable
        columns={[
          { id: 'name', header: '名称', cell: (row) => row.name },
          {
            id: 'status',
            header: '状态',
            cell: (row) => <StatusBadge status={row.status || row.phase} />,
          },
          { id: 'created', header: '创建时间', cell: (row) => row.creationTimestamp || '—' },
        ]}
        data={query.data ?? []}
        getRowId={(row) => row.uid || row.name}
        loading={query.isLoading}
        error={query.isError ? '命名空间加载失败' : undefined}
        empty="还没有命名空间"
      />
    </div>
  )
}
