import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { ImageList, InfoPairs, OverviewCard, PillList, replicaMetricCards, ResourceOverview } from '@/components/console/ResourceOverview'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { configMapApi } from '@/modules/container/api/configmap'
import { deploymentApi } from '@/modules/container/api/deployment'
import { nodeApi } from '@/modules/container/api/node'
import { podApi } from '@/modules/container/api/pod'
import { serviceApi } from '@/modules/container/api/service'
import { statefulSetApi } from '@/modules/container/api/statefulset'
import {
  EventSummary,
  EventTable,
  PodMiniTable,
  PodShortcutList,
  RelatedList,
  ResourceActions,
  StubPanel,
  TextBlock,
  countLabel,
  useDetailTab,
  useResourceEvents,
  workloadTabs,
} from '@/modules/container/components/detailLayout'
import { PodLogPanel } from '@/modules/container/components/PodLogPanel'
import { PodShell } from '@/modules/container/components/PodShell'
import { YamlPanel } from '@/modules/container/components/YamlPanel'
import { WorkloadEnvPanel } from '@/modules/container/components/WorkloadEnvPanel'
import { WorkloadPodsPanel } from '@/modules/container/components/WorkloadPodsPanel'
import { WorkloadVolumePanel } from '@/modules/container/components/WorkloadVolumePanel'
import { NodeMonitor, PodMonitor } from '@/modules/container/components/ResourceMonitors'
import { formatBytes } from '@/modules/container/utils/format'
import {
  deploymentAvailability,
  recordPills,
  replicaField,
  selectorPills,
} from '@/modules/container/utils/workloadStatus'
import type { NodeDetail } from '@/modules/container/types/resource'

function Field({ label, value }: { label: string; value?: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 rounded-md border px-3 py-2">
      <span className="text-xs text-muted-foreground">{label}</span>
      <span className="text-sm">{value || '—'}</span>
    </div>
  )
}

export function NodeDetailPage() {
  const { clusterId = '', name = '' } = useParams()
  const [tab, setTab] = useDetailTab()
  const query = useQuery({
    queryKey: ['container-node', clusterId, name],
    queryFn: () => nodeApi.get(clusterId, name),
    enabled: !!clusterId && !!name,
  })
  const node = query.data
  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/clusters/${clusterId}/nodes`}>返回</Link>
        </Button>
      }
      title={name}
      meta={
        node ? (
          <StatusIcon status={node.status} />
        ) : query.isLoading ? (
          '加载中…'
        ) : query.isError ? (
          '加载失败'
        ) : null
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '监控', label: '监控' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '监控' && <NodeMonitor clusterId={clusterId} name={name} />}
      {tab === '事件' && <StubPanel label="节点事件" />}
      {tab === '概览' && query.isLoading && <TextBlock>加载中…</TextBlock>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">节点加载失败</p>}
      {tab === '概览' && node && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="状态" value={<StatusIcon status={node.status} />} />
            <Field label="角色" value={node.role} />
            <Field label="CPU" value={node.cpuCapacity} />
            <Field label="内存" value={formatBytes(node.memoryCapacity)} />
            <Field label="Pod" value={node.podCount} />
            <Field label="运行时" value={node.containerRuntime} />
            <Field label="系统" value={node.osImage} />
            <Field label="内核" value={node.kernelVersion} />
            <Field label="架构" value={node.architecture} />
            <Field label="Pod CIDR" value={node.podCIDR} />
          </div>
          <section className="flex flex-col gap-2">
            <h2 className="text-sm font-medium">地址</h2>
            <DataTable
              columns={[
                { id: 'type', header: '类型', cell: (item) => item.type },
                { id: 'address', header: '地址', cell: (item) => item.address },
              ]}
              data={node.addresses ?? []}
              getRowId={(item) => `${item.type}-${item.address}`}
              empty="没有地址"
            />
          </section>
          <RecordTable title="容量" record={node.capacity} />
          <RecordTable title="可分配" record={node.allocatable} />
          <RecordTable title="标签" record={node.labels} />
        </div>
      )}
    </DetailShell>
  )
}

export function PodDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const backTo = (location.state as { back?: string } | null)?.back || `/container/clusters/${clusterId}/pods`
  const queryClient = useQueryClient()
  const [tab, setTab] = useDetailTab()
  const query = useQuery({
    queryKey: ['container-pod', clusterId, namespace, name],
    queryFn: () => podApi.get(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const events = useResourceEvents(clusterId, namespace, query.data?.uid, tab === '概览' || tab === '事件')
  const remove = useMutation({
    mutationFn: () => podApi.delete(clusterId, namespace, name),
    onSuccess: async () => {
      toast.success('Pod 已删除')
      await queryClient.invalidateQueries({ queryKey: ['container-pods'] })
      void navigate(backTo)
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  const pod = query.data
  const containers = pod?.containers.map((item) => item.name) ?? []
  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={backTo}>返回</Link>
        </Button>
      }
      title={`${namespace}/${name}`}
      meta={
        pod ? (
          <StatusIcon status={pod.status} />
        ) : query.isLoading ? (
          '加载中…'
        ) : query.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        <Button
          variant="outline"
          size="sm"
          className="text-destructive"
          onClick={() => {
            if (window.confirm(`删除 Pod「${name}」？`)) remove.mutate()
          }}
        >
          删除
        </Button>
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '事件', label: '事件' },
        { value: '监控', label: '监控' },
        { value: '日志', label: '日志' },
        { value: 'YAML', label: 'YAML' },
        { value: '终端', label: '终端' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Pod 加载失败</p>}
      {tab === '概览' && pod && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="状态" value={<StatusIcon status={pod.status} />} />
            <Field label="节点" value={pod.nodeName} />
            <Field label="IP" value={pod.podIP} />
            <Field label="就绪" value={`${pod.readyContainers}/${pod.containerCount}`} />
            <Field label="重启" value={pod.restarts} />
            <Field label="QoS" value={pod.qosClass} />
            <Field label="Owner" value={pod.ownerReference} />
          </div>
          <DataTable
            columns={[
              { id: 'name', header: '容器', cell: (item) => <span className="font-medium">{item.name}</span> },
              { id: 'state', header: '状态', cell: (item) => <StatusIcon status={item.state} /> },
              { id: 'image', header: '镜像', className: 'max-w-sm truncate', cell: (item) => item.image },
              { id: 'ready', header: '就绪', cell: (item) => (item.ready ? '是' : '否') },
              { id: 'restarts', header: '重启', cell: (item) => String(item.restartCount) },
            ]}
            data={pod.containers}
            getRowId={(item) => item.name}
            empty="没有容器"
          />
        </div>
      )}
      {tab === '事件' && (
        <DataTable
          columns={[
            { id: 'type', header: '类型', cell: (item) => item.type },
            { id: 'reason', header: '原因', cell: (item) => item.reason },
            { id: 'message', header: '消息', cell: (item) => item.message },
            { id: 'count', header: '次数', cell: (item) => String(item.count ?? '—') },
          ]}
          data={events.data ?? []}
          getRowId={(item) => `${item.type}-${item.reason}-${item.message}-${item.lastTimestamp ?? ''}`}
          loading={events.isFetching}
          error={events.isError ? '事件加载失败' : undefined}
          empty="还没有事件"
        />
      )}
      {tab === '监控' && (
        <PodMonitor clusterId={clusterId} namespace={namespace} name={name} containers={containers} />
      )}
      {tab === '容器' && pod && (
        <DataTable
          columns={[
            { id: 'name', header: '容器', cell: (item) => item.name },
            { id: 'state', header: '状态', cell: (item) => <StatusIcon variant="dot" status={item.state} /> },
            { id: 'image', header: '镜像', cell: (item) => item.image },
            { id: 'ready', header: '就绪', cell: (item) => (item.ready ? '是' : '否') },
            { id: 'restart', header: '重启', cell: (item) => String(item.restartCount) },
          ]}
          data={pod.containers}
          getRowId={(item) => item.name}
          empty="没有容器"
        />
      )}
      {tab === '事件' && <EventTable events={events.data} loading={events.isLoading} error={events.isError} />}
      {tab === '监控' && <PodMonitor clusterId={clusterId} namespace={namespace} name={name} containers={containers} />}
      {tab === '日志' && <PodLogPanel clusterId={clusterId} namespace={namespace} name={name} containers={containers} />}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-pod-yaml', clusterId, namespace, name]}
          load={() => podApi.yaml(clusterId, namespace, name)}
        />
      )}
      {tab === '终端' && <PodShell clusterId={clusterId} namespace={namespace} name={name} containers={containers} />}
      {tab === '卷' && <StubPanel label="Pod 卷" />}
      {tab === '关联' && (
        <OverviewCard title="关联资源">
          <RelatedList items={pod?.ownerReference ? [{ kind: 'Owner', name: pod.ownerReference }] : []} />
        </OverviewCard>
      )}
    </DetailShell>
  )
}

export function DeploymentDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [tab, setTab] = useDetailTab()
  const [replicas, setReplicas] = useState('')
  const query = useQuery({
    queryKey: ['container-deployment', clusterId, namespace, name],
    queryFn: () => deploymentApi.get(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const pods = useQuery({
    queryKey: ['container-deployment-pods', clusterId, namespace, name],
    queryFn: () => deploymentApi.pods(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const volumes = useQuery({
    queryKey: ['container-deployment-volumes', clusterId, namespace, name],
    queryFn: () => deploymentApi.volumes(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const item = query.data
  const events = useResourceEvents(clusterId, namespace, item?.uid, tab === '概览' || tab === '事件')
  const scale = useMutation({
    mutationFn: () => deploymentApi.scale(clusterId, namespace, name, Number(replicas)),
    onSuccess: async () => {
      toast.success('已调整副本')
      setReplicas('')
      await queryClient.invalidateQueries({ queryKey: ['container-deployment', clusterId, namespace, name] })
    },
    onError: (error: Error) => toast.error(error.message || '扩缩容失败'),
  })
  const restart = useMutation({
    mutationFn: () => deploymentApi.restart(clusterId, namespace, name),
    onSuccess: () => toast.success('已触发重启'),
    onError: (error: Error) => toast.error(error.message || '重启失败'),
  })
  const remove = useMutation({
    mutationFn: () => deploymentApi.delete(clusterId, namespace, name),
    onSuccess: async () => {
      toast.success('Deployment 已删除')
      void navigate(`/container/clusters/${clusterId}/deployments`)
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  const podRows = pods.data ?? []
  const containerCount = item?.containers?.length
  const volumeCount = volumes.data?.volumes.length ?? item?.volumes?.length
  const updated = replicaField(item?.status, 'updated')
  const related = (item?.volumes ?? [])
    .filter((volume) => volume.volumeType && volume.volumeType !== 'EmptyDir' && volume.volumeType !== 'Other')
    .map((volume) => ({ kind: volume.volumeType || 'Volume', name: volume.name }))

  return (
    <DetailShell
      title={name}
      description={namespaceLine(namespace)}
      actions={
        <ResourceActions
          onRefresh={() => {
            void query.refetch()
            void pods.refetch()
            void volumes.refetch()
            void events.refetch()
          }}
          refreshing={query.isFetching}
          showRestart
          onRestart={() => restart.mutate()}
          restarting={restart.isPending}
          onDelete={() => confirmDelete('Deployment', name, () => remove.mutate())}
          deleting={remove.isPending}
        />
      }
      tabs={workloadTabs({ pods: pods.data ? podRows.length : undefined, containers: containerCount, volumes: volumeCount })}
      value={tab}
      onValueChange={setTab}
      metrics={
        tab === '概览' && item
          ? replicaMetricCards({
              status: <StatusIcon variant="dot" status={deploymentAvailability(item)} />,
              desired: item.desiredReplicas,
              ready: `${item.readyReplicas}/${item.desiredReplicas}`,
              updated: updated ?? '—',
              available: item.availableReplicas,
              created: item.age || '—',
              createdHint: item.creationTimestamp || undefined,
            })
          : undefined
      }
    >
      {tab === '概览' && query.isLoading && <TextBlock>加载中…</TextBlock>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Deployment 加载失败</p>}
      {tab === '概览' && item && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="状态" value={<StatusIcon status={item.status} />} />
            <Field label="就绪" value={`${item.readyReplicas}/${item.desiredReplicas}`} />
            <Field label="可用" value={item.availableReplicas} />
            <Field label="策略" value={item.strategy} />
            <Field label="镜像" value={item.image} />
            <Field label="选择器" value={item.selector} />
          </div>
          <div className="flex items-center gap-2">
            <Input
              value={replicas}
              onChange={(event) => setReplicas(event.target.value)}
              placeholder={String(item.desiredReplicas)}
              className="max-w-32"
            />
            <Button
              size="sm"
              disabled={replicas === '' || Number.isNaN(Number(replicas))}
              onClick={() => scale.mutate()}
            >
              调整副本
            </Button>
          </div>
          <DataTable
            columns={[
              { id: 'name', header: '容器', cell: (container) => container.name },
              { id: 'image', header: '镜像', className: 'max-w-sm truncate', cell: (container) => container.image },
              {
                id: 'cpu',
                header: 'CPU',
                cell: (container) => `${container.cpuRequest || '—'} / ${container.cpuLimit || '—'}`,
              },
              {
                id: 'memory',
                header: '内存',
                cell: (container) => `${container.memRequest || '—'} / ${container.memLimit || '—'}`,
              },
            ]}
            data={item.containers ?? []}
            getRowId={(container) => container.name}
            empty="没有容器"
          />
        </div>
      )}
      {tab === 'Pods' && (
        <WorkloadPodsPanel
          clusterId={clusterId}
          queryKey={['container-deployment-pods', clusterId, namespace, name]}
          load={() => deploymentApi.pods(clusterId, namespace, name)}
        />
      )}
      {tab === '容器' && (
        <div className="flex flex-col gap-4">
          <DataTable
            columns={[
              { id: 'name', header: '容器', cell: (row) => row.name },
              { id: 'image', header: '镜像', cell: (row) => row.image },
              { id: 'cpu', header: 'CPU', cell: (row) => `${row.cpuRequest || '—'} / ${row.cpuLimit || '—'}` },
              { id: 'mem', header: '内存', cell: (row) => `${row.memRequest || '—'} / ${row.memLimit || '—'}` },
            ]}
            data={item?.containers ?? []}
            getRowId={(row) => row.name}
            loading={query.isLoading}
            empty="没有容器"
          />
          <WorkloadEnvPanel
            queryKey={['container-deployment-env', clusterId, namespace, name]}
            load={() => deploymentApi.env(clusterId, namespace, name)}
            save={(data) => deploymentApi.updateEnv(clusterId, namespace, name, data)}
          />
        </div>
      )}
      {tab === '卷' && (
        <WorkloadVolumePanel
          queryKey={['container-deployment-volumes', clusterId, namespace, name]}
          load={() => deploymentApi.volumes(clusterId, namespace, name)}
          save={(data) => deploymentApi.updateVolumes(clusterId, namespace, name, data)}
        />
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-deployment-yaml', clusterId, namespace, name]}
          load={() => deploymentApi.yaml(clusterId, namespace, name)}
          save={(yaml) => deploymentApi.updateYaml(clusterId, namespace, name, yaml)}
        />
      )}
      {tab === '日志' && (
        <PodShortcutList clusterId={clusterId} pods={podRows} tab="日志" loading={pods.isLoading} empty="还没有 Pod" />
      )}
      {tab === '终端' && (
        <PodShortcutList clusterId={clusterId} pods={podRows} tab="终端" loading={pods.isLoading} empty="还没有 Pod" />
      )}
      {tab === '关联' && (
        <OverviewCard title="关联资源">
          <RelatedList items={related} />
        </OverviewCard>
      )}
      {tab === '历史' && <StubPanel label="历史" />}
      {tab === '事件' && <EventTable events={events.data} loading={events.isLoading} error={events.isError} />}
      {tab === '监控' && (
        <PodShortcutList clusterId={clusterId} pods={podRows} tab="监控" loading={pods.isLoading} empty="还没有 Pod" />
      )}
    </DetailShell>
  )
}

export function ServiceDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const [tab, setTab] = useDetailTab()
  const query = useQuery({
    queryKey: ['container-service', clusterId, namespace, name],
    queryFn: () => serviceApi.get(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const item = query.data
  const events = useResourceEvents(clusterId, namespace, item?.uid, tab === '概览' || tab === '事件')
  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/clusters/${clusterId}/services`}>返回</Link>
        </Button>
      }
      title={`${namespace}/${name}`}
      description={item?.type}
      meta={query.isLoading ? '加载中…' : query.isError ? '加载失败' : null}
      tabs={[
        { value: '概览', label: '概览' },
        { value: 'YAML', label: 'YAML' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Service 加载失败</p>}
      {tab === '概览' && item && (
        <div className="flex flex-col gap-4">
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="类型" value={item.type} />
            <Field label="ClusterIP" value={item.clusterIP} />
            <Field label="ExternalIP" value={item.externalIP} />
            <Field label="会话保持" value={item.sessionAffinity} />
          </div>
          <DataTable
            columns={[
              { id: 'name', header: '名称', cell: (port) => port.name || '—' },
              { id: 'port', header: '端口', cell: (port) => String(port.port) },
              { id: 'target', header: '目标端口', cell: (port) => port.targetPort || '—' },
              { id: 'node', header: 'NodePort', cell: (port) => port.nodePort || '—' },
              { id: 'protocol', header: '协议', cell: (port) => port.protocol || '—' },
            ]}
            data={item.ports ?? []}
            getRowId={(port) => `${port.name ?? ''}-${port.port}-${port.protocol ?? ''}`}
            empty="没有端口"
          />
        </div>
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-service-yaml', clusterId, namespace, name]}
          load={() => serviceApi.yaml(clusterId, namespace, name)}
        />
      )}
    </DetailShell>
  )
}

export function ConfigMapDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useDetailTab()
  const queryClient = useQueryClient()
  const remove = useMutation({
    mutationFn: () => configMapApi.delete(clusterId, namespace, name),
    onSuccess: () => {
      toast.success('ConfigMap 已删除')
      void navigate(`/container/clusters/${clusterId}/configmaps`)
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/clusters/${clusterId}/configmaps`}>返回</Link>
        </Button>
      }
      title={`${namespace}/${name}`}
      actions={
        <Button
          variant="outline"
          size="sm"
          className="text-destructive"
          onClick={() => {
            if (window.confirm(`删除 ConfigMap「${name}」？`)) remove.mutate()
          }}
        >
          删除
        </Button>
      }
    >
      <YamlPanel
        queryKey={['container-configmap-yaml', clusterId, namespace, name]}
        load={() => configMapApi.yaml(clusterId, namespace, name)}
        save={(yaml) => configMapApi.updateYaml(clusterId, namespace, name, yaml)}
      />
    </DetailShell>
  )
}

export function StatefulSetDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const [tab, setTab] = useDetailTab()
  const pods = useQuery({
    queryKey: ['container-statefulset-pods', clusterId, namespace, name],
    queryFn: () => statefulSetApi.pods(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const volumes = useQuery({
    queryKey: ['container-statefulset-volumes', clusterId, namespace, name],
    queryFn: () => statefulSetApi.volumes(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const remove = useMutation({
    mutationFn: () => statefulSetApi.delete(clusterId, namespace, name),
    onSuccess: () => {
      toast.success('StatefulSet 已删除')
      void navigate(`/container/clusters/${clusterId}/statefulsets`)
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  const podRows = pods.data ?? []
  const ready = podRows.filter((pod) => pod.containerCount > 0 && pod.readyContainers >= pod.containerCount).length
  const status = podRows.length === 0 ? 'Unknown' : ready === podRows.length ? 'Available' : 'Progressing'
  const related = (volumes.data?.volumes ?? []).map((volume) => ({
    kind: volume.type || 'Volume',
    name: volume.source || volume.name,
  }))
  const containerCount = volumes.data?.containers.length

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/clusters/${clusterId}/statefulsets`}>返回</Link>
        </Button>
      }
      title={`${namespace}/${name}`}
      actions={
        <Button
          variant="outline"
          size="sm"
          className="text-destructive"
          onClick={() => {
            if (window.confirm(`删除 StatefulSet「${name}」？`)) remove.mutate()
          }}
        >
          删除
        </Button>
      }
      tabs={[
        { value: '环境变量', label: '环境变量' },
        { value: '容器', label: '容器' },
        { value: '挂载卷', label: '挂载卷' },
        { value: 'YAML', label: 'YAML' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '环境变量' && (
        <WorkloadEnvPanel
          queryKey={['container-statefulset-env', clusterId, namespace, name]}
          load={() => statefulSetApi.env(clusterId, namespace, name)}
          save={(data) => statefulSetApi.updateEnv(clusterId, namespace, name, data)}
        />
      )}
      {tab === 'Pods' && (
        <WorkloadPodsPanel
          clusterId={clusterId}
          queryKey={['container-statefulset-pods', clusterId, namespace, name]}
          load={() => statefulSetApi.pods(clusterId, namespace, name)}
        />
      )}
      {tab === '容器' && (
        <WorkloadEnvPanel
          queryKey={['container-statefulset-env', clusterId, namespace, name]}
          load={() => statefulSetApi.env(clusterId, namespace, name)}
          save={(data) => statefulSetApi.updateEnv(clusterId, namespace, name, data)}
        />
      )}
      {tab === '卷' && (
        <WorkloadVolumePanel
          queryKey={['container-statefulset-volumes', clusterId, namespace, name]}
          load={() => statefulSetApi.volumes(clusterId, namespace, name)}
          save={(data) => statefulSetApi.updateVolumes(clusterId, namespace, name, data)}
        />
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-statefulset-yaml', clusterId, namespace, name]}
          load={() => statefulSetApi.yaml(clusterId, namespace, name)}
          save={(yaml) => statefulSetApi.updateYaml(clusterId, namespace, name, yaml)}
        />
      )}
    </DetailShell>
  )
}

function RecordTable({ title, record }: { title: string; record?: Record<string, string> | null }) {
  const entries = Object.entries(record ?? {}).map(([key, value]) => ({ key, value }))
  if (entries.length === 0) return null
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-sm font-medium">{title}</h2>
      <DataTable
        columns={[
          { id: 'key', header: '键', cell: (row) => row.key },
          { id: 'value', header: '值', cell: (row) => row.value },
        ]}
        data={entries}
        getRowId={(row) => row.key}
      />
    </section>
  )
}
