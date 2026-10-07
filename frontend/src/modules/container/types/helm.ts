/** Helm chart / release 契约。后端未落地前由 mock 实现同一形状。 */

export interface HelmRepository {
  id: string
  name: string
  type: 'oci'
  url: string
  createdAt: string
}

export interface HelmChartVersion {
  artifactId: string
  version: string
  appVersion?: string
  chartRef: string
  digest?: string
  createdAt: string
}

export interface HelmChartSummary {
  repositoryId: string
  repositoryName: string
  name: string
  description?: string
  latestVersion: string
  appVersion?: string
  versionCount: number
  updatedAt: string
}

export interface HelmChartDetail {
  repositoryId: string
  repositoryName: string
  name: string
  description?: string
  version: string
  appVersion?: string
  chartRef: string
  artifactId: string
  keywords: string[]
  readme: string
  versions: HelmChartVersion[]
}

export interface HelmChartArtifact {
  id: string
  repositoryId: string
  repositoryName: string
  chartName: string
  version: string
  appVersion?: string
  description?: string
  digest?: string
  size?: number
  chartRef: string
  createdAt: string
}

export interface HelmValuesResponse {
  valuesYaml: string
}

export interface HelmReleaseResource {
  apiVersion: string
  kind: string
  namespace?: string
  name: string
  status?: string
}

export interface HelmReleaseHistoryItem {
  revision: number
  chartName: string
  chartVersion: string
  appVersion?: string
  status: string
  description: string
  updatedAt: string
  valuesYaml: string
}

export interface HelmRelease {
  clusterId: string
  namespace: string
  name: string
  chartName: string
  chartVersion: string
  appVersion?: string
  chartRef: string
  repositoryId?: string
  artifactId?: string
  status: string
  revision: number
  valuesYaml: string
  notes?: string
  manifest: string
  resources: HelmReleaseResource[]
  history: HelmReleaseHistoryItem[]
  updatedAt: string
}

/** `valuesYaml` 对应方案里的 values，前端以 YAML 文本提交，由后端解析。 */
export interface HelmInstallRequest {
  name: string
  chartRef?: string
  artifactId?: string
  valuesYaml: string
  createNamespace?: boolean
  wait?: boolean
}

export interface HelmUpgradeRequest {
  chartRef?: string
  artifactId?: string
  version?: string
  valuesYaml: string
  valuesStrategy: 'keep' | 'reset'
  wait?: boolean
  rollbackOnFailure?: boolean
}

export interface HelmRollbackRequest {
  revision: number
}

export interface HelmDryRunResult {
  manifest: string
  resources: HelmReleaseResource[]
}
