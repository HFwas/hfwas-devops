<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'
import { NButton, NCard, NDataTable, NInput, NSpace, NTag, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { serviceApi } from '@/modules/container/api/service'
import type { ServiceSummary } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'
import { useClusterStore } from '@/modules/container/stores/cluster'

const props = defineProps<{ clusterId: string }>()
const router = useRouter()
const message = useMessage()
const clusterStore = useClusterStore()

const loading = ref(false)
const rows = ref<ServiceSummary[]>([])
const keyword = ref('')
const pagination = usePagination({ pageSize: 20 })

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    const page = await serviceApi.list(props.clusterId, {
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

function toDetail(row: ServiceSummary) {
  router.push(`/container/clusters/${props.clusterId}/services/${row.namespace}/${row.name}`)
}

const typeTagType = (t: string) => {
  switch (t) {
    case 'ClusterIP': return 'info' as const
    case 'NodePort': return 'warning' as const
    case 'LoadBalancer': return 'success' as const
    case 'ExternalName': return 'default' as const
    default: return 'default' as const
  }
}

const columns: DataTableColumns<ServiceSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true },
    render: (row) => h(NButton, { size: 'tiny', text: true, type: 'primary', onClick: () => toDetail(row) }, () => row.name) },
  { title: 'Namespace', key: 'namespace', width: 140 },
  { title: '类型', key: 'type', width: 120,
    render: (row) => h(NTag, { type: typeTagType(row.type), size: 'small' }, () => row.type) },
  { title: 'Cluster IP', key: 'clusterIP', width: 140 },
  { title: '外部 IP', key: 'externalIP', ellipsis: { tooltip: true }, width: 140 },
  { title: '端口数', key: 'portCount', width: 70 },
  { title: '年龄', key: 'age', width: 80 },
]

onMounted(load)

watch(() => clusterStore.currentNamespace, () => {
  pagination.resetPage()
  load()
})
</script>

<template>
  <div class="service-list-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>Service 列表</span>
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
        :row-key="(row: ServiceSummary) => `${row.namespace}/${row.name}`"
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
.service-list-page {
  max-width: 1200px;
  margin: 0 auto;
}
.pagination-wrap {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>