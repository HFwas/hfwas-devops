<template>
  <div class="extract-editor">
    <n-data-table
      :columns="columns"
      :data="extracts"
      :bordered="false"
      size="small"
      :max-height="300"
    />
    <n-button size="small" style="margin-top: 8px;" @click="handleAdd">
      添加提取
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { NButton, NSelect, NInput } from 'naive-ui'
import { EXTRACT_SOURCE_OPTIONS } from '@/modules/api-test/debug/types/debug'

const EXTRACT_SOURCE_OPTIONS_MUTABLE = [...EXTRACT_SOURCE_OPTIONS]

const props = defineProps<{
  extracts: any[]
}>()

const emit = defineEmits<{
  'update:extracts': [value: any[]]
}>()

const columns = [
  {
    title: '变量名',
    key: 'variableName',
    width: 140,
    render: (row: any, index: number) => h(NInput, {
      value: row.variableName,
      size: 'small',
      placeholder: '变量名',
      onUpdateValue: (v: string) => updateRow(index, 'variableName', v),
    }),
  },
  {
    title: '来源',
    key: 'source',
    width: 140,
    render: (row: any, index: number) => h(NSelect, {
      value: row.source,
      options: EXTRACT_SOURCE_OPTIONS_MUTABLE,
      size: 'small',
      placeholder: '来源',
      onUpdateValue: (v: string) => updateRow(index, 'source', v),
    }),
  },
  {
    title: '表达式',
    key: 'expression',
    ellipsis: { tooltip: true },
    render: (row: any, index: number) => h(NInput, {
      value: row.expression,
      size: 'small',
      placeholder: 'JSONPath / Header名',
      onUpdateValue: (v: string) => updateRow(index, 'expression', v),
    }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 60,
    render: (_row: any, index: number) => h(NButton, {
      size: 'tiny',
      type: 'error',
      quaternary: true,
      onClick: () => removeRow(index),
    }, { default: () => '删除' }),
  },
]

function updateRow(index: number, field: string, value: any) {
  const list = [...props.extracts]
  list[index] = { ...list[index], [field]: value }
  emit('update:extracts', list)
}

function handleAdd() {
  emit('update:extracts', [
    ...props.extracts,
    { variableName: '', source: 'RESPONSE_BODY', expression: '' },
  ])
}

function removeRow(index: number) {
  const list = [...props.extracts]
  list.splice(index, 1)
  emit('update:extracts', list)
}
</script>