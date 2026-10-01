export type WorkflowStatus = 'Running' | 'Succeeded' | 'Failed' | 'Error' | 'Pending' | 'Skipped'

export type WorkflowPhase =
  | 'Pending' | 'Running' | 'Succeeded' | 'Failed' | 'Error' | 'Skipped' | 'Omitted'

export type NodeGroup = 'trigger' | 'task' | 'flow-control' | 'notification' | 'kubernetes'

export type TriggerType = 'manual' | 'cron' | 'webhook' | 'event'

export interface WorkflowSummary {
  name: string
  namespace: string
  status: WorkflowStatus
  startedAt: string
  finishedAt?: string
  duration?: string
  trigger: TriggerType
  entrypoint?: string
  createdBy?: string
}

export interface WorkflowDetail extends WorkflowSummary {
  phase: WorkflowPhase
  parameters?: { name: string; value?: string }[]
  templates?: unknown[]
  nodeStatus?: Record<string, unknown>
}

export interface WorkflowTemplate {
  name: string
  namespace: string
  description?: string
  entrypoint: string
  templates?: unknown[]
  arguments?: { name: string; value?: string; description?: string }[]
  createdAt?: string
}

export interface WorkflowRun {
  id: string
  workflowName: string
  status: WorkflowStatus
  startedAt: string
  finishedAt?: string
  duration?: string
  trigger: TriggerType
  triggeredBy?: string
  nodeStatus?: Record<string, unknown>
}

export interface WorkflowRunLog {
  nodeId: string
  nodeName: string
  logs: string
  phase: WorkflowPhase
}

export interface WorkflowEvent {
  id: string
  type: string
  reason: string
  message: string
  timestamp: string
  source: string
}

export interface PageRequest {
  pageNo: number
  pageSize: number
  keyword?: string
  status?: string
}

export interface PageResponse<T> {
  records: T[]
  total: number
  pageNo: number
  pageSize: number
  pages: number
}

// ---- Vue Flow types ----

export interface NodePortDef {
  id: string
  label: string
  type: 'source' | 'target'
}

export interface NodeMeta {
  type: string
  label: string
  group: NodeGroup
  description: string
  color: string
  bgColor: string
  defaultImage: string
  defaultCommand: string
  inputs: NodePortDef[]
  outputs: NodePortDef[]
}

export interface FlowNodeData {
  label: string
  type: string
  meta: NodeMeta
  config: {
    image?: string
    command?: string
    args?: string[]
    env?: Record<string, string>
    retry?: { limit?: number }
    timeout?: number
    script?: string
    url?: string
    method?: string
    condition?: string
    message?: string
  }
  phase?: WorkflowPhase
  message?: string
  duration?: string
  [key: string]: unknown // allow index signature for ReactFlow types
}