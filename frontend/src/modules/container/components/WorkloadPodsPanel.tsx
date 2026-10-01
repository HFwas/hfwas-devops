import { useQuery } from '@tanstack/react-query'
import { Link, useLocation } from 'react-router'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { StatusBadge } from '@/modules/container/components/StatusBadge'
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

  if (query.isLoading) return <p className="text-sm text-muted-foreground">加载 Pod…</p>
  if (query.isError) return <p className="text-sm text-destructive">Pod 加载失败</p>

  const pods = query.data ?? []
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Pod</TableHead>
          <TableHead>容器</TableHead>
          <TableHead>状态</TableHead>
          <TableHead>节点</TableHead>
          <TableHead>IP</TableHead>
          <TableHead>就绪</TableHead>
          <TableHead>重启</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {pods.length === 0 && (
          <TableRow>
            <TableCell colSpan={7} className="py-8 text-center text-muted-foreground">
              还没有 Pod
            </TableCell>
          </TableRow>
        )}
        {pods.map((pod) => (
          <TableRow key={pod.name}>
            <TableCell className="font-medium">
              <Link
                className="text-primary hover:underline"
                to={`/container/clusters/${clusterId}/pods/${pod.namespace}/${encodeURIComponent(pod.name)}`}
                state={{ back: `${location.pathname}${location.search}` }}
              >
                {pod.name}
              </Link>
            </TableCell>
            <TableCell>{pod.containerNames?.join(', ') || '—'}</TableCell>
            <TableCell>
              <StatusBadge status={pod.status} />
            </TableCell>
            <TableCell>{pod.nodeName || '—'}</TableCell>
            <TableCell>{pod.podIP || '—'}</TableCell>
            <TableCell>
              {pod.readyContainers}/{pod.containerCount}
            </TableCell>
            <TableCell>{pod.restarts}</TableCell>
          </TableRow>
        ))}
      </TableBody>
    </Table>
  )
}
