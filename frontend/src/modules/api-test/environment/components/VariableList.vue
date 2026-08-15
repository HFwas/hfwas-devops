<template>
  <div class="variable-list">
    <n-data-table
      :columns="columns"
      :data="variables"
      :bordered="false"
      size="small"
      :max-height="400"
    />
    <n-button size="small" style="margin-top: 8px;" @click="handleAdd">
      添加变量
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { NButton, NInput, NSwitch, NInputNumber } from 'naive-ui'
import type { EnvironmentVariableDTO } from '@/modules/api-test/environment/types/environment'

const props = defineProps<{
  variables: EnvironmentVariableDTO[]
}>()

const emit = defineEmits<{
  'update:variables': [value: EnvironmentVariableDTO[]]
}>()

const columns = [
  {
    title: '变量名',
    key: 'name',
    width: 160,
    render: (row: EnvironmentVariableDTO, index: number) => h(NInput, {
      value: row.name,
      size: 'small',
      placeholder: '变量名',
      onUpdateValue: (v: string) => updateRow(index, 'name', v),
    }),
  },
  {
    title: '变量值',
    key: 'value',
    ellipsis: { tooltip: true },
    render: (row: EnvironmentVariableDTO, index: number) => h(NInput, {
      value: row.value,
      size: 'small',
      placeholder: '变量值',
      type: row.isSecret ? 'password' : 'text',
      showPasswordOnClick: true,
      onUpdateValue: (v: string) => updateRow(index, 'value', v),
    }),
  },
  {
    title: '描述',
    key: 'description',
    render: (row: EnvironmentVariableDTO, index: number) => h(NInput, {
      value: row.description,
      size: 'small',
      placeholder: '描述',
      onUpdateValue: (v: string) => updateRow(index, 'description', v),
    }),
  },
  {
    title: '敏感',
    key: 'isSecret',
    width: 60,
    render: (row: EnvironmentVariableDTO, index: number) => h(NSwitch, {
      value: !!row.isSecret,
      size: 'small',
      onUpdateValue: (v: boolean) => updateRow(index, 'isSecret', v),
    }),
  },
  {
    title: '排序',
    key: 'sortOrder',
    width: 80,
    render: (row: EnvironmentVariableDTO, index: number) => h(NInputNumber, {
      value: row.sortOrder ?? 0,
      size: 'small',
      min: 0,
      style: 'width: 70px;',
      onUpdateValue: (v: number | null) => updateRow(index, 'sortOrder', v ?? 0),
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
  const list = [...props.variables]
  list[index] = { ...list[index], [field]: value }
  emit('update:variables', list)
}

function handleAdd() {
  emit('update:variables', [
    ...props.variables,
    { name: '', value: '', description: '', isSecret: false, sortOrder: props.variables.length },
  ])
}

function removeRow(index: number) {
  const list = [...props.variables]
  list.splice(index, 1)
  emit('update:variables', list)
}
</script>