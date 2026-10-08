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
 * 真实路径。Chart 不绑集群：`/container/helm/*`。
 * Release 绑集群：`/container/clusters/{clusterId}/helm/releases/*`。
 * 超时对齐后端：查询 60s，安装/升级/回滚/卸载 180s，`--wait` / `--atomic` 600s，
 * 再留出详情回读时间。未缓存 Chart 的详情和默认 Values 可能触发 helm pull（推仓超时 120s）。
 */

const QUERY_MS = 60_000
const ACTION_MS = 180_000
const WAIT_MS = 600_000
const DESCRIBE_MS = 120_000
const CHART_CONTENT_MS = 300_000

function actionTimeout(wait?: boolean, rollbackOnFailure?: boolean) {
  return (wait || rollbackOnFailure ? WAIT_MS : ACTION_MS) + DESCRIBE_MS
}

function seg(value: string) {
  return encodeURIComponent(value)
}

function releaseBase(clusterId: string, namespace: string) {
  return `/container/clusters/${seg(clusterId)}/helm/releases/${seg(namespace)}`
}

export const helmHttp: HelmClient = {
  listRepositories: () => get<HelmRepository[]>('/container/helm/repositories', undefined, QUERY_MS),
  listCharts: (query) => get<HelmChartSummary[]>('/container/helm/charts', query, QUERY_MS),
  getChart: (repositoryId, name, version) =>
    get<HelmChartDetail>(`/container/helm/charts/${seg(name)}`, { repositoryId, version }, CHART_CONTENT_MS),
  getValues: (repositoryId, name, version) =>
    get<HelmValuesResponse>(
      `/container/helm/charts/${seg(name)}/versions/${seg(version)}/values`,
      { repositoryId },
      CHART_CONTENT_MS,
    ),
  uploadChart: (file, repositoryId) => {
    const form = new FormData()
    form.append('file', file)
    if (repositoryId) form.append('repositoryId', repositoryId)
    return postFormData<HelmChartArtifact>('/container/helm/charts/upload', form, 120_000)
  },
  listReleases: (clusterId, namespace) =>
    get<HelmRelease[]>(
      `/container/clusters/${seg(clusterId)}/helm/releases`,
      namespace ? { namespace } : undefined,
      QUERY_MS,
    ),
  getRelease: (clusterId, namespace, name) =>
    get<HelmRelease>(`${releaseBase(clusterId, namespace)}/${seg(name)}`, undefined, ACTION_MS),
  dryRunInstall: (clusterId, namespace, body) =>
    post<HelmDryRunResult>(`${releaseBase(clusterId, namespace)}/dry-run`, body, ACTION_MS),
  install: (clusterId, namespace, body) =>
    post<HelmRelease>(releaseBase(clusterId, namespace), body, actionTimeout(body.wait)),
  dryRunUpgrade: (clusterId, namespace, name, body) =>
    put<HelmDryRunResult>(`${releaseBase(clusterId, namespace)}/${seg(name)}/upgrade/dry-run`, body, ACTION_MS),
  upgrade: (clusterId, namespace, name, body) =>
    put<HelmRelease>(
      `${releaseBase(clusterId, namespace)}/${seg(name)}/upgrade`,
      body,
      actionTimeout(body.wait, body.rollbackOnFailure),
    ),
  rollback: (clusterId, namespace, name, body) =>
    put<HelmRelease>(
      `${releaseBase(clusterId, namespace)}/${seg(name)}/rollback`,
      body satisfies HelmRollbackRequest,
      actionTimeout(false),
    ),
  uninstall: (clusterId, namespace, name) => del<void>(`${releaseBase(clusterId, namespace)}/${seg(name)}`, ACTION_MS),
}
