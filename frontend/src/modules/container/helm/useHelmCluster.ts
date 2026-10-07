import { HELM_USE_MOCK } from '@/modules/container/api/helm'
import { useContainerCluster } from '@/modules/container/clusterStore'

/** Release 操作挂在当前集群上。后端与集群列表都不可用时，示例数据使用 demo-cluster。 */
export function useHelmCluster() {
  const currentId = useContainerCluster((state) => state.currentId)
  const namespace = useContainerCluster((state) => state.namespace)
  if (currentId) return { clusterId: currentId, namespace, synthetic: false }
  if (HELM_USE_MOCK) return { clusterId: 'demo-cluster', namespace, synthetic: true }
  return { clusterId: null, namespace, synthetic: false }
}
