import { parseAllDocuments, stringify } from 'yaml'

const PARSE_OPTIONS = {
  uniqueKeys: false,
  logLevel: 'silent' as const,
  maxAliasCount: 50,
}

const MAX_ANNOTATION_JSON = 1_000_000

/**
 * 资源 YAML 接口目前返回 Jackson 美化后的 JSON。这里只改展示：
 * 转成块状 YAML，并把注解里整段 JSON 对象/数组展开，避免一行转义字符串。
 */
export function presentResourceYaml(raw: string): string {
  const parsed = parseResourceDocument(raw)
  if (parsed == null || typeof parsed !== 'object') return raw
  return stringify(stripNulls(expandAnnotationJson(parsed)), {
    indent: 2,
    lineWidth: 120,
    collectionStyle: 'block',
    aliasDuplicateObjects: false,
  })
}

/**
 * 未改动时原样回传接口字符串。改过的 YAML 再收成 JSON，注解对象收回字符串，供现有保存接口读取。
 */
export function resourceYamlForSave(edited: string, original: string): string {
  if (edited === presentResourceYaml(original) && isJsonDocument(original)) return original
  const parsed = parseResourceDocument(edited)
  if (parsed == null || typeof parsed !== 'object' || Array.isArray(parsed)) {
    throw new Error('YAML 无法解析')
  }
  return JSON.stringify(collapseAnnotationJson(parsed))
}

function isJsonDocument(raw: string): boolean {
  const trimmed = raw.trim()
  return trimmed.startsWith('{') || trimmed.startsWith('[')
}

function parseResourceDocument(raw: string): unknown {
  const trimmed = raw.trim()
  if (!trimmed) return null
  if (isJsonDocument(trimmed)) {
    try {
      return JSON.parse(trimmed) as unknown
    } catch {
      // 不是 JSON 时再按 YAML 试一次。
    }
  }
  try {
    const docs = parseAllDocuments(trimmed, PARSE_OPTIONS)
    const doc = docs.find((item) => item.contents != null) ?? docs[0]
    if (!doc || doc.errors.length > 0) return null
    return doc.toJS({ maxAliasCount: PARSE_OPTIONS.maxAliasCount })
  } catch {
    return null
  }
}

function expandAnnotationJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map((item) => expandAnnotationJson(item))
  if (!isPlainObject(value)) return value
  const out: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(value)) {
    if (key === 'annotations' && isPlainObject(child)) {
      const expanded: Record<string, unknown> = {}
      for (const [name, raw] of Object.entries(child)) {
        expanded[name] = expandAnnotationJson(parseJsonContainer(raw))
      }
      out[key] = expanded
    } else {
      out[key] = expandAnnotationJson(child)
    }
  }
  return out
}

function collapseAnnotationJson(value: unknown): unknown {
  if (Array.isArray(value)) return value.map((item) => collapseAnnotationJson(item))
  if (!isPlainObject(value)) return value
  const out: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(value)) {
    const next = collapseAnnotationJson(child)
    out[key] = key === 'annotations' && isPlainObject(next) ? stringifyAnnotationValues(next) : next
  }
  return out
}

function stringifyAnnotationValues(map: Record<string, unknown>): Record<string, unknown> {
  const out: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(map)) {
    if (typeof child === 'string') out[key] = child
    else if (child == null) continue
    else if (typeof child === 'object') out[key] = JSON.stringify(child)
    else out[key] = String(child)
  }
  return out
}

function parseJsonContainer(value: unknown): unknown {
  if (typeof value !== 'string') return value
  const trimmed = value.trim()
  if (trimmed.length < 2 || trimmed.length > MAX_ANNOTATION_JSON) return value
  if (!(trimmed.startsWith('{') && trimmed.endsWith('}')) && !(trimmed.startsWith('[') && trimmed.endsWith(']'))) {
    return value
  }
  try {
    const parsed = JSON.parse(trimmed) as unknown
    if (parsed == null || typeof parsed !== 'object') return value
    return parsed
  } catch {
    return value
  }
}

function stripNulls(value: unknown): unknown {
  if (Array.isArray(value)) return value.map((item) => stripNulls(item))
  if (!isPlainObject(value)) return value
  const out: Record<string, unknown> = {}
  for (const [key, child] of Object.entries(value)) {
    if (child == null) continue
    out[key] = stripNulls(child)
  }
  return out
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) return false
  const proto = Object.getPrototypeOf(value)
  return proto === Object.prototype || proto === null
}
