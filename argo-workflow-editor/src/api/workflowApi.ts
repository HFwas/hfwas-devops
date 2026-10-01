import type { PageRequest, PageResponse, WorkflowSummary, WorkflowDetail, WorkflowRun, WorkflowRunLog, WorkflowEvent, WorkflowTemplate, WorkflowRun as WorkflowRunDetail } from '../types/workflow'

const BASE = '/api/argo'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(BASE + url, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  })
  if (!res.ok) {
    const text = await res.text().catch(() => '')
    throw new Error(`请求失败 (${res.status}): ${text || res.statusText}`)
  }
  const body = await res.json()
  if (body.code !== 200) {
    throw new Error(body.msg || '请求失败')
  }
  return body.data as T
}

// ---- Workflows ----

export async function pageWorkflows(req: PageRequest): Promise<PageResponse<WorkflowSummary>> {
  return request('POST', { body: JSON.stringify(req), method: 'POST' })
}

export async function getWorkflow(name: string): Promise<WorkflowDetail> {
  return request(`GET /workflows/${name}`, { method: 'GET' })
}

export async function createWorkflow(spec: unknown): Promise<void> {
  return request(`POST /workflows`, { body: JSON.stringify(spec), method: 'POST' })
}

export async function deleteWorkflow(name: string): Promise<void> {
  return request(`DELETE /workflows/${name}`, { method: 'DELETE' })
}

export async function submitWorkflow(name: string): Promise<{ runId: string }> {
  return request(`POST /workflows/${name}/submit`, { method: 'POST' })
}

// ---- Runs ----

export async function getWorkflowRuns(name: string, req: PageRequest): Promise<PageResponse<WorkflowRun>> {
  return request(`GET /workflows/${name}/runs?pageNo=${req.pageNo}&pageSize=${req.pageSize}`, { method: 'GET' })
}

export async function getWorkflowRun(runId: string): Promise<WorkflowRunDetail> {
  return request(`GET /workflows/runs/${runId}`, { method: 'GET' })
}

export async function getWorkflowRunLogs(runId: string, nodeId?: string): Promise<WorkflowRunLog[]> {
  const params = nodeId ? `?nodeId=${nodeId}` : ''
  return request(`GET /workflows/runs/${runId}/logs${params}`, { method: 'GET' })
}

export async function getWorkflowRunEvents(runId: string): Promise<WorkflowEvent[]> {
  return request(`GET /workflows/runs/${runId}/events`, { method: 'GET' })
}

// ---- Templates ----

export async function pageTemplates(req: PageRequest): Promise<PageResponse<WorkflowTemplate>> {
  return request(`POST /templates/page`, { body: JSON.stringify(req), method: 'POST' })
}

export async function getTemplate(name: string): Promise<WorkflowTemplate> {
  return request(`GET /templates/${name}`, { method: 'GET' })
}

export async function saveTemplate(tmpl: WorkflowTemplate): Promise<void> {
  return request(`POST /templates`, { body: JSON.stringify(tmpl), method: 'POST' })
}