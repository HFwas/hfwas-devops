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
  detail: (id: number | string) => get<ApiDefinitionDetailVO>(`/apitest/definitions/${id}`),

  /** 创建接口定义 */
  create: (data: ApiDefinitionCreateDTO) =>
    post<ApiDefinitionDetailVO>('/apitest/definitions', data),

  /** 更新接口定义 */
  update: (id: number, data: ApiDefinitionUpdateDTO) =>
    request.put<BaseResult<ApiDefinitionDetailVO>>(`/apitest/definitions/${id}`, data)
      .then(res => res.data.data),

  /** 删除接口定义 */
  delete: (id: number | string) =>
    request.delete<BaseResult<void>>(`/apitest/definitions/${id}`)
      .then(res => res.data.data),

  /** 发布接口（草稿→已发布） */
  publish: (id: number | string) =>
    post<void>(`/apitest/definitions/${id}/publish`, {}),

  /** 废弃接口（已发布→已废弃） */
  deprecate: (id: number | string) =>
    post<void>(`/apitest/definitions/${id}/deprecate`, {}),

  /** 恢复草稿（已发布/已废弃→草稿） */
  revertDraft: (id: number | string) =>
    post<void>(`/apitest/definitions/${id}/revert-draft`, {}),
}