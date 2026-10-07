import type { LucideIcon } from 'lucide-react'
import {
  Activity,
  BookOpen,
  Box,
  Clock,
  Container,
  Cpu,
  Database,
  FileCode,
  FileJson,
  Folder,
  Gauge,
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
    key: 'overview',
    label: '概览',
    items: [
      { key: 'overview', label: '概览', icon: LayoutDashboard, needsCluster: false },
    ],
  },
  {
    key: 'apps',
    label: '应用',
    items: [
      { key: 'helm-releases', label: 'Helm Releases', icon: Package, suffix: '/helm', needsCluster: true },
      { key: 'helm-charts', label: 'Helm Charts', icon: BookOpen, to: '/container/helm/charts', needsCluster: false },
    ],
  },
  {
    key: 'workloads',
    label: '工作负载',
    items: [
      { key: 'pods', label: 'Pod', icon: Box, suffix: '/pods', needsCluster: true },
      { key: 'deployments', label: 'Deployment', icon: Layers, suffix: '/deployments', needsCluster: true },
      { key: 'statefulsets', label: 'StatefulSet', icon: Database, suffix: '/statefulsets', needsCluster: true },
      { key: 'daemonsets', label: 'DaemonSet', icon: Cpu, suffix: '/daemonsets', needsCluster: true },
      { key: 'jobs', label: 'Job', icon: Play, suffix: '/jobs', needsCluster: true },
      { key: 'cronjobs', label: 'CronJob', icon: Clock, suffix: '/cronjobs', needsCluster: true },
    ],
  },
  {
    key: 'network',
    label: '网络',
    items: [
      { key: 'ingresses', label: 'Ingress', icon: Route, suffix: '/ingresses', needsCluster: true },
      { key: 'networkpolicies', label: 'NetworkPolicy', icon: Shield, suffix: '/networkpolicies', needsCluster: true },
      { key: 'services', label: 'Service', icon: Globe, suffix: '/services', needsCluster: true },
    ],
  },
  {
    key: 'storage',
    label: '存储',
    items: [
      { key: 'pvcs', label: 'PVC', icon: HardDrive, suffix: '/pvcs', needsCluster: true },
      { key: 'pvs', label: 'PV', icon: Database, suffix: '/pvs', needsCluster: true },
      { key: 'storageclasses', label: 'StorageClass', icon: Database, suffix: '/storageclasses', needsCluster: true },
    ],
  },
  {
    key: 'config',
    label: '配置',
    items: [
      { key: 'configmaps', label: 'ConfigMap', icon: FileJson, suffix: '/configmaps', needsCluster: true },
      { key: 'secrets', label: 'Secret', icon: KeyRound, suffix: '/secrets', needsCluster: true },
      { key: 'hpa', label: 'HPA', icon: Gauge, suffix: '/hpa', needsCluster: true },
      { key: 'pdb', label: 'PDB', icon: LifeBuoy, suffix: '/pdb', needsCluster: true },
    ],
  },
  {
    key: 'security',
    label: '安全',
    items: [
      { key: 'serviceaccounts', label: 'ServiceAccount', icon: User, suffix: '/serviceaccounts', needsCluster: true },
      { key: 'roles', label: 'Role', icon: Shield, suffix: '/roles', needsCluster: true },
      { key: 'rolebindings', label: 'RoleBinding', icon: Link, suffix: '/rolebindings', needsCluster: true },
      { key: 'clusterroles', label: 'ClusterRole', icon: Shield, suffix: '/clusterroles', needsCluster: true },
      { key: 'clusterrolebindings', label: 'ClusterRoleBinding', icon: Link, suffix: '/clusterrolebindings', needsCluster: true },
    ],
  },
  {
    key: 'cluster',
    label: '集群',
    items: [
      { key: 'namespaces', label: 'Namespace', icon: Folder, suffix: '/namespaces', needsCluster: true },
      { key: 'nodes', label: 'Node', icon: HardDrive, suffix: '/nodes', needsCluster: true },
      { key: 'events', label: 'Event', icon: Activity, suffix: '/events', needsCluster: true },
      { key: 'crds', label: 'CRD', icon: FileCode, suffix: '/crds', needsCluster: true },
    ],
  },
  {
    key: 'platform',
    label: '平台',
    items: [
      { key: 'clusters', label: '集群管理', icon: Server, to: '/container/clusters', needsCluster: false },
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

export function clusterIdFromPath(pathname: string): string | null {
  const match = pathname.match(/^\/container\/clusters\/([^/]+)/)
  return match?.[1] ?? null
}

export function containerNavHref(item: ContainerNavItem, clusterId: string | null): string | null {
  if (item.key === 'overview') {
    return clusterId ? `/container/clusters/${clusterId}` : '/container/clusters'
  }
  if (item.to) return item.to
  if (!clusterId || !item.suffix) return null
  return `/container/clusters/${clusterId}${item.suffix}`
}

export function isContainerNavActive(pathname: string, item: ContainerNavItem): boolean {
  if (item.key === 'overview') {
    return /^\/container\/clusters\/[^/]+$/.test(pathname)
  }
  if (item.key === 'clusters') {
    return pathname === '/container/clusters'
  }
  if (item.to) return pathname === item.to || pathname.startsWith(`${item.to}/`)
  if (!item.suffix) return false
  const segment = item.suffix.replace(/^\//, '')
  const match = pathname.match(/^\/container\/clusters\/[^/]+\/(.+)$/)
  if (!match?.[1]) return false
  const rest = match[1]
  return rest === segment || rest.startsWith(`${segment}/`)
}

export interface PathCrumb {
  label: string
  to?: string
}

function resourceMatch(rest: string): { label: string; suffix: string } | undefined {
  const ranked = CONTAINER_NAV.filter((item) => item.suffix)
    .map((item) => ({ label: item.label, suffix: item.suffix!.replace(/^\//, '') }))
    .sort((a, b) => b.suffix.length - a.suffix.length)
  return ranked.find((item) => rest === item.suffix || rest.startsWith(`${item.suffix}/`))
}

/** 容器模块面包屑。最后一项不带 to，表示当前页。 */
export function containerBreadcrumbs(pathname: string): PathCrumb[] {
  const root: PathCrumb = { label: '容器管理', to: '/container/clusters' }
  if (pathname === '/container' || pathname === '/container/clusters') {
    return [{ label: '容器管理' }, { label: '集群管理' }]
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

  const rest = parts.slice(3).join('/')
  if (!rest) {
    return [root, { label: '概览' }]
  }

  const matched = resourceMatch(rest)
  const label = matched?.label ?? parts[3] ?? '详情'
  const suffix = matched?.suffix ?? parts[3] ?? ''
  const list = `/container/clusters/${parts[2]}/${suffix}`
  const extra = (matched ? rest.slice(suffix.length) : `/${parts.slice(4).join('/')}`).split('/').filter(Boolean)
  if (extra.length === 0) return [root, { label }]
  const name = decodeURIComponent(extra[extra.length - 1] ?? '')
  return [root, { label, to: list }, { label: name || '详情' }]
}
