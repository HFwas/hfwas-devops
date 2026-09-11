<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import { SERIES_COLORS, toChartPoints, type MonitorRange, type MonitorSeries } from '@/modules/container/types/monitor'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import MonitorStackChart from '@/modules/container/components/MonitorStackChart.vue'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{
  clusterId: string
  namespace: string
  name: string
  range: MonitorRange
}>()

const message = useMessage()

type ChartSeries = { name: string; color: string; data: [number, number][] }

const heapSeries = ref<ChartSeries[]>([])
const poolSeries = ref<ChartSeries[]>([])
const afterGcSeries = ref<ChartSeries[]>([])
const gcCountSeries = ref<ChartSeries[]>([])
const gcElapsedSeries = ref<ChartSeries[]>([])
const threadTotalSeries = ref<ChartSeries[]>([])
const threadStateSeries = ref<ChartSeries[]>([])
const classCountSeries = ref<ChartSeries[]>([])
const classLoadSeries = ref<ChartSeries[]>([])
const nonHeapSeries = ref<ChartSeries[]>([])
const cpuUtilSeries = ref<ChartSeries[]>([])
const cpuTimeSeries = ref<ChartSeries[]>([])
const cpuCoreRef = ref<{ value: number; label: string; color: string }[]>([])

const loading = ref(false)

function colorAt(i: number): string {
  return SERIES_COLORS[i % SERIES_COLORS.length]
}

function poolName(s: MonitorSeries): string {
  return s.labels.pool || s.labels.jvm_memory_pool_name || 'unknown'
}

function poolColor(pool: string): string {
  if (pool.includes('Eden')) return '#2080f0'
  if (pool.includes('Survivor')) return '#18a058'
  if (pool.includes('Old')) return '#f0a020'
  if (pool.includes('Metaspace')) return '#a060d0'
  return '#aaa'
}

function gcName(s: MonitorSeries): string {
  return s.labels.jvm_gc_name || s.labels.jvm_gc_action || 'GC'
}

function threadStateName(s: MonitorSeries): string {
  const daemon = s.labels.jvm_thread_daemon === 'true' ? '守护' : '业务'
  const stateMap: Record<string, string> = {
    runnable: 'runnable',
    waiting: 'waiting',
    timed_waiting: 'timed_waiting',
    blocked: 'blocked',
    new: 'new',
    terminated: 'terminated',
  }
  const state = stateMap[s.labels.jvm_thread_state || ''] || s.labels.jvm_thread_state || 'unknown'
  return `${daemon} / ${state}`
}

function lastPoint(s: MonitorSeries | undefined): number | undefined {
  if (!s?.points?.length) return undefined
  return Number(s.points[s.points.length - 1].value)
}

async function load() {
  loading.value = true
  try {
    const [heapData, nonHeapData, gcData, threadData, poolData, classData, cpuData, afterGcData] = await Promise.all([
      monitorApi.jvmHeap(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmNonHeap(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmGc(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmThread(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmMemoryPools(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmClass(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmCpu(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmAfterGc(props.clusterId, props.namespace, props.name, props.range),
    ])

    const heapUsed = heapData.find(s => s.labels.metric === 'used')
    const heapMax = heapData.find(s => s.labels.metric === 'max')
    const heapCommitted = heapData.find(s => s.labels.metric === 'committed')

    heapSeries.value = []
    if (heapUsed) {
      heapSeries.value.push({ name: '已用', color: '#2080f0', data: toChartPoints(heapUsed.points) })
    }
    if (heapCommitted) {
      heapSeries.value.push({ name: '承诺', color: '#f0a020', data: toChartPoints(heapCommitted.points) })
    }
    if (heapMax) {
      heapSeries.value.push({ name: '上限', color: '#aaa', data: toChartPoints(heapMax.points) })
    }

    nonHeapSeries.value = nonHeapData.map(s => ({
      name: '非堆',
      color: '#18a058',
      data: toChartPoints(s.points),
    }))

    gcCountSeries.value = gcData.filter(s => s.labels.metric === 'count').map((s, i) => ({
      name: gcName(s),
      color: colorAt(i),
      data: toChartPoints(s.points),
    }))
    gcElapsedSeries.value = gcData.filter(s => s.labels.metric === 'elapsed').map((s, i) => ({
      name: gcName(s),
      color: colorAt(i),
      data: toChartPoints(s.points),
    }))

    threadTotalSeries.value = threadData.filter(s => s.labels.metric === 'total').map(s => ({
      name: '合计',
      color: '#2080f0',
      data: toChartPoints(s.points),
    }))
    threadStateSeries.value = threadData.filter(s => s.labels.metric === 'by_state').map((s, i) => ({
      name: threadStateName(s),
      color: colorAt(i),
      data: toChartPoints(s.points),
    }))

    poolSeries.value = poolData.map(s => {
      const name = poolName(s)
      return {
        name,
        color: poolColor(name),
        data: toChartPoints(s.points),
      }
    })

    afterGcSeries.value = afterGcData.map(s => {
      const name = poolName(s)
      return {
        name,
        color: poolColor(name),
        data: toChartPoints(s.points),
      }
    })

    const classCurrent = classData.find(s => s.labels.metric === 'current')
    const classLoaded = classData.find(s => s.labels.metric === 'loaded')
    const classUnloaded = classData.find(s => s.labels.metric === 'unloaded')
    classCountSeries.value = classCurrent
      ? [{ name: '当前已加载', color: '#2080f0', data: toChartPoints(classCurrent.points) }]
      : []
    classLoadSeries.value = []
    if (classLoaded) {
      classLoadSeries.value.push({ name: '累计加载', color: '#18a058', data: toChartPoints(classLoaded.points) })
    }
    if (classUnloaded) {
      classLoadSeries.value.push({ name: '累计卸载', color: '#d03050', data: toChartPoints(classUnloaded.points) })
    }

    const cpuUtil = cpuData.find(s => s.labels.metric === 'utilization')
    const cpuTime = cpuData.find(s => s.labels.metric === 'cpu_time')
    const cpuCores = cpuData.find(s => s.labels.metric === 'cores')
    cpuUtilSeries.value = cpuUtil
      ? [{ name: '利用率', color: '#2080f0', data: toChartPoints(cpuUtil.points) }]
      : []
    cpuTimeSeries.value = cpuTime
      ? [{ name: 'CPU 时间', color: '#f0a020', data: toChartPoints(cpuTime.points) }]
      : []
    const cores = lastPoint(cpuCores)
    cpuCoreRef.value = cores != null
      ? [{ value: cores, label: `可用核 ${cores}`, color: '#aaa' }]
      : []
  } catch (e) {
    message.error(isApiError(e) ? e.message : '加载 JVM 监控数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
watch(() => props.range, () => load())
</script>

<template>
  <div>
    <n-grid :cols="2" :x-gap="12" :y-gap="12">
      <n-gi :span="2">
        <MonitorLineChart title="JVM 堆内存" :series="heapSeries" y-axis-label="bytes"
          :loading="loading" :empty="heapSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorStackChart title="内存池分布" :series="poolSeries" y-axis-label="bytes"
          :loading="loading" :empty="poolSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorStackChart title="GC 后堆内存" :series="afterGcSeries" y-axis-label="bytes"
          :loading="loading" :empty="afterGcSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="GC 次数（按收集器）" :series="gcCountSeries"
          :loading="loading" :empty="gcCountSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="GC 耗时（按收集器）" :series="gcElapsedSeries" y-axis-label="s"
          :loading="loading" :empty="gcElapsedSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="线程数合计" :series="threadTotalSeries"
          :loading="loading" :empty="threadTotalSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorStackChart title="线程构成" :series="threadStateSeries"
          :loading="loading" :empty="threadStateSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="非堆内存" :series="nonHeapSeries" y-axis-label="bytes"
          :loading="loading" :empty="nonHeapSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="当前已加载类" :series="classCountSeries"
          :loading="loading" :empty="classCountSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="类加载 / 卸载累计" :series="classLoadSeries"
          :loading="loading" :empty="classLoadSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="JVM CPU 利用率" :series="cpuUtilSeries" y-axis-label="%"
          :loading="loading" :empty="cpuUtilSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="JVM CPU 时间" :series="cpuTimeSeries" y-axis-label="core"
          :loading="loading" :empty="cpuTimeSeries.length === 0" :reference-lines="cpuCoreRef" />
      </n-gi>
    </n-grid>
  </div>
</template>
