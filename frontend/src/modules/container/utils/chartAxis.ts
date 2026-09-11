import type { GridComponentOption, YAXisComponentOption } from 'echarts'
import { byteScale, formatBytes, formatBytesFixed, formatCpu } from './format'

export type ChartSeriesData = { data: [number, number][] }

const FALLBACK_RGB = '32, 128, 240'

/** Canvas `addColorStop` rejects `#aaa40` (3-digit hex + alpha suffix). Use rgba. */
export function withAlpha(color: string | undefined, alpha: number): string {
  const a = Math.min(1, Math.max(0, alpha))
  if (!color) return `rgba(${FALLBACK_RGB}, ${a})`
  const hex = color.trim()
  const short = /^#([0-9a-fA-F]{3})$/.exec(hex)
  if (short) {
    const [r, g, b] = [...short[1]].map(c => parseInt(c + c, 16))
    return `rgba(${r}, ${g}, ${b}, ${a})`
  }
  const long = /^#([0-9a-fA-F]{6})$/.exec(hex)
  if (long) {
    const n = long[1]
    return `rgba(${parseInt(n.slice(0, 2), 16)}, ${parseInt(n.slice(2, 4), 16)}, ${parseInt(n.slice(4, 6), 16)}, ${a})`
  }
  const rgb = /^rgba?\(\s*(\d+)\s*,\s*(\d+)\s*,\s*(\d+)/.exec(hex)
  if (rgb) return `rgba(${rgb[1]}, ${rgb[2]}, ${rgb[3]}, ${a})`
  return `rgba(${FALLBACK_RGB}, ${a})`
}

export function seriesMaxAbs(series: ChartSeriesData[]): number {
  let max = 0
  for (const s of series) {
    for (const [, value] of s.data ?? []) {
      if (Number.isFinite(value) && Math.abs(value) > max) max = Math.abs(value)
    }
  }
  return max
}

export function formatMetricValue(value: unknown, yAxisLabel?: string): string {
  if (typeof value !== 'number' || !Number.isFinite(value)) return String(value ?? '')
  if (yAxisLabel?.startsWith('bytes')) {
    const text = formatBytes(value)
    return yAxisLabel === 'bytes/s' ? `${text}/s` : text
  }
  if (yAxisLabel === '%') return `${value.toFixed(1)}%`
  if (yAxisLabel === 'millicores') return formatCpu(value)
  if (yAxisLabel === 'core') return `${value.toFixed(2)} core`
  return value >= 1000 ? value.toFixed(0) : value.toFixed(1)
}

export function valueAxis(yAxisLabel: string | undefined, series: ChartSeriesData[]): YAXisComponentOption {
  const bytes = !!yAxisLabel?.startsWith('bytes')
  const scale = bytes ? byteScale(seriesMaxAbs(series)) : null
  const millicoresAsCore = yAxisLabel === 'millicores' && seriesMaxAbs(series) >= 1000
  const name = scale
    ? (yAxisLabel === 'bytes/s' ? `${scale.unit}/s` : scale.unit)
    : millicoresAsCore
      ? 'core'
      : (yAxisLabel === 'millicores' ? 'm' : (yAxisLabel === 'core' ? 'core' : (yAxisLabel || '')))

  return {
    type: 'value',
    name,
    nameTextStyle: { fontSize: 11 },
    axisLabel: {
      fontSize: 11,
      hideOverlap: true,
      formatter: (raw: number | string) => {
        const v = Number(raw)
        if (!Number.isFinite(v)) return ''
        if (scale) return formatBytesFixed(v, scale.divisor)
        if (millicoresAsCore) return (v / 1000).toFixed(v >= 10000 ? 1 : 2)
        if (yAxisLabel === '%') return v.toFixed(0)
        return Number.isInteger(v) ? String(v) : v.toFixed(1)
      },
    },
    splitLine: { lineStyle: { type: 'dashed', opacity: 0.3 } },
  }
}

export function chartGrid(hasTitle: boolean, seriesCount: number): GridComponentOption {
  return {
    left: 12,
    right: 16,
    top: hasTitle ? 36 : 16,
    bottom: seriesCount > 1 ? 40 : 24,
    containLabel: true,
  }
}
