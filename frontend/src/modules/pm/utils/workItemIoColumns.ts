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
