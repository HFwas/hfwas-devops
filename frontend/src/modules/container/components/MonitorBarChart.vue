<script setup lang="ts">
import * as echarts from 'echarts'
import { onMounted, onUnmounted, ref, watch } from 'vue'

const props = defineProps<{
  title?: string
  series: {
    name: string
    color: string
    data: [number, number][]
  }[]
  yAxisLabel?: string
  loading?: boolean
  empty?: boolean
  error?: string | null
}>()

const chartRef = ref<HTMLElement | null>(null)
let chartInstance: echarts.ECharts | null = null

function initChart() {
  if (!chartRef.value) return
  if (chartInstance) chartInstance.dispose()
  chartInstance = echarts.init(chartRef.value)
  updateChart()
}

function updateChart() {
  if (!chartInstance) return
  chartInstance.setOption(buildOption(), true)
}

function buildOption(): echarts.EChartsOption {
  // Group data by time for bar chart with multiple series side-by-side
  const timeMap = new Map<number, Record<string, number>>()
  for (const s of props.series) {
    for (const [ts, val] of s.data) {
      if (!timeMap.has(ts)) timeMap.set(ts, {})
      timeMap.get(ts)![s.name] = val
    }
  }
  const sortedTimes = [...timeMap.keys()].sort()

  return {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => typeof v === 'number' ? v.toFixed(1) : String(v),
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      textStyle: { fontSize: 12 },
    },
    grid: {
      left: 50,
      right: 16,
      top: props.title ? 36 : 16,
      bottom: props.series.length > 1 ? 40 : 24,
    },
    xAxis: {
      type: 'time',
      axisLabel: { fontSize: 11, hideOverlap: true },
    },
    yAxis: {
      type: 'value',
      name: props.yAxisLabel || '',
      nameTextStyle: { fontSize: 11 },
      axisLabel: { fontSize: 11 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } },
    },
    series: props.series.map(s => ({
      type: 'bar',
      name: s.name,
      data: s.data,
      itemStyle: { color: s.color },
      barMaxWidth: 20,
    })) as echarts.SeriesOption[],
  }
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  chartInstance?.dispose()
})

watch(() => [props.series, props.yAxisLabel], () => {
  updateChart()
}, { deep: true })
</script>

<template>
  <n-card :bordered="false" size="small">
    <template v-if="title" #header>
      <span style="font-size: 14px; font-weight: 500">{{ title }}</span>
    </template>
    <n-spin :show="loading" size="small">
      <div v-if="error" style="padding: 24px; text-align: center">
        <n-alert type="error" :title="error" closable />
      </div>
      <n-empty v-else-if="empty || (!loading && series.every(s => s.data.length === 0))" description="暂无数据" style="padding: 24px" />
      <div v-else ref="chartRef" style="width: 100%; height: 280px" />
    </n-spin>
  </n-card>
</template>