export type QueryOperator =
  | 'EQ'
  | 'NE'
  | 'GT'
  | 'GTE'
  | 'LT'
  | 'LTE'
  | 'LIKE'
  | 'IN'
  | 'NOT_IN'
  | 'BETWEEN'
  | 'IS_NULL'
  | 'IS_NOT_NULL'

export type QueryLogic = 'AND' | 'OR'

export interface QueryCondition {
  field: string
  operator: QueryOperator
  value: unknown
}

export interface QueryConditionGroup {
  logic: QueryLogic
  conditions: QueryCondition[]
  groups?: QueryConditionGroup[]
}

export interface SortSpec {
  field: string
  order: 'ASC' | 'DESC'
}

export interface QuerySpec {
  projectId?: number | string
  typeCode?: string
  logic?: QueryLogic
  conditions?: QueryCondition[]
  groups?: QueryConditionGroup[]
  sort?: SortSpec[]
  pageNo?: number
  pageSize?: number
}

export interface FieldDefinition {
  id?: number | string
  projectId?: number
  scope?: string
  fieldKey: string
  fieldName: string
  fieldType: string
  config?: Record<string, unknown>
  applicableTypes?: string[]
  requiredFlag?: number
  sortOrder?: number
  systemFlag?: number
  showInList?: boolean
  searchable?: boolean
  showInCreate?: boolean
  listOrder?: number
}

export interface TypeFieldLayoutConfig {
  listFields: string[]
  searchFields: string[]
  createFields: string[]
}

export interface ExportedFieldOption {
  optionKey: string
  optionLabel: string
  sortOrder?: number
}

export interface ExportedFieldDefinition {
  fieldKey: string
  fieldName: string
  fieldType: string
  requiredFlag?: number
  sortOrder?: number
  config?: Record<string, unknown>
  options?: ExportedFieldOption[]
}

/** @deprecated Legacy field-only export; use IssueTypeSchemeExport */
export interface TypeFieldSchemeExport {
  schemaVersion: number
  kind: 'pm_type_field_scheme'
  typeCode: string
  typeName?: string
  layout: TypeFieldLayoutConfig
  customFields: ExportedFieldDefinition[]
  exportedAt?: string
}

/** @deprecated Legacy project field export; use ProjectIssueTypeSchemeExport */
export interface ProjectFieldSchemeExport {
  schemaVersion: number
  kind: 'pm_project_field_schemes'
  schemes: TypeFieldSchemeExport[]
  exportedAt?: string
}

export interface FieldSchemeSection {
  layout: TypeFieldLayoutConfig
  customFields: ExportedFieldDefinition[]
}

export interface ExportedStatusDefinition {
  statusCode: string
  statusName: string
  sortOrder?: number
  isInitial?: number
  isFinal?: number
  transitions?: string[]
}

export interface StatusWorkflowSection {
  statuses: ExportedStatusDefinition[]
}

/** Unified issue type scheme (fields + workflow + extensible sections). */
export interface IssueTypeSchemeExport {
  schemaVersion: number
  kind: 'pm_issue_type_scheme'
  typeCode: string
  typeName?: string
  fieldScheme?: FieldSchemeSection
  statusWorkflow?: StatusWorkflowSection
  exportedAt?: string
}

export interface ProjectIssueTypeSchemeExport {
  schemaVersion: number
  kind: 'pm_project_issue_type_schemes'
  schemes: IssueTypeSchemeExport[]
  exportedAt?: string
}

export type SchemeImportMode = 'MERGE' | 'REPLACE'

/** @deprecated alias */
export type FieldSchemeImportMode = SchemeImportMode

export interface IssueTypeSchemeImportPreview {
  typeCode: string
  sourceTypeCode: string
  sections: string[]
  customFieldCount: number
  layoutFieldCount: number
  fieldsToCreate: number
  fieldsToUpdate: number
  statusCount: number
  statusWorkflowWillApply: boolean
  warnings: string[]
}

export interface IssueTypeSchemeImportResult {
  typeCode: string
  fieldsCreated: number
  fieldsUpdated: number
  fieldsSkipped: number
  layoutApplied: boolean
  statusWorkflowApplied: boolean
  statusCount: number
  sectionsApplied: string[]
  warnings: string[]
}

/** Section key → display label for import/export UI */
export const SCHEME_SECTION_LABELS: Record<string, string> = {
  fieldScheme: '字段配置',
  statusWorkflow: '状态流转',
}

export interface FieldOption {
  id?: number
  fieldId?: number
  optionKey: string
  optionLabel: string
  sortOrder?: number
}

export type FieldOptionSource = 'static' | 'remote'

export interface FieldRemoteOptionsConfig {
  url: string
  method?: 'GET' | 'POST'
  headers?: Record<string, string>
  body?: string
  dataPath?: string
  valueField?: string
  labelField?: string
  cacheSeconds?: number
}

export interface ResolvedFieldOption {
  value: string
  label: string
}

export interface RemoteOptionFetchResult {
  success: boolean
  message?: string
  options?: ResolvedFieldOption[]
}

export interface StatusDefinition {
  id?: number | string
  statusCode: string
  statusName: string
  sortOrder?: number
  isInitial?: number
  isFinal?: number
  transitions?: string[]
}

export interface StatusWorkflow {
  projectId?: number
  typeCode?: string
  customized?: boolean
  statuses: StatusDefinition[]
}

export interface AllowedTransitions {
  fromStatus?: string
  targets: StatusDefinition[]
}

export const ANY_STATUS_CODE = '__any__'

export interface PmProject {
  id?: number
  tenantId?: number | string
  code: string
  name: string
  description?: string
  settings?: Record<string, unknown>
}

export interface ProjectAccessContext {
  projectId: number | string
  projectName: string
  tenantId: number | string
}

export interface PmProjectModule {
  id?: number
  projectId: number
  parentId?: number | null
  name: string
  description?: string
  sortOrder?: number
  enabled?: number
  pathLabel?: string
  children?: PmProjectModule[]
}

export interface PmWorkItem {
  id?: number | string
  projectId: number | string
  itemNo?: number
  itemKey?: string
  typeCode: string
  title: string
  description?: string
  status?: string
  priority?: string
  assigneeId?: number
  reporterId?: number
  parentId?: number
  moduleId?: number | null
  customFields?: Record<string, unknown>
  updateTime?: string
}

export interface WorkItemIoColumn {
  fieldKey: string
  fieldName: string
  fieldType: string
  systemField: boolean
  exportable: boolean
  importable: boolean
  defaultSelected: boolean
}

export type WorkItemImportMode = 'CREATE' | 'UPSERT'

export type WorkItemExportScope = 'selected' | 'query' | 'all'

export interface WorkItemImportPreview {
  totalRows: number
  validRows: number
  detectedHeaders: string[]
  sampleRows: Array<Record<string, string>>
  warnings: string[]
}

export interface WorkItemImportResult {
  created: number
  updated: number
  skipped: number
  failed: number
  errors: string[]
  warnings: string[]
}

export interface PmWorkItemComment {
  id: string
  workItemId: string
  parentId?: string | null
  content: string
  authorName: string
  authorId?: string
  createTime?: string
  deletable?: boolean
}

export type WorkItemActivityEventType = 'CREATE' | 'FIELD_CHANGE' | 'LINK_ADD'

export interface PmWorkItemActivity {
  id: string
  workItemId: string
  batchId?: string | null
  eventType: WorkItemActivityEventType
  actorId?: string
  actorName: string
  fieldKey?: string
  fieldName?: string
  fieldType?: string
  oldValue?: string | null
  newValue?: string | null
  oldLabel?: string | null
  newLabel?: string | null
  extraJson?: string | null
  createTime?: string
}

export type ActivityFilter = 'all' | 'change' | 'comment' | 'link'

export interface PmWorkItemTimelineItem {
  kind: 'activity' | 'comment'
  time: string
  activity?: PmWorkItemActivity
  comment?: PmWorkItemComment
  /** grouped field changes in one save batch */
  groupedChanges?: PmWorkItemActivity[]
}

export interface PmWorkItemType {
  id: number
  code: string
  name: string
  icon?: string
}

export interface PmSavedView {
  id?: number
  projectId: number
  name: string
  typeCode?: string
  querySpec: QuerySpec
  columns?: Array<Record<string, unknown>>
}

export const OPERATORS: { label: string; value: QueryOperator }[] = [
  { label: '等于', value: 'EQ' },
  { label: '不等于', value: 'NE' },
  { label: '包含', value: 'LIKE' },
  { label: '在列表中', value: 'IN' },
  { label: '不在列表中', value: 'NOT_IN' },
  { label: '为空', value: 'IS_NULL' },
  { label: '不为空', value: 'IS_NOT_NULL' },
  { label: '大于', value: 'GT' },
  { label: '小于', value: 'LT' },
]

export const TYPE_META: Record<string, { label: string; color: string }> = {
  requirement: { label: '需求', color: '#2080f0' },
  task: { label: '任务', color: '#18a058' },
  bug: { label: '缺陷', color: '#d03050' },
  test_case: { label: '测试用例', color: '#f0a020' },
}

export const WORK_ITEM_TYPE_CODES = Object.keys(TYPE_META) as Array<keyof typeof TYPE_META>

export const FIELD_TYPE_LABELS: Record<string, string> = {
  TEXT: '单行文本',
  TEXTAREA: '多行文本',
  MARKDOWN: 'Markdown',
  NUMBER: '数字',
  SELECT: '单选列表',
  MULTI_SELECT: '多选列表',
  DATE: '日期',
  DATETIME: '日期时间',
  USER: '用户',
  BOOLEAN: '布尔',
  STATUS: '状态',
  PRIORITY: '优先级',
  MODULE: '功能模块',
}

export const FIELD_TYPE_OPTIONS = Object.entries(FIELD_TYPE_LABELS)
  .filter(([k]) => !['STATUS', 'PRIORITY'].includes(k))
  .map(([value, label]) => ({ label, value }))

export const SYSTEM_FIELD_PROP_MAP: Record<string, string> = {
  assignee_id: 'assigneeId',
  type_code: 'typeCode',
  module_id: 'moduleId',
}

export function systemFieldProp(fieldKey: string): string {
  return SYSTEM_FIELD_PROP_MAP[fieldKey] ?? fieldKey
}

export function resolveWorkItemTypeCode(path: string, fallback = 'task'): string {
  const match = path.match(/\/(?:items|board|settings\/types)\/(requirement|task|bug|test_case)/)
  return match?.[1] ?? fallback
}

export const STATUS_OPTIONS = [
  { label: '待处理', value: 'open' },
  { label: '进行中', value: 'in_progress' },
  { label: '已完成', value: 'done' },
  { label: '已关闭', value: 'closed' },
]

export const STATUS_COLOR_PALETTE = ['#2080f0', '#18a058', '#f0a020', '#d03050', '#7c3aed', '#0891b2', '#64748b']

export function statusTagColor(status: StatusDefinition, index = 0): string {
  if (status.isInitial === 1) return '#2080f0'
  if (status.isFinal === 1) return '#64748b'
  return STATUS_COLOR_PALETTE[index % STATUS_COLOR_PALETTE.length]
}

export const PRIORITY_OPTIONS = [
  { label: '低', value: 'low' },
  { label: '中', value: 'medium' },
  { label: '高', value: 'high' },
  { label: '紧急', value: 'critical' },
]

export function emptyQuerySpec(projectId?: number | string, typeCode?: string): QuerySpec {
  return {
    projectId,
    typeCode,
    logic: 'AND',
    conditions: [],
    groups: [],
    pageNo: 1,
    pageSize: 20,
  }
}
