<script setup lang="ts">
import { h, type VNodeChild } from 'vue'
import { NButton, NPopconfirm } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { FieldDefinition, PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { PRIORITY_OPTIONS, typeLabel, systemFieldProp } from '@/modules/pm/types'
import { useProjectIssueTypes } from '@/modules/pm/composables/useIssueTypes'
import { useProjectModules } from '@/modules/pm/composables/useProjectModules'
import { useStatusOptions } from '@/modules/pm/composables/useStatusOptions'
import { useUserOptions } from '@/modules/pm/composables/useUserOptions'
import { asId, routeId } from '@/modules/pm/utils/id'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  querySpec: QuerySpec
  data: PmWorkItem[]
  loading?: boolean
  commentCounts?: Record<string, number>
  selectable?: boolean
  checkedRowKeys?: string[]
}>()

const route = useRoute()
const projectId = computed(() => props.querySpec.projectId ?? (routeId(route.params.projectId) || undefined))
const typeCode = computed(() => props.querySpec.typeCode)
const { types: projectTypes } = useProjectIssueTypes(projectId)
const { labelMap: moduleLabelMap } = useProjectModules(projectId)
const { labelMap: userLabelMap } = useUserOptions()
const { labelMap: statusLabelMap } = useStatusOptions(projectId, typeCode)

const emit = defineEmits<{
  rowClick: [PmWorkItem]
  open: [PmWorkItem]
  delete: [PmWorkItem]
  refresh: []
  'update:checkedRowKeys': [string[]]
}>()

const internalCheckedKeys = ref<string[]>([])

const checkedKeys = computed({
  get: () => props.checkedRowKeys ?? internalCheckedKeys.value,
  set: (keys: string[]) => {
    internalCheckedKeys.value = keys
    emit('update:checkedRowKeys', keys)
  },
})

const listFields = computed(() =>
  props.fieldDefs
    .filter((f) => f.showInList)
    .sort((a, b) => (a.listOrder ?? 99) - (b.listOrder ?? 99)),
)

const columns = computed<DataTableColumns<PmWorkItem>>(() => {
  const cols: DataTableColumns<PmWorkItem> = listFields.value.map((f) => ({
    title: f.fieldName,
    key: f.systemFlag ? f.fieldKey : `custom.${f.fieldKey}`,
    ellipsis: { tooltip: true },
    render: (row) => renderCell(row, f),
  }))
  const base: DataTableColumns<PmWorkItem> = [
    {
      title: '编号',
      key: 'itemKey',
      width: 120,
      ellipsis: { tooltip: true },
      render: (row) => row.itemKey ?? (row.itemNo != null ? `#${row.itemNo}` : String(row.id ?? '')),
    },
    ...cols,
    {
      title: '操作',
      key: 'actions',
      width: 100,
      fixed: 'right',
      render: (row) =>
        h('div', { class: 'pm-row-actions' }, [
          h(
            NButton,
            {
              text: true,
              type: 'primary',
              size: 'small',
              onClick: (e: Event) => {
                e.stopPropagation()
                emit('open', row)
              },
            },
            () => '打开',
          ),
          h(
            NPopconfirm,
            { onPositiveClick: () => emit('delete', row) },
            {
              trigger: () =>
                h(
                  NButton,
                  {
                    text: true,
                    type: 'error',
                    size: 'small',
                    onClick: (e: Event) => e.stopPropagation(),
                  },
                  () => '删除',
                ),
              default: () => '确定删除该事项吗？',
            },
          ),
        ]),
    },
  ]
  if (props.selectable) {
    return [{ type: 'selection', fixed: 'left', width: 40 }, ...base]
  }
  return base
})

function statusPillClass(code: string): string {
  const c = code.toLowerCase()
  if (['done', 'closed', 'resolved', 'cancelled', 'canceled'].includes(c)) {
    return 'pm-pill pm-pill--status-done'
  }
  if (['in_progress', 'doing', 'active', 'testing'].includes(c) || c.includes('progress')) {
    return 'pm-pill pm-pill--status-in_progress'
  }
  if (['open', 'todo', 'new', 'pending'].includes(c)) {
    return 'pm-pill pm-pill--status-open'
  }
  return 'pm-pill pm-pill--status-default'
}

function priorityPillClass(value: string): string {
  const v = value.toLowerCase()
  if (v === 'low') return 'pm-pill pm-pill--priority-low'
  if (v === 'high' || v === 'critical') return 'pm-pill pm-pill--priority-high'
  return 'pm-pill pm-pill--priority-medium'
}

function priorityLabel(value: string): string {
  return PRIORITY_OPTIONS.find((o) => o.value === value)?.label ?? value
}

function userInitial(name: string): string {
  const t = name.trim()
  return t ? t.slice(0, 1) : '?'
}

function renderCell(row: PmWorkItem, field: FieldDefinition): VNodeChild {
  if (field.fieldKey === 'type_code') {
    return typeLabel(row.typeCode, projectTypes.value)
  }

  const raw = readValue(row, field)
  if (raw == null || raw === '') return '-'

  if (field.fieldKey === 'status' || field.fieldType === 'STATUS') {
    const code = String(raw)
    const label = statusLabelMap.value[code] ?? code
    return h('span', { class: statusPillClass(code) }, label)
  }

  if (field.fieldKey === 'priority' || field.fieldType === 'PRIORITY') {
    const code = String(raw)
    return h('span', { class: priorityPillClass(code) }, priorityLabel(code))
  }

  if (field.fieldType === 'USER') {
    const id = asId(raw as string | number)
    const name = userLabelMap.value[id] ?? String(raw)
    return h('span', { class: 'pm-user-cell' }, [
      h('span', { class: 'pm-user-avatar' }, userInitial(name)),
      h('span', name),
    ])
  }

  if (field.fieldType === 'MODULE') {
    return moduleLabelMap.value[asId(raw as string | number)] ?? String(raw)
  }

  if (field.fieldType === 'MARKDOWN') {
    const text = String(raw)
    return text.length > 40 ? `${text.slice(0, 40)}…` : text
  }

  return String(raw)
}

function readValue(row: PmWorkItem, field: FieldDefinition): unknown {
  if (field.systemFlag === 1) {
    const prop = systemFieldProp(field.fieldKey)
    return (row as unknown as Record<string, unknown>)[prop]
  }
  return row.customFields?.[field.fieldKey]
}
</script>

<template>
  <n-data-table
    class="pm-work-item-table"
    size="small"
    :bordered="false"
    :single-line="false"
    :columns="columns"
    :data="data"
    :loading="loading"
    :scroll-x="1200"
    :row-key="(row: PmWorkItem) => asId(row.id)"
    :checked-row-keys="selectable ? checkedKeys : undefined"
    @update:checked-row-keys="selectable ? (keys: string[]) => { checkedKeys = keys } : undefined"
    @row-click="(_: unknown, row: PmWorkItem) => emit('rowClick', row)"
  />
</template>

<style scoped>
.pm-work-item-table :deep(.pm-row-actions) {
  display: flex;
  align-items: center;
  gap: 8px;
}
</style>
