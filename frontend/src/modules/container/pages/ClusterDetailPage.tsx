import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { OverviewMetricCards } from '@/components/console/ResourceOverview'
import { PageHeader } from '@/components/console/PageHeader'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { clusterApi } from '@/modules/container/api/cluster'
import { formatBytes, formatCpu } from '@/modules/container/utils/format'
import { ClusterMonitor } from '@/modules/container/components/ResourceMonitors'

const links = [
  ['nodes', '节点'],
  ['pods', 'Pod'],
  ['deployments', 'Deployment'],
  ['statefulsets', 'StatefulSet'],
  ['services', 'Service'],
  ['configmaps', 'ConfigMap'],
  ['secrets', 'Secret'],
  ['pvcs', 'PVC'],
  ['storageclasses', 'StorageClass'],
] as const

export function ClusterDetailPage() {
  const { clusterId = '' } = useParams()
  const cluster = useQuery({
    queryKey: ['container-cluster', clusterId],
    queryFn: () => clusterApi.get(clusterId),
    enabled: !!clusterId,
  })
  const stats = useQuery({
    queryKey: ['container-cluster-stats', clusterId],
    queryFn: () => clusterApi.stats(clusterId),
    enabled: !!clusterId,
  })

  const info = cluster.data
  const numbers = stats.data

  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={info?.alias || info?.name || '集群'}
        description={
          <span className="inline-flex flex-wrap items-center gap-2">
            <span>{info?.name || '集群概览'}</span>
            {info ? <StatusIcon status={info.status} /> : null}
            {info?.version ? <span>{info.version}</span> : null}
          </span>
        }
      />
      {stats.isError && <p className="text-sm text-destructive">统计信息加载失败</p>}
      <OverviewMetricCards
        items={[
          { label: '节点', value: numbers?.nodeCount ?? '—', emphasis: true },
          { label: '命名空间', value: numbers?.namespaceCount ?? '—', emphasis: true },
          { label: 'Pod', value: numbers?.podCount ?? '—', emphasis: true },
          { label: 'Deployment', value: numbers?.deploymentCount ?? '—', emphasis: true },
          { label: 'Service', value: numbers?.serviceCount ?? '—', emphasis: true },
          { label: 'CPU', value: numbers ? formatCpu(numbers.cpuTotal) : '—', emphasis: true },
          { label: '内存', value: numbers ? formatBytes(numbers.memoryTotal) : '—', emphasis: true },
        ]}
      />
      <ClusterMonitor clusterId={clusterId} />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {links.map(([path, label]) => (
            <Link key={path} to={`/container/clusters/${clusterId}/${path}`}>
            <Card className="transition-shadow hover:shadow-md">
              <CardHeader className="p-4">
                <CardTitle className="text-base">{label}</CardTitle>
                <CardDescription>查看{label}列表</CardDescription>
              </CardHeader>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  )
}

