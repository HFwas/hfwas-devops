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
  referenceLines?: { value: number; label: string; color: string }[]
  colors?: string[]
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
  const option: echarts.EChartsOption = {
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => {
        if (typeof v === 'number') {
          if (props.yAxisLabel === 'bytes' && v >= 1024) {
            const units = ['B', 'KB', 'MB', 'GB']
            let unitIdx = 0
            let val = v
            while (val >= 1024 && unitIdx < units.length - 1) {
              val /= 1024
              unitIdx++
            }
            return val.toFixed(1) + ' ' + units[unitIdx]
          }
          if (props.yAxisLabel === '%') return v.toFixed(1) + '%'
          if (v >= 1000) return v.toFixed(1)
          return v.toFixed(1)
        }
        return String(v)
      },
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
      axisLabel: {
        fontSize: 11,
        hideOverlap: true,
      },
    },
    yAxis: {
      type: 'value',
      name: props.yAxisLabel || '',
      nameTextStyle: { fontSize: 11 },
      axisLabel: { fontSize: 11 },
      splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } },
    },
    series: [] as echarts.SeriesOption[],
  }

  // Add main series
  for (const s of props.series) {
    (option.series as echarts.SeriesOption[]).push({
      type: 'line',
      name: s.name,
      data: s.data,
      smooth: true,
      showSymbol: false,
      lineStyle: { width: 2, color: s.color },
      itemStyle: { color: s.color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: s.color + '40' },
          { offset: 1, color: s.color + '05' },
        ]),
      },
    })
  }

  // Add reference lines
  if (props.referenceLines) {
    for (const refLine of props.referenceLines) {
      (option.series as echarts.SeriesOption[]).push({
        type: 'line',
        name: refLine.label,
        data: [],
        markLine: {
          silent: true,
          symbol: 'none',
          lineStyle: { type: 'dashed', color: refLine.color, width: 1 },
          label: {
            formatter: refLine.label + ': ' + refLine.value,
            fontSize: 11,
          },
          data: [{ yAxis: refLine.value }],
        },
      })
    }
  }

  return option
}

onMounted(() => {
  initChart()
  window.addEventListener('resize', () => chartInstance?.resize())
})

onUnmounted(() => {
  chartInstance?.dispose()
})

watch(() => [props.series, props.referenceLines, props.yAxisLabel], () => {
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