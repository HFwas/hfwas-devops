<script setup lang="ts">
import { RefreshCw, Trash2 } from '@lucide/vue'
import { NButton, NCard, NDataTable, NSelect, NInput, NSpace, NTag, NBadge, useDialog, useMessage, NPopconfirm } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { podApi } from '@/modules/container/api/pod'
import { useClusterStore } from '@/modules/container/stores/cluster'
import type { PodSummary } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'

const props = defineProps<{ clusterId: string }>()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()
const clusterStore = useClusterStore()

const loading = ref(false)
const rows = ref<PodSummary[]>([])
const keyword = ref('')
const pagination = usePagination({ pageSize: 20 })

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    const page = await podApi.list(Number(props.clusterId), {
      ...pagination.query.value,
      keyword: keyword.value.trim() || undefined,
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

function openDetail(row: PodSummary) {
  router.push(`/container/clusters/${props.clusterId}/pods/${row.namespace}/${row.name}`)
}

function confirmDelete(row: PodSummary) {
  dialog.warning({
    title: '删除 Pod',
    content: `确认删除 Pod「${row.name}」？`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await podApi.delete(Number(props.clusterId), row.namespace, row.name)
        message.success('已删除')
        await load()
      } catch (e) {
        message.error(errorMessage(e))
      }
    },
  })
}

const statusTagType = (s: string) => {
  switch (s) {
    case 'Running': return 'success' as const
    case 'Pending': return 'warning' as const
    case 'Succeeded': return 'info' as const
    case 'Failed': return 'error' as const
    default: return 'default' as const
  }
}

const columns: DataTableColumns<PodSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true },
    render: (row) => h('button', { type: 'button', class: 'link-btn', onClick: () => openDetail(row) }, row.name) },
  { title: 'Namespace', key: 'namespace', width: 140 },
  { title: '状态', key: 'status', width: 100,
    render: (row) => h(NTag, { type: statusTagType(row.status), size: 'small' }, () => row.status) },
  { title: 'Node', key: 'nodeName', ellipsis: { tooltip: true }, width: 140 },
  { title: 'IP', key: 'podIP', width: 130 },
  { title: '容器', key: 'containerCount', width: 70,
    render: (row) => `${row.readyContainers}/${row.containerCount}` },
  { title: '重启', key: 'restarts', width: 60 },
  { title: '年龄', key: 'age', width: 80 },
  { title: '操作', key: 'actions', width: 100,
    render: (row) => h(NButton, { size: 'tiny', quaternary: true, type: 'error', onClick: () => confirmDelete(row) }, () => '删除') },
]

onMounted(load)
</script>

<template>
  <div class="pod-list-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>Pod 列表</span>
          <n-input v-model:value="keyword" placeholder="搜索 Pod 名称" clearable style="width: 240px" @keyup.enter="onSearch" />
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
        :row-key="(row: PodSummary) => `${row.namespace}/${row.name}`"
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
.pod-list-page {
  max-width: 1200px;
  margin: 0 auto;
}
.link-btn {
  background: none;
  border: none;
  color: var(--n-primary-color);
  cursor: pointer;
  padding: 0;
  font-size: inherit;
}
.link-btn:hover { text-decoration: underline; }
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>