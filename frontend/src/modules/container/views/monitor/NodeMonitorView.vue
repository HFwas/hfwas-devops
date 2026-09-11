<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import { toChartPoints, type MonitorRange } from '@/modules/container/types/monitor'
import MonitorTimeRange from '@/modules/container/components/MonitorTimeRange.vue'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string; name: string }>()
const message = useMessage()

const range = ref<MonitorRange>('1h')

const cpuSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const memSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const netSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const diskSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const connSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])

const loading = ref(false)

async function load() {
  loading.value = true
  try {
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
      data: toChartPoints(s.points),
    }))

    memSeries.value = memData.map(s => ({
      name: '内存',
      color: '#18a058',
      data: toChartPoints(s.points),
    }))

    netSeries.value = netData.map(s => ({
      name: (s.labels.device || 'eth') + ' ' + (s.labels.direction === 'receive' ? 'RX' : 'TX'),
      color: s.labels.direction === 'receive' ? '#f0a020' : '#d03050',
      data: toChartPoints(s.points),
    }))

    diskSeries.value = diskData.map(s => ({
      name: (s.labels.direction === 'read' ? '读' : '写'),
      color: s.labels.direction === 'read' ? '#a060d0' : '#18a058',
      data: toChartPoints(s.points),
    }))

    connSeries.value = connData.map(s => ({
      name: 'TCP 连接数',
      color: '#2080f0',
      data: toChartPoints(s.points),
    }))
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

    <n-grid :cols="2" :x-gap="12" :y-gap="12">
      <n-gi>
        <MonitorLineChart title="CPU 使用率" :series="cpuSeries" :loading="loading"
          y-axis-label="%" :empty="cpuSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="内存使用率" :series="memSeries" :loading="loading"
          y-axis-label="%" :empty="memSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="网络 IO" :series="netSeries" y-axis-label="bytes/s" :loading="loading"
          :empty="netSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="磁盘 IO" :series="diskSeries" y-axis-label="bytes/s" :loading="loading"
          :empty="diskSeries.length === 0" />
      </n-gi>
      <n-gi :span="2">
        <MonitorLineChart title="TCP 连接数" :series="connSeries" :loading="loading"
          :empty="connSeries.length === 0" />
      </n-gi>
    </n-grid>
  </div>
</template>

<style scoped>
.monitor-panel {
  min-height: 100px;
}
</style>
