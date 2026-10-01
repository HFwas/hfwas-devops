import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Card, CardDescription, CardHeader, CardTitle } from '@/components/ui/card'
import { clusterApi } from '@/modules/container/api/cluster'
import { formatBytes, formatCpu } from '@/modules/container/utils/format'
import { StatusBadge } from '@/modules/container/components/StatusBadge'
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
      <div>
        <h1 className="text-xl font-semibold">{info?.alias || info?.name || '集群'}</h1>
        <div className="mt-2 flex items-center gap-2 text-sm text-muted-foreground">
          <span>{info?.name}</span>
          {info && <StatusBadge status={info.status} />}
          <span>{info?.version || ''}</span>
        </div>
      </div>
      {stats.isError && <p className="text-sm text-destructive">统计信息加载失败</p>}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <Stat label="节点" value={numbers?.nodeCount} />
        <Stat label="命名空间" value={numbers?.namespaceCount} />
        <Stat label="Pod" value={numbers?.podCount} />
        <Stat label="Deployment" value={numbers?.deploymentCount} />
        <Stat label="Service" value={numbers?.serviceCount} />
        <Stat label="CPU" value={numbers ? formatCpu(numbers.cpuTotal) : '—'} />
        <Stat label="内存" value={numbers ? formatBytes(numbers.memoryTotal) : '—'} />
      </div>
      <ClusterMonitor clusterId={clusterId} />
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
        {links.map(([path, label]) => (
          <Link key={path} to={`/container/clusters/${clusterId}/${path}`}>
            <Card className="transition-shadow hover:shadow-md">
              <CardHeader>
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

function Stat({ label, value }: { label: string; value?: number | string }) {
  return (
    <Card>
      <CardHeader>
        <CardDescription>{label}</CardDescription>
        <CardTitle>{value ?? '—'}</CardTitle>
      </CardHeader>
    </Card>
  )
}
