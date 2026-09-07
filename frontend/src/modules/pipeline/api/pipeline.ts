import { del, get, post, put } from '@/shared/api/request'
import type { PageResult } from '@/shared/types/common'
import type {
  CredentialSavePayload,
  EntityId,
  PipelineCredential,
  PipelineRun,
  PipelineSavePayload,
  PipelineSummary,
  ToolchainOption,
} from '@/modules/pipeline/types/pipeline'

function asId(value: EntityId): string {
  return String(value)
}

export const pipelineApi = {
  toolchains: () => get<ToolchainOption[]>('/pipeline/toolchains'),
  page: (data: { pageNo?: number; pageSize?: number; keyword?: string }) =>
    post<PageResult<PipelineSummary>>('/pipeline/pipelines/page', data),
  get: (id: EntityId) => get<PipelineSummary>(`/pipeline/pipelines/${asId(id)}`),
  create: (data: PipelineSavePayload) => post<EntityId>('/pipeline/pipelines', data),
  update: (id: EntityId, data: PipelineSavePayload) => put<EntityId>(`/pipeline/pipelines/${asId(id)}`, data),
  delete: (id: EntityId) => del<void>(`/pipeline/pipelines/${asId(id)}`),
  start: (id: EntityId) => post<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs`),
  getRun: (id: EntityId, runId: EntityId) =>
    get<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs/${asId(runId)}`),
  cancel: (id: EntityId, runId: EntityId) =>
    post<PipelineRun>(`/pipeline/pipelines/${asId(id)}/runs/${asId(runId)}/cancel`),
}

export const pipelineCredentialApi = {
  list: () => get<PipelineCredential[]>('/pipeline/credentials'),
  get: (id: EntityId) => get<PipelineCredential>(`/pipeline/credentials/${asId(id)}`),
  save: (data: CredentialSavePayload) => post<EntityId>('/pipeline/credentials', data),
  delete: (id: EntityId) => del<void>(`/pipeline/credentials/${asId(id)}`),
}
