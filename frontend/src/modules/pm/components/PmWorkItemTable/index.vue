<script setup lang="ts">
import { h, type VNodeChild } from 'vue'
import { NButton, NDropdown } from 'naive-ui'
import type { DataTableColumns, DropdownOption } from 'naive-ui'
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
  open: [PmWorkItem]
  edit: [PmWorkItem]
  copy: [PmWorkItem]
  delete: [PmWorkItem]
  refresh: []
  'update:checkedRowKeys': [string[]]
}>()

const actionOptions: DropdownOption[] = [
  { label: '复制', key: 'copy' },
  { label: '编辑', key: 'edit' },
  { label: '删除', key: 'delete' },
]

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
    minWidth: f.fieldKey === 'title' ? 200 : 100,
    render: (row) => {
      if (f.fieldKey === 'title') {
        return renderLinkCell(row, String(readValue(row, f) ?? '') || '-')
      }
      return renderCell(row, f)
    },
  }))
  const base: DataTableColumns<PmWorkItem> = [
    {
      title: '编号',
      key: 'itemKey',
      width: 120,
      ellipsis: { tooltip: true },
      render: (row) => {
        const text = row.itemKey ?? (row.itemNo != null ? `#${row.itemNo}` : String(row.id ?? ''))
        return renderLinkCell(row, text)
      },
    },
    ...cols,
    {
      title: '操作',
      key: 'actions',
      width: 56,
      fixed: 'right',
      render: (row) =>
        h(
          NDropdown,
          {
            trigger: 'click',
            placement: 'bottom-end',
            options: actionOptions,
            onSelect: (key: string | number) => onActionSelect(String(key), row),
          },
          {
            default: () =>
              h(
                NButton,
                {
                  text: true,
                  size: 'small',
                  class: 'pm-more-btn',
                  onClick: (e: Event) => e.stopPropagation(),
                },
                () => '⋯',
              ),
          },
        ),
    },
  ]
  if (props.selectable) {
    return [{ type: 'selection', fixed: 'left', width: 40 }, ...base]
  }
  return base
})

function onActionSelect(key: string, row: PmWorkItem) {
  if (key === 'copy') emit('copy', row)
  else if (key === 'edit') emit('edit', row)
  else if (key === 'delete') emit('delete', row)
}

function renderLinkCell(row: PmWorkItem, text: string): VNodeChild {
  return h(
    'a',
    {
      class: 'pm-cell-link',
      href: 'javascript:;',
      onClick: (e: Event) => {
        e.preventDefault()
        e.stopPropagation()
        emit('open', row)
      },
    },
    text,
  )
}

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
    :scroll-x="1100"
    :row-key="(row: PmWorkItem) => asId(row.id)"
    :checked-row-keys="selectable ? checkedKeys : undefined"
    @update:checked-row-keys="selectable ? (keys: string[]) => { checkedKeys = keys } : undefined"
  />
</template>

<style scoped>
.pm-work-item-table :deep(.pm-cell-link) {
  color: var(--pm-primary, #3370ff);
  text-decoration: none;
  cursor: pointer;
}

.pm-work-item-table :deep(.pm-cell-link:hover) {
  text-decoration: underline;
}

.pm-work-item-table :deep(.pm-more-btn) {
  font-size: 18px;
  line-height: 1;
  letter-spacing: 1px;
  color: var(--pm-text-secondary, #646a73);
  padding: 0 4px;
}
</style>
