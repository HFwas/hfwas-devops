<template>
  <div class="key-value-editor">
    <n-data-table
      :columns="columns"
      :data="pairList"
      :bordered="false"
      size="small"
      :max-height="250"
    />
    <n-button size="tiny" class="key-value-editor__add" @click="handleAdd">
      添加
    </n-button>
  </div>
</template>

<script setup lang="ts">
import { computed, h } from 'vue'
import { NButton, NInput, NSwitch } from 'naive-ui'

const props = defineProps<{
  pairs: Record<string, string>
  keyPlaceholder?: string
  valuePlaceholder?: string
}>()

const emit = defineEmits<{
  'update:pairs': [value: Record<string, string>]
}>()

const pairList = computed(() => {
  return Object.entries(props.pairs || {}).map(([key, value]) => ({ key, value }))
})

const columns = [
  {
    title: '键',
    key: 'key',
    width: 200,
    render: (row: any, index: number) => h(NInput, {
      value: row.key,
      size: 'small',
      placeholder: props.keyPlaceholder || '键',
      onUpdateValue: (v: string) => updateKey(index, v),
    }),
  },
  {
    title: '值',
    key: 'value',
    render: (row: any, index: number) => h(NInput, {
      value: row.value,
      size: 'small',
      placeholder: props.valuePlaceholder || '值',
      onUpdateValue: (v: string) => updateValue(index, v),
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

function updateKey(index: number, newKey: string) {
  const entries = Object.entries(props.pairs || {})
  const oldKey = entries[index][0]
  const value = entries[index][1]
  const newPairs = { ...props.pairs }
  delete newPairs[oldKey]
  newPairs[newKey] = value
  emit('update:pairs', newPairs)
}

function updateValue(index: number, newValue: string) {
  const entries = Object.entries(props.pairs || {})
  const key = entries[index][0]
  emit('update:pairs', { ...props.pairs, [key]: newValue })
}

function handleAdd() {
  emit('update:pairs', { ...props.pairs, ['']: '' })
}

function removeRow(index: number) {
  const entries = Object.entries(props.pairs || {})
  const key = entries[index][0]
  const newPairs = { ...props.pairs }
  delete newPairs[key]
  emit('update:pairs', newPairs)
}
</script>

<style scoped>
.key-value-editor__add {
  margin-top: 2px;
}
</style>