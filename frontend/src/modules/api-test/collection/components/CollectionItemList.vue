<template>
  <div class="collection-item-list">
    <n-data-table
      :columns="columns"
      :data="items"
      :bordered="false"
      :loading="loading"
      size="small"
      :row-key="(row: any) => row.id"
    />
  </div>
</template>

<script setup lang="ts">
import { h } from 'vue'
import { NButton, NSwitch, NTag, NPopconfirm, NDataTable } from 'naive-ui'
import type { CollectionItemVO } from '@/modules/api-test/collection/types/collection'

const props = defineProps<{
  items: CollectionItemVO[]
  loading?: boolean
}>()

const emit = defineEmits<{
  'edit': [item: CollectionItemVO]
  'delete': [item: CollectionItemVO]
  'toggleEnabled': [item: CollectionItemVO]
}>()

const columns = [
  {
    title: '排序',
    key: 'sortOrder',
    width: 60,
  },
  {
    title: '请求方式',
    key: 'method',
    width: 80,
    render: (row: CollectionItemVO) => h(NTag, { type: methodType(row.method), size: 'small' }, { default: () => row.method }),
  },
  {
    title: '接口名称',
    key: 'name',
    ellipsis: { tooltip: true },
    render: (row: CollectionItemVO) => row.name || `${row.method} ${row.path}`,
  },
  {
    title: '请求路径',
    key: 'path',
    ellipsis: { tooltip: true },
    width: 200,
  },
  {
    title: '启用',
    key: 'enabled',
    width: 60,
    render: (row: CollectionItemVO) => h(NSwitch, {
      value: row.enabled,
      size: 'small',
      onUpdateValue: () => emit('toggleEnabled', row),
    }),
  },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row: CollectionItemVO) => h('div', { style: 'display: flex; gap: 8px;' }, [
      h(NButton, { size: 'tiny', onClick: () => emit('edit', row) }, { default: () => '编辑' }),
      h(NPopconfirm, { onPositiveClick: () => emit('delete', row) }, {
        default: () => '确定删除该集合项？',
        trigger: () => h(NButton, { size: 'tiny', type: 'error' }, { default: () => '删除' }),
      }),
    ]),
  },
]

function methodType(method: string): 'success' | 'info' | 'warning' | 'error' {
  const map: Record<string, 'success' | 'info' | 'warning' | 'error'> = {
    GET: 'success',
    POST: 'info',
    PUT: 'warning',
    PATCH: 'warning',
    DELETE: 'error',
  }
  return map[method] || 'default'
}
</script>

<style scoped>
.collection-item-list {
  width: 100%;
}
</style>