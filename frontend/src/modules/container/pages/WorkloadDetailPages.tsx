import { useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useLocation, useNavigate, useParams, useSearchParams } from 'react-router'
import { toast } from 'sonner'
import { DetailShell } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { configMapApi } from '@/modules/container/api/configmap'
import { deploymentApi } from '@/modules/container/api/deployment'
import { eventApi } from '@/modules/container/api/event'
import { nodeApi } from '@/modules/container/api/node'
import { podApi } from '@/modules/container/api/pod'
import { serviceApi } from '@/modules/container/api/service'
import { statefulSetApi } from '@/modules/container/api/statefulset'
import { PodLogPanel } from '@/modules/container/components/PodLogPanel'
import { PodShell } from '@/modules/container/components/PodShell'
import { YamlPanel } from '@/modules/container/components/YamlPanel'
import { WorkloadEnvPanel } from '@/modules/container/components/WorkloadEnvPanel'
import { WorkloadPodsPanel } from '@/modules/container/components/WorkloadPodsPanel'
import { WorkloadVolumePanel } from '@/modules/container/components/WorkloadVolumePanel'
import { formatBytes } from '@/modules/container/utils/format'
import { NodeMonitor, PodMonitor } from '@/modules/container/components/ResourceMonitors'

function BackTitle({ to, title, extra }: { to: string; title: string; extra?: ReactNode }) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex items-center gap-3">
        <Button variant="ghost" size="sm" asChild>
          <Link to={to}>返回</Link>
        </Button>
        <h1 className="text-xl font-semibold">{title}</h1>
      </div>
      {extra}
    </div>
  )
}

function TabBar({ tabs, value, onChange }: { tabs: string[]; value: string; onChange: (value: string) => void }) {
  return (
    <div className="flex gap-1 border-b">
      {tabs.map((tab) => (
        <button
          key={tab}
          type="button"
          className={`-mb-px border-b-2 px-3 py-2 text-sm ${value === tab ? 'border-primary font-medium text-primary' : 'border-transparent text-muted-foreground'}`}
          onClick={() => onChange(tab)}
        >
          {tab}
        </button>
      ))}
    </div>
  )
}

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
  const [tab, setTab] = useState('概览')
  const query = useQuery({
    queryKey: ['container-node', clusterId, name],
    queryFn: () => nodeApi.get(clusterId, name),
    enabled: !!clusterId && !!name,
  })
  const node = query.data
  return (
    <div className="flex flex-col gap-4">
      <BackTitle to={`/container/clusters/${clusterId}/nodes`} title={name} />
      <TabBar tabs={['概览', '监控']} value={tab} onChange={setTab} />
      {tab === '监控' && <NodeMonitor clusterId={clusterId} name={name} />}
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">节点加载失败</p>}
      {tab === '概览' && node && (
        <>
          <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
            <Field label="状态" value={node.status} />
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
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>类型</TableHead>
                  <TableHead>地址</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(node.addresses ?? []).map((item) => (
                  <TableRow key={`${item.type}-${item.address}`}>
                    <TableCell>{item.type}</TableCell>
                    <TableCell>{item.address}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </section>
          <RecordTable title="容量" record={node.capacity} />
          <RecordTable title="可分配" record={node.allocatable} />
          <RecordTable title="标签" record={node.labels} />
        </>
      )}
    </div>
  )
}

export function PodDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const location = useLocation()
  const navigate = useNavigate()
  const backTo = (location.state as { back?: string } | null)?.back || `/container/clusters/${clusterId}/pods`
  const queryClient = useQueryClient()
  const [tab, setTab] = useState('概览')
  const query = useQuery({
    queryKey: ['container-pod', clusterId, namespace, name],
    queryFn: () => podApi.get(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const events = useQuery({
    queryKey: ['container-pod-events', clusterId, namespace, query.data?.uid],
    queryFn: () => eventApi.list(clusterId, namespace, query.data?.uid),
    enabled: tab === '事件' && !!query.data?.uid,
  })
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
    <div className="flex flex-col gap-4">
      <BackTitle
        to={backTo}
        title={`${namespace}/${name}`}
        extra={
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
      />
      <TabBar tabs={['概览', '事件', '监控', '日志', 'YAML', '终端']} value={tab} onChange={setTab} />
      {tab === '概览' && (
        <>
          {query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
          {query.isError && <p className="text-sm text-destructive">Pod 加载失败</p>}
          {pod && (
            <>
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
                <Field label="状态" value={pod.status} />
                <Field label="节点" value={pod.nodeName} />
                <Field label="IP" value={pod.podIP} />
                <Field label="就绪" value={`${pod.readyContainers}/${pod.containerCount}`} />
                <Field label="重启" value={pod.restarts} />
                <Field label="QoS" value={pod.qosClass} />
                <Field label="Owner" value={pod.ownerReference} />
              </div>
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>容器</TableHead>
                    <TableHead>状态</TableHead>
                    <TableHead>镜像</TableHead>
                    <TableHead>就绪</TableHead>
                    <TableHead>重启</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {pod.containers.map((item) => (
                    <TableRow key={item.name}>
                      <TableCell className="font-medium">{item.name}</TableCell>
                      <TableCell>{item.state}</TableCell>
                      <TableCell className="max-w-sm truncate">{item.image}</TableCell>
                      <TableCell>{item.ready ? '是' : '否'}</TableCell>
                      <TableCell>{item.restartCount}</TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </>
          )}
        </>
      )}
      {tab === '事件' && (
        <>
          {events.isLoading && <p className="text-sm text-muted-foreground">加载事件…</p>}
          {events.isError && <p className="text-sm text-destructive">事件加载失败</p>}
          {events.isSuccess && (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>类型</TableHead>
                  <TableHead>原因</TableHead>
                  <TableHead>消息</TableHead>
                  <TableHead>次数</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {(events.data ?? []).length === 0 && (
                  <TableRow>
                    <TableCell colSpan={4} className="py-8 text-center text-muted-foreground">
                      还没有事件
                    </TableCell>
                  </TableRow>
                )}
                {(events.data ?? []).map((item, index) => (
                  <TableRow key={`${item.reason}-${index}`}>
                    <TableCell>{item.type}</TableCell>
                    <TableCell>{item.reason}</TableCell>
                    <TableCell>{item.message}</TableCell>
                    <TableCell>{item.count ?? '—'}</TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          )}
        </>
      )}
      {tab === '监控' && (
        <PodMonitor clusterId={clusterId} namespace={namespace} name={name} containers={containers} />
      )}
      {tab === '日志' && (
        <PodLogPanel clusterId={clusterId} namespace={namespace} name={name} containers={containers} />
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-pod-yaml', clusterId, namespace, name]}
          load={() => podApi.yaml(clusterId, namespace, name)}
        />
      )}
      {tab === '终端' && (
        <PodShell clusterId={clusterId} namespace={namespace} name={name} containers={containers} />
      )}
    </div>
  )
}

export function DeploymentDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '概览'
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '概览') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }
  const [replicas, setReplicas] = useState('')
  const query = useQuery({
    queryKey: ['container-deployment', clusterId, namespace, name],
    queryFn: () => deploymentApi.get(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const item = query.data
  const scale = useMutation({
    mutationFn: () => deploymentApi.scale(clusterId, namespace, name, Number(replicas)),
    onSuccess: async () => {
      toast.success('已调整副本')
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

  return (
    <DetailShell
      leading={
        <Button variant="ghost" size="sm" asChild>
          <Link to={`/container/clusters/${clusterId}/deployments`}>返回</Link>
        </Button>
      }
      title={`${namespace}/${name}`}
      meta={
        item ? (
          <StatusIcon status={item.status} />
        ) : query.isLoading ? (
          '加载中…'
        ) : query.isError ? (
          '加载失败'
        ) : null
      }
      actions={
        <>
          <Button variant="outline" size="sm" disabled={restart.isPending} onClick={() => restart.mutate()}>
            重启
          </Button>
          <Button
            variant="outline"
            size="sm"
            className="text-destructive"
            onClick={() => {
              if (window.confirm(`删除 Deployment「${name}」？`)) remove.mutate()
            }}
          >
            删除
          </Button>
        </>
      }
      tabs={[
        { value: '概览', label: '概览' },
        { value: '容器', label: '容器' },
        { value: '环境变量', label: '环境变量' },
        { value: '挂载卷', label: '挂载卷' },
        { value: 'YAML', label: 'YAML' },
      ]}
      value={tab}
      onValueChange={setTab}
    >
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
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>容器</TableHead>
                <TableHead>镜像</TableHead>
                <TableHead>CPU</TableHead>
                <TableHead>内存</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(item.containers ?? []).map((container) => (
                <TableRow key={container.name}>
                  <TableCell>{container.name}</TableCell>
                  <TableCell className="max-w-sm truncate">{container.image}</TableCell>
                  <TableCell>{container.cpuRequest || '—'} / {container.cpuLimit || '—'}</TableCell>
                  <TableCell>{container.memRequest || '—'} / {container.memLimit || '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {tab === '概览' && query.isLoading && <p className="text-sm text-muted-foreground">加载中…</p>}
      {tab === '概览' && query.isError && <p className="text-sm text-destructive">Deployment 加载失败</p>}
      {tab === '容器' && (
        <WorkloadPodsPanel
          clusterId={clusterId}
          queryKey={['container-deployment-pods', clusterId, namespace, name]}
          load={() => deploymentApi.pods(clusterId, namespace, name)}
        />
      )}
      {tab === '环境变量' && (
        <WorkloadEnvPanel
          queryKey={['container-deployment-env', clusterId, namespace, name]}
          load={() => deploymentApi.env(clusterId, namespace, name)}
          save={(data) => deploymentApi.updateEnv(clusterId, namespace, name, data)}
        />
      )}
      {tab === '挂载卷' && (
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
    </DetailShell>
  )
}

export function ServiceDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const [tab, setTab] = useState('概览')
  const query = useQuery({
    queryKey: ['container-service', clusterId, namespace, name],
    queryFn: () => serviceApi.get(clusterId, namespace, name),
    enabled: !!clusterId && !!namespace && !!name,
  })
  const item = query.data
  return (
    <div className="flex flex-col gap-4">
      <BackTitle to={`/container/clusters/${clusterId}/services`} title={`${namespace}/${name}`} />
      <TabBar tabs={['概览', 'YAML']} value={tab} onChange={setTab} />
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
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>名称</TableHead>
                <TableHead>端口</TableHead>
                <TableHead>目标端口</TableHead>
                <TableHead>NodePort</TableHead>
                <TableHead>协议</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {(item.ports ?? []).map((port) => (
                <TableRow key={`${port.name}-${port.port}`}>
                  <TableCell>{port.name || '—'}</TableCell>
                  <TableCell>{port.port}</TableCell>
                  <TableCell>{port.targetPort || '—'}</TableCell>
                  <TableCell>{port.nodePort || '—'}</TableCell>
                  <TableCell>{port.protocol || '—'}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}
      {tab === 'YAML' && (
        <YamlPanel
          queryKey={['container-service-yaml', clusterId, namespace, name]}
          load={() => serviceApi.yaml(clusterId, namespace, name)}
        />
      )}
    </div>
  )
}

export function ConfigMapDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const remove = useMutation({
    mutationFn: () => configMapApi.delete(clusterId, namespace, name),
    onSuccess: () => {
      toast.success('ConfigMap 已删除')
      void navigate(`/container/clusters/${clusterId}/configmaps`)
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
  return (
    <div className="flex flex-col gap-4">
      <BackTitle
        to={`/container/clusters/${clusterId}/configmaps`}
        title={`${namespace}/${name}`}
        extra={
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
      />
      <YamlPanel
        queryKey={['container-configmap-yaml', clusterId, namespace, name]}
        load={() => configMapApi.yaml(clusterId, namespace, name)}
        save={(yaml) => configMapApi.updateYaml(clusterId, namespace, name, yaml)}
      />
    </div>
  )
}

export function StatefulSetDetailPage() {
  const { clusterId = '', namespace = '', name = '' } = useParams()
  const navigate = useNavigate()
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || '环境变量'
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === '环境变量') next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }
  const remove = useMutation({
    mutationFn: () => statefulSetApi.delete(clusterId, namespace, name),
    onSuccess: () => {
      toast.success('StatefulSet 已删除')
      void navigate(`/container/clusters/${clusterId}/statefulsets`)
    },
    onError: (error: Error) => toast.error(error.message || '删除失败'),
  })
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
      {tab === '容器' && (
        <WorkloadPodsPanel
          clusterId={clusterId}
          queryKey={['container-statefulset-pods', clusterId, namespace, name]}
          load={() => statefulSetApi.pods(clusterId, namespace, name)}
        />
      )}
      {tab === '挂载卷' && (
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
  const entries = Object.entries(record ?? {})
  if (entries.length === 0) return null
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-sm font-medium">{title}</h2>
      <Table>
        <TableHeader>
          <TableRow>
            <TableHead>键</TableHead>
            <TableHead>值</TableHead>
          </TableRow>
        </TableHeader>
        <TableBody>
          {entries.map(([key, value]) => (
            <TableRow key={key}>
              <TableCell>{key}</TableCell>
              <TableCell>{value}</TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </section>
  )
}
