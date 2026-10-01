import { useEffect, useRef } from 'react'
import * as echarts from 'echarts'
import { chartGrid, formatMetricValue, valueAxis, withAlpha } from '@/modules/container/utils/chartAxis'

export type ChartSeries = {
  name: string
  color: string
  data: [number, number][]
}

export function MonitorLineChart({
  title,
  series,
  yAxisLabel,
  loading,
  stacked,
  referenceLines,
}: {
  title: string
  series: ChartSeries[]
  yAxisLabel?: string
  loading?: boolean
  stacked?: boolean
  referenceLines?: { value: number; label: string; color: string }[]
}) {
  const hostRef = useRef<HTMLDivElement>(null)
  const chartRef = useRef<echarts.ECharts | null>(null)
  const plot = series.filter((item) => item.data.length > 0)
  const empty = !loading && plot.length === 0

  useEffect(() => {
    const host = hostRef.current
    if (!host) return
    const chart = chartRef.current ?? echarts.init(host)
    chartRef.current = chart
    const observer = new ResizeObserver(() => chart.resize())
    observer.observe(host)
    return () => {
      observer.disconnect()
    }
  }, [])

  useEffect(() => {
    const chart = chartRef.current
    if (!chart) return
    if (empty) {
      chart.clear()
      return
    }
    const seriesOpts: echarts.SeriesOption[] = plot.map((item) => ({
      type: 'line',
      name: item.name,
      data: item.data,
      stack: stacked ? 'total' : undefined,
      smooth: true,
      showSymbol: false,
      animation: false,
      lineStyle: { width: 2, color: item.color },
      itemStyle: { color: item.color },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: withAlpha(item.color, 0.25) },
          { offset: 1, color: withAlpha(item.color, 0.02) },
        ]),
      },
    }))
    if (referenceLines?.length && seriesOpts[0]) {
      const first = seriesOpts[0] as echarts.LineSeriesOption
      first.markLine = {
        silent: true,
        symbol: 'none',
        data: referenceLines.map((line) => ({
          yAxis: line.value,
          name: line.label,
          lineStyle: { type: 'dashed' as const, color: line.color, width: 1 },
          label: { formatter: `${line.label}: ${line.value}`, fontSize: 11 },
        })),
      }
    }
    chart.setOption(
      {
        animation: false,
        tooltip: {
          trigger: 'axis',
          valueFormatter: (value: unknown) => formatMetricValue(value, yAxisLabel),
        },
        legend: { type: 'scroll', bottom: 0, textStyle: { fontSize: 12 } },
        grid: chartGrid(true, plot.length),
        xAxis: { type: 'time', axisLabel: { fontSize: 11, hideOverlap: true } },
        yAxis: valueAxis(yAxisLabel, plot),
        series: seriesOpts,
      },
      { notMerge: true },
    )
    chart.resize()
  }, [empty, plot, referenceLines, stacked, yAxisLabel])

  useEffect(() => {
    return () => {
      chartRef.current?.dispose()
      chartRef.current = null
    }
  }, [])

  return (
    <section className="rounded-lg border bg-card p-3">
      <h2 className="mb-2 text-sm font-medium">{title}</h2>
      <div className="relative h-72">
        {loading && <p className="absolute inset-0 z-10 flex items-center justify-center text-sm text-muted-foreground">加载中…</p>}
        {empty && <p className="absolute inset-0 z-10 flex items-center justify-center text-sm text-muted-foreground">暂无数据</p>}
        <div ref={hostRef} className={`h-72 w-full ${empty ? 'invisible' : ''}`} />
      </div>
    </section>
  )
}
