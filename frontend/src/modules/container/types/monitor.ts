export interface MonitorPoint {
  timestamp: number
  value: number
}

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