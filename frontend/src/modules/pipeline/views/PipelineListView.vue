<script setup lang="ts">
import { h } from 'vue'
import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { repoShortName, stackSummary } from '@/modules/pipeline/graph/pipelineGraph'
import type { PipelineSummary } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'
import { useAuthStore } from '@/modules/user/stores/auth'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const loading = ref(false)
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const pagination = usePagination()
const rows = ref<PipelineSummary[]>([])

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function statusType(status?: string | null) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'RUNNING') return 'info'
  if (status === 'CANCELLED') return 'warning'
  return 'default'
}

function formatTime(value?: string | null): string {
  if (!value) return '—'
  return String(value).replace('T', ' ').slice(0, 19)
}

const columns: DataTableColumns<PipelineSummary> = [
  { title: '名称', key: 'name', ellipsis: { tooltip: true } },
  {
    title: '技术栈',
    key: 'stack',
    render: (row) => stackSummary(row.stack, row.runtimeVersion, row.toolVersion),
  },
  {
    title: '仓库',
    key: 'repoUrl',
    ellipsis: { tooltip: true },
    render: (row) => repoShortName(row.repoUrl),
  },
  {
    title: '最近运行',
    key: 'lastRunStatus',
    render: (row) =>
      h(NSpace, { size: 8, align: 'center' }, () => [
        row.lastRunStatus
          ? h(NTag, { size: 'small', type: statusType(row.lastRunStatus), bordered: false }, () => row.lastRunStatus)
          : h('span', { class: 'muted' }, '未运行'),
        h('span', { class: 'muted' }, formatTime(row.lastRunTime)),
      ]),
  },
  {
    title: '操作',
    key: 'actions',
    width: 220,
    render: (row) =>
      h(NSpace, { size: 8 }, () => [
        h(NButton, { size: 'small', type: 'primary', ghost: true, onClick: () => void run(row) }, () => '运行'),
        h(NButton, { size: 'small', onClick: () => router.push(`/pipeline/pipelines/${row.id}`) }, () => '编辑'),
        h(NButton, { size: 'small', type: 'error', ghost: true, onClick: () => confirmDelete(row) }, () => '删除'),
      ]),
  },
]

async function load() {
  loading.value = true
  try {
    const page = await pipelineApi.page({
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
  void load()
}

async function run(row: PipelineSummary) {
  try {
    const result = await pipelineApi.start(row.id)
    await router.push(`/pipeline/pipelines/${row.id}/runs/${result.id}`)
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function confirmDelete(row: PipelineSummary) {
  dialog.warning({
    title: '删除流水线',
    content: `确认删除「${row.name}」？删除后不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await pipelineApi.delete(row.id)
      message.success('已删除')
      pagination.afterDelete(rows.value.length)
      await load()
    },
  })
}

onMounted(load)
watch(
  () => route.query.keyword,
  (value) => {
    keyword.value = typeof value === 'string' ? value : ''
    pagination.resetPage()
    void load()
  },
)
watch(
  () => [auth.activeTenantId, auth.tenantVersion] as const,
  () => {
    pagination.resetPage()
    void load()
  },
)
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header title="流水线" subtitle="配置仓库的 clone / 构建 / 测试，并在控制台手动运行">
      <template #extra>
        <n-button type="primary" @click="router.push('/pipeline/pipelines/new')">新建</n-button>
      </template>
    </n-page-header>
    <n-space>
      <n-input
        v-model:value="keyword"
        placeholder="搜索流水线名称"
        clearable
        style="width: 280px"
        @keyup.enter="onSearch"
      />
      <n-button @click="onSearch">查询</n-button>
    </n-space>
    <n-data-table :columns="columns" :data="rows" :loading="loading" :bordered="false" :single-line="false" />
    <AppPagination :pagination="pagination" :on-change="load" />
  </n-space>
</template>

<style scoped>
.muted {
  color: var(--wb-muted, #6b7280);
  font-size: 12px;
}
</style>
