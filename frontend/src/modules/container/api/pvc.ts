import { del, get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { PvcSummary } from '../types/resource'

export const pvcApi = {
  list: (clusterId: string, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<PvcSummary>>(`/container/clusters/${clusterId}/pvcs`, params),
  delete: (clusterId: string, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/pvcs/${name}`),
}