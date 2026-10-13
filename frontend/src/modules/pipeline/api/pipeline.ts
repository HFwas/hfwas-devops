import { del, get, patch, post, put } from '@/shared/api/request'
import { asId } from '@/lib/utils'
import type { PageResult } from '@/shared/types/common'
import type {
  CredentialSavePayload,
  EntityId,
  PipelineCredential,
  PipelineRun,
  PipelineSavePayload,
  PipelineSummary,
  PodContainersVO,
  RunParamValue,
  TaskKindPublishPayload,
  TaskKindUpdatePayload,
  TaskKindVO,
  TaskKindVersion,
  ToolchainOption,
} from '@/modules/pipeline/types/pipeline'

function kindPath(kind: string): string {
  return encodeURIComponent(kind)
}

export const pipelineApi = {
  toolchains: () => get<ToolchainOption[]>('/pipeline/toolchains'),
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<PipelineSummary>>('/pipeline/pipelines/page', data),
  get: (id: EntityId) => get<PipelineSummary>(`/pipeline/pipelines/${asId(id)}`),
  create: (data: PipelineSavePayload) => post<EntityId>('/pipeline/pipelines', data),
  update: (id: EntityId, data: PipelineSavePayload) => put<EntityId>(`/pipeline/pipelines/${asId(id)}`, data),
  delete: (id: EntityId) => del<void>(`/pipeline/pipelines/${asId(id)}`),
  start: (id: EntityId, params?: Record<string, string>) =>
    post<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs`, params ? { params } : {}),
  getDefaultParams: (id: EntityId) =>
    get<RunParamValue[]>(`/pipeline/pipelines/${asId(id)}/runs/default-params`),
  getRun: (id: EntityId, runId: EntityId) =>
    get<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs/${asId(runId)}`),
  pageRuns: (id: EntityId, params?: { pageNo?: number; pageSize?: number }) =>
    get<PageResult<PipelineRun>>(`/pipeline/pipelines/${asId(id)}/runs`, params),
  cancel: (id: EntityId, runId: EntityId) =>
    post<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs/${asId(runId)}/cancel`),
  approve: (id: EntityId, runId: EntityId) =>
    post<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs/${asId(runId)}/approve`),
  getContainers: (id: EntityId, runId: EntityId, jobId: EntityId) =>
    get<PodContainersVO>(`/pipeline/pipelines/${asId(id)}/runs/${asId(runId)}/jobs/${asId(jobId)}/containers`),
}

export const pipelineJobParamApi = {
  previewApi: (data: { apiUrl: string; apiMethod?: string; apiHeaders?: Record<string, string>; apiResponsePath?: string }) =>
    post<{ success: boolean; options: string[]; errorMessage?: string }>('/pipeline/job-params/preview-api', data),
}

export const pipelineCredentialApi = {
  list: () => get<PipelineCredential[]>('/pipeline/credentials'),
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<PipelineCredential>>('/pipeline/credentials/page', data),
  get: (id: EntityId) => get<PipelineCredential>(`/pipeline/credentials/${asId(id)}`),
  save: (data: CredentialSavePayload) => post<EntityId>('/pipeline/credentials', data),
  delete: (id: EntityId) => del<void>(`/pipeline/credentials/${asId(id)}`),
}

export const pipelineTaskKindApi = {
  list: () => get<TaskKindVO[]>('/pipeline/task-kinds'),
  get: (kind: string) => get<TaskKindVO>(`/pipeline/task-kinds/${kindPath(kind)}`),
  update: (kind: string, data: TaskKindUpdatePayload) =>
    put<void>(`/pipeline/task-kinds/${kindPath(kind)}`, data),
  toggle: (kind: string) =>
    patch<void>(`/pipeline/task-kinds/${kindPath(kind)}/toggle`),
  versions: (kind: string) =>
    get<TaskKindVersion[]>(`/pipeline/task-kinds/${kindPath(kind)}/versions`),
  publish: (kind: string, data: TaskKindPublishPayload) =>
    post<TaskKindVersion>(`/pipeline/task-kinds/${kindPath(kind)}/versions`, data),
  activate: (kind: string, version: string) =>
    put<TaskKindVO>(`/pipeline/task-kinds/${kindPath(kind)}/versions/${kindPath(version)}/activate`),
}
