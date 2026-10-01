import type { Component } from 'vue'

// ============================================================
// 核心类型
// ============================================================

export type WorkflowStatus = 'Running' | 'Succeeded' | 'Failed' | 'Error' | 'Pending' | 'Skipped'

export type WorkflowPhase =
  | 'Pending'
  | 'Running'
  | 'Succeeded'
  | 'Failed'
  | 'Error'
  | 'Skipped'
  | 'Omitted'

export type TriggerType = 'manual' | 'cron' | 'webhook' | 'event'

// ============================================================
// 工作流摘要（列表用）
// ============================================================

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

// ============================================================
// 工作流详情
// ============================================================

export interface WorkflowDetail {
  name: string
  namespace: string
  status: WorkflowStatus
  phase: WorkflowPhase
  startedAt: string
  finishedAt?: string
  duration?: string
  entrypoint: string
  parameters: Parameter[]
  templates: WorkflowTemplateSpec[]
  nodeStatus: Record<string, NodeStatus>
  trigger: TriggerType
}

// ============================================================
// WorkflowTemplate 类型
// ============================================================

export interface WorkflowTemplate {
  name: string
  description?: string
  entrypoint: string
  arguments?: { parameters?: Parameter[] }
  templates: WorkflowTemplateSpec[]
  volumes?: Volume[]
}

export interface WorkflowTemplateSpec {
  name: string
  inputs?: { parameters?: Parameter[]; artifacts?: Artifact[] }
  outputs?: { parameters?: OutputParameter[]; artifacts?: Artifact[] }
  steps?: StepGroup[]
  dag?: { tasks: DagTask[] }
  container?: Container
  script?: Script
  retryStrategy?: RetryStrategy
  activeDeadlineSeconds?: number
  nodeSelector?: Record<string, string>
  resources?: Resources
}

export interface Parameter {
  name: string
  value?: string
  default?: string
  description?: string
  enum?: string[]
  required?: boolean
}

export interface OutputParameter {
  name: string
  valueFrom: { path?: string; parameter?: string; jqFilter?: string }
}

export interface Artifact {
  name: string
  path?: string
  from?: string
  s3?: S3Artifact
  archive?: { none?: Record<string, never>; tar?: Record<string, never>; zip?: Record<string, never> }
  optional?: boolean
}

export interface S3Artifact {
  key: string
  bucket?: string
  endpoint?: string
}

export interface Container {
  image: string
  command?: string[]
  args?: string[]
  env?: EnvVar[]
  resources?: Resources
  workingDir?: string
}

export interface Script {
  image: string
  command: string[]
  source: string
  env?: EnvVar[]
  resources?: Resources
}

export interface EnvVar {
  name: string
  value?: string
  valueFrom?: { secretKeyRef?: KeyRef; configMapKeyRef?: KeyRef }
}

export interface KeyRef {
  name: string
  key: string
}

export interface Resources {
  requests?: { cpu?: string; memory?: string }
  limits?: { cpu?: string; memory?: string }
}

export interface RetryStrategy {
  limit?: number
  retryPolicy?: 'Always' | 'OnFailure' | 'OnError'
  backoff?: { duration?: string; factor?: number; maxDuration?: string }
}

export interface Volume {
  name: string
  persistentVolumeClaim?: { claimName: string }
  emptyDir?: Record<string, never>
  configMap?: { name: string }
  secret?: { secretName: string }
}

// ============================================================
// Step / DAG 类型
// ============================================================

export interface StepGroup {
  name?: string
  template?: string
  arguments?: { parameters?: ParameterRef[] }
  steps?: StepGroup[][]  // 嵌套并行
}

export interface DagTask {
  name: string
  template: string
  arguments?: { parameters?: ParameterRef[] }
  dependencies?: string[]
  when?: string
  withParam?: string
}

export interface ParameterRef {
  name: string
  value?: string
  from?: { parameter?: string }
}

// ============================================================
// 运行时节点状态
// ===========================================================

export interface NodeStatus {
  id: string
  name: string
  displayName: string
  phase: WorkflowPhase
  type: string
  templateName: string
  startedAt?: string
  finishedAt?: string
  duration?: string
  message?: string
  outputs?: Record<string, unknown>
  children?: string[]
  outboundNodes?: string[]
  podName?: string
  hostNodeName?: string
}

// ============================================================
// 编辑器节点类型
// ============================================================

export interface WorkflowNodePort {
  id: string
  label: string
  type: 'source' | 'target'
}

export interface WorkflowNodeMeta {
  type: string
  label: string
  group: NodeGroup
  description: string
  icon: Component
  color: string
  bgColor: string
  defaultImage: string
  defaultCommand: string
  inputs: WorkflowNodePort[]
  outputs: WorkflowNodePort[]
}

export type NodeGroup = 'trigger' | 'task' | 'flow-control' | 'notification' | 'kubernetes'

// ============================================================
// Vue Flow 编辑器类型
// ============================================================

export interface FlowNodeData {
  label: string
  type: string
  meta: WorkflowNodeMeta
  config: {
    image?: string
    command?: string
    args?: string[]
    env?: Record<string, string>
    retry?: RetryStrategy
    timeout?: number
    resources?: Resources
    script?: string
    url?: string
    method?: string
    condition?: string
    message?: string
  }
  phase?: WorkflowPhase
  message?: string
  duration?: string
}

export interface FlowEdgeData {
  label?: string
  condition?: string
  type: 'success' | 'failure' | 'default'
}

// ============================================================
// 分页类型
// ============================================================

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

// ============================================================
// 运行历史
// ============================================================

export interface WorkflowRun {
  id: string
  workflowName: string
  status: WorkflowStatus
  startedAt: string
  finishedAt?: string
  duration?: string
  trigger: TriggerType
  triggeredBy?: string
  parameters?: Parameter[]
  nodeStatus: Record<string, NodeStatus>
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