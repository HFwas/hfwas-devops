import { get } from '@/shared/api/request'
import type { JvmCheckResult, MonitorOverview, MonitorRange, MonitorSeries } from '../types/monitor'
import { RANGE_STEP_MAP } from '../types/monitor'

function rangeParams(range: MonitorRange) {
  return { range, step: RANGE_STEP_MAP[range].step }
}

export const monitorApi = {
  // ── Cluster overview ──
  overview: (clusterId: string) =>
    get<MonitorOverview>(`/container/clusters/${clusterId}/monitor/overview`),

  // ── Node ──
  nodeCpu: (clusterId: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/nodes/${name}/cpu`, rangeParams(range)),

  nodeMemory: (clusterId: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/nodes/${name}/memory`, rangeParams(range)),

  nodeNetwork: (clusterId: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/nodes/${name}/network`, rangeParams(range)),

  nodeConnections: (clusterId: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/nodes/${name}/connections`, rangeParams(range)),

  nodeDisk: (clusterId: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/nodes/${name}/disk`, rangeParams(range)),

  // ── Pod ──
  podCpu: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/cpu`, rangeParams(range)),

  podMemory: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/memory`, rangeParams(range)),

  podNetwork: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/network`, rangeParams(range)),

  // ── JVM ──
  jvmCheck: (clusterId: string, namespace: string, name: string) =>
    get<JvmCheckResult>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/jvm/check`),

  jvmHeap: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/jvm/heap`, rangeParams(range)),

  jvmNonHeap: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/jvm/nonheap`, rangeParams(range)),

  jvmGc: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/jvm/gc`, rangeParams(range)),

  jvmThread: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/jvm/thread`, rangeParams(range)),

  jvmMemoryPools: (clusterId: string, namespace: string, name: string, range: MonitorRange) =>
    get<MonitorSeries[]>(`/container/clusters/${clusterId}/monitor/namespaces/${namespace}/pods/${name}/jvm/memory-pools`, rangeParams(range)),
}