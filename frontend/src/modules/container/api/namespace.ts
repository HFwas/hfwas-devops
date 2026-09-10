import { get } from '@/shared/api/request'
import type { NamespaceInfo } from '../types/resource'

export const namespaceApi = {
  list: (clusterId: string) =>
    get<NamespaceInfo[]>(`/container/clusters/${clusterId}/namespaces`),
}