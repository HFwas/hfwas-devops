<script setup lang="ts">
import type { FieldDefinition, PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { TYPE_META, systemFieldProp } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  querySpec: QuerySpec
  data: PmWorkItem[]
  loading?: boolean
}>()

const emit = defineEmits<{ rowClick: [PmWorkItem]; refresh: [] }>()

const listFields = computed(() =>
  props.fieldDefs
    .filter((f) => f.showInList)
    .sort((a, b) => (a.listOrder ?? 99) - (b.listOrder ?? 99)),
)

const columns = computed(() => {
  const cols = listFields.value.map((f) => ({
    title: f.fieldName,
    key: f.systemFlag ? f.fieldKey : `custom.${f.fieldKey}`,
    render: (row: PmWorkItem) => renderCell(row, f),
  }))
  return [
    { title: 'ID', key: 'id', width: 80 },
    ...cols,
    { title: '更新时间', key: 'updateTime', width: 170 },
  ]
})

function renderCell(row: PmWorkItem, field: FieldDefinition) {
  if (field.fieldKey === 'type_code') {
    const meta = TYPE_META[row.typeCode]
    return meta ? meta.label : row.typeCode
  }
  if (field.fieldType === 'MARKDOWN') {
    const text = readValue(row, field)
    return text ? `${String(text).slice(0, 40)}${String(text).length > 40 ? '…' : ''}` : ''
  }
  return readValue(row, field)
}

function readValue(row: PmWorkItem, field: FieldDefinition) {
  if (field.systemFlag === 1) {
    const prop = systemFieldProp(field.fieldKey)
    return (row as Record<string, unknown>)[prop]
  }
  return row.customFields?.[field.fieldKey]
}
</script>

<template>
  <n-data-table
    :columns="columns"
    :data="data"
    :loading="loading"
    :row-key="(row: PmWorkItem) => row.id!"
    @update:page="emit('refresh')"
    @row-click="(_: unknown, row: PmWorkItem) => emit('rowClick', row)"
  />
</template>
