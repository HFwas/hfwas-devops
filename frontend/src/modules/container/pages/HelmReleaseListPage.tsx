import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { helmApi } from '@/modules/container/api/helm'
import { HelmMockNotice } from '@/modules/container/components/HelmMockNotice'
import { helmReleasePath } from '@/modules/container/helm/paths'
import { useHelmCluster } from '@/modules/container/helm/useHelmCluster'
import { formatHelmTime } from '@/modules/container/helm/yaml'
import { errorMessage } from '@/shared/errors/apiError'

export function HelmReleaseListPage() {
  const { clusterId, namespace, synthetic } = useHelmCluster()
  const query = useQuery({
    queryKey: ['helm-releases', clusterId, namespace],
    queryFn: () => helmApi.listReleases(clusterId!, namespace || undefined),
    enabled: !!clusterId,
  })

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title="Helm Release"
        description={namespace ? `当前命名空间 ${namespace}` : '当前集群中的 Helm Release。'}
      />
      <HelmMockNotice syntheticCluster={synthetic} />
      {!clusterId ? <p className="text-sm text-muted-foreground">请先接入并选择集群。</p> : null}
      <DataTable
        loading={query.isLoading}
        error={query.isError ? errorMessage(query.error, 'Release 列表加载失败') : undefined}
        empty="这个集群还没有 Helm Release。"
        data={query.data ?? []}
        getRowId={(row) => `${row.namespace}/${row.name}`}
        columns={[
          {
            id: 'name',
            header: '名称',
            cell: (row) => (
              <Link className="font-medium text-primary hover:underline" to={helmReleasePath(row.namespace, row.name)}>
                {row.name}
              </Link>
            ),
          },
          { id: 'namespace', header: '命名空间', cell: (row) => row.namespace },
          { id: 'chart', header: 'Chart', cell: (row) => row.chartName },
          { id: 'version', header: '版本', cell: (row) => <span className="font-mono text-xs">{row.chartVersion}</span> },
          { id: 'revision', header: 'Revision', cell: (row) => row.revision },
          { id: 'status', header: '状态', cell: (row) => <StatusIcon status={row.status} /> },
          { id: 'updated', header: '更新时间', cell: (row) => formatHelmTime(row.updatedAt) },
        ]}
      />
    </div>
  )
}
