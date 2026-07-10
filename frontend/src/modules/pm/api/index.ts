import { get, post, postBlob, postFormData } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type { EntityId } from '@/modules/pm/utils/id'
import type { AllowedTransitions, FieldDefinition, FieldOption, FieldRemoteOptionsConfig, IssueTypeSchemeExport, IssueTypeSchemeImportPreview, IssueTypeSchemeImportResult, PmProject, PmProjectModule, PmSavedView, PmWorkItem, PmWorkItemActivity, PmWorkItemComment, PmWorkItemType, ProjectAccessContext, ProjectFieldSchemeExport, ProjectIssueTypeSchemeExport, QuerySpec, RemoteOptionFetchResult, ResolvedFieldOption, SchemeImportMode, StatusDefinition, StatusWorkflow, TransitionMeta, TransitionPostFunctionMeta, TypeFieldLayoutConfig, TypeFieldSchemeExport, WorkItemImportMode, WorkItemImportPreview, WorkItemImportResult } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'

export const pmModuleApi = {
  tree: (projectId: EntityId) => get<PmProjectModule[]>(`/pm/project-modules/tree?projectId=${asId(projectId)}`),
  flat: (projectId: EntityId) => get<PmProjectModule[]>(`/pm/project-modules/flat?projectId=${asId(projectId)}`),
  save: (data: PmProjectModule) => post<number>('/pm/project-modules/save', data),
  delete: (id: number) => post<void>(`/pm/project-modules/delete?id=${id}`, {}),
}

export const pmProjectApi = {
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<PmProject>>('/pm/projects/page', data),
  save: (data: PmProject) => post<number>('/pm/projects/save', data),
  getById: (id: EntityId) => get<PmProject>(`/pm/projects/${asId(id)}`),
  accessContext: (id: EntityId) => get<ProjectAccessContext>(`/pm/projects/${asId(id)}/access-context`),
  delete: (id: EntityId) => post<void>(`/pm/projects/delete?id=${asId(id)}`, {}),
}

export const pmWorkItemApi = {
  page: (spec: QuerySpec) => post<PageResult<PmWorkItem>>('/pm/work-items/page', spec),
  save: (data: PmWorkItem) => post<number>('/pm/work-items/save', data),
  getById: (id: EntityId) => get<PmWorkItem>(`/pm/work-items/${asId(id)}`),
  delete: (id: EntityId) => post<void>(`/pm/work-items/delete?id=${asId(id)}`, {}),
  transition: (id: EntityId, payload: { transitionId: string; fields?: Record<string, unknown> }) =>
    post<void>(`/pm/work-items/${asId(id)}/transition`, payload),
  addLink: (sourceId: EntityId, targetId: EntityId, linkType: string) =>
    post<number>('/pm/work-items/links/save', { sourceId, targetId, linkType }),
  listLinks: (id: EntityId) =>
    get<Array<{ id: number; sourceId: number; targetId: number; linkType: string }>>(`/pm/work-items/${asId(id)}/links`),
  listComments: (id: EntityId) => get<PmWorkItemComment[]>(`/pm/work-items/${asId(id)}/comments`),
  countComments: (id: EntityId) => get<number>(`/pm/work-items/${asId(id)}/comments/count`),
  countCommentsBatch: (workItemIds: EntityId[]) =>
    post<Record<string, number>>('/pm/work-items/comments/counts', workItemIds.map(asId)),
  saveComment: (data: { workItemId: EntityId; content: string; parentId?: string | null }) =>
    post<number>('/pm/work-items/comments/save', data),
  deleteComment: (id: EntityId) => post<void>(`/pm/work-items/comments/delete?id=${asId(id)}`, {}),
  listActivities: (id: EntityId) => get<PmWorkItemActivity[]>(`/pm/work-items/${asId(id)}/activities`),
}

export const pmWorkItemIoApi = {
  exportExcel: async (payload: {
    projectId: EntityId
    typeCode: string
    ids?: string[]
    querySpec?: QuerySpec
    fieldKeys: string[]
  }) => {
    const { blob, filename } = await postBlob('/pm/work-items/io/export', {
      ...payload,
      projectId: asId(payload.projectId),
      ids: payload.ids?.map(asId),
    })
    return { blob, filename }
  },
  downloadImportTemplate: async (payload: {
    projectId: EntityId
    typeCode: string
    fieldKeys: string[]
  }) => {
    const { blob, filename } = await postBlob(
      '/pm/work-items/io/import/template',
      {
        projectId: asId(payload.projectId),
        typeCode: payload.typeCode,
        fieldKeys: payload.fieldKeys,
      },
      'import-template.xlsx',
    )
    return { blob, filename }
  },
  previewImport: (projectId: EntityId, typeCode: string, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return postFormData<WorkItemImportPreview>(
      `/pm/work-items/io/import/preview?projectId=${asId(projectId)}&typeCode=${typeCode}`,
      form,
    )
  },
  importExcel: (projectId: EntityId, typeCode: string, file: File, mode: WorkItemImportMode, fieldKeys?: string[]) => {
    const form = new FormData()
    form.append('file', file)
    const params = new URLSearchParams({
      projectId: asId(projectId),
      typeCode,
      mode,
    })
    if (fieldKeys?.length) {
      params.set('fieldKeys', JSON.stringify(fieldKeys))
    }
    return postFormData<WorkItemImportResult>(`/pm/work-items/io/import?${params.toString()}`, form)
  },
}

export const pmFieldApi = {
  list: (projectId: EntityId, typeCode: string) =>
    post<FieldDefinition[]>('/pm/fields/definitions/list', { projectId, typeCode }),
  catalog: (projectId: EntityId) =>
    post<FieldDefinition[]>('/pm/fields/definitions/catalog', { projectId }),
  getById: (id: number | string) => get<FieldDefinition>(`/pm/fields/definitions/${id}`),
  options: (fieldId: number | string) => get<FieldOption[]>(`/pm/fields/definitions/options?fieldId=${fieldId}`),
  resolveOptions: (fieldId: number | string) =>
    get<ResolvedFieldOption[]>(`/pm/fields/definitions/options/resolve?fieldId=${fieldId}`),
  previewRemoteOptions: (config: FieldRemoteOptionsConfig) =>
    post<RemoteOptionFetchResult>('/pm/fields/definitions/options/remote/preview', config),
  save: (definition: FieldDefinition, options?: FieldOption[]) =>
    post<number>('/pm/fields/definitions/save', { definition, options }),
  delete: (id: number | string) => post<void>(`/pm/fields/definitions/delete?id=${id}`, {}),
  listAvailable: (projectId: EntityId, typeCode: string) =>
    post<FieldDefinition[]>('/pm/fields/definitions/available', { projectId, typeCode }),
  addToType: (projectId: EntityId, fieldId: EntityId, typeCode: string) =>
    post<void>('/pm/fields/definitions/add-to-type', { projectId, fieldId, typeCode }),
  removeFromType: (projectId: EntityId, fieldId: EntityId, typeCode: string) =>
    post<void>('/pm/fields/definitions/remove-from-type', { projectId, fieldId, typeCode }),
}

export const pmFieldLayoutApi = {
  get: (projectId: EntityId, typeCode: string) =>
    post<TypeFieldLayoutConfig>('/pm/fields/layout/get', { projectId, typeCode }),
  save: (projectId: EntityId, typeCode: string, layout: TypeFieldLayoutConfig) =>
    post<void>('/pm/fields/layout/save', { projectId, typeCode, layout }),
}

export const pmIssueTypeSchemeApi = {
  exportType: (projectId: EntityId, typeCode: string) =>
    post<IssueTypeSchemeExport>('/pm/issue-type-schemes/export', { projectId, typeCode }),
  exportProject: (projectId: EntityId) =>
    post<ProjectIssueTypeSchemeExport>('/pm/issue-type-schemes/export-project', { projectId }),
  previewImport: (
    projectId: EntityId,
    typeCode: string,
    payload: { scheme?: IssueTypeSchemeExport; legacyScheme?: TypeFieldSchemeExport },
  ) => post<IssueTypeSchemeImportPreview>('/pm/issue-type-schemes/preview', { projectId, typeCode, ...payload }),
  importType: (
    projectId: EntityId,
    typeCode: string,
    payload: { scheme?: IssueTypeSchemeExport; legacyScheme?: TypeFieldSchemeExport },
    mode: SchemeImportMode,
  ) => post<IssueTypeSchemeImportResult>('/pm/issue-type-schemes/import', { projectId, typeCode, mode, ...payload }),
  importProject: (
    projectId: EntityId,
    payload: { projectScheme?: ProjectIssueTypeSchemeExport; legacyProjectScheme?: ProjectFieldSchemeExport },
    mode: SchemeImportMode,
  ) =>
    post<IssueTypeSchemeImportResult[]>('/pm/issue-type-schemes/import-project', {
      projectId,
      mode,
      ...payload,
    }),
}

export const pmStatusApi = {
  get: (projectId: EntityId, typeCode: string) =>
    post<StatusWorkflow>('/pm/status/workflow/get', { projectId, typeCode }),
  options: (projectId: EntityId, typeCode: string) =>
    post<StatusDefinition[]>('/pm/status/workflow/options', { projectId, typeCode }),
  allowed: (
    projectId: EntityId,
    typeCode: string,
    fromStatus: string,
    workItemId?: EntityId,
  ) =>
    post<AllowedTransitions>('/pm/status/workflow/allowed', {
      projectId,
      typeCode,
      fromStatus,
      ...(workItemId != null ? { workItemId } : {}),
    }),
  save: (projectId: EntityId, typeCode: string, statuses: StatusDefinition[]) =>
    post<void>('/pm/status/workflow/save', { projectId, typeCode, statuses }),
  reset: (projectId: EntityId, typeCode: string) =>
    post<void>('/pm/status/workflow/reset', { projectId, typeCode }),
  postFunctionMeta: (projectId: EntityId, typeCode: string) =>
    post<TransitionPostFunctionMeta>('/pm/status/workflow/post-function-meta', { projectId, typeCode }),
  transitionMeta: (projectId: EntityId, typeCode: string, transitionId: string, fromStatus?: string) =>
    post<TransitionMeta>('/pm/status/workflow/transition-meta', {
      projectId,
      typeCode,
      transitionId,
      fromStatus,
    }),
}

export const pmViewApi = {
  list: (projectId: number, typeCode?: string) =>
    post<PmSavedView[]>('/pm/views/list', { projectId, typeCode }),
  save: (view: PmSavedView) => post<number>('/pm/views/save', view),
  delete: (id: number) => post<void>(`/pm/views/delete?id=${id}`, {}),
}

export const pmMetaApi = {
  types: () => post<PmWorkItemType[]>('/pm/meta/types', {}),
  board: (projectId: EntityId, typeCode: string) =>
    post<Record<string, PmWorkItem[]>>('/pm/board', { projectId, typeCode }),
}
