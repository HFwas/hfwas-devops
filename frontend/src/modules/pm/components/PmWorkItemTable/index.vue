<script setup lang="ts">
import type { FieldDefinition, PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { TYPE_META } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  querySpec: QuerySpec
  data: PmWorkItem[]
  loading?: boolean
}>()

const emit = defineEmits<{ rowClick: [PmWorkItem]; refresh: [] }>()

const columns = computed(() => {
  const cols = props.fieldDefs
    .filter((f) => ['title', 'status', 'priority', 'type_code', 'assignee_id'].includes(f.fieldKey) || f.systemFlag !== 1)
    .slice(0, 8)
    .map((f) => ({
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
  if (field.systemFlag === 1) {
    return (row as Record<string, unknown>)[field.fieldKey] as string
  }
  return row.customFields?.[field.fieldKey] as string
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
