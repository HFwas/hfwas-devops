import { get, post } from '@/shared/api/request'
import request from '@/shared/api/request'
import type { BaseResult } from '@/shared/types/common'
import type { PageResult } from '@/shared/types/common'
import type { ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'
import type { DebugHistoryQueryDTO, DebugHistoryDetailVO } from '@/modules/api-test/debug/types/debugHistory'

export const debugHistoryApi = {
  /** 分页查询调试历史 */
  page: (query: DebugHistoryQueryDTO) =>
    get<PageResult<ApiDebugHistoryVO>>('/apitest/debug-histories/page', query),

  /** 获取调试历史详情 */
  detail: (id: number) =>
    get<DebugHistoryDetailVO>(`/apitest/debug-histories/${id}`),

  /** 查询某接口的调试历史 */
  listByDefinition: (definitionId: number, limit = 20) =>
    get<ApiDebugHistoryVO[]>('/apitest/debug-histories/by-definition', { definitionId, limit }),

  /** 删除调试历史 */
  delete: (id: number) =>
    request.delete<BaseResult<void>>(`/apitest/debug-histories/${id}`).then(res => res.data.data),

  /** 批量删除 */
  deleteBatch: (ids: number[]) =>
    request.delete<BaseResult<void>>('/apitest/debug-histories/batch', { params: { ids: ids.join(',') } }).then(res => res.data.data),
}