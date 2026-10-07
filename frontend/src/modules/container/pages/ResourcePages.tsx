import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useParams } from 'react-router'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
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
import { formatBytes } from '@/modules/container/utils/format'

export function NodeListPage() {
  const { clusterId = '' } = useParams()
  const [keyword, setKeyword] = useState('')
  const query = useQuery({
    queryKey: ['container-nodes', clusterId, keyword],
    queryFn: () => nodeApi.list(clusterId, keyword || undefined),
    enabled: !!clusterId,
  })
  return (
    <ResourceFrame title="Node" keyword={keyword} onKeyword={setKeyword} loading={query.isLoading} error={query.isError}>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>名称</TableHead>
            <TableHead>状态</TableHead>
            <TableHead>角色</TableHead>
            <TableHead>CPU</TableHead>
            <TableHead>内存</TableHead>
            <TableHead>Pod</TableHead>
            <TableHead>运行时</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
            {(query.data ?? []).map((node) => (
            <TableRow key={node.name}>
              <TableCell className="font-medium">
                <Link className="text-primary hover:underline" to={`/container/clusters/${clusterId}/nodes/${encodeURIComponent(node.name)}`}>
                  {node.name}
                </Link>
              </TableCell>
              <TableCell><StatusBadge status={node.status} /></TableCell>
              <TableCell>{node.role}</TableCell>
              <TableCell>{node.cpuCapacity}</TableCell>
              <TableCell>{formatBytes(node.memoryCapacity)}</TableCell>
              <TableCell>{node.podCount}</TableCell>
              <TableCell>{node.containerRuntime || '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ResourceFrame>
  )
}

export function PodListPage() {
  return (
    <PagedList
      title="Pod"
      queryKey="container-pods"
      load={(clusterId, namespace, keyword) => podApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      headers={['名称', '命名空间', '状态', '节点', 'IP', '就绪', '重启']}
      to={(clusterId, pod) => `/container/clusters/${clusterId}/pods/${pod.namespace}/${encodeURIComponent(pod.name)}`}
      render={(pod) => [
        pod.name,
        pod.namespace,
        <StatusBadge key="s" status={pod.status} />,
        pod.nodeName || '—',
        pod.podIP || '—',
        `${pod.readyContainers}/${pod.containerCount}`,
        String(pod.restarts),
      ]}
    />
  )
}

export function DeploymentListPage() {
  return (
    <PagedList
      title="Deployment"
      queryKey="container-deployments"
      load={(clusterId, namespace, keyword) => deploymentApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      headers={['名称', '命名空间', '就绪', '策略']}
      to={(clusterId, item) => `/container/clusters/${clusterId}/deployments/${item.namespace}/${encodeURIComponent(item.name)}`}
      render={(item) => [
        item.name,
        item.namespace,
        `${item.readyReplicas}/${item.desiredReplicas}`,
        item.strategy || '—',
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
      headers={['名称', '命名空间', '就绪', 'Service']}
      to={(clusterId, item) => `/container/clusters/${clusterId}/statefulsets/${item.namespace}/${encodeURIComponent(item.name)}`}
      render={(item) => [
        item.name,
        item.namespace,
        `${item.readyReplicas}/${item.desiredReplicas}`,
        item.serviceName || '—',
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
      headers={['名称', '命名空间', '类型', 'ClusterIP', '端口数']}
      to={(clusterId, item) => `/container/clusters/${clusterId}/services/${item.namespace}/${encodeURIComponent(item.name)}`}
      render={(item) => [item.name, item.namespace, item.type, item.clusterIP, String(item.portCount)]}
    />
  )
}

export function ConfigMapListPage() {
  return (
    <PagedList
      title="ConfigMap"
      queryKey="container-configmaps"
      load={(clusterId, namespace, keyword) => configMapApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      headers={['名称', '命名空间', '数据条数']}
      to={(clusterId, item) => `/container/clusters/${clusterId}/configmaps/${item.namespace}/${encodeURIComponent(item.name)}`}
      render={(item) => [item.name, item.namespace, String(item.dataCount)]}
    />
  )
}

export function SecretListPage() {
  return (
    <PagedList
      title="Secret"
      queryKey="container-secrets"
      load={(clusterId, namespace, keyword) => secretApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      headers={['名称', '命名空间', '类型', '数据条数']}
      render={(item) => [item.name, item.namespace, item.type || '—', String(item.dataCount)]}
    />
  )
}

export function PvcListPage() {
  return (
    <PagedList
      title="PVC"
      queryKey="container-pvcs"
      load={(clusterId, namespace, keyword) => pvcApi.list(clusterId, { namespace, keyword, pageNo: 1, pageSize: 100 })}
      headers={['名称', '命名空间', '状态', '容量', 'StorageClass']}
      render={(item) => [
        item.name,
        item.namespace,
        <StatusBadge key="s" status={item.status} />,
        item.capacity || '—',
        item.storageClass || '—',
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
  return (
    <ResourceFrame title="StorageClass" loading={query.isLoading} error={query.isError}>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>名称</TableHead>
            <TableHead>Provisioner</TableHead>
            <TableHead>回收策略</TableHead>
            <TableHead>绑定模式</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {(query.data ?? []).map((item) => (
            <TableRow key={item.name}>
              <TableCell className="font-medium">{item.name}</TableCell>
              <TableCell>{item.provisioner || '—'}</TableCell>
              <TableCell>{item.reclaimPolicy || '—'}</TableCell>
              <TableCell>{item.volumeBindingMode || '—'}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ResourceFrame>
  )
}

function PagedList<T extends { name: string; namespace: string }>({
  title,
  queryKey,
  load,
  headers,
  render,
  to,
}: {
  title: string
  queryKey: string
  load: (clusterId: string, namespace: string | undefined, keyword: string | undefined) => Promise<{ records: T[] }>
  headers: string[]
  render: (item: T) => Array<string | ReactNode>
  to?: (clusterId: string, item: T) => string
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
  return (
    <ResourceFrame title={title} keyword={keyword} onKeyword={setKeyword} loading={query.isLoading} error={query.isError}>
      <Table>
        <TableHeader>
          <TableRow>
            {headers.map((header) => (
              <TableHead key={header}>{header}</TableHead>
            ))}
          </TableRow>
        </TableHeader>
        <TableBody>
          {records.map((item) => (
            <TableRow key={`${item.namespace}/${item.name}`}>
              {render(item).map((cell, index) => (
                <TableCell key={headers[index]} className={index === 0 ? 'font-medium' : undefined}>
                  {index === 0 && to ? (
                    <Link className="text-primary hover:underline" to={to(clusterId, item)}>
                      {cell}
                    </Link>
                  ) : (
                    cell
                  )}
                </TableCell>
              ))}
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </ResourceFrame>
  )
}

function ResourceFrame({
  title,
  keyword,
  onKeyword,
  loading,
  error,
  children,
}: {
  title: string
  keyword?: string
  onKeyword?: (value: string) => void
  loading: boolean
  error: boolean
  children: ReactNode
}) {
  return (
    <div className="flex flex-col gap-4">
      <div className="flex items-center justify-between gap-3">
        <h1 className="text-xl font-semibold">{title}</h1>
        {onKeyword && (
          <Input
            value={keyword}
            onChange={(event) => onKeyword(event.target.value)}
            placeholder="搜索名称"
            className="max-w-xs"
          />
        )}
      </div>
      {loading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {error && <p className="text-sm text-destructive">{title}加载失败</p>}
      {!loading && !error && children}
    </div>
  )
}
