export interface ClusterVO {
  id: number
  tenantId: number
  name: string
  alias: string | null
  provider: string | null
  version: string | null
  mode: string
  status: 'Connected' | 'Degraded' | 'Disconnected' | 'Unknown'
  labels: Record<string, string> | null
  nodeCount: number | null
  podCount: number | null
  createdAt: string
  updatedAt: string
}

export interface ClusterSaveDTO {
  name: string
  alias?: string
  provider?: string
  kubeconfig: string
  mode?: 'proxy' | 'direct'
  labels?: Record<string, string>
}

export interface ClusterUpdateDTO {
  alias?: string
  provider?: string
  kubeconfig?: string
  labels?: Record<string, string>
}

export interface ClusterStatsVO {
  nodeCount: number
  podCount: number
  deploymentCount: number
  serviceCount: number
  namespaceCount: number
  cpuTotal: number
  memoryTotal: number
}

export type ClusterStatus = 'Connected' | 'Degraded' | 'Disconnected' | 'Unknown'