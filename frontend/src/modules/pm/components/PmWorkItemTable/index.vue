<script setup lang="ts">
import { h } from 'vue'
import { NButton, NPopconfirm } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import type { FieldDefinition, PmWorkItem, QuerySpec } from '@/modules/pm/types'
import { TYPE_META, systemFieldProp } from '@/modules/pm/types'
import { useProjectModules } from '@/modules/pm/composables/useProjectModules'
import { useUserOptions } from '@/modules/pm/composables/useUserOptions'
import { formatDateTime } from '@/modules/pm/utils/comment'
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
const { labelMap: moduleLabelMap } = useProjectModules(projectId)
const { labelMap: userLabelMap } = useUserOptions()

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
    render: (row) => renderCell(row, f),
  }))
  const base: DataTableColumns<PmWorkItem> = [
    { title: '编号', key: 'itemKey', width: 120, ellipsis: { tooltip: true },
      render: (row) => row.itemKey ?? (row.itemNo != null ? `#${row.itemNo}` : String(row.id ?? '')),
    },
    ...cols,
    {
      title: '评论',
      key: 'comments',
      width: 70,
      render: (row) => {
        const count = row.id != null ? (props.commentCounts?.[asId(row.id)] ?? 0) : 0
        return count > 0 ? String(count) : '-'
      },
    },
    {
      title: '更新时间',
      key: 'updateTime',
      width: 170,
      render: (row) => formatDateTime(row.updateTime),
    },
    {
      title: '操作',
      key: 'actions',
      width: 120,
      fixed: 'right',
      render: (row) =>
        h('div', { style: 'display:flex;align-items:center;gap:8px' }, [
          h(
            NButton,
            {
              text: true,
              type: 'primary',
              onClick: (e: Event) => {
                e.stopPropagation()
                emit('open', row)
              },
            },
            () => '打开',
          ),
          h(
            NPopconfirm,
            {
              onPositiveClick: () => emit('delete', row),
            },
            {
              trigger: () =>
                h(
                  NButton,
                  {
                    text: true,
                    type: 'error',
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
    return [{ type: 'selection', fixed: 'left' }, ...base]
  }
  return base
})

function renderCell(row: PmWorkItem, field: FieldDefinition) {
  if (field.fieldKey === 'type_code') {
    const meta = TYPE_META[row.typeCode]
    return meta ? meta.label : row.typeCode
  }
  if (field.fieldType === 'MODULE') {
    const val = readValue(row, field)
    if (val == null || val === '') return '-'
    return moduleLabelMap.value[asId(val as string | number)] ?? String(val)
  }
  if (field.fieldType === 'USER') {
    const val = readValue(row, field)
    if (val == null || val === '') return '-'
    return userLabelMap.value[asId(val as string | number)] ?? String(val)
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
    :scroll-x="1200"
    :row-key="(row: PmWorkItem) => asId(row.id)"
    :checked-row-keys="selectable ? checkedKeys : undefined"
    @update:checked-row-keys="selectable ? (keys: string[]) => { checkedKeys = keys } : undefined"
    @row-click="(_: unknown, row: PmWorkItem) => emit('rowClick', row)"
  />
</template>
