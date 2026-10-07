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
  Search,
  Server,
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

export const CONTAINER_NAV: ContainerNavItem[] = [
  { key: 'clusters', label: '集群', icon: Server, to: '/container/clusters', needsCluster: false },
  { key: 'nodes', label: '节点', icon: HardDrive, suffix: '/nodes', needsCluster: true },
  { key: 'registries', label: '镜像仓库', icon: Container, to: '/container/registries', needsCluster: false },
  { key: 'images', label: '镜像', icon: Search, to: '/container/images', needsCluster: false },
  { key: 'pods', label: 'Pod', icon: Box, suffix: '/pods', needsCluster: true },
  { key: 'deployments', label: 'Deployment', icon: Layers, suffix: '/deployments', needsCluster: true },
  { key: 'statefulsets', label: 'StatefulSet', icon: Cpu, suffix: '/statefulsets', needsCluster: true },
  { key: 'services', label: 'Service', icon: Globe, suffix: '/services', needsCluster: true },
  { key: 'configmaps', label: 'ConfigMap', icon: FileJson, suffix: '/configmaps', needsCluster: true },
  { key: 'secrets', label: 'Secret', icon: KeyRound, suffix: '/secrets', needsCluster: true },
  { key: 'pvcs', label: 'PVC', icon: HardDrive, suffix: '/pvcs', needsCluster: true },
  { key: 'storageclasses', label: 'StorageClass', icon: Database, suffix: '/storageclasses', needsCluster: true },
]

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
