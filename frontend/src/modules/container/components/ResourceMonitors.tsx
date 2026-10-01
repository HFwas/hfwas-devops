import { useState, type ReactNode } from 'react'
import { useQuery } from '@tanstack/react-query'
import { MonitorLineChart, type ChartSeries } from '@/modules/container/components/MonitorLineChart'
import { MonitorTimeRange } from '@/modules/container/components/MonitorTimeRange'
import { monitorApi } from '@/modules/container/api/monitor'
import { SERIES_COLORS, toChartPoints, type MonitorRange, type MonitorSeries } from '@/modules/container/types/monitor'

function paint(
  series: MonitorSeries[] | undefined,
  nameOf: (item: MonitorSeries, index: number) => string,
  colorOf: (item: MonitorSeries, index: number) => string,
): ChartSeries[] {
  return (series ?? []).map((item, index) => ({
    name: nameOf(item, index),
    color: colorOf(item, index),
    data: toChartPoints(item.points),
  }))
}

function directionName(item: MonitorSeries, receive: string, send: string) {
  return item.labels.direction === 'receive' || item.labels.direction === 'read' ? receive : send
}

export function ClusterMonitor({ clusterId }: { clusterId: string }) {
  const [range, setRange] = useState<MonitorRange>('1h')
  const cpu = useQuery({
    queryKey: ['monitor-cluster-cpu', clusterId, range],
    queryFn: () => monitorApi.nodeCpu(clusterId, '.*', range),
    enabled: !!clusterId,
  })
  const memory = useQuery({
    queryKey: ['monitor-cluster-memory', clusterId, range],
    queryFn: () => monitorApi.nodeMemory(clusterId, '.*', range),
    enabled: !!clusterId,
  })
  const network = useQuery({
    queryKey: ['monitor-cluster-network', clusterId, range],
    queryFn: () => monitorApi.nodeNetwork(clusterId, '.*', range),
    enabled: !!clusterId,
  })
  const loading = cpu.isLoading || memory.isLoading || network.isLoading
  const failed = cpu.isError && memory.isError && network.isError

  return (
    <MonitorGrid
      range={range}
      onRange={setRange}
      loading={loading}
      failed={failed}
      charts={[
        {
          title: '节点 CPU',
          yAxisLabel: '%',
          series: paint(cpu.data, (item) => item.labels.nodename || item.labels.instance || 'CPU', (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length]),
        },
        {
          title: '节点内存',
          yAxisLabel: '%',
          series: paint(memory.data, (item) => item.labels.nodename || item.labels.instance || '内存', (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length]),
        },
        {
          title: '网络 IO',
          yAxisLabel: 'bytes/s',
          wide: true,
          series: paint(
            network.data,
            (item) => `${item.labels.device || 'eth'} ${directionName(item, 'RX', 'TX')}`,
            (item) => (item.labels.direction === 'receive' ? SERIES_COLORS[2] : SERIES_COLORS[3]),
          ),
        },
      ]}
    />
  )
}

export function NodeMonitor({ clusterId, name }: { clusterId: string; name: string }) {
  const [range, setRange] = useState<MonitorRange>('1h')
  const enabled = !!clusterId && !!name
  const cpu = useQuery({
    queryKey: ['monitor-node-cpu', clusterId, name, range],
    queryFn: () => monitorApi.nodeCpu(clusterId, name, range),
    enabled,
  })
  const memory = useQuery({
    queryKey: ['monitor-node-memory', clusterId, name, range],
    queryFn: () => monitorApi.nodeMemory(clusterId, name, range),
    enabled,
  })
  const network = useQuery({
    queryKey: ['monitor-node-network', clusterId, name, range],
    queryFn: () => monitorApi.nodeNetwork(clusterId, name, range),
    enabled,
  })
  const disk = useQuery({
    queryKey: ['monitor-node-disk', clusterId, name, range],
    queryFn: () => monitorApi.nodeDisk(clusterId, name, range),
    enabled,
  })
  const connections = useQuery({
    queryKey: ['monitor-node-connections', clusterId, name, range],
    queryFn: () => monitorApi.nodeConnections(clusterId, name, range),
    enabled,
  })
  const load = useQuery({
    queryKey: ['monitor-node-load', clusterId, name, range],
    queryFn: () => monitorApi.nodeLoad1(clusterId, name, range),
    enabled,
  })
  const queries = [cpu, memory, network, disk, connections, load]
  return (
    <MonitorGrid
      range={range}
      onRange={setRange}
      loading={queries.some((query) => query.isLoading)}
      failed={queries.every((query) => query.isError)}
      charts={[
        { title: 'CPU 使用率', yAxisLabel: '%', series: paint(cpu.data, () => 'CPU', () => SERIES_COLORS[0]) },
        { title: '内存使用率', yAxisLabel: '%', series: paint(memory.data, () => '内存', () => SERIES_COLORS[1]) },
        {
          title: '网络 IO',
          yAxisLabel: 'bytes/s',
          series: paint(
            network.data,
            (item) => `${item.labels.device || 'eth'} ${directionName(item, 'RX', 'TX')}`,
            (item) => (item.labels.direction === 'receive' ? SERIES_COLORS[2] : SERIES_COLORS[3]),
          ),
        },
        {
          title: '磁盘 IO',
          yAxisLabel: 'bytes/s',
          series: paint(
            disk.data,
            (item) => directionName(item, '读', '写'),
            (item) => (item.labels.direction === 'read' ? SERIES_COLORS[4] : SERIES_COLORS[1]),
          ),
        },
        { title: 'TCP 连接数', wide: true, series: paint(connections.data, () => 'TCP 连接数', () => SERIES_COLORS[0]) },
        { title: '1 分钟负载', wide: true, series: paint(load.data, () => 'load1', () => SERIES_COLORS[2]) },
      ]}
    />
  )
}

export function PodMonitor({
  clusterId,
  namespace,
  name,
  containers,
}: {
  clusterId: string
  namespace: string
  name: string
  containers: string[]
}) {
  const [range, setRange] = useState<MonitorRange>('1h')
  const [container, setContainer] = useState('')
  const enabled = !!clusterId && !!namespace && !!name
  const cpu = useQuery({
    queryKey: ['monitor-pod-cpu', clusterId, namespace, name, range],
    queryFn: () => monitorApi.podCpu(clusterId, namespace, name, range),
    enabled,
  })
  const memory = useQuery({
    queryKey: ['monitor-pod-memory', clusterId, namespace, name, range],
    queryFn: () => monitorApi.podMemory(clusterId, namespace, name, range),
    enabled,
  })
  const network = useQuery({
    queryKey: ['monitor-pod-network', clusterId, namespace, name, range],
    queryFn: () => monitorApi.podNetwork(clusterId, namespace, name, range),
    enabled,
  })
  const jvm = useQuery({
    queryKey: ['monitor-pod-jvm-check', clusterId, namespace, name],
    queryFn: () => monitorApi.jvmCheck(clusterId, namespace, name),
    enabled,
  })
  const filter = (series: MonitorSeries[] | undefined) => {
    if (!container) return series
    return (series ?? []).filter((item) => item.labels.container === container)
  }
  return (
    <div className="flex flex-col gap-4">
      <MonitorGrid
        range={range}
        onRange={setRange}
        loading={cpu.isLoading || memory.isLoading || network.isLoading}
        failed={cpu.isError && memory.isError && network.isError}
        extra={
          containers.length > 1 ? (
            <select
              className="h-8 rounded-md border border-input bg-background px-2 text-sm"
              value={container}
              onChange={(event) => setContainer(event.target.value)}
            >
              <option value="">全部容器</option>
              {containers.map((item) => (
                <option key={item} value={item}>
                  {item}
                </option>
              ))}
            </select>
          ) : undefined
        }
        charts={[
          {
            title: 'CPU 使用',
            yAxisLabel: 'millicores',
            wide: true,
            series: paint(filter(cpu.data), (item) => item.labels.container || 'cpu', (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length]),
          },
          {
            title: '内存使用',
            yAxisLabel: 'bytes',
            wide: true,
            series: paint(filter(memory.data), (item) => item.labels.container || 'memory', (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length]),
          },
          {
            title: '网络 IO',
            yAxisLabel: 'bytes/s',
            wide: true,
            series: paint(
              network.data,
              (item) => directionName(item, 'RX', 'TX'),
              (item) => (item.labels.direction === 'receive' ? SERIES_COLORS[2] : SERIES_COLORS[3]),
            ),
          },
        ]}
      />
      {jvm.data?.hasJvmMetrics && (
        <JvmMonitor clusterId={clusterId} namespace={namespace} name={name} range={range} />
      )}
    </div>
  )
}

function JvmMonitor({
  clusterId,
  namespace,
  name,
  range,
}: {
  clusterId: string
  namespace: string
  name: string
  range: MonitorRange
}) {
  const enabled = !!clusterId && !!namespace && !!name
  const heap = useQuery({
    queryKey: ['monitor-jvm-heap', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmHeap(clusterId, namespace, name, range),
    enabled,
  })
  const nonHeap = useQuery({
    queryKey: ['monitor-jvm-nonheap', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmNonHeap(clusterId, namespace, name, range),
    enabled,
  })
  const gc = useQuery({
    queryKey: ['monitor-jvm-gc', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmGc(clusterId, namespace, name, range),
    enabled,
  })
  const threads = useQuery({
    queryKey: ['monitor-jvm-thread', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmThread(clusterId, namespace, name, range),
    enabled,
  })
  const pools = useQuery({
    queryKey: ['monitor-jvm-pools', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmMemoryPools(clusterId, namespace, name, range),
    enabled,
  })
  const classes = useQuery({
    queryKey: ['monitor-jvm-class', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmClass(clusterId, namespace, name, range),
    enabled,
  })
  const cpu = useQuery({
    queryKey: ['monitor-jvm-cpu', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmCpu(clusterId, namespace, name, range),
    enabled,
  })
  const afterGc = useQuery({
    queryKey: ['monitor-jvm-after-gc', clusterId, namespace, name, range],
    queryFn: () => monitorApi.jvmAfterGc(clusterId, namespace, name, range),
    enabled,
  })

  const heapSeries = (heap.data ?? []).flatMap((item) => {
    const metric = item.labels.metric
    if (metric === 'used') return [{ name: '已用', color: SERIES_COLORS[0], data: toChartPoints(item.points) }]
    if (metric === 'committed') return [{ name: '承诺', color: SERIES_COLORS[2], data: toChartPoints(item.points) }]
    if (metric === 'max') return [{ name: '上限', color: SERIES_COLORS[7], data: toChartPoints(item.points) }]
    return []
  })
  const classCurrent = (classes.data ?? []).find((item) => item.labels.metric === 'current')
  const classLoaded = (classes.data ?? []).filter((item) => item.labels.metric === 'loaded' || item.labels.metric === 'unloaded')
  const cpuUtil = (cpu.data ?? []).find((item) => item.labels.metric === 'utilization')
  const cpuTime = (cpu.data ?? []).find((item) => item.labels.metric === 'cpu_time')
  const cpuCores = (cpu.data ?? []).find((item) => item.labels.metric === 'cores')
  const coreValue = cpuCores?.points?.length ? Number(cpuCores.points[cpuCores.points.length - 1].value) : undefined
  const loading = [heap, nonHeap, gc, threads, pools, classes, cpu, afterGc].some((query) => query.isLoading)

  return (
    <div className="grid gap-3 lg:grid-cols-2">
      <MonitorLineChart title="JVM 堆内存" series={heapSeries} yAxisLabel="bytes" loading={loading} />
      <MonitorLineChart
        title="内存池分布"
        stacked
        yAxisLabel="bytes"
        loading={loading}
        series={paint(pools.data, poolName, (item) => poolColor(poolName(item)))}
      />
      <MonitorLineChart
        title="GC 后堆内存"
        stacked
        yAxisLabel="bytes"
        loading={loading}
        series={paint(afterGc.data, poolName, (item) => poolColor(poolName(item)))}
      />
      <MonitorLineChart
        title="非堆内存"
        yAxisLabel="bytes"
        loading={loading}
        series={paint(nonHeap.data, () => '非堆', () => SERIES_COLORS[1])}
      />
      <MonitorLineChart
        title="GC 次数"
        loading={loading}
        series={paint(
          (gc.data ?? []).filter((item) => item.labels.metric === 'count'),
          gcName,
          (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length],
        )}
      />
      <MonitorLineChart
        title="GC 耗时"
        yAxisLabel="s"
        loading={loading}
        series={paint(
          (gc.data ?? []).filter((item) => item.labels.metric === 'elapsed'),
          gcName,
          (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length],
        )}
      />
      <MonitorLineChart
        title="线程数"
        loading={loading}
        series={paint(
          (threads.data ?? []).filter((item) => item.labels.metric === 'total'),
          () => '合计',
          () => SERIES_COLORS[0],
        )}
      />
      <MonitorLineChart
        title="线程构成"
        stacked
        loading={loading}
        series={paint(
          (threads.data ?? []).filter((item) => item.labels.metric === 'by_state'),
          threadStateName,
          (_item, index) => SERIES_COLORS[index % SERIES_COLORS.length],
        )}
      />
      <MonitorLineChart
        title="当前已加载类"
        loading={loading}
        series={classCurrent ? [{ name: '当前已加载', color: SERIES_COLORS[0], data: toChartPoints(classCurrent.points) }] : []}
      />
      <MonitorLineChart
        title="类加载 / 卸载累计"
        loading={loading}
        series={classLoaded.map((item, index) => ({
          name: item.labels.metric === 'loaded' ? '累计加载' : '累计卸载',
          color: SERIES_COLORS[index % SERIES_COLORS.length],
          data: toChartPoints(item.points),
        }))}
      />
      <MonitorLineChart
        title="JVM CPU 利用率"
        yAxisLabel="%"
        loading={loading}
        series={cpuUtil ? [{ name: '利用率', color: SERIES_COLORS[0], data: toChartPoints(cpuUtil.points) }] : []}
      />
      <MonitorLineChart
        title="JVM CPU 时间"
        yAxisLabel="core"
        loading={loading}
        series={cpuTime ? [{ name: 'CPU 时间', color: SERIES_COLORS[2], data: toChartPoints(cpuTime.points) }] : []}
        referenceLines={
          coreValue != null && Number.isFinite(coreValue)
            ? [{ value: coreValue, label: '核数', color: SERIES_COLORS[3] }]
            : undefined
        }
      />
    </div>
  )
}

function poolName(item: MonitorSeries) {
  return item.labels.pool || item.labels.jvm_memory_pool_name || 'unknown'
}

function poolColor(name: string) {
  if (name.includes('Eden')) return SERIES_COLORS[0]
  if (name.includes('Survivor')) return SERIES_COLORS[1]
  if (name.includes('Old')) return SERIES_COLORS[2]
  if (name.includes('Metaspace')) return SERIES_COLORS[4]
  return SERIES_COLORS[7]
}

function gcName(item: MonitorSeries) {
  return item.labels.jvm_gc_name || item.labels.jvm_gc_action || 'GC'
}

function threadStateName(item: MonitorSeries) {
  const daemon = item.labels.jvm_thread_daemon === 'true' ? '守护' : '业务'
  const state = item.labels.jvm_thread_state || 'unknown'
  return `${daemon} / ${state}`
}

function MonitorGrid({
  range,
  onRange,
  charts,
  loading,
  failed,
  extra,
}: {
  range: MonitorRange
  onRange: (value: MonitorRange) => void
  charts: { title: string; series: ChartSeries[]; yAxisLabel?: string; wide?: boolean; stacked?: boolean }[]
  loading: boolean
  failed: boolean
  extra?: ReactNode
}) {
  return (
    <div className="flex flex-col gap-3">
      <MonitorTimeRange value={range} onChange={onRange} extra={extra} />
      {failed && <p className="text-sm text-destructive">监控数据加载失败</p>}
      <div className="grid gap-3 lg:grid-cols-2">
        {charts.map((chart) => (
          <div key={chart.title} className={chart.wide ? 'lg:col-span-2' : undefined}>
            <MonitorLineChart
              title={chart.title}
              series={chart.series}
              yAxisLabel={chart.yAxisLabel}
              loading={loading}
              stacked={chart.stacked}
            />
          </div>
        ))}
      </div>
    </div>
  )
}
