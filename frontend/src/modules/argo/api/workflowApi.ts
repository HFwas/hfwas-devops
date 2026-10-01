import { del, get, post } from '@/shared/api/request'
import type {
  WorkflowSummary,
  WorkflowDetail,
  WorkflowRun,
  WorkflowRunLog,
  WorkflowEvent,
  WorkflowTemplate,
  PageRequest,
  PageResponse,
} from '@/modules/argo/types/workflow'

/**
 * Argo Workflows API
 * 通过 Spring Boot 后端代理关联 Argo Workflows Server
 *
 * 注意：axios baseURL 已是 '/api'，所以 API 路径以 /argo 开头（不要 /api/argo）
 */

// ---- 工作流 ----

export async function pageWorkflows(req: PageRequest): Promise<PageResponse<WorkflowSummary>> {
  return post('/argo/workflows/page', req)
}

export async function getWorkflow(name: string): Promise<WorkflowDetail> {
  return get(`/argo/workflows/${name}`)
}

export async function createWorkflow(spec: Record<string, unknown>): Promise<{ name: string }> {
  return post('/argo/workflows', spec)
}

export async function deleteWorkflow(name: string): Promise<void> {
  return del(`/argo/workflows/${name}`)
}

export async function submitWorkflow(
  name: string,
  params?: Record<string, string>,
): Promise<{ runId: string }> {
  return post(`/argo/workflows/${name}/submit`, params)
}

// ---- 运行 ----

export async function getWorkflowRuns(
  name: string,
  pageReq: PageRequest,
): Promise<PageResponse<WorkflowRun>> {
  return get(`/argo/workflows/${name}/runs`, pageReq)
}

export async function getWorkflowRun(runId: string): Promise<WorkflowRun> {
  return get(`/argo/workflows/runs/${runId}`)
}

export async function getWorkflowRunLogs(
  runId: string,
  nodeId?: string,
): Promise<WorkflowRunLog[]> {
  return get(`/argo/workflows/runs/${runId}/logs`, { nodeId })
}

export async function getWorkflowRunEvents(runId: string): Promise<WorkflowEvent[]> {
  return get(`/argo/workflows/runs/${runId}/events`)
}

// ---- 模板 ----

export async function pageTemplates(req: PageRequest): Promise<PageResponse<WorkflowTemplate>> {
  return post('/argo/templates/page', req)
}

export async function getTemplate(name: string): Promise<WorkflowTemplate> {
  return get(`/argo/templates/${name}`)
}

export async function saveTemplate(tmpl: WorkflowTemplate): Promise<void> {
  return post('/argo/templates', tmpl)
}