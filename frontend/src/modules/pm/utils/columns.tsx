import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { StatusIcon } from '@/components/console/StatusIcon'
import type { UserProfile } from '@/modules/user/types'
import { priorityLabel, workflowStatusTone } from '@/modules/pm/statusTone'
import type {
  FieldDefinition,
  FilterFieldType,
  PmWorkItem,
  PmWorkItemType,
  StatusDefinition,
} from '@/modules/pm/types'
import { typeLabel } from '@/modules/pm/types'
import { formatDate, formatDateTime } from '@/modules/pm/utils/format'

export type PmColumnKind = 'pseudo' | 'system' | 'custom'

export interface PmColumnMeta {
  key: string
  label: string
  kind: PmColumnKind
  fieldType: string
  fieldId?: string
  queryField: string
  sortable: boolean
  searchable: boolean
  filterable: boolean
  batchEditable: boolean
  filterType: FilterFieldType
  widthClass: string
  align?: 'left' | 'right'
  missing?: boolean
}

export interface ColumnCatalog {
  byKey: Map<string, PmColumnMeta>
  candidates: PmColumnMeta[]
  missing: (key: string) => PmColumnMeta
}

const SYSTEM_COLUMNS: PmColumnMeta[] = [
  { key: 'itemKey', label: '编号', kind: 'pseudo', fieldType: 'TEXT', queryField: 'item_no', sortable: true, searchable: true, filterable: true, batchEditable: false, filterType: 'text', widthClass: 'w-24' },
  { key: 'type_code', label: '类型', kind: 'system', fieldType: 'SELECT', queryField: 'type_code', sortable: true, searchable: false, filterable: true, batchEditable: false, filterType: 'enum', widthClass: 'w-20' },
  { key: 'title', label: '标题', kind: 'system', fieldType: 'TEXT', queryField: 'title', sortable: true, searchable: true, filterable: true, batchEditable: false, filterType: 'text', widthClass: 'min-w-48' },
  { key: 'status', label: '状态', kind: 'system', fieldType: 'STATUS', queryField: 'status', sortable: true, searchable: true, filterable: true, batchEditable: false, filterType: 'enum', widthClass: 'w-24' },
  { key: 'priority', label: '优先级', kind: 'system', fieldType: 'PRIORITY', queryField: 'priority', sortable: true, searchable: true, filterable: true, batchEditable: true, filterType: 'enum', widthClass: 'w-20' },
  { key: 'assignee_id', label: '负责人', kind: 'system', fieldType: 'USER', queryField: 'assignee_id', sortable: true, searchable: true, filterable: true, batchEditable: true, filterType: 'user', widthClass: 'w-28' },
  { key: 'end_time', label: '截止时间', kind: 'system', fieldType: 'DATETIME', queryField: 'end_time', sortable: true, searchable: false, filterable: true, batchEditable: true, filterType: 'date', widthClass: 'w-24' },
  // 故事点/风险是原型约定的扩展字段，当前项目没有对应字段定义；先保证显示与文案正确，
  // 待后端补齐字段定义后再开启排序与筛选，避免查询走到 custom.* 时报未知字段。
  { key: 'storyPoints', label: '故事点', kind: 'pseudo', fieldType: 'NUMBER', queryField: 'custom.storyPoints', sortable: false, searchable: false, filterable: false, batchEditable: true, filterType: 'number', widthClass: 'w-20', align: 'right' },
  { key: 'risk', label: '风险等级', kind: 'pseudo', fieldType: 'SELECT', queryField: 'custom.risk', sortable: false, searchable: false, filterable: false, batchEditable: true, filterType: 'enum', widthClass: 'w-24' },
  { key: 'update_time', label: '更新时间', kind: 'system', fieldType: 'DATETIME', queryField: 'update_time', sortable: true, searchable: false, filterable: false, batchEditable: false, filterType: 'date', widthClass: 'w-28' },
  { key: 'description', label: '描述', kind: 'system', fieldType: 'MARKDOWN', queryField: 'description', sortable: false, searchable: true, filterable: false, batchEditable: false, filterType: 'text', widthClass: 'min-w-48' },
  { key: 'module_id', label: '功能模块', kind: 'system', fieldType: 'MODULE', queryField: 'module_id', sortable: false, searchable: false, filterable: true, batchEditable: true, filterType: 'enum', widthClass: 'w-28' },
]

function filterTypeOf(fieldType: string): FilterFieldType {
  switch (fieldType) {
    case 'NUMBER':
      return 'number'
    case 'DATE':
    case 'DATETIME':
      return 'date'
    case 'USER':
      return 'user'
    case 'SELECT':
    case 'MULTI_SELECT':
    case 'PRIORITY':
    case 'STATUS':
    case 'MODULE':
    case 'BOOLEAN':
      return 'enum'
    default:
      return 'text'
  }
}

function customMeta(def: FieldDefinition): PmColumnMeta {
  const filterType = filterTypeOf(def.fieldType)
  return {
    key: def.fieldKey,
    label: def.fieldName || def.fieldKey,
    kind: 'custom',
    fieldType: def.fieldType,
    fieldId: def.id != null ? String(def.id) : undefined,
    queryField: `custom.${def.fieldKey}`,
    sortable: filterType === 'number' || filterType === 'date',
    searchable: filterType === 'text',
    filterable: true,
    batchEditable: def.batchEditable === 1,
    filterType,
    widthClass: filterType === 'number' ? 'w-20' : 'w-28',
    align: filterType === 'number' ? 'right' : undefined,
  }
}

function missingMeta(key: string): PmColumnMeta {
  return {
    key,
    label: key,
    kind: 'custom',
    fieldType: 'TEXT',
    queryField: `custom.${key}`,
    sortable: false,
    searchable: false,
    filterable: false,
    batchEditable: false,
    filterType: 'text',
    widthClass: 'w-24',
    missing: true,
  }
}

/**
 * 列目录 = 固定系统/伪列 + 当前项目字段定义。
 * 缺失字段不阻塞渲染：按请求 key 生成 missing 元数据，单元格显示 —。
 */
export function buildColumnCatalog(fields: FieldDefinition[] = []): ColumnCatalog {
  const system = SYSTEM_COLUMNS.map((column) => ({ ...column }))
  const customMetas = fields
    .filter((def) => def.fieldKey && (def.systemFlag == null || def.systemFlag === 0))
    .map(customMeta)
  // 与内置/伪列同名的自定义字段：把可筛选、可排序等能力合并到内置元数据上，
  // 避免伪字段（如 storyPoints/risk）永远不可查询，也避免重复列。
  for (const meta of customMetas) {
    const base = system.find((column) => column.key === meta.key)
    if (!base) continue
    base.filterable = meta.filterable
    base.sortable = meta.sortable
    base.searchable = meta.searchable
    base.queryField = meta.queryField
    base.fieldId = meta.fieldId
    base.batchEditable = meta.batchEditable
  }
  const custom = customMetas.filter((meta) => !system.some((column) => column.key === meta.key))
  const candidates = [...system, ...custom]
  const byKey = new Map(candidates.map((column) => [column.key, column]))
  return {
    byKey,
    candidates,
    missing: (key: string) => byKey.get(key) ?? missingMeta(key),
  }
}

export function resolveColumnMetas(keys: string[], catalog: ColumnCatalog): PmColumnMeta[] {
  return keys.map((key) => catalog.missing(key))
}

export function defaultColumnKeys() {
  return ['itemKey', 'type_code', 'title', 'status', 'priority', 'assignee_id', 'end_time', 'storyPoints']
}

export interface CellContext {
  projectId: string
  /** 列表内打开详情弹窗的链接（保留当前列表参数）。 */
  detailHref: (item: PmWorkItem) => string
  types: PmWorkItemType[]
  statusByType: Map<string, Map<string, StatusDefinition>>
  usersById: Map<string, UserProfile>
  optionLabels: Map<string, Map<string, string>>
}

function optionText(column: PmColumnMeta, value: unknown, ctx: CellContext): string {
  if (value == null) return '—'
  const labels = ctx.optionLabels.get(column.key)
  if (Array.isArray(value)) {
    return value.map((item) => labels?.get(String(item)) ?? String(item)).join('、') || '—'
  }
  return labels?.get(String(value)) ?? String(value)
}

function renderCustom(item: PmWorkItem, column: PmColumnMeta, ctx: CellContext): ReactNode {
  const value = item.customFields?.[column.key]
  if (value == null || value === '') return '—'
  switch (column.fieldType) {
    case 'NUMBER':
      return <span className="tabular-nums">{String(value)}</span>
    case 'DATE':
      return formatDate(String(value))
    case 'DATETIME':
      return formatDateTime(String(value))
    case 'USER': {
      const user = ctx.usersById.get(String(value))
      return user?.displayName || String(value)
    }
    case 'BOOLEAN':
      return value === true || value === 'true' || value === 1 ? '是' : '否'
    case 'MULTI_SELECT':
      return optionText(column, value, ctx)
    default:
      return optionText(column, value, ctx)
  }
}

export function renderPmCell(item: PmWorkItem, column: PmColumnMeta, ctx: CellContext): ReactNode {
  if (column.missing) return '—'
  switch (column.key) {
    case 'itemKey':
      return <span className="font-mono text-xs">{item.itemKey || '—'}</span>
    case 'title': {
      return (
        <Link
          className="block max-w-[28rem] truncate font-medium text-primary hover:underline"
          title={item.title}
          to={ctx.detailHref(item)}
        >
          {item.title || '—'}
        </Link>
      )
    }
    case 'type_code':
      return typeLabel(item.typeCode, ctx.types)
    case 'status': {
      const definition = ctx.statusByType.get(item.typeCode)?.get(item.status ?? '')
      return (
        <StatusIcon
          status={item.status}
          label={definition?.statusName || item.status || '未设置'}
          tone={workflowStatusTone(definition)}
        />
      )
    }
    case 'priority':
      return priorityLabel(item.priority)
    case 'assignee_id':
      return item.assigneeId != null ? ctx.usersById.get(String(item.assigneeId))?.displayName ?? '—' : '—'
    case 'end_time':
      return formatDate(item.endTime)
    case 'update_time':
      return formatDateTime(item.updateTime)
    case 'storyPoints': {
      const value = item.customFields?.storyPoints ?? item.customFields?.story_points
      return value == null || value === '' ? '—' : <span className="tabular-nums">{String(value)}</span>
    }
    case 'risk': {
      const value = item.customFields?.risk
      return value == null || value === '' ? '—' : optionText(column, value, ctx)
    }
    default:
      return renderCustom(item, column, ctx)
  }
}

export function columnLabel(key: string, catalog: ColumnCatalog) {
  return catalog.missing(key).label
}

export function filterTypeOfColumn(column: PmColumnMeta): FilterFieldType {
  return column.filterType
}
