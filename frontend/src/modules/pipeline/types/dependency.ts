export interface DependencyComponent {
  id: number
  artifactId: number
  runId: number
  purl: string
  groupName?: string | null
  name: string
  version: string
  license?: string | null
  scope?: string | null
  language: string
  pipelineId?: number | null
  pipelineName?: string | null
  repoUrl?: string | null
  createTime: string
}

export interface DependencyComponentAggregate {
  purl: string
  groupName?: string | null
  name: string
  version: string
  license?: string | null
  language: string
  occurrenceCount: number
  usedInRepos: string[]
}

export interface DependencyScanSubmitPayload {
  repoUrl: string
  gitRef?: string
  credentialId?: number | null
  stack?: string
  runtimeVersion?: string
}

export interface BatchDependencyScanSubmitPayload {
  scans: DependencyScanSubmitPayload[]
}

export interface DependencyScanTask {
  pipelineId: number
  pipelineName: string
  runId: number
  repoUrl: string
  gitRef: string
  status: string
  commitSha?: string | null
  triggeredByName?: string | null
  componentCount?: number | null
  startedAt?: string | null
  finishedAt?: string | null
  errorMessage?: string | null
}