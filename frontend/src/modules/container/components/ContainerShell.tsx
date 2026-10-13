import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Outlet, useMatch } from 'react-router'
import { AsyncSelect } from '@/components/console/AsyncSelect'
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

  useEffect(() => {
    if (!setActions) return
    if (!current) {
      setActions(null)
      return () => setActions(null)
    }
    const clusterId = current.id
    setActions(
      <div className="flex items-center gap-2">
        <StatusBadge status={current.status} />
        <AsyncSelect
          label="命名空间"
          className="w-48"
          value={namespace}
          onChange={setNamespace}
          clearLabel="全部命名空间"
          searchPlaceholder="搜索命名空间"
          emptyText="没有命名空间"
          fetchPage={async ({ page, pageSize, keyword }) => {
            const result = await namespaceApi.page(clusterId, {
              pageNo: page,
              pageSize,
              keyword: keyword || undefined,
            })
            return {
              records: (result.records ?? []).map((item) => ({ value: item.name, label: item.name })),
              total: result.total,
              pages: result.pages,
            }
          }}
        />
      </div>,
    )
    return () => setActions(null)
  }, [current, namespace, setActions, setNamespace])

  return <Outlet />
}