import { get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { NamespaceInfo } from '../types/resource'

export const namespaceApi = {
  page: (clusterId: string, params: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    get<PageResult<NamespaceInfo>>(`/container/clusters/${clusterId}/namespaces`, params),
}