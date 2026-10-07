import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useLocation, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { InfoGrid, OverviewCard, PillList, ResourceOverview } from '@/components/console/ResourceOverview'
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

function namespaceLine(namespace: string) {
  return `命名空间：${namespace}`
}

function confirmDelete(label: string, name: string, run: () => void) {
  if (window.confirm(`删除 ${label}「${name}」？`)) run()
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
      title={name}
      description={node?.role ? `角色：${node.role}` : '节点'}
      actions={<ResourceActions onRefresh={() => void query.refetch()} refreshing={query.isFetching} />}
      tabs={[
        { value: '概览', label: '概览' },
        { value: '监控', label: '监控' },
        { value: '事件', label: '事件' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '监控' && <NodeMonitor clusterId={clusterId} name={name} />}
      {tab === '事件' && <StubPanel label="节点事件" />}
      {tab === '概览' && query.isLoading && <TextBlock>加载中…</TextBlock>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">节点加载失败</p>}
      {tab === '概览' && node && <NodeOverview node={node} />}
    </DetailShell>
  )
}

function NodeOverview({ node }: { node: NodeDetail }) {
  return (
    <ResourceOverview
      metrics={[
        { label: '状态', value: <StatusIcon variant="dot" status={node.status} /> },
        { label: '角色', value: node.role || '—' },
        { label: 'CPU', value: node.cpuCapacity, hint: '核', emphasis: true },
        { label: '内存', value: formatBytes(node.memoryCapacity), emphasis: true },
        { label: 'Pod', value: node.podCount, hint: '个', emphasis: true },
        { label: '创建', value: node.age || '—', hint: node.creationTimestamp || undefined, emphasis: true },
      ]}
      main={
        <>
          <OverviewCard title="地址">
            <DataTable
              columns={[
                { id: 'type', header: '类型', cell: (row) => row.type },
                { id: 'address', header: '地址', cell: (row) => row.address },
              ]}
              data={node.addresses ?? []}
              getRowId={(row) => `${row.type}-${row.address}`}
              empty="没有地址"
            />
          </OverviewCard>
          <OverviewCard title="容量">
            <RecordList record={node.capacity} />
          </OverviewCard>
          <OverviewCard title="可分配">
            <RecordList record={node.allocatable} />
          </OverviewCard>
        </>
      }
      side={
        <>
          <OverviewCard title="标签">
            <PillList items={recordPills(node.labels)} empty="没有标签" />
          </OverviewCard>
          <OverviewCard title="注解">
            <PillList items={recordPills(node.annotations)} empty="没有注解" />
          </OverviewCard>
          <OverviewCard title="污点">
            <PillList
              items={(node.taints ?? []).map((taint) => `${taint.key}${taint.value ? `=${taint.value}` : ''}:${taint.effect}`)}
              empty="没有污点"
            />
          </OverviewCard>
        </>
      }
    />
  )
}

function RecordList({ record }: { record?: Record<string, string> | null }) {
  const entries = Object.entries(record ?? {})
  if (entries.length === 0) return <TextBlock>暂无数据</TextBlock>
  return (
    <dl className="grid grid-cols-[minmax(0,10rem)_1fr] gap-x-3 gap-y-2 text-sm">
      {entries.map(([key, value]) => (
        <div key={key} className="contents">
          <dt className="truncate text-muted-foreground">{key}</dt>
          <dd className="truncate">{value}</dd>
        </div>
      ))}
    </dl>
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
      title={name}
      description={namespaceLine(namespace)}
      actions={
        <ResourceActions
          onRefresh={() => void query.refetch()}
          refreshing={query.isFetching}
          onDelete={() => confirmDelete('Pod', name, () => remove.mutate())}
          deleting={remove.isPending}
        />
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '容器', label: countLabel('容器', pod?.containers.length) },
        { value: 'YAML', label: 'YAML' },
        { value: '日志', label: '日志' },
        { value: '终端', label: '终端' },
        { value: '卷', label: '卷' },
        { value: '关联', label: '关联' },
        { value: '历史', label: '历史' },
        { value: '事件', label: '事件' },
        { value: '监控', label: '监控' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <TextBlock>加载中…</TextBlock>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Pod 加载失败</p>}
      {tab === '概览' && pod && (
        <ResourceOverview
          metrics={[
            { label: '状态', value: <StatusIcon variant="dot" status={pod.status} /> },
            { label: '就绪', value: `${pod.readyContainers}/${pod.containerCount}`, emphasis: true },
            { label: '重启', value: pod.restarts, emphasis: true },
            { label: 'QoS', value: pod.qosClass || '—' },
            { label: '节点', value: pod.nodeName || '—' },
            { label: '创建', value: pod.age || '—', hint: pod.creationTimestamp || undefined, emphasis: true },
          ]}
          main={
            <>
              <OverviewCard title={countLabel('容器', pod.containers.length)}>
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
              </OverviewCard>
              <OverviewCard title="信息">
                <InfoGrid
                  rows={[
                    { label: '所有者', value: pod.ownerReference || '—' },
                    { label: 'IP', value: pod.podIP || '—' },
                    { label: '节点', value: pod.nodeName || '—' },
                    { label: 'QoS', value: pod.qosClass || '—' },
                    { label: 'UID', value: pod.uid },
                  ]}
                />
              </OverviewCard>
            </>
          }
          side={
            <>
              <OverviewCard title={`事件 (${events.data?.length ?? 0})`}>
                <EventSummary events={events.data} loading={events.isLoading} error={events.isError} />
              </OverviewCard>
              <OverviewCard title="标签">
                <PillList items={recordPills(pod.labels)} empty="没有标签" />
              </OverviewCard>
              <OverviewCard title="注解">
                <PillList items={recordPills(pod.annotations)} empty="没有注解" />
              </OverviewCard>
            </>
          }
        />
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
      {tab === '历史' && <StubPanel label="历史" />}
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
    >
      {tab === '概览' && query.isLoading && <TextBlock>加载中…</TextBlock>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Deployment 加载失败</p>}
      {tab === '概览' && item && (
        <ResourceOverview
          metrics={[
            { label: '状态', value: <StatusIcon variant="dot" status={deploymentAvailability(item)} /> },
            { label: '期望', value: item.desiredReplicas, hint: '个 Pod', emphasis: true },
            { label: '就绪', value: item.readyReplicas, hint: '个 Pod', emphasis: true },
            { label: '最新', value: updated ?? '—', hint: '个 Pod', emphasis: true },
            { label: '可用', value: item.availableReplicas, hint: '个 Pod', emphasis: true },
            { label: '创建', value: item.age || '—', hint: item.creationTimestamp || undefined, emphasis: true },
          ]}
          main={
            <>
              <OverviewCard title={countLabel('Pods', podRows.length)}>
                <PodMiniTable clusterId={clusterId} pods={podRows} loading={pods.isLoading} />
              </OverviewCard>
              <OverviewCard title="信息">
                <InfoGrid
                  rows={[
                    { label: '选择器', value: <PillList items={selectorPills(item.selector)} empty="没有选择器" /> },
                    { label: '镜像', value: <ImageLines volumes={item.containers} /> },
                    { label: '策略', value: item.strategy || '—' },
                    { label: '容器', value: String(item.containers?.length ?? 0) },
                    { label: '卷', value: String(item.volumes?.length ?? 0) },
                    { label: '最小就绪', value: item.minReadySeconds || '—' },
                    { label: '修订历史上限', value: item.revisionHistoryLimit || '—' },
                    { label: '状态明细', value: item.status || '—' },
                    { label: 'UID', value: item.uid },
                  ]}
                />
                <div className="mt-4 flex items-center gap-2 border-t pt-3">
                  <Input
                    value={replicas}
                    onChange={(event) => setReplicas(event.target.value)}
                    placeholder={String(item.desiredReplicas)}
                    className="h-8 max-w-24"
                    aria-label="副本数"
                  />
                  <Button
                    size="sm"
                    disabled={replicas === '' || Number.isNaN(Number(replicas)) || scale.isPending}
                    onClick={() => scale.mutate()}
                  >
                    调整副本
                  </Button>
                </div>
              </OverviewCard>
            </>
          }
          side={
            <>
              <OverviewCard title={`事件 (${events.data?.length ?? 0})`}>
                <EventSummary events={events.data} loading={events.isLoading} error={events.isError} />
              </OverviewCard>
              <OverviewCard title="关联资源">
                <RelatedList items={related} />
              </OverviewCard>
              <OverviewCard title="标签">
                <PillList items={recordPills(item.labels)} empty="没有标签" />
              </OverviewCard>
              <OverviewCard title="注解">
                <PillList items={recordPills(item.annotations)} empty="没有注解" />
              </OverviewCard>
            </>
          }
        />
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

function ImageLines({ volumes }: { volumes?: { name: string; image: string }[] }) {
  if (!volumes?.length) return '—'
  return (
    <div className="flex flex-col gap-1">
      {volumes.map((item) => (
        <div key={item.name} className="flex flex-col">
          <span className="font-medium">{item.name}</span>
          <span className="text-muted-foreground">{item.image}</span>
        </div>
      ))}
    </div>
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
      title={name}
      description={namespaceLine(namespace)}
      actions={<ResourceActions onRefresh={() => void query.refetch()} refreshing={query.isFetching} />}
      tabs={[
        { value: '概览', label: '概览' },
        { value: 'YAML', label: 'YAML' },
        { value: '关联', label: '关联' },
        { value: '事件', label: '事件' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && query.isLoading && <TextBlock>加载中…</TextBlock>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Service 加载失败</p>}
      {tab === '概览' && item && (
        <ResourceOverview
          metrics={[
            { label: '类型', value: item.type || '—' },
            { label: 'ClusterIP', value: item.clusterIP || '—' },
            { label: '端口', value: item.ports?.length ?? item.portCount, emphasis: true },
            { label: '会话保持', value: item.sessionAffinity || '—' },
            { label: 'ExternalIP', value: item.externalIP || '—' },
            { label: '创建', value: item.age || '—', hint: item.creationTimestamp || undefined, emphasis: true },
          ]}
          main={
            <>
              <OverviewCard title="端口">
                <DataTable
                  columns={[
                    { id: 'name', header: '名称', cell: (port) => port.name || '—' },
                    { id: 'port', header: '端口', cell: (port) => String(port.port) },
                    { id: 'target', header: '目标端口', cell: (port) => port.targetPort || '—' },
                    { id: 'node', header: 'NodePort', cell: (port) => port.nodePort || '—' },
                    { id: 'protocol', header: '协议', cell: (port) => port.protocol || '—' },
                  ]}
                  data={item.ports ?? []}
                  getRowId={(port) => `${port.name ?? ''}-${port.port}`}
                  empty="没有端口"
                />
              </OverviewCard>
              <OverviewCard title="信息">
                <InfoGrid
                  rows={[
                    { label: '选择器', value: <PillList items={selectorPills(item.selector)} empty="没有选择器" /> },
                    { label: 'UID', value: item.uid },
                  ]}
                />
              </OverviewCard>
            </>
          }
          side={
            <>
              <OverviewCard title={`事件 (${events.data?.length ?? 0})`}>
                <EventSummary events={events.data} loading={events.isLoading} error={events.isError} />
              </OverviewCard>
              <OverviewCard title="标签">
                <PillList items={recordPills(item.labels)} empty="没有标签" />
              </OverviewCard>
              <OverviewCard title="注解">
                <PillList items={recordPills(item.annotations)} empty="没有注解" />
              </OverviewCard>
            </>
          }
        />
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-service-yaml', clusterId, namespace, name]}
          load={() => serviceApi.yaml(clusterId, namespace, name)}
        />
      )}
      {tab === '关联' && (
        <OverviewCard title="关联资源">
          <TextBlock>按选择器关联的 Pod 即将支持</TextBlock>
          <div className="mt-3">
            <PillList items={selectorPills(item?.selector)} empty="没有选择器" />
          </div>
        </OverviewCard>
      )}
      {tab === '事件' && <EventTable events={events.data} loading={events.isLoading} error={events.isError} />}
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
      title={name}
      description={namespaceLine(namespace)}
      actions={
        <ResourceActions
          onRefresh={() => void queryClient.invalidateQueries({ queryKey: ['container-configmap-yaml', clusterId, namespace, name] })}
          onDelete={() => confirmDelete('ConfigMap', name, () => remove.mutate())}
          deleting={remove.isPending}
        />
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: 'YAML', label: 'YAML' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && (
        <ResourceOverview
          metrics={[
            { label: '名称', value: name },
            { label: '命名空间', value: namespace },
          ]}
          main={
            <OverviewCard title="数据">
              <TextBlock>键值在 YAML 中查看和编辑。结构化概览即将支持。</TextBlock>
            </OverviewCard>
          }
          side={
            <OverviewCard title="标签">
              <TextBlock>标签随 YAML 提供，概览里的标签卡即将支持。</TextBlock>
            </OverviewCard>
          }
        />
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-configmap-yaml', clusterId, namespace, name]}
          load={() => configMapApi.yaml(clusterId, namespace, name)}
          save={(yaml) => configMapApi.updateYaml(clusterId, namespace, name, yaml)}
        />
      )}
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
      title={name}
      description={namespaceLine(namespace)}
      actions={
        <ResourceActions
          onRefresh={() => {
            void pods.refetch()
            void volumes.refetch()
          }}
          refreshing={pods.isFetching}
          showRestart
          onDelete={() => confirmDelete('StatefulSet', name, () => remove.mutate())}
          deleting={remove.isPending}
        />
      }
      tabs={workloadTabs({
        pods: pods.data ? podRows.length : undefined,
        containers: containerCount,
        volumes: volumes.data?.volumes.length,
      })}
      value={tab}
      onValueChange={setTab}
    >
      {tab === '概览' && (
        <ResourceOverview
          metrics={[
            { label: '状态', value: pods.isLoading ? '…' : <StatusIcon variant="dot" status={status} /> },
            { label: '期望', value: '—', hint: '详情接口尚未提供', emphasis: true },
            { label: '就绪', value: pods.isLoading ? '—' : ready, hint: '个 Pod', emphasis: true },
            { label: '最新', value: '—', hint: '详情接口尚未提供', emphasis: true },
            { label: '可用', value: pods.isLoading ? '—' : ready, hint: '个 Pod', emphasis: true },
            { label: '创建', value: '—', emphasis: true },
          ]}
          main={
            <>
              <OverviewCard title={countLabel('Pods', pods.data ? podRows.length : undefined)}>
                <PodMiniTable clusterId={clusterId} pods={podRows} loading={pods.isLoading} />
              </OverviewCard>
              <OverviewCard title="信息">
                <InfoGrid
                  rows={[
                    { label: '当前 Pod', value: pods.isLoading ? '—' : String(podRows.length) },
                    { label: '卷', value: volumes.isLoading ? '—' : String(volumes.data?.volumes.length ?? 0) },
                    { label: '容器', value: volumes.isLoading ? '—' : String(containerCount ?? 0) },
                  ]}
                />
                <TextBlock>副本期望、服务名和 UID 还没有 StatefulSet 详情接口。</TextBlock>
              </OverviewCard>
            </>
          }
          side={
            <>
              <OverviewCard title="事件">
                <TextBlock>事件需要资源 UID，StatefulSet 详情接口尚未提供。</TextBlock>
              </OverviewCard>
              <OverviewCard title="关联资源">
                <RelatedList items={related} />
              </OverviewCard>
            </>
          }
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
      {tab === '事件' && <StubPanel label="事件" />}
      {tab === '监控' && (
        <PodShortcutList clusterId={clusterId} pods={podRows} tab="监控" loading={pods.isLoading} empty="还没有 Pod" />
      )}
    </DetailShell>
  )
}
