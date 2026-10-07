import type { LucideIcon } from 'lucide-react'
import {
  Box,
  Container,
  Cpu,
  Database,
  FileJson,
  Globe,
  HardDrive,
  KeyRound,
  Layers,
  Package,
  Search,
  Server,
  Ship,
  Upload,
} from 'lucide-react'

export interface ContainerNavItem {
  key: string
  label: string
  icon: LucideIcon
  /** 不依赖当前集群的固定路径 */
  to?: string
  /** 挂在 `/container/clusters/:id` 后的资源段，例如 `/nodes` */
  suffix?: string
  needsCluster: boolean
}

export const CONTAINER_NAV_GROUPS: { key: string; label: string; items: ContainerNavItem[] }[] = [
  {
    key: 'cluster',
    label: '集群',
    items: [
      { key: 'clusters', label: '集群', icon: Server, to: '/container/clusters', needsCluster: false },
      { key: 'nodes', label: '节点', icon: HardDrive, suffix: '/nodes', needsCluster: true },
    ],
  },
  {
    key: 'workloads',
    label: '工作负载',
    items: [
      { key: 'pods', label: 'Pod', icon: Box, suffix: '/pods', needsCluster: true },
      { key: 'deployments', label: 'Deployment', icon: Layers, suffix: '/deployments', needsCluster: true },
      { key: 'statefulsets', label: 'StatefulSet', icon: Cpu, suffix: '/statefulsets', needsCluster: true },
    ],
  },
  {
    key: 'network',
    label: '网络与配置',
    items: [
      { key: 'services', label: 'Service', icon: Globe, suffix: '/services', needsCluster: true },
      { key: 'configmaps', label: 'ConfigMap', icon: FileJson, suffix: '/configmaps', needsCluster: true },
      { key: 'secrets', label: 'Secret', icon: KeyRound, suffix: '/secrets', needsCluster: true },
    ],
  },
  {
    key: 'storage',
    label: '存储',
    items: [
      { key: 'pvcs', label: 'PVC', icon: HardDrive, suffix: '/pvcs', needsCluster: true },
      { key: 'storageclasses', label: 'StorageClass', icon: Database, suffix: '/storageclasses', needsCluster: true },
    ],
  },
  {
    key: 'images',
    label: '镜像',
    items: [
      { key: 'registries', label: '镜像仓库', icon: Container, to: '/container/registries', needsCluster: false },
      { key: 'images', label: '镜像', icon: Search, to: '/container/images', needsCluster: false },
    ],
  },
  {
    key: 'helm',
    label: '应用发布',
    items: [
      { key: 'helm-releases', label: 'Helm Release', icon: Ship, to: '/container/helm/releases', needsCluster: false },
      { key: 'helm-charts', label: 'Chart 目录', icon: Package, to: '/container/helm/charts', needsCluster: false },
      { key: 'helm-upload', label: '上传 Chart', icon: Upload, to: '/container/helm/upload', needsCluster: false },
    ],
  },
]

export const CONTAINER_NAV: ContainerNavItem[] = CONTAINER_NAV_GROUPS.flatMap((group) => group.items)

const RESOURCE_LABELS = Object.fromEntries(
  CONTAINER_NAV.filter((item) => item.suffix).map((item) => [item.suffix!.replace(/^\//, ''), item.label]),
) as Record<string, string>

export function clusterIdFromPath(pathname: string): string | null {
  const match = pathname.match(/^\/container\/clusters\/([^/]+)/)
  return match?.[1] ?? null
}

export function containerNavHref(item: ContainerNavItem, clusterId: string | null): string | null {
  if (item.to) return item.to
  if (!clusterId || !item.suffix) return null
  return `/container/clusters/${clusterId}${item.suffix}`
}

export function isContainerNavActive(pathname: string, item: ContainerNavItem): boolean {
  if (item.key === 'clusters') {
    return pathname === '/container/clusters' || /^\/container\/clusters\/[^/]+$/.test(pathname)
  }
  if (item.to) return pathname === item.to || pathname.startsWith(`${item.to}/`)
  if (!item.suffix) return false
  const segment = item.suffix.replace(/^\//, '')
  return pathname.split('/').includes(segment)
}

export interface PathCrumb {
  label: string
  to?: string
}

/** 容器模块面包屑。最后一项不带 to，表示当前页。 */
export function containerBreadcrumbs(pathname: string): PathCrumb[] {
  const root: PathCrumb = { label: '容器管理', to: '/container/clusters' }
  if (pathname === '/container' || pathname === '/container/clusters') {
    return [{ label: '容器管理' }, { label: '集群' }]
  }
  if (pathname === '/container/registries' || pathname.startsWith('/container/registries/')) {
    return [root, { label: '镜像仓库' }]
  }
  if (pathname === '/container/images' || pathname.startsWith('/container/images/')) {
    return [root, { label: '镜像' }]
  }
  if (pathname === '/container/helm/upload') {
    return [root, { label: '上传 Chart' }]
  }
  if (pathname === '/container/helm/charts' || pathname.startsWith('/container/helm/charts/')) {
    const parts = pathname.split('/').filter(Boolean)
    if (parts.length <= 3) return [root, { label: 'Chart 目录' }]
    const name = decodeURIComponent(parts[parts.length - 1] ?? '')
    return [root, { label: 'Chart 目录', to: '/container/helm/charts' }, { label: name || '详情' }]
  }
  if (pathname === '/container/helm/releases' || pathname.startsWith('/container/helm/releases/')) {
    const parts = pathname.split('/').filter(Boolean)
    if (parts.length <= 3) return [root, { label: 'Helm Release' }]
    const name = decodeURIComponent(parts[parts.length - 1] ?? '')
    return [root, { label: 'Helm Release', to: '/container/helm/releases' }, { label: name || '详情' }]
  }

  const parts = pathname.split('/').filter(Boolean)
  if (parts[0] !== 'container' || parts[1] !== 'clusters' || !parts[2]) {
    return [{ label: '容器管理' }]
  }

  const resource = parts[3]
  if (!resource) {
    return [root, { label: '集群', to: '/container/clusters' }, { label: '详情' }]
  }

  const label = RESOURCE_LABELS[resource] ?? resource
  const list = `/container/clusters/${parts[2]}/${resource}`
  if (parts.length <= 4) return [root, { label }]
  const name = decodeURIComponent(parts[parts.length - 1] ?? '')
  return [root, { label, to: list }, { label: name || '详情' }]
}
