import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { DataTable } from '@/components/console/DataTable'
import { StatusIcon } from '@/components/console/StatusIcon'
import type { PodSummary } from '@/modules/container/types/resource'

export function WorkloadPodsPanel({
  clusterId,
  queryKey,
  load,
}: {
  clusterId: string
  queryKey: unknown[]
  load: () => Promise<PodSummary[]>
}) {
  const location = useLocation()
  const query = useQuery({ queryKey, queryFn: load })
  const pods = query.data ?? []
  return (
    <DataTable
      columns={[
        {
          id: 'name',
          header: 'Pod',
          cell: (pod) => (
            <Link
              className="font-medium text-primary hover:underline"
              to={`/container/clusters/${clusterId}/pods/${pod.namespace}/${encodeURIComponent(pod.name)}`}
              state={{ back: `${location.pathname}${location.search}` }}
            >
              {pod.name}
            </Link>
          ),
        },
        { id: 'containers', header: '容器', cell: (pod) => pod.containerNames?.join(', ') || '—' },
        { id: 'status', header: '状态', cell: (pod) => <StatusIcon variant="dot" status={pod.status} /> },
        { id: 'node', header: '节点', cell: (pod) => pod.nodeName || '—' },
        { id: 'ip', header: 'IP', cell: (pod) => pod.podIP || '—' },
        { id: 'ready', header: '就绪', cell: (pod) => `${pod.readyContainers}/${pod.containerCount}` },
        { id: 'restart', header: '重启', cell: (pod) => String(pod.restarts) },
      ]}
      data={pods}
      getRowId={(pod) => `${pod.namespace}/${pod.name}`}
      loading={query.isLoading}
      error={query.isError ? 'Pod 加载失败' : undefined}
      empty="还没有 Pod"
    />
  )
}
