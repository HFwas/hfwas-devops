export type EntityId = number | string

export type JobKind = 'CLONE' | 'BUILD' | 'TEST' | 'CUSTOM'
export type RunStatus = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED'
export type CredentialKind = 'PASSWORD' | 'TOKEN'
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
  stack: string
  runtimeVersion: string
  toolVersion?: string | null
  updateTime?: string | null
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
  stack: string
  runtimeVersion: string
  toolVersion?: string | null
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
}

export interface EditorStage extends Omit<PipelineStage, 'jobs'> {
  clientKey: string
  jobs: EditorJob[]
}
