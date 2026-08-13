import { get, post } from '@/shared/api/request'
import request from '@/shared/api/request'
import type { BaseResult } from '@/shared/types/common'
import type { PageResult } from '@/shared/types/common'
import type {
  ApiDefinitionVO,
  ApiDefinitionDetailVO,
  ApiDefinitionCreateDTO,
  ApiDefinitionUpdateDTO,
  ApiDefinitionQueryDTO,
} from '@/modules/api-test/define/types/definition'

export const apiDefinitionApi = {
  /** 分页查询接口列表 */
  page: (query: ApiDefinitionQueryDTO) =>
    get<PageResult<ApiDefinitionVO>>('/apitest/definitions/page', query),

  /** 获取接口详情 */
  detail: (id: number) => get<ApiDefinitionDetailVO>(`/apitest/definitions/${id}`),

  /** 创建接口定义 */
  create: (data: ApiDefinitionCreateDTO, userId: number) =>
    post<ApiDefinitionDetailVO>(`/apitest/definitions?userId=${userId}`, data),

  /** 更新接口定义 */
  update: (id: number, data: ApiDefinitionUpdateDTO, userId: number) =>
    request.put<BaseResult<ApiDefinitionDetailVO>>(`/apitest/definitions/${id}?userId=${userId}`, data)
      .then(res => res.data.data),

  /** 删除接口定义 */
  delete: (id: number) =>
    request.delete<BaseResult<void>>(`/apitest/definitions/${id}`)
      .then(res => res.data.data),

  /** 发布接口（草稿→已发布） */
  publish: (id: number, userId: number) =>
    post<void>(`/apitest/definitions/${id}/publish?userId=${userId}`, {}),

  /** 废弃接口（已发布→已废弃） */
  deprecate: (id: number, userId: number) =>
    post<void>(`/apitest/definitions/${id}/deprecate?userId=${userId}`, {}),

  /** 恢复草稿（已发布/已废弃→草稿） */
  revertDraft: (id: number, userId: number) =>
    post<void>(`/apitest/definitions/${id}/revert-draft?userId=${userId}`, {}),
}