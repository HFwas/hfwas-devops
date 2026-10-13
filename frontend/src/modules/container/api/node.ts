import { get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { NodeDetail, NodeSummary } from '../types/resource'

export const nodeApi = {
  page: (clusterId: string, params: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    get<PageResult<NodeSummary>>(`/container/clusters/${clusterId}/nodes`, params),
  get: (clusterId: string, name: string) =>
    get<NodeDetail>(`/container/clusters/${clusterId}/nodes/${name}`),
}