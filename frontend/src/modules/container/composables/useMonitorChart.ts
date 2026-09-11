import * as echarts from 'echarts'
import { nextTick, onMounted, onUnmounted, watch, type Ref } from 'vue'

/**
 * Keep an ECharts instance bound to a always-mounted DOM node.
 * Charts that v-if the canvas on empty/loading never get a second onMounted, so
 * later data updates call setOption on a null instance.
 */
export function useMonitorChart(
  chartRef: Ref<HTMLElement | null>,
  buildOption: () => echarts.EChartsOption,
  watchSource: () => unknown,
  active?: () => boolean,
) {
  let chart: echarts.ECharts | null = null
  let observer: ResizeObserver | null = null

  function resize() {
    if (!chart || chart.getWidth() === 0) return
    chart.resize()
  }

  function render() {
    if (!chartRef.value) return
    if (active && !active()) {
      chart?.clear()
      return
    }
    if (!chart) {
      chart = echarts.init(chartRef.value)
      if (typeof ResizeObserver !== 'undefined') {
        observer = new ResizeObserver(resize)
        observer.observe(chartRef.value)
      }
    }
    chart.setOption(buildOption(), { notMerge: true })
    chart.resize()
  }

  onMounted(() => {
    void nextTick(render)
    window.addEventListener('resize', resize)
  })

  onUnmounted(() => {
    window.removeEventListener('resize', resize)
    observer?.disconnect()
    observer = null
    chart?.dispose()
    chart = null
  })

  watch(
    () => [watchSource(), active ? active() : true],
    () => {
      void nextTick(render)
    },
    { deep: true, flush: 'post' },
  )
}
