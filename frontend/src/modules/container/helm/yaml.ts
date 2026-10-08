import { parseAllDocuments } from 'yaml'

const DNS_LABEL = /^[a-z0-9]([-a-z0-9]*[a-z0-9])?$/

/** 与后端 HelmReleaseService 的 Values 上限一致。 */
const MAX_VALUES_CHARS = 1024 * 1024

const VALUES_PARSE_OPTIONS = {
  version: '1.1' as const,
  uniqueKeys: false,
  logLevel: 'silent' as const,
  maxAliasCount: 20,
  strict: true,
}

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

/**
 * Helm values 必须是 YAML 映射。空白表示不传 --values，由 Chart 默认值生效。
 * 语法错误与非映射根节点返回中文说明，供编辑时提示和提交前拦截。
 */
export function valuesYamlError(text: string): string | null {
  if (text.length > MAX_VALUES_CHARS) return 'Values 超过大小限制'
  if (!text.trim()) return null
  let loaded: unknown
  try {
    const docs = parseAllDocuments(text, VALUES_PARSE_OPTIONS)
    if (docs.length > 1) return 'Values 不是合法的 YAML 对象'
    const doc = docs[0]
    if (!doc) return null
    if (doc.errors.length > 0) return 'Values 不是合法的 YAML 对象'
    loaded = doc.toJS()
  } catch {
    return 'Values 不是合法的 YAML 对象'
  }
  if (loaded == null) return null
  if (!isYamlMapping(loaded)) return 'Values 必须是 YAML 对象'
  return null
}

function isYamlMapping(value: unknown): boolean {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const proto = Object.getPrototypeOf(value)
  return proto === Object.prototype || proto === null
}

export function formatHelmTime(value?: string): string {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString()
}
