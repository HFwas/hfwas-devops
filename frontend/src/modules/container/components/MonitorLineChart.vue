<script setup lang="ts">
import * as echarts from 'echarts'
import { computed, ref } from 'vue'
import { useMonitorChart } from '@/modules/container/composables/useMonitorChart'
import { chartGrid, formatMetricValue, valueAxis, withAlpha } from '@/modules/container/utils/chartAxis'

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

const plotSeries = computed(() =>
  (props.series ?? []).filter(s => (s.data?.length ?? 0) > 0),
)

const showEmpty = computed(() =>
  !!props.empty || !!props.error || (!props.loading && plotSeries.value.length === 0),
)

function buildOption(): echarts.EChartsOption {
  const seriesOpts: echarts.SeriesOption[] = plotSeries.value.map(s => ({
    type: 'line',
    name: s.name,
    data: s.data,
    smooth: true,
    showSymbol: false,
    animation: false,
    lineStyle: { width: 2, color: s.color || '#2080f0' },
    itemStyle: { color: s.color || '#2080f0' },
    areaStyle: {
      color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
        { offset: 0, color: withAlpha(s.color, 0.25) },
        { offset: 1, color: withAlpha(s.color, 0.02) },
      ]),
    },
  }))

  if (props.referenceLines?.length && seriesOpts.length > 0) {
    const first = seriesOpts[0] as echarts.LineSeriesOption
    first.markLine = {
      silent: true,
      symbol: 'none',
      data: props.referenceLines.map(refLine => ({
        yAxis: refLine.value,
        name: refLine.label,
        lineStyle: { type: 'dashed' as const, color: refLine.color, width: 1 },
        label: {
          formatter: `${refLine.label}: ${refLine.value}`,
          fontSize: 11,
        },
      })),
    }
  }

  return {
    animation: false,
    tooltip: {
      trigger: 'axis',
      valueFormatter: (v: unknown) => formatMetricValue(v, props.yAxisLabel),
    },
    legend: {
      type: 'scroll',
      bottom: 0,
      textStyle: { fontSize: 12 },
    },
    grid: chartGrid(!!props.title, plotSeries.value.length),
    xAxis: {
      type: 'time',
      axisLabel: {
        fontSize: 11,
        hideOverlap: true,
      },
    },
    yAxis: valueAxis(props.yAxisLabel, plotSeries.value),
    series: seriesOpts,
  }
}

useMonitorChart(
  chartRef,
  buildOption,
  () => [plotSeries.value, props.referenceLines, props.yAxisLabel],
  () => !showEmpty.value,
)
</script>

<template>
  <n-card :bordered="false" size="small">
    <template v-if="title" #header>
      <span style="font-size: 14px; font-weight: 500">{{ title }}</span>
    </template>

    <n-spin :show="loading" size="small">
      <div class="chart-wrap">
        <div v-if="error" class="chart-overlay">
          <n-alert type="error" :title="error" closable />
        </div>
        <div v-else-if="showEmpty" class="chart-overlay">
          <n-empty description="暂无数据" />
        </div>
        <div
          ref="chartRef"
          class="chart-canvas"
          :class="{ 'chart-hidden': !!error || showEmpty }"
        />
      </div>
    </n-spin>
  </n-card>
</template>

<style scoped>
.chart-wrap {
  position: relative;
  width: 100%;
  height: 280px;
}
.chart-canvas {
  width: 100%;
  height: 280px;
}
.chart-hidden {
  visibility: hidden;
}
.chart-overlay {
  position: absolute;
  inset: 0;
  z-index: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
}
</style>
