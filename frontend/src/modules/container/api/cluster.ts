import { del, get, post, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { ClusterComponentVO, ClusterSaveDTO, ClusterStatsVO, ClusterUpdateDTO, ClusterVO } from '../types/cluster'

export const clusterApi = {
  page: (data: { pageNo?: number; pageSize?: number }) =>
    post<PageResult<ClusterVO>>('/container/clusters/page', data),
  get: (id: string) => get<ClusterVO>(`/container/clusters/${id}`),
  create: (data: ClusterSaveDTO) => post<number>('/container/clusters', data),
  update: (id: string, data: ClusterUpdateDTO) => put<void>(`/container/clusters/${id}`, data),
  delete: (id: string) => del<void>(`/container/clusters/${id}`),
  test: (id: string) => post<boolean>(`/container/clusters/${id}/test`),
  stats: (id: string) => get<ClusterStatsVO>(`/container/clusters/${id}/stats`),
  components: (id: string) => get<ClusterComponentVO>(`/container/clusters/${id}/components`),
}