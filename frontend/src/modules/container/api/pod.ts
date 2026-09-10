import { del, get, post } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { PodDetail, PodSummary } from '../types/resource'

export const podApi = {
  list: (clusterId: number, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<PodSummary>>(`/container/clusters/${clusterId}/pods`, params),
  get: (clusterId: number, namespace: string, name: string) =>
    get<PodDetail>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}`),
  yaml: (clusterId: number, namespace: string, name: string) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/yaml`),
  logs: (clusterId: number, namespace: string, name: string, params?: { container?: string; tailLines?: number }) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}/logs`, params),
  delete: (clusterId: number, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/pods/${name}`),
}