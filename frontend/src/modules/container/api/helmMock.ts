import { ApiError } from '@/shared/errors/apiError'
import { ResultCode } from '@/shared/errors/resultCode'
import type { HelmClient } from '@/modules/container/api/helmClient'
import type {
  HelmChartArtifact,
  HelmChartDetail,
  HelmChartSummary,
  HelmChartVersion,
  HelmDryRunResult,
  HelmInstallRequest,
  HelmRelease,
  HelmReleaseHistoryItem,
  HelmReleaseResource,
  HelmRepository,
  HelmUpgradeRequest,
} from '@/modules/container/types/helm'

interface ArtifactRecord extends HelmChartArtifact {
  description?: string
  keywords: string[]
  readme: string
  valuesYaml: string
}

const NGINX_VALUES = `replicaCount: 1
service:
  type: ClusterIP
  port: 80
image:
  repository: nginx
  tag: stable
`

const REDIS_VALUES = `replicaCount: 1
auth:
  enabled: false
`

const NGINX_README = `示例 Chart，用于在后端 Helm API 落地前走通目录、安装和升级。

上传 .tgz 后由仓库保存制品，安装始终引用 chartRef，而不是本地文件。
`

function nowIso() {
  return new Date().toISOString()
}

function parseResources(manifest: string, namespace: string): HelmReleaseResource[] {
  return manifest
    .split(/^---$/m)
    .map((doc) => doc.trim())
    .filter(Boolean)
    .map((doc) => {
      const apiVersion = doc.match(/^apiVersion:\s*(.+)$/m)?.[1]?.trim() ?? 'v1'
      const kind = doc.match(/^kind:\s*(.+)$/m)?.[1]?.trim() ?? 'Resource'
      const name = doc.match(/^metadata:[\s\S]*?^\s*name:\s*(.+)$/m)?.[1]?.trim() ?? 'unnamed'
      return { apiVersion, kind, namespace, name, status: 'ready' }
    })
}

function renderManifest(name: string, namespace: string, chartName: string): string {
  return `apiVersion: v1
kind: Service
metadata:
  name: ${name}
  namespace: ${namespace}
  labels:
    app.kubernetes.io/instance: ${name}
    app.kubernetes.io/name: ${chartName}
spec:
  type: ClusterIP
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: ${name}
  namespace: ${namespace}
  labels:
    app.kubernetes.io/instance: ${name}
spec:
  replicas: 1
`
}

function parseUploadName(filename: string): { chartName: string; version: string } {
  const base = filename.replace(/\.tgz$/i, '')
  const match = base.match(/^(.*)-(\d+\.\d+\.\d+[\w.+-]*)$/)
  if (match?.[1] && match[2]) return { chartName: match[1], version: match[2] }
  return { chartName: base || 'chart', version: '0.1.0' }
}

function seedArtifacts(repo: HelmRepository): ArtifactRecord[] {
  const nginxLatest: ArtifactRecord = {
    id: 'art-nginx-120',
    repositoryId: repo.id,
    repositoryName: repo.name,
    chartName: 'nginx',
    version: '1.2.0',
    appVersion: '1.27.0',
    description: '示例 Web 服务 Chart',
    digest: 'sha256:nginx120',
    sizeBytes: 18432,
    chartRef: `${repo.url}/nginx:1.2.0`,
    keywords: ['web', 'nginx'],
    readme: NGINX_README,
    valuesYaml: NGINX_VALUES,
    createdAt: '2026-10-01T08:00:00.000Z',
  }
  const nginxOld: ArtifactRecord = {
    ...nginxLatest,
    id: 'art-nginx-110',
    version: '1.1.0',
    appVersion: '1.25.0',
    digest: 'sha256:nginx110',
    chartRef: `${repo.url}/nginx:1.1.0`,
    valuesYaml: NGINX_VALUES.replace('tag: stable', 'tag: "1.25"'),
    createdAt: '2026-09-12T08:00:00.000Z',
  }
  const redis: ArtifactRecord = {
    id: 'art-redis-080',
    repositoryId: repo.id,
    repositoryName: repo.name,
    chartName: 'redis',
    version: '0.8.0',
    appVersion: '7.2.0',
    description: '示例缓存 Chart',
    digest: 'sha256:redis080',
    sizeBytes: 22016,
    chartRef: `${repo.url}/redis:0.8.0`,
    keywords: ['cache', 'redis'],
    readme: '示例 Redis Chart。自定义 Values 与默认 Values 分栏编辑后再试运行。\n',
    valuesYaml: REDIS_VALUES,
    createdAt: '2026-09-20T08:00:00.000Z',
  }
  return [nginxLatest, nginxOld, redis]
}

function seedRelease(clusterId: string, artifact: ArtifactRecord): HelmRelease {
  const valuesYaml = 'replicaCount: 2\nservice:\n  type: ClusterIP\n  port: 80\n'
  const manifest = renderManifest('demo-nginx', 'default', 'nginx')
  const history: HelmReleaseHistoryItem[] = [
    {
      revision: 1,
      chartName: 'nginx',
      chartVersion: '1.1.0',
      appVersion: '1.25.0',
      status: 'superseded',
      description: 'Install',
      updatedAt: '2026-09-18T02:00:00.000Z',
      valuesYaml: 'replicaCount: 1\n',
    },
    {
      revision: 2,
      chartName: artifact.chartName,
      chartVersion: artifact.version,
      appVersion: artifact.appVersion,
      status: 'deployed',
      description: 'Upgrade',
      updatedAt: '2026-10-02T03:30:00.000Z',
      valuesYaml,
    },
  ]
  return {
    clusterId,
    namespace: 'default',
    name: 'demo-nginx',
    chartName: artifact.chartName,
    chartVersion: artifact.version,
    appVersion: artifact.appVersion,
    chartRef: artifact.chartRef,
    repositoryId: artifact.repositoryId,
    artifactId: artifact.id,
    status: 'deployed',
    revision: 2,
    valuesYaml,
    notes: '示例 Release。升级会新开一个 revision，回滚同样追加 revision。',
    manifest,
    resources: parseResources(manifest, 'default'),
    history,
    updatedAt: '2026-10-02T03:30:00.000Z',
  }
}

function releaseKey(clusterId: string, namespace: string, name: string) {
  return `${clusterId}/${namespace}/${name}`
}

function toSummary(records: ArtifactRecord[]): HelmChartSummary[] {
  const groups = new Map<string, ArtifactRecord[]>()
  records.forEach((item) => {
    const key = `${item.repositoryId}/${item.chartName}`
    const list = groups.get(key) ?? []
    list.push(item)
    groups.set(key, list)
  })
  return Array.from(groups.values()).map((versions) => {
    const sorted = [...versions].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
    const latest = sorted[0]
    return {
      repositoryId: latest.repositoryId,
      repositoryName: latest.repositoryName,
      chartName: latest.chartName,
      description: latest.description,
      latestVersion: latest.version,
      appVersion: latest.appVersion,
      versionCount: versions.length,
      updatedAt: latest.createdAt,
    }
  })
}

function toVersion(record: ArtifactRecord): HelmChartVersion {
  return {
    artifactId: record.id,
    version: record.version,
    appVersion: record.appVersion,
    chartRef: record.chartRef,
    digest: record.digest,
    createdAt: record.createdAt,
  }
}

export interface HelmMockStore extends HelmClient {
  reset: () => void
}

export function createHelmMockStore(): HelmMockStore {
  const repo: HelmRepository = {
    id: 'repo-harbor',
    name: 'Harbor Charts',
    type: 'oci',
    url: 'oci://harbor.local/charts',
    createdAt: '2026-09-01T00:00:00.000Z',
  }
  let artifacts: ArtifactRecord[] = []
  let releases: HelmRelease[] = []
  let initializedClusters = new Set<string>()

  const reset = () => {
    artifacts = seedArtifacts(repo)
    releases = [seedRelease('demo-cluster', artifacts[0])]
    initializedClusters = new Set(['demo-cluster'])
  }
  reset()

  function findArtifact(repositoryId: string, name: string, version?: string) {
    const matches = artifacts.filter((item) => item.repositoryId === repositoryId && item.chartName === name)
    if (!version) {
      return [...matches].sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))[0]
    }
    return matches.find((item) => item.version === version)
  }

  function requireArtifact(repositoryId: string, name: string, version?: string) {
    const found = findArtifact(repositoryId, name, version)
    if (!found) throw new ApiError(ResultCode.NOT_FOUND, 'Chart 不存在')
    return found
  }

  function requireRelease(clusterId: string, namespace: string, name: string) {
    const found = releases.find(
      (item) => item.clusterId === clusterId && item.namespace === namespace && item.name === name,
    )
    if (!found) throw new ApiError(ResultCode.NOT_FOUND, 'Release 不存在')
    return found
  }

  function resolveInstallArtifact(body: HelmInstallRequest) {
    if (body.artifactId) {
      const found = artifacts.find((item) => item.id === body.artifactId)
      if (!found) throw new ApiError(ResultCode.NOT_FOUND, 'Chart 制品不存在')
      return found
    }
    if (body.chartRef) {
      const found = artifacts.find((item) => item.chartRef === body.chartRef)
      if (!found) throw new ApiError(ResultCode.NOT_FOUND, 'chartRef 不在仓库中')
      return found
    }
    throw new ApiError(ResultCode.BAD_REQUEST, '安装需要 chartRef 或 artifactId')
  }

  function dryRun(name: string, namespace: string, chartName: string): HelmDryRunResult {
    const manifest = renderManifest(name, namespace, chartName)
    return { manifest, resources: parseResources(manifest, namespace) }
  }

  const client: HelmMockStore = {
    reset,
    async listRepositories() {
      return [repo]
    },
    async listCharts(query) {
      const keyword = query?.name?.trim().toLowerCase()
      return toSummary(artifacts).filter((item) => {
        if (query?.repositoryId && item.repositoryId !== query.repositoryId) return false
        if (!keyword) return true
        return (
          item.chartName.toLowerCase().includes(keyword) ||
          (item.description ?? '').toLowerCase().includes(keyword) ||
          item.repositoryName.toLowerCase().includes(keyword)
        )
      })
    },
    async getChart(repositoryId, name, version) {
      const selected = requireArtifact(repositoryId, name, version)
      const versions = artifacts
        .filter((item) => item.repositoryId === repositoryId && item.chartName === name)
        .sort((a, b) => (a.createdAt < b.createdAt ? 1 : -1))
        .map(toVersion)
      const detail: HelmChartDetail = {
        repositoryId: selected.repositoryId,
        repositoryName: selected.repositoryName,
        chartName: selected.chartName,
        description: selected.description,
        version: selected.version,
        appVersion: selected.appVersion,
        chartRef: selected.chartRef,
        artifactId: selected.id,
        keywords: selected.keywords,
        readme: selected.readme,
        versions,
      }
      return detail
    },
    async getValues(repositoryId, name, version) {
      return { valuesYaml: requireArtifact(repositoryId, name, version).valuesYaml }
    },
    async uploadChart(file, repositoryId) {
      if (repositoryId !== repo.id) throw new ApiError(ResultCode.NOT_FOUND, '仓库不存在')
      if (!file.name.toLowerCase().endsWith('.tgz')) {
        throw new ApiError(ResultCode.FILE_INVALID, '只接受 .tgz Helm chart 包')
      }
      const parsed = parseUploadName(file.name)
      const clash = artifacts.find(
        (item) =>
          item.repositoryId === repositoryId && item.chartName === parsed.chartName && item.version === parsed.version,
      )
      if (clash) {
        throw new ApiError(ResultCode.DUPLICATE, `同仓库已存在 ${parsed.chartName}@${parsed.version}，禁止覆盖`)
      }
      const createdAt = nowIso()
      const record: ArtifactRecord = {
        id: `art-${parsed.chartName}-${parsed.version}`.replace(/[^a-zA-Z0-9-]+/g, '-'),
        repositoryId: repo.id,
        repositoryName: repo.name,
        chartName: parsed.chartName,
        version: parsed.version,
        description: '本地上传的 Chart（示例会话，未推送 Harbor）',
        sizeBytes: file.size,
        chartRef: `${repo.url}/${parsed.chartName}:${parsed.version}`,
        keywords: ['upload'],
        readme: `${parsed.chartName} ${parsed.version}\n\n由上传向导写入当前会话。后端接入后，同一请求会校验 Chart.yaml 并 helm push 到 Harbor OCI。\n`,
        valuesYaml: 'replicaCount: 1\n',
        createdAt,
      }
      artifacts = [record, ...artifacts]
      return record
    },
    async listReleases(clusterId, namespace) {
      if (!initializedClusters.has(clusterId)) {
        initializedClusters.add(clusterId)
        const nginx = artifacts.find((item) => item.chartName === 'nginx' && item.version === '1.2.0')
        if (nginx) releases = [...releases, seedRelease(clusterId, nginx)]
      }
      const scoped = releases.filter((item) => item.clusterId === clusterId)
      return namespace ? scoped.filter((item) => item.namespace === namespace) : scoped
    },
    async getRelease(clusterId, namespace, name) {
      await client.listReleases(clusterId)
      return requireRelease(clusterId, namespace, name)
    },
    async dryRunInstall(clusterId, namespace, body) {
      if (!clusterId) throw new ApiError(ResultCode.BAD_REQUEST, '请先选择集群')
      const artifact = resolveInstallArtifact(body)
      return dryRun(body.name, namespace, artifact.chartName)
    },
    async install(clusterId, namespace, body) {
      const artifact = resolveInstallArtifact(body)
      const existing = releases.find(
        (item) => item.clusterId === clusterId && item.namespace === namespace && item.name === body.name,
      )
      if (existing) throw new ApiError(ResultCode.DUPLICATE, `Release ${namespace}/${body.name} 已存在`)
      const createdAt = nowIso()
      const preview = dryRun(body.name, namespace, artifact.chartName)
      const release: HelmRelease = {
        clusterId,
        namespace,
        name: body.name,
        chartName: artifact.chartName,
        chartVersion: artifact.version,
        appVersion: artifact.appVersion,
        chartRef: artifact.chartRef,
        repositoryId: artifact.repositoryId,
        artifactId: artifact.id,
        status: 'deployed',
        revision: 1,
        valuesYaml: body.valuesYaml,
        notes: body.createNamespace ? '已请求创建命名空间' : undefined,
        manifest: preview.manifest,
        resources: preview.resources,
        history: [
          {
            revision: 1,
            chartName: artifact.chartName,
            chartVersion: artifact.version,
            appVersion: artifact.appVersion,
            status: 'deployed',
            description: 'Install',
            updatedAt: createdAt,
            valuesYaml: body.valuesYaml,
          },
        ],
        updatedAt: createdAt,
      }
      releases = [...releases, release]
      return release
    },
    async dryRunUpgrade(clusterId, namespace, name, body) {
      const current = requireRelease(clusterId, namespace, name)
      return dryRun(current.name, current.namespace, current.chartName)
    },
    async upgrade(clusterId, namespace, name, body) {
      const current = requireRelease(clusterId, namespace, name)
      const artifact =
        (body.artifactId && artifacts.find((item) => item.id === body.artifactId)) ||
        (body.chartRef && artifacts.find((item) => item.chartRef === body.chartRef)) ||
        artifacts.find(
          (item) => item.chartName === current.chartName && item.version === (body.version || current.chartVersion),
        )
      if (!artifact) throw new ApiError(ResultCode.NOT_FOUND, '目标 Chart 版本不存在')
      const createdAt = nowIso()
      const revision = current.revision + 1
      const valuesYaml = body.valuesYaml
      const preview = dryRun(current.name, current.namespace, artifact.chartName)
      const next: HelmRelease = {
        ...current,
        chartName: artifact.chartName,
        chartVersion: artifact.version,
        appVersion: artifact.appVersion,
        chartRef: artifact.chartRef,
        artifactId: artifact.id,
        repositoryId: artifact.repositoryId,
        status: 'deployed',
        revision,
        valuesYaml,
        manifest: preview.manifest,
        resources: preview.resources,
        updatedAt: createdAt,
        notes: body.rollbackOnFailure ? '失败时回滚已打开（示例会话不会真实失败）' : current.notes,
        history: [
          ...current.history.map((item) => (item.status === 'deployed' ? { ...item, status: 'superseded' } : item)),
          {
            revision,
            chartName: artifact.chartName,
            chartVersion: artifact.version,
            appVersion: artifact.appVersion,
            status: 'deployed',
            description: body.valuesStrategy === 'reset' ? 'Upgrade reset values' : 'Upgrade',
            updatedAt: createdAt,
            valuesYaml,
          },
        ],
      }
      releases = releases.map((item) => (releaseKey(item.clusterId, item.namespace, item.name) === releaseKey(clusterId, namespace, name) ? next : item))
      return next
    },
    async rollback(clusterId, namespace, name, body) {
      const current = requireRelease(clusterId, namespace, name)
      const target = current.history.find((item) => item.revision === body.revision)
      if (!target) throw new ApiError(ResultCode.NOT_FOUND, 'Revision 不存在')
      const createdAt = nowIso()
      const revision = current.revision + 1
      const next: HelmRelease = {
        ...current,
        chartVersion: target.chartVersion,
        appVersion: target.appVersion,
        status: 'deployed',
        revision,
        valuesYaml: target.valuesYaml,
        updatedAt: createdAt,
        history: [
          ...current.history.map((item) => (item.status === 'deployed' ? { ...item, status: 'superseded' } : item)),
          {
            ...target,
            revision,
            status: 'deployed',
            description: `Rollback to ${body.revision}`,
            updatedAt: createdAt,
          },
        ],
      }
      releases = releases.map((item) => (releaseKey(item.clusterId, item.namespace, item.name) === releaseKey(clusterId, namespace, name) ? next : item))
      return next
    },
    async uninstall(clusterId, namespace, name) {
      const before = releases.length
      releases = releases.filter(
        (item) => !(item.clusterId === clusterId && item.namespace === namespace && item.name === name),
      )
      if (releases.length === before) throw new ApiError(ResultCode.NOT_FOUND, 'Release 不存在')
    },
  }

  return client
}

export const helmMock = createHelmMockStore()
