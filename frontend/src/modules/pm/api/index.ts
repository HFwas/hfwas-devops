import { get, post } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { FieldDefinition, FieldOption, PmProject, PmSavedView, PmWorkItem, PmWorkItemType, QuerySpec } from '@/modules/pm/types'

export const pmProjectApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<PmProject>>('/pm/projects/page', data),
  save: (data: PmProject) => post<number>('/pm/projects/save', data),
  getById: (id: number) => get<PmProject>(`/pm/projects/${id}`),
  delete: (id: number) => post<void>(`/pm/projects/delete?id=${id}`, {}),
}

export const pmWorkItemApi = {
  page: (spec: QuerySpec) => post<PageResult<PmWorkItem>>('/pm/work-items/page', spec),
  save: (data: PmWorkItem) => post<number>('/pm/work-items/save', data),
  getById: (id: number) => get<PmWorkItem>(`/pm/work-items/${id}`),
  delete: (id: number) => post<void>(`/pm/work-items/delete?id=${id}`, {}),
  transition: (id: number, toStatus: string) =>
    post<void>(`/pm/work-items/${id}/transition`, { toStatus }),
  addLink: (sourceId: number, targetId: number, linkType: string) =>
    post<number>('/pm/work-items/links/save', { sourceId, targetId, linkType }),
  listLinks: (id: number) => get<Array<{ id: number; sourceId: number; targetId: number; linkType: string }>>(`/pm/work-items/${id}/links`),
}

export const pmFieldApi = {
  list: (projectId: number, typeCode: string) =>
    post<FieldDefinition[]>('/pm/fields/definitions/list', { projectId, typeCode }),
  save: (definition: FieldDefinition, options?: FieldOption[]) =>
    post<number>('/pm/fields/definitions/save', { definition, options }),
}

export const pmViewApi = {
  list: (projectId: number, typeCode?: string) =>
    post<PmSavedView[]>('/pm/views/list', { projectId, typeCode }),
  save: (view: PmSavedView) => post<number>('/pm/views/save', view),
  delete: (id: number) => post<void>(`/pm/views/delete?id=${id}`, {}),
}

export const pmMetaApi = {
  types: () => post<PmWorkItemType[]>('/pm/meta/types', {}),
  board: (projectId: number, typeCode: string) =>
    post<Record<string, PmWorkItem[]>>('/pm/board', { projectId, typeCode }),
}
