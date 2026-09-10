import { del, get, post, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { RegistrySaveDTO, RegistryUpdateDTO, RegistryVO } from '../types/registry'

export const registryApi = {
  page: (data: { pageNo?: number; pageSize?: number }) =>
    post<PageResult<RegistryVO>>('/container/registries/page', data),
  get: (id: string) => get<RegistryVO>(`/container/registries/${id}`),
  create: (data: RegistrySaveDTO) => post<number>('/container/registries', data),
  update: (id: string, data: RegistryUpdateDTO) => put<void>(`/container/registries/${id}`, data),
  delete: (id: string) => del<void>(`/container/registries/${id}`),
  test: (id: string) => post<boolean>(`/container/registries/${id}/test`),
}