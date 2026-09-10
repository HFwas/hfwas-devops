import { del, get, post, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { ClusterSaveDTO, ClusterStatsVO, ClusterUpdateDTO, ClusterVO } from '../types/cluster'

export const clusterApi = {
  page: (data: { pageNo?: number; pageSize?: number }) =>
    post<PageResult<ClusterVO>>('/container/clusters/page', data),
  get: (id: number) => get<ClusterVO>(`/container/clusters/${id}`),
  create: (data: ClusterSaveDTO) => post<number>('/container/clusters', data),
  update: (id: number, data: ClusterUpdateDTO) => put<void>(`/container/clusters/${id}`, data),
  delete: (id: number) => del<void>(`/container/clusters/${id}`),
  test: (id: number) => post<boolean>(`/container/clusters/${id}/test`),
  stats: (id: number) => get<ClusterStatsVO>(`/container/clusters/${id}/stats`),
}