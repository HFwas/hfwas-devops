<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import type { MonitorRange } from '@/modules/container/types/monitor'
import MonitorTimeRange from '@/modules/container/components/MonitorTimeRange.vue'
import MonitorStatCard from '@/modules/container/components/MonitorStatCard.vue'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import { formatPercent } from '@/modules/container/utils/format'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string; name: string }>()
const message = useMessage()

const range = ref<MonitorRange>('1h')

const cpuSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const memSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const netSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const diskSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const connSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])

const statCpu = ref(0)
const statMem = ref(0)
const statConn = ref(0)

const loading = ref(false)

async function load() {
  loading.value = true
  try {
    // Load all data in parallel
    const [cpuData, memData, netData, diskData, connData] = await Promise.all([
      monitorApi.nodeCpu(props.clusterId, props.name, range.value),
      monitorApi.nodeMemory(props.clusterId, props.name, range.value),
      monitorApi.nodeNetwork(props.clusterId, props.name, range.value),
      monitorApi.nodeDisk(props.clusterId, props.name, range.value),
      monitorApi.nodeConnections(props.clusterId, props.name, range.value),
    ])

    cpuSeries.value = cpuData.map(s => ({
      name: 'CPU',
      color: '#2080f0',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    memSeries.value = memData.map(s => ({
      name: '内存',
      color: '#18a058',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    netSeries.value = netData.map(s => ({
      name: s.labels.device || 'eth' + ' ' + (s.labels.direction === 'receive' ? 'RX' : 'TX'),
      color: s.labels.direction === 'receive' ? '#f0a020' : '#d03050',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    diskSeries.value = diskData.map(s => ({
      name: (s.labels.direction === 'read' ? '读' : '写'),
      color: s.labels.direction === 'read' ? '#a060d0' : '#18a058',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    connSeries.value = connData.map(s => ({
      name: 'TCP 连接数',
      color: '#2080f0',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    // Stat card values (latest point)
    if (cpuData.length && cpuData[0].points.length) {
      statCpu.value = cpuData[0].points[cpuData[0].points.length - 1].value
    }
    if (memData.length && memData[0].points.length) {
      statMem.value = memData[0].points[memData[0].points.length - 1].value
    }
    if (connData.length && connData[0].points.length) {
      statConn.value = connData[0].points[connData[0].points.length - 1].value
    }
  } catch (e) {
    message.error(isApiError(e) ? e.message : '加载监控数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="monitor-panel">
    <MonitorTimeRange :value="range" @update:value="(v) => { range = v; load() }" />

    <n-grid :cols="4" :x-gap="12" style="margin-bottom: 16px">
      <n-gi>
        <MonitorStatCard title="CPU 使用率" :value="statCpu.toFixed(1)" unit="%" :loading="loading" />
      </n-gi>
      <n-gi>
        <MonitorStatCard title="内存使用率" :value="statMem.toFixed(1)" unit="%" :loading="loading" />
      </n-gi>
      <n-gi>
        <MonitorStatCard title="TCP 连接数" :value="statConn" unit="" :loading="loading" />
      </n-gi>
      <n-gi>
        <MonitorStatCard title="负载 1m" :value="0" unit="" :loading="loading" />
      </n-gi>
    </n-grid>

    <n-grid :cols="2" :x-gap="12" :y-gap="12">
      <n-gi>
        <MonitorLineChart title="CPU / 内存" :series="[...cpuSeries, ...memSeries]" :loading="loading"
          y-axis-label="%" :empty="cpuSeries.length === 0 && memSeries.length === 0" />
      </n-gi>
      <n-gi :span="1">
        <MonitorLineChart title="TCP 连接数" :series="connSeries" :loading="loading"
          :empty="connSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="网络 IO" :series="netSeries" y-axis-label="bytes/s" :loading="loading"
          :empty="netSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="磁盘 IO" :series="diskSeries" y-axis-label="bytes/s" :loading="loading"
          :empty="diskSeries.length === 0" />
      </n-gi>
    </n-grid>
  </div>
</template>

<style scoped>
.monitor-panel {
  min-height: 100px;
}
</style>