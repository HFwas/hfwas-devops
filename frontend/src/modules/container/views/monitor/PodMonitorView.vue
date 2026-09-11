<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import { SERIES_COLORS, toChartPoints, type MonitorRange, type MonitorSeries } from '@/modules/container/types/monitor'
import MonitorTimeRange from '@/modules/container/components/MonitorTimeRange.vue'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{
  clusterId: string
  namespace: string
  name: string
  containers: string[]
  range: MonitorRange
}>()

const emit = defineEmits<{
  'update:range': [value: MonitorRange]
}>()

const message = useMessage()

const container = ref<string>('')

const cpuSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const memSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const netSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])

const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const [cpuData, memData, netData] = await Promise.all([
      monitorApi.podCpu(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.podMemory(props.clusterId, props.namespace, props.name, props.range),
      monitorApi.podNetwork(props.clusterId, props.namespace, props.name, props.range),
    ])

    // Filter by selected container if set
    const filterByContainer = (s: MonitorSeries[]) => {
      if (!container.value) return s
      return s.filter(item => item.labels.container === container.value)
    }

    const filteredCpu = filterByContainer(cpuData)
    const filteredMem = filterByContainer(memData)

    cpuSeries.value = filteredCpu.map((s, i) => ({
      name: s.labels.container || 'cpu',
      color: SERIES_COLORS[i % SERIES_COLORS.length],
      data: toChartPoints(s.points),
    }))

    memSeries.value = filteredMem.map((s, i) => ({
      name: s.labels.container || 'memory',
      color: SERIES_COLORS[i % SERIES_COLORS.length],
      data: toChartPoints(s.points),
    }))

    netSeries.value = netData.map(s => ({
      name: s.labels.direction === 'receive' ? 'RX' : 'TX',
      color: s.labels.direction === 'receive' ? '#f0a020' : '#d03050',
      data: toChartPoints(s.points),
    }))
  } catch (e) {
    message.error(isApiError(e) ? e.message : '加载 Pod 监控数据失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadData()
})

watch(container, () => loadData())
watch(() => props.range, () => loadData())
</script>

<template>
  <div class="monitor-panel">
    <MonitorTimeRange :value="range" @update:value="(v) => $emit('update:range', v)">
      <n-space v-if="containers.length > 1" align="center">
        <n-select
          v-model:value="container"
          :options="containers.map(c => ({ label: c, value: c }))"
          placeholder="选择容器"
          clearable
          style="width: 160px"
          size="small"
        />
      </n-space>
    </MonitorTimeRange>

    <n-grid :cols="1" :x-gap="12" :y-gap="12">
      <n-gi>
        <MonitorLineChart title="CPU 使用" :series="cpuSeries" y-axis-label="millicores"
          :loading="loading" :empty="cpuSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="内存使用" :series="memSeries" y-axis-label="bytes"
          :loading="loading" :empty="memSeries.length === 0" />
      </n-gi>
      <n-gi>
        <MonitorLineChart title="网络 IO" :series="netSeries" y-axis-label="bytes/s"
          :loading="loading" :empty="netSeries.length === 0" />
      </n-gi>
    </n-grid>
  </div>
</template>

<style scoped>
.monitor-panel {
  min-height: 100px;
}
</style>