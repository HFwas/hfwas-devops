export function byteScale(maxAbs: number): { divisor: number; unit: string } {
  const n = Math.abs(maxAbs)
  if (n >= 1024 ** 3) return { divisor: 1024 ** 3, unit: 'GB' }
  if (n >= 1024 ** 2) return { divisor: 1024 ** 2, unit: 'MB' }
  if (n >= 1024) return { divisor: 1024, unit: 'KB' }
  return { divisor: 1, unit: 'B' }
}

/** Axis tick in a fixed unit so 0 / 50 / 100 stay comparable. */
export function formatBytesFixed(bytes: number, divisor: number): string {
  if (!Number.isFinite(bytes)) return ''
  if (divisor === 1) return String(Math.round(bytes))
  const val = bytes / divisor
  if (Math.abs(val) >= 100) return val.toFixed(0)
  if (Math.abs(val) >= 10) return val.toFixed(1)
  return val.toFixed(2)
}

export function formatBytes(bytes: number): string {
  if (!Number.isFinite(bytes)) return '0 B'
  const { divisor, unit } = byteScale(bytes)
  if (divisor === 1) return `${Math.round(bytes)} B`
  return `${(bytes / divisor).toFixed(1)} ${unit}`
}

export function formatBytesPerSec(bytes: number): string {
  return formatBytes(bytes) + '/s'
}

export function formatCpu(millicores: number): string {
  if (millicores >= 1000) return (millicores / 1000).toFixed(2) + ' core'
  return millicores.toFixed(0) + ' m'
}

export function formatPercent(value: number): string {
  return value.toFixed(1) + '%'
}