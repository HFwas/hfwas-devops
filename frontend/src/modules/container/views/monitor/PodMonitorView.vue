<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { monitorApi } from '@/modules/container/api/monitor'
import type { MonitorRange } from '@/modules/container/types/monitor'
import MonitorTimeRange from '@/modules/container/components/MonitorTimeRange.vue'
import MonitorStatCard from '@/modules/container/components/MonitorStatCard.vue'
import MonitorLineChart from '@/modules/container/components/MonitorLineChart.vue'
import PodJvmMonitor from './PodJvmMonitor.vue'
import { formatBytes, formatCpu, formatBytesPerSec } from '@/modules/container/utils/format'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{
  clusterId: string
  namespace: string
  name: string
  containers: string[]
}>()

const message = useMessage()

const range = ref<MonitorRange>('1h')
const container = ref<string>('')

const cpuSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const memSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const netSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])
const restartSeries = ref<{ name: string; color: string; data: [number, number][] }[]>([])

const statCpu = ref<string>('0')
const statMem = ref<string>('0')
const statNetRx = ref<string>('0')
const statNetTx = ref<string>('0')

const hasJvm = ref(false)
const jvmChecking = ref(true)
const loading = ref(false)

async function loadData() {
  loading.value = true
  try {
    const [cpuData, memData, netData] = await Promise.all([
      monitorApi.podCpu(props.clusterId, props.namespace, props.name, range.value),
      monitorApi.podMemory(props.clusterId, props.namespace, props.name, range.value),
      monitorApi.podNetwork(props.clusterId, props.namespace, props.name, range.value),
    ])

    // Filter by selected container if set
    const filterByContainer = (s: { labels: Record<string, string>; points: { timestamp: number; value: number }[] }[]) => {
      if (!container.value) return s
      return s.filter(item => item.labels.container === container.value)
    }

    const filteredCpu = filterByContainer(cpuData)
    const filteredMem = filterByContainer(memData)

    cpuSeries.value = filteredCpu.map(s => ({
      name: s.labels.container || 'cpu',
      color: '#2080f0',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    memSeries.value = filteredMem.map(s => ({
      name: s.labels.container || 'memory',
      color: '#18a058',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    netSeries.value = netData.map(s => ({
      name: s.labels.direction === 'receive' ? 'RX' : 'TX',
      color: s.labels.direction === 'receive' ? '#f0a020' : '#d03050',
      data: s.points.map(p => [p.timestamp * 1000, p.value] as [number, number]),
    }))

    // Stat card latest values
    if (filteredCpu.length && filteredCpu[0].points.length) {
      const last = filteredCpu[0].points[filteredCpu[0].points.length - 1].value
      statCpu.value = formatCpu(last)
    }
    if (filteredMem.length && filteredMem[0].points.length) {
      const last = filteredMem[0].points[filteredMem[0].points.length - 1].value
      statMem.value = formatBytes(last)
    }
    if (netData.length) {
      for (const s of netData) {
        if (s.labels.direction === 'receive' && s.points.length) {
          statNetRx.value = formatBytesPerSec(s.points[s.points.length - 1].value)
        }
        if (s.labels.direction === 'transmit' && s.points.length) {
          statNetTx.value = formatBytesPerSec(s.points[s.points.length - 1].value)
        }
      }
    }
  } catch (e) {
    message.error(isApiError(e) ? e.message : '加载 Pod 监控数据失败')
  } finally {
    loading.value = false
  }
}

async function checkJvm() {
  jvmChecking.value = true
  try {
    const result = await monitorApi.jvmCheck(props.clusterId, props.namespace, props.name)
    hasJvm.value = result.hasJvmMetrics
  } catch {
    hasJvm.value = false
  } finally {
    jvmChecking.value = false
  }
}

onMounted(() => {
  loadData()
  checkJvm()
})

watch(container, () => loadData())
</script>

<template>
  <div class="monitor-panel">
    <MonitorTimeRange :value="range" @update:value="(v) => { range = v; loadData() }">
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

    <!-- JVM subtabs -->
    <n-tabs v-if="hasJvm && !jvmChecking" type="line" animated style="margin-bottom: 12px">
      <n-tab-pane name="basic" tab="基础指标">
        <n-grid :cols="4" :x-gap="12" style="margin-bottom: 16px">
          <n-gi><MonitorStatCard title="CPU 使用" :value="statCpu" :loading="loading" /></n-gi>
          <n-gi><MonitorStatCard title="内存使用" :value="statMem" :loading="loading" /></n-gi>
          <n-gi><MonitorStatCard title="网络 RX" :value="statNetRx" :loading="loading" /></n-gi>
          <n-gi><MonitorStatCard title="网络 TX" :value="statNetTx" :loading="loading" /></n-gi>
        </n-grid>

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
      </n-tab-pane>
      <n-tab-pane name="jvm" tab="JVM 监控">
        <PodJvmMonitor :clusterId :namespace :name :range />
      </n-tab-pane>
    </n-tabs>

    <!-- No JVM: show basic only -->
    <template v-if="!hasJvm || jvmChecking">
      <n-grid :cols="4" :x-gap="12" style="margin-bottom: 16px">
        <n-gi><MonitorStatCard title="CPU 使用" :value="statCpu" :loading="loading" /></n-gi>
        <n-gi><MonitorStatCard title="内存使用" :value="statMem" :loading="loading" /></n-gi>
        <n-gi><MonitorStatCard title="网络 RX" :value="statNetRx" :loading="loading" /></n-gi>
        <n-gi><MonitorStatCard title="网络 TX" :value="statNetTx" :loading="loading" /></n-gi>
      </n-grid>

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
    </template>
  </div>
</template>

<style scoped>
.monitor-panel {
  min-height: 100px;
}
</style>