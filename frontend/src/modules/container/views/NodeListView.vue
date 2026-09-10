<script setup lang="ts">
import { RefreshCw } from '@lucide/vue'
import { NButton, NCard, NDataTable, NInput, NSpace, NTag, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { nodeApi } from '@/modules/container/api/node'
import type { NodeSummary } from '@/modules/container/types/resource'
import { isApiError } from '@/shared/errors/apiError'

const props = defineProps<{ clusterId: string }>()
const router = useRouter()
const message = useMessage()

const loading = ref(false)
const rows = ref<NodeSummary[]>([])
const keyword = ref('')

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    rows.value = await nodeApi.list(Number(props.clusterId), keyword.value.trim() || undefined)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

function onSearch() {
  load()
}

function openDetail(row: NodeSummary) {
  router.push(`/container/clusters/${props.clusterId}/nodes/${row.name}`)
}

function formatMem(mb: number): string {
  if (mb >= 1024) return (mb / 1024).toFixed(1) + ' GB'
  return mb + ' MB'
}

const statusTagType = (s: string) => {
  switch (s) {
    case 'Ready': return 'success' as const
    case 'NotReady': return 'error' as const
    default: return 'default' as const
  }
}

const columns: DataTableColumns<NodeSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true },
    render: (row) => h('button', { type: 'button', class: 'link-btn', onClick: () => openDetail(row) }, row.name) },
  { title: '状态', key: 'status', width: 100,
    render: (row) => h(NTag, { type: statusTagType(row.status), size: 'small' }, () => row.status) },
  { title: '角色', key: 'role', width: 120 },
  { title: 'Kubelet', key: 'kubeletVersion', width: 100 },
  { title: '容器运行时', key: 'containerRuntime', ellipsis: { tooltip: true }, width: 140 },
  { title: 'OS', key: 'osImage', ellipsis: { tooltip: true }, width: 160 },
  { title: '架构', key: 'architecture', width: 80 },
  { title: 'CPU', key: 'cpuCapacity', width: 60 },
  { title: '内存', key: 'memoryCapacity', width: 100,
    render: (row) => formatMem(row.memoryCapacity) },
  { title: 'Pod 数', key: 'podCount', width: 70 },
  { title: 'Pod CIDR', key: 'podCIDR', width: 120 },
]

onMounted(load)
</script>

<template>
  <div class="node-list-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>Node 列表</span>
          <n-input v-model:value="keyword" placeholder="搜索 Node 名称" clearable style="width: 240px" @keyup.enter="onSearch" />
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
        :row-key="(row: NodeSummary) => row.name"
      />
    </n-card>
  </div>
</template>

<style scoped>
.node-list-page {
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
</style>