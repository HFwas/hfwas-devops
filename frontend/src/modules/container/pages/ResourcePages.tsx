import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { DataTable, type DataTableColumn } from '@/components/console/DataTable'
import { PageHeader } from '@/components/console/PageHeader'
import { Input } from '@/components/ui/input'
import { configMapApi } from '@/modules/container/api/configmap'
import { deploymentApi } from '@/modules/container/api/deployment'
import { nodeApi } from '@/modules/container/api/node'
import { podApi } from '@/modules/container/api/pod'
import { pvcApi } from '@/modules/container/api/pvc'
import { secretApi } from '@/modules/container/api/secret'
import { serviceApi } from '@/modules/container/api/service'
import { statefulSetApi } from '@/modules/container/api/statefulset'
import { storageClassApi } from '@/modules/container/api/storageClass'
import { useContainerCluster } from '@/modules/container/clusterStore'
import { StatusBadge } from '@/modules/container/components/StatusBadge'
import type { NodeSummary, PodSummary, StorageClassSummary } from '@/modules/container/types/resource'
import { formatBytes } from '@/modules/container/utils/format'

function KeywordInput({
  value,
  onChange,
  label,
}: {
  value: string
  onChange: (value: string) => void
  label: string
}) {
  return (
    <Input
      value={value}
      onChange={(event) => onChange(event.target.value)}
      placeholder="搜索名称"
      aria-label={label}
      className="h-8 w-56"
    />
  )
}

function ResourceList<T>({
  title,
  description,
  keyword,
  onKeyword,
  columns,
  rows,
  getRowId,
  loading,
  error,
}: {
  title: string
  description?: string
  keyword?: string
  onKeyword?: (value: string) => void
  columns: DataTableColumn<T>[]
  rows: T[]
  getRowId: (row: T) => string
  loading: boolean
  error?: string
}) {
  return (
    <div className="flex flex-col gap-4">
      <PageHeader
        title={title}
        description={description}
        actions={onKeyword ? <KeywordInput value={keyword ?? ''} onChange={onKeyword} label={`搜索${title}`} /> : undefined}
      />
      <DataTable columns={columns} data={rows} getRowId={getRowId} loading={loading} error={error} empty={`没有匹配的${title}`} />
    </div>
  )
}

function nameLink(label: ReactNode, to?: string) {
  if (!to) return <span className="font-medium">{label}</span>
  return (
    <Link className="font-medium text-primary hover:underline" to={to}>
      {label}
    </Link>
  )
}

export function NodeListPage() {
  const { clusterId = '' } = useParams()
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['container-nodes', clusterId, keyword],
    queryFn: () => nodeApi.list(clusterId, keyword || undefined),
    enabled: !!clusterId,
  })
  const rows = query.data ?? []

  return (
    <ResourceList<NodeSummary>
      title="节点"
      description={query.isSuccess ? `共 ${rows.length} 个节点` : '查看节点容量与状态'}
      keyword={keyword}
      onKeyword={setKeyword}
      columns={[
        {
          id: 'name',
          header: '名称',
          cell: (node) => nameLink(node.name, `/container/clusters/${clusterId}/nodes/${encodeURIComponent(node.name)}`),
        },
        { id: 'status', header: '状态', cell: (node) => <StatusBadge status={node.status} /> },
        { id: 'role', header: '角色', cell: (node) => node.role },
        { id: 'cpu', header: 'CPU', cell: (node) => String(node.cpuCapacity) },
        { id: 'memory', header: '内存', cell: (node) => formatBytes(node.memoryCapacity) },
        { id: 'pods', header: 'Pod', cell: (node) => String(node.podCount) },
        { id: 'runtime', header: '运行时', cell: (node) => node.containerRuntime || '—' },
      ]}
      rows={rows}
      getRowId={(node) => node.name}
      loading={query.isFetching}
      error={query.isError ? '节点列表加载失败' : undefined}
    />
  )
}

export function PodListPage() {
  const { clusterId = '' } = useParams()
  const namespace = useContainerCluster((s) => s.namespace)
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['container-pods', clusterId, namespace, keyword],
    queryFn: () => podApi.list(clusterId, { namespace: namespace || undefined, keyword: keyword || undefined, pageNo: 1, pageSize: 100 }),
    enabled: !!clusterId,
  })
  const rows = query.data?.records ?? []
  const scope = namespace ? `命名空间 ${namespace}` : '全部命名空间'

  return (
    <ResourceList<PodSummary>
      title="Pod"
      description={query.isSuccess ? `${scope} · 共 ${query.data?.total ?? rows.length} 个` : scope}
      keyword={keyword}
      onKeyword={setKeyword}
      columns={[
        {
          id: 'name',
          header: '名称',
          cell: (pod) =>
            nameLink(pod.name, `/container/clusters/${clusterId}/pods/${pod.namespace}/${encodeURIComponent(pod.name)}`),
        },
        { id: 'namespace', header: '命名空间', cell: (pod) => pod.namespace },
        { id: 'status', header: '状态', cell: (pod) => <StatusBadge status={pod.status} /> },
        { id: 'node', header: '节点', cell: (pod) => pod.nodeName || '—' },
        { id: 'ip', header: 'IP', cell: (pod) => pod.podIP || '—' },
        { id: 'ready', header: '就绪', cell: (pod) => `${pod.readyContainers}/${pod.containerCount}` },
        { id: 'restarts', header: '重启', cell: (pod) => String(pod.restarts) },
      ]}
      rows={rows}
      getRowId={(pod) => `${pod.namespace}/${pod.name}`}
      loading={query.isFetching}
      error={query.isError ? 'Pod 列表加载失败' : undefined}
    />
  )
}

export function DeploymentListPage() {
  return (
    <PagedList
      title="Deployment"
      queryKey="container-deployments"
      load={(clusterId, namespace, keyword) => deploymentApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      columns={(clusterId) => [
        {
          id: 'name',
          header: '名称',
          cell: (item) =>
            nameLink(item.name, `/container/clusters/${clusterId}/deployments/${item.namespace}/${encodeURIComponent(item.name)}`),
        },
        { id: 'namespace', header: '命名空间', cell: (item) => item.namespace },
        { id: 'ready', header: '就绪', cell: (item) => `${item.readyReplicas}/${item.desiredReplicas}` },
        { id: 'strategy', header: '策略', cell: (item) => item.strategy || '—' },
      ]}
    />
  )
}

export function StatefulSetListPage() {
  return (
    <PagedList
      title="StatefulSet"
      queryKey="container-statefulsets"
      load={(clusterId, namespace, keyword) => statefulSetApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      columns={(clusterId) => [
        {
          id: 'name',
          header: '名称',
          cell: (item) =>
            nameLink(
              item.name,
              `/container/clusters/${clusterId}/statefulsets/${item.namespace}/${encodeURIComponent(item.name)}`,
            ),
        },
        { id: 'namespace', header: '命名空间', cell: (item) => item.namespace },
        { id: 'ready', header: '就绪', cell: (item) => `${item.readyReplicas}/${item.desiredReplicas}` },
        { id: 'service', header: 'Service', cell: (item) => item.serviceName || '—' },
      ]}
    />
  )
}

export function ServiceListPage() {
  return (
    <PagedList
      title="Service"
      queryKey="container-services"
      load={(clusterId, namespace, keyword) => serviceApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      columns={(clusterId) => [
        {
          id: 'name',
          header: '名称',
          cell: (item) =>
            nameLink(item.name, `/container/clusters/${clusterId}/services/${item.namespace}/${encodeURIComponent(item.name)}`),
        },
        { id: 'namespace', header: '命名空间', cell: (item) => item.namespace },
        { id: 'type', header: '类型', cell: (item) => item.type },
        { id: 'ip', header: 'ClusterIP', cell: (item) => item.clusterIP },
        { id: 'ports', header: '端口数', cell: (item) => String(item.portCount) },
      ]}
    />
  )
}

export function ConfigMapListPage() {
  return (
    <PagedList
      title="ConfigMap"
      queryKey="container-configmaps"
      load={(clusterId, namespace, keyword) => configMapApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      columns={(clusterId) => [
        {
          id: 'name',
          header: '名称',
          cell: (item) =>
            nameLink(item.name, `/container/clusters/${clusterId}/configmaps/${item.namespace}/${encodeURIComponent(item.name)}`),
        },
        { id: 'namespace', header: '命名空间', cell: (item) => item.namespace },
        { id: 'count', header: '数据条数', cell: (item) => String(item.dataCount) },
      ]}
    />
  )
}

export function SecretListPage() {
  return (
    <PagedList
      title="Secret"
      queryKey="container-secrets"
      load={(clusterId, namespace, keyword) => secretApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      columns={() => [
        { id: 'name', header: '名称', cell: (item) => <span className="font-medium">{item.name}</span> },
        { id: 'namespace', header: '命名空间', cell: (item) => item.namespace },
        { id: 'type', header: '类型', cell: (item) => item.type || '—' },
        { id: 'count', header: '数据条数', cell: (item) => String(item.dataCount) },
      ]}
    />
  )
}

export function PvcListPage() {
  return (
    <PagedList
      title="PVC"
      queryKey="container-pvcs"
      load={(clusterId, namespace, keyword) => pvcApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      columns={() => [
        { id: 'name', header: '名称', cell: (item) => <span className="font-medium">{item.name}</span> },
        { id: 'namespace', header: '命名空间', cell: (item) => item.namespace },
        { id: 'status', header: '状态', cell: (item) => <StatusBadge status={item.status} /> },
        { id: 'capacity', header: '容量', cell: (item) => item.capacity || '—' },
        { id: 'class', header: 'StorageClass', cell: (item) => item.storageClass || '—' },
      ]}
    />
  )
}

export function StorageClassListPage() {
  const { clusterId = '' } = useParams()
  const query = useQuery({
    queryKey: ['container-storageclasses', clusterId],
    queryFn: () => storageClassApi.list(clusterId),
    enabled: !!clusterId,
  })
  const rows = query.data ?? []

  return (
    <ResourceList<StorageClassSummary>
      title="StorageClass"
      description={query.isSuccess ? `共 ${rows.length} 个存储类` : '集群存储类'}
      columns={[
        { id: 'name', header: '名称', cell: (item) => <span className="font-medium">{item.name}</span> },
        { id: 'provisioner', header: 'Provisioner', cell: (item) => item.provisioner || '—' },
        { id: 'reclaim', header: '回收策略', cell: (item) => item.reclaimPolicy || '—' },
        { id: 'binding', header: '绑定模式', cell: (item) => item.volumeBindingMode || '—' },
      ]}
      rows={rows}
      getRowId={(item) => item.name}
      loading={query.isFetching}
      error={query.isError ? 'StorageClass 列表加载失败' : undefined}
    />
  )
}

function PagedList<T extends { name: string; namespace: string }>({
  title,
  queryKey,
  load,
  columns,
}: {
  title: string
  queryKey: string
  load: (clusterId: string, namespace: string | undefined, keyword: string | undefined) => Promise<{ records: T[]; total?: number | string }>
  columns: (clusterId: string) => DataTableColumn<T>[]
}) {
  const { clusterId = '' } = useParams()
  const namespace = useContainerCluster((s) => s.namespace)
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: [queryKey, clusterId, namespace, keyword],
    queryFn: () => load(clusterId, namespace || undefined, keyword || undefined),
    enabled: !!clusterId,
  })
  const records = query.data?.records ?? []
  const scope = namespace ? `命名空间 ${namespace}` : '全部命名空间'

  return (
    <ResourceList
      title={title}
      description={query.isSuccess ? `${scope} · 共 ${query.data?.total ?? records.length} 个` : scope}
      keyword={keyword}
      onKeyword={setKeyword}
      columns={columns(clusterId)}
      rows={records}
      getRowId={(item) => `${item.namespace}/${item.name}`}
      loading={query.isFetching}
      error={query.isError ? `${title}列表加载失败` : undefined}
    />
  )
}
