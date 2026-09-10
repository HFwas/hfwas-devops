<script setup lang="ts">
import { RefreshCw, Trash2, Eye } from '@lucide/vue'
import { NButton, NCard, NDataTable, NInput, NSpace, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { configMapApi } from '@/modules/container/api/configmap'
import { useClusterStore } from '@/modules/container/stores/cluster'
import type { ConfigMapSummary } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'

const props = defineProps<{ clusterId: string }>()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const clusterStore = useClusterStore()

const loading = ref(false)
const rows = ref<ConfigMapSummary[]>([])
const keyword = ref('')
const pagination = usePagination({ pageSize: 20 })

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    const page = await configMapApi.list(props.clusterId, {
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
      namespace: clusterStore.currentNamespace || undefined,
    })
    rows.value = page.records ?? []
    pagination.setTotal(page.total)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  pagination.resetPage()
  load()
}

function openDetail(row: ConfigMapSummary) {
  router.push(`/container/clusters/${props.clusterId}/configmaps/${row.namespace}/${row.name}`)
}

function confirmDelete(row: ConfigMapSummary) {
  dialog.warning({
    title: '删除 ConfigMap',
    content: `确认删除 ConfigMap「${row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await configMapApi.delete(props.clusterId, row.namespace, row.name)
        message.success('已删除')
        await load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const columns: DataTableColumns<ConfigMapSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  { title: 'Namespace', key: 'namespace', width: 140 },
  { title: '数据条目', key: 'dataCount', width: 90 },
  { title: '年龄', key: 'age', width: 80 },
  {
    title: '操作', key: 'actions', width: 140,
    render: (row) => [
      h(NButton, { size: 'tiny', quaternary: true, style: 'margin-right: 8px', onClick: () => openDetail(row) }, () => 'YAML'),
      h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, () => '删除'),
    ],
  },
]

onMounted(load)

watch(() => clusterStore.currentNamespace, () => {
  pagination.resetPage()
  load()
})
</script>

<template>
  <div class="configmap-list-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>ConfigMap 列表</span>
          <n-input v-model:value="keyword" placeholder="搜索" clearable style="width: 240px" @keyup.enter="onSearch" />
          <n-button quaternary @click="onSearch">搜索</n-button>
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
        :row-key="(row: ConfigMapSummary) => `${row.namespace}/${row.name}`"
        :row-props="(row: ConfigMapSummary) => ({
          style: 'cursor: pointer',
          onClick: () => openDetail(row),
        })"
      />
      <div class="pagination-wrap">
        <AppPagination
          :pagination="pagination"
          @change="load"
        />
      </div>
    </n-card>
  </div>
</template>

<style scoped>
.configmap-list-page {
  max-width: 1200px;
  margin: 0 auto;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>