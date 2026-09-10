<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import type { MonitorRange } from '@/modules/container/types/monitor'
import MonitorStatCard from '@/modules/container/components/MonitorStatCard.vue'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import MonitorStackChart from '@/modules/container/components/MonitorStackChart.vue'
import MonitorBarChart from '@/modules/container/components/MonitorBarChart.vue'
import { formatBytes, formatPercent } from '@/modules/container/utils/format'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{
  clusterId: string
  namespace: string
  name: string
  range: MonitorRange
}>()

const message = useMessage()

const heapSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const poolSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const gcCountSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const gcElapsedSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const threadSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const classSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const nonHeapSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])

const statHeapPct = ref(0)
const statNonHeap = ref('0')
const statThreads = ref(0)
const statGcFreq = ref(0)

const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const [heapData, nonHeapData, gcData, threadData, poolData] = await Promise.all([
      monitorApi.jvmHeap(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmNonHeap(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmGc(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmThread(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.jvmMemoryPools(props.clusterId, props.namespace, props.name, props.range),
    ])

    // Heap: used / committed / max
    const heapUsed = heapData.find(s => s.labels.metric === 'used')
    const heapMax = heapData.find(s => s.labels.metric === 'max')
    const heapCommitted = heapData.find(s => s.labels.metric === 'committed')

    heapSeries.value = []
    if (heapUsed) {
      heapSeries.value.push({
        name: '已用',
        color: '#2080f0',
        data: heapUsed.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
      })
    }
    if (heapCommitted) {
      heapSeries.value.push({
        name: '承诺',
        color: '#f0a020',
        data: heapCommitted.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
      })
    }
    if (heapMax) {
      heapSeries.value.push({
        name: '上限',
        color: '#aaa',
        data: heapMax.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
      })
    }

    // Non-heap
    nonHeapSeries.value = nonHeapData.map(s => ({
      name: '非堆',
      color: '#18a058',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    // GC
    const gcCount = gcData.find(s => s.labels.metric === 'count')
    const gcElapsed = gcData.find(s => s.labels.metric === 'elapsed')
    gcCountSeries.value = gcCount ? [{
      name: 'GC 次数',
      color: '#2080f0',
      data: gcCount.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }] : []
    gcElapsedSeries.value = gcElapsed ? [{
      name: 'GC 耗时',
      color: '#d03050',
      data: gcElapsed.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }] : []

    // Thread
    threadSeries.value = threadData.map(s => ({
      name: '线程数',
      color: '#2080f0',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    // Memory pools (stacked)
    poolSeries.value = poolData.map(s => ({
      name: s.labels.pool || 'unknown',
      color: poolColor(s.labels.pool || ''),
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    // Stat cards: compute latest values
    if (heapUsed?.points.length && heapMax?.points.length) {
      const lastUsed = heapUsed.points[heapUsed.points.length - 1].value
      const lastMax = heapMax.points[heapMax.points.length - 1].value
      statHeapPct.value = lastMax > 0 ? (lastUsed / lastMax) * 100 : 0
    }
    if (nonHeapData.length && nonHeapData[0].points.length) {
      statNonHeap.value = formatBytes(nonHeapData[0].points[nonHeapData[0].points.length - 1].value)
    }
    if (threadData.length && threadData[0].points.length) {
      statThreads.value = threadData[0].points[threadData[0].points.length - 1].value
    }
    if (gcCount?.points.length) {
      statGcFreq.value = gcCount.points[gcCount.points.length - 1].value
    }
  } catch (e) {
    message.error(isApiError(e) ? e.message : '加载 JVM 监控数据失败')
  } finally {
    loading.value = false
  }
}

function poolColor(pool: string): string {
  if (pool.includes('Eden')) return '#2080f0'
  if (pool.includes('Survivor')) return '#18a058'
  if (pool.includes('Old')) return '#f0a020'
  if (pool.includes('Metaspace')) return '#a060d0'
  return '#aaa'
}

onMounted(load)
watch(() => props.range, () => load())
</script>

<template>
  <div>
    <!-- JVM Stat Cards -->
    <n-grid :cols="4" :x-gap="12" style="margin-bottom: 16px">
      <n-gi>
        <MonitorStatCard title="堆内存使用率" :value="statHeapPct.toFixed(1)" unit="%" :loading="loading" />
      </n-gi>
      <n-gi>
        <MonitorStatCard title="非堆使用" :value="statNonHeap" :loading="loading" />
      </n-gi>
      <n-gi>
        <MonitorStatCard title="线程数" :value="statThreads" :loading="loading" />
      </n-gi>
      <n-gi>
        <MonitorStatCard title="GC 频率" :value="statGcFreq.toFixed(2)" unit="次/s" :loading="loading" />
      </n-gi>
    </n-grid>

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
        <MonitorBarChart title="GC 统计" :series="[...gcCountSeries, ...gcElapsedSeries]" y-axis-label=""
          :loading="loading" :empty="gcCountSeries.length === 0 && gcElapsedSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="线程数趋势" :series="threadSeries" :loading="loading"
          :empty="threadSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="非堆内存" :series="nonHeapSeries" y-axis-label="bytes"
          :loading="loading" :empty="nonHeapSeries.length === 0" />
      </n-gi>
    </n-grid>
  </div>
</template>