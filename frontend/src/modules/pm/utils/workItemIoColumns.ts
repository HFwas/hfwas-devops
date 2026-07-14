import type { FieldDefinition, WorkItemIoColumn } from '@/modules/pm/types'

const CORE_FIELDS = new Set(['title', 'status', 'priority'])

export function buildIoColumns(fieldDefs: FieldDefinition[]): WorkItemIoColumn[] {
  const columns: WorkItemIoColumn[] = [
    {
      fieldKey: 'itemKey',
      fieldName: '编号',
      fieldType: 'ITEM_KEY',
      systemField: true,
      exportable: true,
      importable: true,
      defaultSelected: true,
    },
  ]
  for (const def of fieldDefs) {
    if (def.fieldKey === 'type_code') continue
    columns.push({
      fieldKey: def.fieldKey,
      fieldName: def.fieldName,
      fieldType: def.fieldType,
      systemField: def.systemFlag === 1,
      exportable: true,
      importable: true,
      defaultSelected: def.showInList === true || CORE_FIELDS.has(def.fieldKey),
    })
  }
  return columns
}

export function defaultExportFieldKeys(columns: WorkItemIoColumn[]): string[] {
  return columns.filter((c) => c.exportable !== false && c.defaultSelected !== false).map((c) => c.fieldKey)
}

export function defaultImportFieldKeys(columns: WorkItemIoColumn[]): string[] {
  return columns.filter((c) => c.importable !== false && c.fieldKey !== 'itemKey').map((c) => c.fieldKey)
}

/** 类型配置的默认字段；空则回退 showInList / 内置默认 */
export function resolveDefaultFieldKeys(
  configured: string[] | undefined | null,
  columns: WorkItemIoColumn[],
  mode: 'export' | 'import',
): string[] {
  const allowed = new Set(
    columns
      .filter((c) => (mode === 'export' ? c.exportable !== false : c.importable !== false))
      .map((c) => c.fieldKey),
  )
  const fromConfig = (configured ?? []).filter((k) => allowed.has(k))
  if (fromConfig.length) return fromConfig
  return mode === 'export' ? defaultExportFieldKeys(columns) : defaultImportFieldKeys(columns)
}
