import { get, post } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { FieldDefinition, FieldOption, PmProject, PmProjectModule, PmSavedView, PmWorkItem, PmWorkItemComment, PmWorkItemType, QuerySpec, TypeFieldLayoutConfig } from '@/modules/pm/types'

export const pmModuleApi = {
  tree: (projectId: number) => get<PmProjectModule[]>(`/pm/project-modules/tree?projectId=${projectId}`),
  flat: (projectId: number) => get<PmProjectModule[]>(`/pm/project-modules/flat?projectId=${projectId}`),
  save: (data: PmProjectModule) => post<number>('/pm/project-modules/save', data),
  delete: (id: number) => post<void>(`/pm/project-modules/delete?id=${id}`, {}),
}

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
  listLinks: (id: number) =>
    get<Array<{ id: number; sourceId: number; targetId: number; linkType: string }>>(`/pm/work-items/${id}/links`),
  listComments: (id: number) => get<PmWorkItemComment[]>(`/pm/work-items/${id}/comments`),
  countComments: (id: number) => get<number>(`/pm/work-items/${id}/comments/count`),
  countCommentsBatch: (workItemIds: number[]) =>
    post<Record<string, number>>('/pm/work-items/comments/counts', workItemIds),
  saveComment: (data: { workItemId: number; content: string; parentId?: string | null; authorName?: string }) =>
    post<number>('/pm/work-items/comments/save', data),
  deleteComment: (id: number | string) => post<void>(`/pm/work-items/comments/delete?id=${id}`, {}),
}

export const pmFieldApi = {
  list: (projectId: number, typeCode: string) =>
    post<FieldDefinition[]>('/pm/fields/definitions/list', { projectId, typeCode }),
  catalog: (projectId: number) =>
    post<FieldDefinition[]>('/pm/fields/definitions/catalog', { projectId }),
  getById: (id: number | string) => get<FieldDefinition>(`/pm/fields/definitions/${id}`),
  options: (fieldId: number | string) => get<FieldOption[]>(`/pm/fields/definitions/options?fieldId=${fieldId}`),
  save: (definition: FieldDefinition, options?: FieldOption[]) =>
    post<number>('/pm/fields/definitions/save', { definition, options }),
  delete: (id: number | string) => post<void>(`/pm/fields/definitions/delete?id=${id}`, {}),
}

export const pmFieldLayoutApi = {
  get: (projectId: number, typeCode: string) =>
    post<TypeFieldLayoutConfig>('/pm/fields/layout/get', { projectId, typeCode }),
  save: (projectId: number, typeCode: string, layout: TypeFieldLayoutConfig) =>
    post<void>('/pm/fields/layout/save', { projectId, typeCode, layout }),
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
