export interface RegistryVO {
  id: string
  tenantId: number
  name: string
  alias: string | null
  type: 'harbor' | 'registry_v2'
  url: string
  insecure: boolean
  credentialUsername: string | null
  source: 'manual' | 'builtin'
  clusterId: string | null
  status: 'Connected' | 'Error' | 'Unknown'
  lastError: string | null
  labels: Record<string, string> | null
  createdAt: string
  updatedAt: string
}

export interface RegistrySaveDTO {
  name: string
  alias?: string
  type: 'harbor' | 'registry_v2'
  url: string
  insecure?: boolean
  credentialUsername?: string
  credentialPassword?: string
  clusterId?: string
  labels?: Record<string, string>
}

export interface RegistryUpdateDTO {
  alias?: string
  url?: string
  insecure?: boolean
  credentialUsername?: string
  credentialPassword?: string
  labels?: Record<string, string>
}

export type RegistryStatus = 'Connected' | 'Error' | 'Unknown'

export interface RegistryProjectVO {
  name: string
  repoCount: number
  creationTime: string
  updateTime: string
}

export interface RegistryRepoVO {
  id: number
  projectName: string
  name: string
  artifactCount: number
  pullCount: number
  creationTime: string
  updateTime: string
}

export interface TagVO {
  name: string
  pushTime: string
  pullTime: string
  immutable: boolean
}

export interface ScanOverviewVO {
  status: string
  severity: string
  totalVulnerabilities: number
  critical: number
  high: number
  medium: number
  low: number
}

export interface ArtifactVO {
  digest: string
  size: string
  tags: TagVO[]
  scanOverview: ScanOverviewVO | null
}

export interface VulnerabilityVO {
  id: string
  packageName: string
  version: string
  fixedVersion: string
  severity: string
  description: string
  links: string[]
}

export interface DeployFromImageRequest {
  clusterId: string
  namespace: string
  name: string
  image: string
  replicas: number
  containerPort?: number
  createPullSecret?: boolean
  pullSecretName?: string
  env?: Array<{ name: string; value: string }>
  resources?: {
    cpu?: string
    memory?: string
  }
}

export interface DeployResultVO {
  clusterId: string
  namespace: string
  deploymentName: string
  pullSecretName: string | null
  serviceName: string | null
}

export interface ImageSearchVO {
  registryId: string
  registryName: string
  registryUrl: string
  projectName: string
  repoName: string
  artifactCount: number
  pullCount: number
  updateTime: string
}