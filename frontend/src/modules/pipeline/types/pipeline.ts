export type EntityId = number | string

export type JobKind =
  | 'CLONE'
  | 'LINT_SEMGREP'
  | 'LINT_SONAR'
  | 'BUILD'
  | 'TEST'
  | 'SCAN'
  | 'PACKAGE'
  | 'CUSTOM'
  | 'IMAGE'
  | 'PUBLISH'
  | 'UPLOAD'
  | 'DEPLOY'
  | 'APPROVAL'
  | 'NOTIFY'
  | 'FORMAT'
  | 'DEPENDENCY_ANALYSIS'
  | 'KUBECTL'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'WAITING_APPROVAL' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export type CredentialKind = 'PASSWORD' | 'TOKEN' | 'KUBECONFIG'
export type PipelineStack = 'JAVA_MAVEN' | 'NODE' | 'GO' | 'PYTHON'

export interface ToolchainOption {
  stack: PipelineStack | string
  runtimeVersion: string
  toolVersion?: string | null
  image: string
  buildCommand: string
  testCommand: string
}

export interface PipelineJob {
  id?: EntityId
  name: string
  kind: JobKind | string
  command?: string | null
  stack?: string | null
  runtimeVersion?: string | null
  toolVersion?: string | null
  sortOrder: number
}

export interface PipelineStage {
  id?: EntityId
  name: string
  sortOrder: number
  jobs: PipelineJob[]
}

export interface PipelineSummary {
  id: EntityId
  name: string
  repoUrl: string
  gitRef: string
  credentialId?: EntityId | null
  updateTime?: string | null
  lastRunId?: EntityId | null
  lastRunStatus?: RunStatus | string | null
  lastRunTime?: string | null
  stages?: PipelineStage[]
}

export interface PipelineSavePayload {
  id?: EntityId
  name: string
  repoUrl: string
  gitRef?: string
  credentialId?: EntityId | null
  stages: PipelineStage[]
}

export interface PipelineRunJob {
  id: EntityId
  jobId?: EntityId | null
  stageName: string
  jobName: string
  kind: JobKind | string
  command?: string | null
  status: RunStatus | string
  logText?: string | null
  podName?: string | null
  namespace?: string | null
  containers?: string[] | null
  startedAt?: string | null
  finishedAt?: string | null
}

export interface PipelineRun {
  id: EntityId
  pipelineId: EntityId
  pipelineName: string
  status: RunStatus | string
  trigger: string
  gitRef?: string | null
  commitSha?: string | null
  triggeredByName?: string | null
  stack: string
  runtimeVersion: string
  toolVersion?: string | null
  image: string
  errorMessage?: string | null
  startedAt?: string | null
  finishedAt?: string | null
  jobs: PipelineRunJob[]
}

export interface PipelineCredential {
  id: EntityId
  name: string
  kind: CredentialKind | string
  username?: string | null
  updateTime?: string | null
}

export interface CredentialSavePayload {
  id?: EntityId
  name: string
  kind: CredentialKind | string
  username?: string
  secret?: string
}

export interface EditorJob extends PipelineJob {
  clientKey: string
  status?: string | null
  runJobId?: EntityId
  startedAt?: string | null
  finishedAt?: string | null
}

export interface EditorStage extends Omit<PipelineStage, 'jobs'> {
  clientKey: string
  jobs: EditorJob[]
}

export interface TaskKindVO {
  kindValue: string
  label: string
  taskGroup: string
  description: string
  hint: string
  defaultCommand: string
  requiresCommand: boolean
  enabled: boolean
  sortOrder: number
  toolImage: string
  defaultImage: string
  commandTemplate: string
}

export interface ContainerInfo {
  name: string
  state: 'running' | 'terminated' | 'waiting' | 'unknown'
  exitCode?: number | null
  hasShell: boolean
  recommendedMode: 'exec' | 'ephemeral' | 'debug_pod' | 'unavailable'
  unavailableReason?: string | null
}

export interface PodContainersVO {
  namespace: string
  podName: string
  podExists: 'true' | 'false' | 'unknown'
  workspaceKind: 'pvc' | 'emptydir' | 'unknown'
  workspacePath: string
  containers: ContainerInfo[]
  defaultContainer: string
}
