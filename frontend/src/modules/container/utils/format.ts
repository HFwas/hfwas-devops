export function formatBytes(bytes: number): string {
  if (bytes >= 1024 ** 3) return (bytes / 1024 ** 3).toFixed(1) + ' GB'
  if (bytes >= 1024 ** 2) return (bytes / 1024 ** 2).toFixed(1) + ' MB'
  if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return bytes + ' B'
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