import { useEffect } from 'react'
import { useQuery } from '@tanstack/react-query'
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
import { NavLink, Outlet, useMatch, useNavigate } from 'react-router'
import { cn } from '@/lib/utils'
import { clusterApi } from '@/modules/container/api/cluster'
import { namespaceApi } from '@/modules/container/api/namespace'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { StatusBadge } from '@/modules/container/components/StatusBadge'

const menu = [
  { key: 'clusters', label: '集群纳管', icon: Server, to: '/container/clusters', needsCluster: false },
  { key: 'nodes', label: '节点', icon: HardDrive, suffix: '/nodes', needsCluster: true },
  { key: 'registries', label: '镜像仓库', icon: Container, to: '/container/registries', needsCluster: false, section: true },
  { key: 'images', label: '镜像', icon: Search, to: '/container/images', needsCluster: false },
  { key: 'pods', label: 'Pod', icon: Box, suffix: '/pods', needsCluster: true, section: true },
  { key: 'deployments', label: 'Deployment', icon: Layers, suffix: '/deployments', needsCluster: true },
  { key: 'statefulsets', label: 'StatefulSet', icon: Cpu, suffix: '/statefulsets', needsCluster: true },
  { key: 'services', label: 'Service', icon: Globe, suffix: '/services', needsCluster: true },
  { key: 'configmaps', label: 'ConfigMap', icon: FileJson, suffix: '/configmaps', needsCluster: true },
  { key: 'secrets', label: 'Secret', icon: KeyRound, suffix: '/secrets', needsCluster: true },
  { key: 'pvcs', label: 'PVC', icon: HardDrive, suffix: '/pvcs', needsCluster: true },
  { key: 'storageclasses', label: 'StorageClass', icon: Database, suffix: '/storageclasses', needsCluster: true },
]

export function ContainerShell() {
  const navigate = useNavigate()
  const clusterMatch = useMatch('/container/clusters/:clusterId/*')
  const detailMatch = useMatch('/container/clusters/:clusterId')
  const routeClusterId = clusterMatch?.params.clusterId ?? detailMatch?.params.clusterId
  const currentId = useContainerCluster((s) => s.currentId)
  const namespace = useContainerCluster((s) => s.namespace)
  const setCurrentId = useContainerCluster((s) => s.setCurrentId)
  const setNamespace = useContainerCluster((s) => s.setNamespace)
  const pickDefault = useContainerCluster((s) => s.pickDefault)

  const clusters = useQuery({
    queryKey: ['container-clusters'],
    queryFn: () => clusterApi.page({ pageNo: 1, pageSize: 100 }),
  })

  useEffect(() => {
    const records = clusters.data?.records ?? []
    if (routeClusterId) setCurrentId(routeClusterId)
    else pickDefault(records)
  }, [clusters.data, pickDefault, routeClusterId, setCurrentId])

  const current = (clusters.data?.records ?? []).find((item) => item.id === currentId) ?? null
  const namespaces = useQuery({
    queryKey: ['container-namespaces', current?.id],
    queryFn: () => namespaceApi.list(current!.id),
    enabled: !!current?.id,
  })

  return (
    <div className="flex min-h-[calc(100svh-3.5rem)]">
      <aside className="flex w-56 shrink-0 flex-col border-r bg-background">
        <button
          type="button"
          className="border-b px-4 py-3 text-left text-sm font-semibold"
          onClick={() => void navigate('/container/clusters')}
        >
          容器管理
        </button>
        <nav className="flex flex-1 flex-col gap-0.5 overflow-y-auto p-2">
          {menu.map((item) => {
            const Icon = item.icon
            const disabled = item.needsCluster && !current
            const to = item.to ?? (current ? `/container/clusters/${current.id}${item.suffix}` : '/container/clusters')
            const className = cn(
              'flex h-9 items-center gap-2 rounded-md px-2.5 text-sm',
              item.section && 'mt-3',
            )
            if (disabled) {
              return (
                <span key={item.key} className={cn(className, 'cursor-not-allowed opacity-40')}>
                  <Icon className="size-4" />
                  {item.label}
                </span>
              )
            }
            return (
              <NavLink
                key={item.key}
                to={to}
                end={item.key === 'clusters' || item.key === 'images'}
                className={({ isActive }) =>
                  cn(className, isActive ? 'bg-primary/10 font-medium text-primary' : 'hover:bg-muted')
                }
              >
                <Icon className="size-4" />
                {item.label}
              </NavLink>
            )
          })}
        </nav>
        {current && (
          <div className="flex items-center gap-2 border-t px-3 py-3">
            <span className="min-w-0 flex-1 truncate text-xs text-muted-foreground">
              {current.alias || current.name}
            </span>
            <StatusBadge status={current.status} />
          </div>
        )}
      </aside>
      <div className="min-w-0 flex-1">
        <header className="flex h-12 items-center gap-3 border-b bg-background px-4">
          <span className="text-sm font-semibold">容器管理</span>
          {current && <StatusBadge status={current.status} />}
          {current && (
            <select
              className="ml-auto h-8 rounded-md border border-input bg-background px-2 text-sm"
              value={namespace}
              onChange={(event) => setNamespace(event.target.value)}
            >
              <option value="">全部命名空间</option>
              {(namespaces.data ?? []).map((item) => (
                <option key={item.name} value={item.name}>
                  {item.name}
                </option>
              ))}
            </select>
          )}
        </header>
        <div className="p-4">
          <Outlet />
        </div>
      </div>
    </div>
  )
}
