<script setup lang="ts">
import { Plus, Search } from '@lucide/vue'
import { NButton, NSpace, NTag, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { repoShortName, stackSummary } from '@/modules/pipeline/graph/pipelineGraph'
import { formatDateTime, formatGitRef, runStatusLabel, runStatusTagType } from '@/modules/pipeline/status'
import type { PipelineSummary } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import AppPagination from '@/shared/components/AppPagination.vue'
import { usePagination } from '@/shared/composables/usePagination'
import { useAuthStore } from '@/modules/user/stores/auth'
import '@/modules/pipeline/styles/pipeline-theme.css'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const auth = useAuthStore()
const loading = ref(false)
const keyword = ref(typeof route.query.keyword === 'string' ? route.query.keyword : '')
const pagination = usePagination({ pageSize: 20, pageSizes: [10, 20, 50] })
const rows = ref<PipelineSummary[]>([])

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

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

function open(row: PipelineSummary) {
  if (row.lastRunId != null) {
    void router.push(`/pipeline/pipelines/${row.id}/runs/${row.lastRunId}`)
    return
  }
  void router.push(`/pipeline/pipelines/${row.id}`)
}

function edit(row: PipelineSummary) {
  void router.push(`/pipeline/pipelines/${row.id}/edit`)
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

const columns: DataTableColumns<PipelineSummary> = [
  {
    title: '名称',
    key: 'name',
    ellipsis: { tooltip: true },
    render: (row) =>
      h(
        'button',
        { type: 'button', class: 'pl-name-link', onClick: (e: MouseEvent) => { e.stopPropagation(); open(row) } },
        row.name,
      ),
  },
  {
    title: '代码源',
    key: 'repoUrl',
    ellipsis: { tooltip: true },
    render: (row) => repoShortName(row.repoUrl),
  },
  {
    title: '分支',
    key: 'gitRef',
    width: 140,
    ellipsis: { tooltip: true },
    render: (row) => formatGitRef(row.gitRef),
  },
  {
    title: '构建环境',
    key: 'stack',
    width: 200,
    ellipsis: { tooltip: true },
    render: (row) => stackSummary(row.stack, row.runtimeVersion, row.toolVersion),
  },
  {
    title: '最近状态',
    key: 'lastRunStatus',
    width: 110,
    render: (row) =>
      h(
        NTag,
        { size: 'small', bordered: false, type: runStatusTagType(row.lastRunStatus) },
        () => runStatusLabel(row.lastRunStatus),
      ),
  },
  {
    title: '最近执行',
    key: 'lastRunTime',
    width: 180,
    render: (row) => formatDateTime(row.lastRunTime),
  },
  {
    title: '操作',
    key: 'actions',
    width: 168,
    render: (row) =>
      h('div', { class: 'pl-table-actions', onClick: (e: MouseEvent) => e.stopPropagation() }, [
        h(NSpace, { size: 8 }, () => [
          h(NButton, { size: 'small', text: true, type: 'primary', onClick: () => run(row) }, () => '运行'),
          h(NButton, { size: 'small', text: true, onClick: () => edit(row) }, () => '编辑'),
          h(NButton, { size: 'small', text: true, type: 'error', onClick: () => confirmDelete(row) }, () => '删除'),
        ]),
      ]),
  },
]

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
  <div class="pl-page">
    <header class="pl-hero">
      <div class="pl-hero-main">
        <h1 class="pl-hero-title">流水线</h1>
        <p class="pl-hero-desc">配置仓库的 clone / 构建 / 测试，并在控制台手动运行</p>
      </div>
      <div class="pl-hero-extra">
        <n-button type="primary" @click="router.push('/pipeline/pipelines/new')">
          <template #icon><Plus :size="14" /></template>
          新建流水线
        </n-button>
      </div>
    </header>

    <div class="pl-toolbar">
      <n-input
        v-model:value="keyword"
        placeholder="搜索流水线名称"
        clearable
        style="width: 280px"
        @keyup.enter="onSearch"
      >
        <template #prefix><Search :size="14" /></template>
      </n-input>
      <n-button @click="onSearch">查询</n-button>
    </div>

    <div class="pl-table">
      <n-data-table
        :columns="columns"
        :data="rows"
        :loading="loading"
        :bordered="false"
        :row-key="(row: PipelineSummary) => String(row.id)"
        :row-props="(row: PipelineSummary) => ({
          style: 'cursor: pointer',
          onClick: () => open(row),
        })"
      />
    </div>

    <div class="pl-footer">
      <AppPagination :pagination="pagination" :on-change="load" />
    </div>
  </div>
</template>
