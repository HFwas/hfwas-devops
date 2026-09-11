<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import { SERIES_COLORS, toChartPoints, type MonitorRange } from '@/modules/container/types/monitor'
import MonitorTimeRange from '@/modules/container/components/MonitorTimeRange.vue'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string }>()
const message = useMessage()

const range = ref<MonitorRange>('1h')
const cpuSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const memSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const netSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])

const loading = ref(false)

async function load() {
  loading.value = true
  try {
    const cpuData = await monitorApi.nodeCpu(props.clusterId, '.*', range.value)
    cpuSeries.value = cpuData.map((s, i) => ({
      name: s.labels.nodename || s.labels.instance || 'Node CPU',
      color: SERIES_COLORS[i % SERIES_COLORS.length],
      data: toChartPoints(s.points),
    }))

    const memData = await monitorApi.nodeMemory(props.clusterId, '.*', range.value)
    memSeries.value = memData.map((s, i) => ({
      name: s.labels.nodename || s.labels.instance || 'Node 内存',
      color: SERIES_COLORS[i % SERIES_COLORS.length],
      data: toChartPoints(s.points),
    }))

    const netData = await monitorApi.nodeNetwork(props.clusterId, '.*', range.value)
    netSeries.value = netData.map(s => ({
      name: (s.labels.device || 'eth') + ' ' + (s.labels.direction === 'receive' ? 'RX' : 'TX'),
      color: s.labels.direction === 'receive' ? '#f0a020' : '#d03050',
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
        <MonitorLineChart title="Node CPU 趋势" :series="cpuSeries" y-axis-label="%" :loading="loading"
          :empty="cpuSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="Node 内存趋势" :series="memSeries" y-axis-label="%" :loading="loading"
          :empty="memSeries.length === 0" />
      </n-gi>
      <n-gi :span="2">
        <MonitorLineChart title="网络 IO" :series="netSeries" y-axis-label="bytes/s" :loading="loading"
          :empty="netSeries.length === 0" />
      </n-gi>
    </n-grid>
  </div>
</template>

<style scoped>
.monitor-panel {
  min-height: 100px;
}
</style>
