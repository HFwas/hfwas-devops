<script setup lang="ts">
import { computed, ref } from 'vue'
import { useMonitorChart } from '@/modules/container/composables/useMonitorChart'
import { chartGrid, formatMetricValue, valueAxis } from '@/modules/container/utils/chartAxis'

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

const plotSeries = computed(() =>
  (props.series ?? []).filter(s => (s.data?.length ?? 0) > 0),
)

const showEmpty = computed(() =>
  !!props.empty || !!props.error || (!props.loading && plotSeries.value.length === 0),
)

function buildOption() {
  return {
    animation: false,
    tooltip: {
      trigger: 'axis' as const,
      valueFormatter: (v: unknown) => formatMetricValue(v, props.yAxisLabel),
    },
    legend: {
      type: 'scroll' as const,
      bottom: 0,
      textStyle: { fontSize: 12 },
    },
    grid: chartGrid(!!props.title, plotSeries.value.length),
    xAxis: {
      type: 'time' as const,
      axisLabel: { fontSize: 11, hideOverlap: true },
    },
    yAxis: valueAxis(props.yAxisLabel, plotSeries.value),
    series: plotSeries.value.map(s => ({
      type: 'bar' as const,
      name: s.name,
      data: s.data,
      animation: false,
      itemStyle: { color: s.color },
      barMaxWidth: 20,
    })),
  }
}

useMonitorChart(
  chartRef,
  buildOption,
  () => [plotSeries.value, props.yAxisLabel],
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
