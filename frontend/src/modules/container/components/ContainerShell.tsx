import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Outlet, useMatch } from 'react-router'
import { useShellActions } from '@/components/console/shell-actions'
import { clusterApi } from '@/modules/container/api/cluster'
import { namespaceApi } from '@/modules/container/api/namespace'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { StatusBadge } from '@/modules/container/components/StatusBadge'

/** 容器路由只负责集群上下文，导航与顶栏由全站 AppShell 承担。 */
export function ContainerShell() {
  const setActions = useShellActions()
  const clusterMatch = useMatch('/container/clusters/:clusterId/*')
  const detailMatch = useMatch('/container/clusters/:clusterId')
  const routeClusterId = clusterMatch?.params.clusterId ?? detailMatch?.params.clusterId
  const currentId = useContainerCluster((s) => s.currentId)
  const namespace = useContainerCluster((s) => s.namespace)
  const setCurrentId = useContainerCluster((s) => s.setCurrentId)
  const setNamespace = useContainerCluster((s) => s.setNamespace)
  const pickDefault = useContainerCluster((s) => s.pickDefault)

  const clusters = useQuery({
    queryKey: ['container-clusters'],
    queryFn: () => clusterApi.page({ pageNo: 1, pageSize: 100 }),
  })

  useEffect(() => {
    const records = clusters.data?.records ?? []
    if (routeClusterId) setCurrentId(routeClusterId)
    else pickDefault(records)
  }, [clusters.data, pickDefault, routeClusterId, setCurrentId])

  const current = (clusters.data?.records ?? []).find((item) => item.id === currentId) ?? null
  const namespaces = useQuery({
    queryKey: ['container-namespaces', current?.id],
    queryFn: () => namespaceApi.list(current!.id),
    enabled: !!current?.id,
  })

  useEffect(() => {
    if (!setActions) return
    if (!current) {
      setActions(null)
      return () => setActions(null)
    }
    setActions(
      <div className="flex items-center gap-2">
        <StatusBadge status={current.status} />
        <select
          aria-label="命名空间"
          className="h-8 max-w-48 rounded-md border border-input bg-background px-2 text-sm"
          value={namespace}
          onChange={(event) => setNamespace(event.target.value)}
        >
          <option value="">全部命名空间</option>
          {(namespaces.data ?? []).map((item) => (
            <option key={item.name} value={item.name}>
              {item.name}
            </option>
          ))}
        </select>
      </div>,
    )
    return () => setActions(null)
  }, [current, namespace, namespaces.data, setActions, setNamespace])

  return <Outlet />
}
