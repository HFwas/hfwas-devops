import { get } from '@/shared/api/request'
import type { NodeDetail, NodeSummary } from '../types/resource'

export const nodeApi = {
  list: (clusterId: number, keyword?: string) =>
    get<NodeSummary[]>(`/container/clusters/${clusterId}/nodes`, { keyword }),
  get: (clusterId: number, name: string) =>
    get<NodeDetail>(`/container/clusters/${clusterId}/nodes/${name}`),
}