import { get, post } from '@/shared/api/request'
import request from '@/shared/api/request'
import type { BaseResult } from '@/shared/types/common'
import type { ApiGroupVO, ApiGroupCreateDTO, ApiGroupUpdateDTO } from '@/modules/api-test/define/types/group'

export const apiGroupApi = {
  /** 获取分组树 */
  tree: (projectId: number) => get<ApiGroupVO[]>(`/apitest/groups/tree?projectId=${projectId}`),

  /** 获取分组详情 */
  detail: (id: number) => get<ApiGroupVO>(`/apitest/groups/${id}`),

  /** 创建分组 */
  create: (data: ApiGroupCreateDTO, userId: number) =>
    post<ApiGroupVO>(`/apitest/groups?userId=${userId}`, data),

  /** 更新分组 */
  update: (id: number, data: ApiGroupUpdateDTO, userId: number) =>
    request.put<BaseResult<ApiGroupVO>>(`/apitest/groups/${id}?userId=${userId}`, data)
      .then(res => res.data.data),

  /** 删除分组 */
  delete: (id: number) =>
    request.delete<BaseResult<void>>(`/apitest/groups/${id}`)
      .then(res => res.data.data),
}