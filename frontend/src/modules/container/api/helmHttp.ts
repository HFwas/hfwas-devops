import { del, get, post, postFormData, put } from '@/shared/api/request'
import type { HelmClient } from '@/modules/container/api/helmClient'
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

/**
 * 前端约定的真实路径。容器产品内：
 * - Chart 仓库与制品：`/container/helm/*`（不绑集群）
 * - Release：`/container/clusters/{clusterId}/helm/releases/*`
 *
 * 已落地的后端只有仓库、列表、版本和上传。详情仍走 `GET /charts/{name}`，
 * values 仍走 `.../values`；这两处以及全部 Release 接口后端还没有。
 * 字段差（`name`/`chartName`、`size`/`sizeBytes`、`repositoryName`）见 docs/frontend/helm-ui.md。
 */

function seg(value: string) {
  return encodeURIComponent(value)
}

function releaseBase(clusterId: string, namespace: string) {
  return `/container/clusters/${seg(clusterId)}/helm/releases/${seg(namespace)}`
}

export const helmHttp: HelmClient = {
  listRepositories: () => get<HelmRepository[]>('/container/helm/repositories'),
  listCharts: (query) => get<HelmChartSummary[]>('/container/helm/charts', query),
  getChart: (repositoryId, name, version) =>
    get<HelmChartDetail>(`/container/helm/charts/${seg(name)}`, { repositoryId, version }),
  getValues: (repositoryId, name, version) =>
    get<HelmValuesResponse>(`/container/helm/charts/${seg(name)}/versions/${seg(version)}/values`, { repositoryId }),
  uploadChart: (file, repositoryId) => {
    const form = new FormData()
    form.append('file', file)
    form.append('repositoryId', repositoryId)
    return postFormData<HelmChartArtifact>('/container/helm/charts/upload', form, 120_000)
  },
  listReleases: (clusterId, namespace) =>
    get<HelmRelease[]>(`/container/clusters/${seg(clusterId)}/helm/releases`, namespace ? { namespace } : undefined),
  getRelease: (clusterId, namespace, name) => get<HelmRelease>(`${releaseBase(clusterId, namespace)}/${seg(name)}`),
  dryRunInstall: (clusterId, namespace, body) =>
    post<HelmDryRunResult>(`${releaseBase(clusterId, namespace)}/dry-run`, body),
  install: (clusterId, namespace, body) => post<HelmRelease>(releaseBase(clusterId, namespace), body),
  dryRunUpgrade: (clusterId, namespace, name, body) =>
    put<HelmDryRunResult>(`${releaseBase(clusterId, namespace)}/${seg(name)}/upgrade/dry-run`, body),
  upgrade: (clusterId, namespace, name, body) =>
    put<HelmRelease>(`${releaseBase(clusterId, namespace)}/${seg(name)}/upgrade`, body),
  rollback: (clusterId, namespace, name, body) =>
    put<HelmRelease>(`${releaseBase(clusterId, namespace)}/${seg(name)}/rollback`, body satisfies HelmRollbackRequest),
  uninstall: (clusterId, namespace, name) => del<void>(`${releaseBase(clusterId, namespace)}/${seg(name)}`),
}
