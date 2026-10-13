import { get } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { StorageClassSummary } from '../types/resource'

export const storageClassApi = {
  page: (clusterId: string, params: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    get<PageResult<StorageClassSummary>>(`/container/clusters/${clusterId}/storageclasses`, params),
}