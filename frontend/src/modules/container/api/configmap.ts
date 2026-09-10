import { del, get, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { ConfigMapSummary } from '../types/resource'

export const configMapApi = {
  list: (clusterId: string, params: { namespace?: string; keyword?: string; pageNo?: number; pageSize?: number }) =>
    get<PageResult<ConfigMapSummary>>(`/container/clusters/${clusterId}/configmaps`, params),
  yaml: (clusterId: string, namespace: string, name: string) =>
    get<string>(`/container/clusters/${clusterId}/namespaces/${namespace}/configmaps/${name}/yaml`),
  updateYaml: (clusterId: string, namespace: string, name: string, yaml: string) =>
    put<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/configmaps/${name}/yaml`, yaml),
  delete: (clusterId: string, namespace: string, name: string) =>
    del<void>(`/container/clusters/${clusterId}/namespaces/${namespace}/configmaps/${name}`),
}