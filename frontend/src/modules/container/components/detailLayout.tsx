import type { ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { Copy, FileText, RefreshCw, RotateCw, Trash2 } from 'lucide-react'
import { toast } from 'sonner'
import { DataTable } from '@/components/console/DataTable'
import type { DetailTab } from '@/components/console/DetailShell'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Button } from '@/components/ui/button'
import { eventApi } from '@/modules/container/api/event'
import type { EventInfo, PodSummary } from '@/modules/container/types/resource'

export function useDetailTab(fallback = '概览') {
  const [searchParams, setSearchParams] = useSearchParams()
  const tab = searchParams.get('tab') || fallback
  const setTab = (value: string) => {
    const next = new URLSearchParams(searchParams)
    if (value === fallback) next.delete('tab')
    else next.set('tab', value)
    setSearchParams(next, { replace: true })
  }
  return [tab, setTab] as const
}

export function countLabel(label: string, count?: number) {
  return count == null ? label : `${label} (${count})`
}

export function StubPanel({ label }: { label: string }) {
  return (
    <div className="flex h-40 items-center justify-center rounded-lg border bg-card text-sm text-muted-foreground">
      {label}即将支持
    </div>
  )
}

export function ResourceActions({
  onRefresh,
  refreshing,
  onRestart,
  restarting,
  showRestart = false,
  onDelete,
  deleting,
}: {
  onRefresh: () => void
  refreshing?: boolean
  onRestart?: () => void
  restarting?: boolean
  showRestart?: boolean
  onDelete?: () => void
  deleting?: boolean
}) {
  const soon = (name: string) => toast.message(`${name}即将支持`)
  return (
    <>
      <Button variant="outline" size="sm" disabled={refreshing} onClick={onRefresh}>
        <RefreshCw />
        刷新
      </Button>
      <Button variant="outline" size="sm" onClick={() => soon('描述')}>
        <FileText />
        描述
      </Button>
      <Button variant="outline" size="sm" onClick={() => soon('克隆')}>
        <Copy />
        克隆
      </Button>
      {showRestart ? (
        <Button
          variant="outline"
          size="sm"
          disabled={restarting}
          onClick={onRestart ?? (() => soon('重启'))}
        >
          <RotateCw />
          重启
        </Button>
      ) : null}
      {onDelete ? (
        <Button variant="destructive" size="sm" disabled={deleting} onClick={onDelete}>
          <Trash2 />
          删除
        </Button>
      ) : null}
    </>
  )
}

export function workloadTabs(counts: { pods?: number; containers?: number; volumes?: number }): DetailTab[] {
  return [
    { value: '概览', label: '概览' },
    { value: 'Pods', label: countLabel('Pods', counts.pods) },
    { value: '容器', label: countLabel('容器', counts.containers) },
    { value: 'YAML', label: 'YAML' },
    { value: '日志', label: '日志' },
    { value: '终端', label: '终端' },
    { value: '卷', label: countLabel('卷', counts.volumes) },
    { value: '关联', label: '关联' },
    { value: '历史', label: '历史' },
    { value: '事件', label: '事件' },
    { value: '监控', label: '监控' },
  ]
}

export function useResourceEvents(clusterId: string, namespace: string, uid: string | undefined, enabled: boolean) {
  return useQuery({
    queryKey: ['container-resource-events', clusterId, namespace, uid],
    queryFn: () => eventApi.list(clusterId, namespace, uid),
    enabled: enabled && !!clusterId && !!namespace && !!uid,
  })
}

export function EventSummary({
  events,
  loading,
  error,
}: {
  events?: EventInfo[]
  loading: boolean
  error: boolean
}) {
  if (loading) return <p className="text-sm text-muted-foreground">加载事件…</p>
  if (error) return <p className="text-sm text-destructive">事件加载失败</p>
  if (!events?.length) return <p className="text-sm text-muted-foreground">暂无近期事件</p>
  return (
    <ul className="flex flex-col gap-3">
      {events.slice(0, 6).map((item, index) => (
        <li key={`${item.reason}-${item.lastTimestamp ?? index}`}>
          <div className="flex items-center gap-2 text-sm">
            <StatusIcon
              variant="dot"
              status={item.type}
              tone={item.type === 'Warning' ? 'warning' : 'success'}
              showLabel={false}
            />
            <span className="font-medium">{item.reason || item.type}</span>
            {item.count != null ? <span className="text-muted-foreground">×{item.count}</span> : null}
          </div>
          {item.message ? <p className="mt-1 text-sm text-muted-foreground">{item.message}</p> : null}
        </li>
      ))}
    </ul>
  )
}

export function EventTable({
  events,
  loading,
  error,
}: {
  events?: EventInfo[]
  loading: boolean
  error: boolean
}) {
  return (
    <DataTable
      columns={[
        { id: 'type', header: '类型', cell: (row) => row.type },
        { id: 'reason', header: '原因', cell: (row) => row.reason },
        { id: 'message', header: '消息', cell: (row) => row.message },
        { id: 'count', header: '次数', cell: (row) => (row.count == null ? '—' : String(row.count)) },
        { id: 'last', header: '最近', cell: (row) => row.lastTimestamp || '—' },
      ]}
      data={events ?? []}
      getRowId={(row) => `${row.reason}-${row.lastTimestamp ?? ''}-${row.message}`}
      loading={loading}
      error={error ? '事件加载失败' : undefined}
      empty="还没有事件"
    />
  )
}

export function PodMiniTable({ clusterId, pods, loading }: { clusterId: string; pods: PodSummary[]; loading: boolean }) {
  return (
    <DataTable
      columns={[
        {
          id: 'name',
          header: 'Pod',
          cell: (pod) => (
            <Link
              className="text-primary hover:underline"
              to={`/container/clusters/${clusterId}/pods/${pod.namespace}/${encodeURIComponent(pod.name)}`}
            >
              <span className="block">{pod.name}</span>
              {pod.podIP ? <span className="block text-xs font-normal text-muted-foreground">{pod.podIP}</span> : null}
            </Link>
          ),
        },
        { id: 'status', header: '状态', cell: (pod) => <StatusIcon variant="dot" status={pod.status} /> },
        {
          id: 'ready',
          header: '就绪',
          cell: (pod) => `${pod.readyContainers}/${pod.containerCount}`,
        },
        { id: 'restart', header: '重启', cell: (pod) => String(pod.restarts) },
        { id: 'node', header: '节点', cell: (pod) => pod.nodeName || '—' },
      ]}
      data={pods}
      getRowId={(pod) => `${pod.namespace}/${pod.name}`}
      loading={loading}
      empty="还没有 Pod"
    />
  )
}

export function PodShortcutList({
  clusterId,
  pods,
  tab,
  loading,
  empty,
}: {
  clusterId: string
  pods: PodSummary[]
  tab: string
  loading: boolean
  empty: string
}) {
  if (loading) return <p className="text-sm text-muted-foreground">加载 Pod…</p>
  if (pods.length === 0) return <p className="text-sm text-muted-foreground">{empty}</p>
  return (
    <div className="flex flex-col gap-3">
      <p className="text-sm text-muted-foreground">打开对应 Pod 查看{tab}</p>
      <ul className="flex flex-col gap-2 text-sm">
        {pods.map((pod) => (
          <li key={`${pod.namespace}/${pod.name}`}>
            <Link
              className="text-primary hover:underline"
              to={`/container/clusters/${clusterId}/pods/${pod.namespace}/${encodeURIComponent(pod.name)}?tab=${encodeURIComponent(tab)}`}
            >
              {pod.namespace}/{pod.name}
            </Link>
          </li>
        ))}
      </ul>
    </div>
  )
}

export function RelatedList({ items }: { items: { kind: string; name: string }[] }) {
  if (items.length === 0) return <p className="text-sm text-muted-foreground">暂无关联资源</p>
  return (
    <ul className="flex flex-col gap-2 text-sm">
      {items.map((item) => (
        <li key={`${item.kind}-${item.name}`} className="flex items-center gap-2">
          <span className="text-muted-foreground">{item.kind}</span>
          <span className="font-medium">{item.name}</span>
        </li>
      ))}
    </ul>
  )
}

export function TextBlock({ children }: { children: ReactNode }) {
  return <p className="text-sm text-muted-foreground">{children}</p>
}
