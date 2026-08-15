import { get, post } from '@/shared/api/request'
import request from '@/shared/api/request'
import type { BaseResult } from '@/shared/types/common'
import type { PageResult } from '@/shared/types/common'
import type { EnvironmentVO, EnvironmentDetailVO, EnvironmentCreateDTO, EnvironmentUpdateDTO, EnvironmentQueryDTO } from '@/modules/api-test/environment/types/environment'

export const environmentApi = {
  /** 分页查询环境列表 */
  page: (query: EnvironmentQueryDTO) =>
    get<PageResult<EnvironmentVO>>('/apitest/environments/page', query),

  /** 查询所有环境 */
  listAll: (projectId: number) =>
    get<EnvironmentVO[]>('/apitest/environments/list', { projectId }),

  /** 获取环境详情 */
  detail: (id: number) =>
    get<EnvironmentDetailVO>(`/apitest/environments/${id}`),

  /** 创建环境 */
  create: (data: EnvironmentCreateDTO, projectId: number, userId: number) =>
    post<EnvironmentDetailVO>(`/apitest/environments?projectId=${projectId}&userId=${userId}`, data),

  /** 更新环境 */
  update: (id: number, data: EnvironmentUpdateDTO, userId: number) =>
    request.put<BaseResult<EnvironmentDetailVO>>(`/apitest/environments/${id}?userId=${userId}`, data)
      .then(res => res.data.data),

  /** 删除环境 */
  delete: (id: number) =>
    request.delete<BaseResult<void>>(`/apitest/environments/${id}`).then(res => res.data.data),
}