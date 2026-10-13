import type { PageResult } from '@/shared/types/common'
import type {
  HelmChartArtifact,
  HelmChartDetail,
  HelmChartSummary,
  HelmDryRunResult,
  HelmInstallRequest,
  HelmRelease,
  HelmRepository,
  HelmRollbackRequest,
  HelmUpgradeRequest,
  HelmValuesResponse,
} from '@/modules/container/types/helm'

export interface HelmClient {
  listRepositories: () => Promise<HelmRepository[]>
  listRepositoriesPage: (params: { page?: number; pageSize?: number; keyword?: string }) => Promise<PageResult<HelmRepository>>
  listCharts: (query?: { repositoryId?: string; name?: string }) => Promise<HelmChartSummary[]>
  getChart: (repositoryId: string, name: string, version?: string) => Promise<HelmChartDetail>
  getValues: (repositoryId: string, name: string, version: string) => Promise<HelmValuesResponse>
  uploadChart: (file: File, repositoryId: string) => Promise<HelmChartArtifact>
  listReleases: (clusterId: string, namespace?: string) => Promise<HelmRelease[]>
  getRelease: (clusterId: string, namespace: string, name: string) => Promise<HelmRelease>
  dryRunInstall: (clusterId: string, namespace: string, body: HelmInstallRequest) => Promise<HelmDryRunResult>
  install: (clusterId: string, namespace: string, body: HelmInstallRequest) => Promise<HelmRelease>
  dryRunUpgrade: (
    clusterId: string,
    namespace: string,
    name: string,
    body: HelmUpgradeRequest,
  ) => Promise<HelmDryRunResult>
  upgrade: (clusterId: string, namespace: string, name: string, body: HelmUpgradeRequest) => Promise<HelmRelease>
  rollback: (clusterId: string, namespace: string, name: string, body: HelmRollbackRequest) => Promise<HelmRelease>
  uninstall: (clusterId: string, namespace: string, name: string) => Promise<void>
}
