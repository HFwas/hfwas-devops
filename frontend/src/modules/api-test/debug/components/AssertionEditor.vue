<template>
  <div class="assertion-editor">
    <n-data-table
      :columns="columns"
      :data="assertions"
      :bordered="false"
      size="small"
      :max-height="300"
    />
    <n-button size="small" style="margin-top: 8px;" @click="handleAdd">
      添加断言
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { NButton, NSelect, NInput, NSwitch } from 'naive-ui'
import { ASSERTION_SOURCE_OPTIONS, COMPARE_TYPE_OPTIONS } from '@/modules/api-test/debug/types/debug'

const props = defineProps<{
  assertions: any[]
}>()

const emit = defineEmits<{
  'update:assertions': [value: any[]]
}>()

const ASSERTION_SOURCE_OPTIONS_MUTABLE = [...ASSERTION_SOURCE_OPTIONS]
const COMPARE_TYPE_OPTIONS_MUTABLE = [...COMPARE_TYPE_OPTIONS]

const columns = [
  {
    title: '名称',
    key: 'name',
    width: 120,
    render: (row: any, index: number) => h(NInput, {
      value: row.name,
      size: 'small',
      placeholder: '断言名称',
      onUpdateValue: (v: string) => updateRow(index, 'name', v),
    }),
  },
  {
    title: '来源',
    key: 'source',
    width: 140,
    render: (row: any, index: number) => h(NSelect, {
      value: row.source,
      options: ASSERTION_SOURCE_OPTIONS_MUTABLE,
      size: 'small',
      placeholder: '选择来源',
      onUpdateValue: (v: string) => updateRow(index, 'source', v),
    }),
  },
  {
    title: '比较方式',
    key: 'compareType',
    width: 120,
    render: (row: any, index: number) => h(NSelect, {
      value: row.compareType,
      options: COMPARE_TYPE_OPTIONS_MUTABLE,
      size: 'small',
      placeholder: '比较方式',
      onUpdateValue: (v: string) => updateRow(index, 'compareType', v),
    }),
  },
  {
    title: '表达式',
    key: 'expression',
    width: 120,
    render: (row: any, index: number) => h(NInput, {
      value: row.expression,
      size: 'small',
      placeholder: 'JSONPath/Header名',
      onUpdateValue: (v: string) => updateRow(index, 'expression', v),
    }),
  },
  {
    title: '期望值',
    key: 'expectedValue',
    ellipsis: { tooltip: true },
    render: (row: any, index: number) => h(NInput, {
      value: row.expectedValue,
      size: 'small',
      placeholder: '期望值',
      onUpdateValue: (v: string) => updateRow(index, 'expectedValue', v),
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
  const list = [...props.assertions]
  list[index] = { ...list[index], [field]: value }
  emit('update:assertions', list)
}

function handleAdd() {
  emit('update:assertions', [
    ...props.assertions,
    { name: '', source: 'RESPONSE_STATUS', compareType: 'EQUALS', expression: '', expectedValue: '' },
  ])
}

function removeRow(index: number) {
  const list = [...props.assertions]
  list.splice(index, 1)
  emit('update:assertions', list)
}
</script>