const DNS_LABEL = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/

export function defaultReleaseName(chartName: string): string {
  const normalized = chartName
    .toLowerCase()
    .replace(/[^a-z0-9-]+/g, '-')
    .replace(/^-+|-+$/g, '')
  return normalized || 'release'
}

export function dnsLabelError(value: string, label: string): string | null {
  const trimmed = value.trim()
  if (!trimmed) return `请填写${label}`
  if (trimmed.length > 53 || !DNS_LABEL.test(trimmed)) {
    return `${label} 只能使用小写字母、数字和连字符`
  }
  return null
}

/** 轻量检查：非空时拒绝 YAML 列表。完整解析由后端完成。 */
export function valuesYamlError(text: string): string | null {
  const first = text
    .split('\n')
    .map((line) => line.trim())
    .find((line) => line && !line.startsWith('#'))
  if (!first) return null
  if (first === '-' || first.startsWith('- ') || first.startsWith('[')) {
    return 'Values 必须是 YAML 对象'
  }
  return null
}

export function formatHelmTime(value?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
