import { get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { ServiceDetail, ServiceSummary } from '../types/resource'

export const serviceApi = {
  list: (clusterId: number, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<ServiceSummary>>(`/container/clusters/${clusterId}/services`, params),
  get: (clusterId: number, namespace: string, name: string) =>
    get<ServiceDetail>(`/container/clusters/${clusterId}/namespaces/${namespace}/services/${name}`),
}