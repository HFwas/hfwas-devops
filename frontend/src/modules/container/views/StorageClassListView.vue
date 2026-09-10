<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'
import { NButton, NCard, NDataTable, NTag, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { storageClassApi } from '@/modules/container/api/storageClass'
import type { StorageClassSummary } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string }>()
const message = useMessage()

const loading = ref(false)
const rows = ref<StorageClassSummary[]>([])

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    rows.value = await storageClassApi.list(props.clusterId)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

const columns: DataTableColumns<StorageClassSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  {
    title: '供给者', key: 'provisioner', width: 200, ellipsis: { tooltip: true },
    render: (row) => row.provisioner || '-',
  },
  {
    title: '回收策略', key: 'reclaimPolicy', width: 120,
    render: (row) => {
      if (!row.reclaimPolicy) return '-'
      return h(NTag, {
        type: row.reclaimPolicy === 'Delete' ? 'warning' : 'info',
        size: 'small',
      }, () => row.reclaimPolicy)
    },
  },
  {
    title: '绑定模式', key: 'volumeBindingMode', width: 160,
    render: (row) => row.volumeBindingMode || '-',
  },
  {
    title: '扩容', key: 'allowVolumeExpansion', width: 80,
    render: (row) => {
      if (row.allowVolumeExpansion == null) return '-'
      return h(NTag, {
        type: row.allowVolumeExpansion ? 'success' : 'default',
        size: 'small',
      }, () => row.allowVolumeExpansion ? '是' : '否')
    },
  },
  { title: '年龄', key: 'age', width: 80 },
]

onMounted(load)
</script>

<template>
  <div class="storageclass-list-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>StorageClass 列表</span>
          <n-button quaternary @click="load">
            <template #icon><RefreshCw :size="16" /></template>
            刷新
          </n-button>
        </n-space>
      </template>
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :bordered="false"
        :row-key="(row: StorageClassSummary) => row.name"
      />
    </n-card>
  </div>
</template>

<style scoped>
.storageclass-list-page {
  max-width: 1200px;
  margin: 0 auto;
}
</style>