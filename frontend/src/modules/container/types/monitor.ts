export interface MonitorPoint {
  /** Unix epoch seconds. Jackson Long→String may deliver this as a string. */
  timestamp: number | string
  value: number
}

/** Convert Prometheus points to ECharts time-axis pairs `[ms, value]`. */
export function toChartPoints(points: MonitorPoint[] | null | undefined): [number, number][] {
  if (!points?.length) return []
  const result: [number, number][] = []
  for (const p of points) {
    const t = Number(p.timestamp) * 1000
    const v = Number(p.value)
    if (Number.isFinite(t) && Number.isFinite(v)) result.push([t, v])
  }
  return result
}

export const SERIES_COLORS = ['#2080f0', '#18a058', '#f0a020', '#d03050', '#8a2be2', '#13c2c2', '#eb2f96', '#2f54eb', '#fa8c16', '#52c41a']

export interface MonitorSeries {
  labels: Record<string, string>
  points: MonitorPoint[]
}

export interface MonitorOverview {
  cpuUsagePercent: number
  memoryUsagePercent: number
  nodeTotal: number
  nodeReady: number
  podTotal: number
  podRunning: number
  diskReadBytesPerSec: number
  diskWriteBytesPerSec: number
}

export interface JvmCheckResult {
  hasJvmMetrics: boolean
}

export type MonitorRange = '1h' | '6h' | '24h' | '7d'

export const RANGE_STEP_MAP: Record<MonitorRange, { step: string; label: string }> = {
  '1h': { step: '15s', label: '1小时' },
  '6h': { step: '1m', label: '6小时' },
  '24h': { step: '5m', label: '24小时' },
  '7d': { step: '30m', label: '7天' },
}

export interface SeriesConfig {
  name: string
  color: string
  data: [number, number][]
}

export interface ReferenceLine {
  value: number
  label: string
  color: string
}