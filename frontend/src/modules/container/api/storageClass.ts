import { get } from '@/shared/api/request'
import type { StorageClassSummary } from '../types/resource'

export const storageClassApi = {
  list: (clusterId: string) =>
    get<StorageClassSummary[]>(`/container/clusters/${clusterId}/storageclasses`),
}