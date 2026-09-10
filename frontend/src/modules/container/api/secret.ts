import { del, get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { SecretSummary } from '../types/resource'

export const secretApi = {
  list: (clusterId: string, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<SecretSummary>>(`/container/clusters/${clusterId}/secrets`, params),
  delete: (clusterId: string, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/secrets/${name}`),
}