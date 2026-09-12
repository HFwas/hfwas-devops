<script setup lang="ts">
import { NButton, NDataTable, NGi, NGrid, NInput, NSelect, NTag, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { dependencyApi } from '@/modules/pipeline/api/dependency'
import type { DependencyComponent } from '@/modules/pipeline/types/dependency'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const message = useMessage()
const loading = ref(false)
const search = ref('')
const language = ref<string | null>(null)
const components = ref<DependencyComponent[]>([])
const pagination = usePagination({ pageSize: 20, pageSizes: [10, 20, 50] })

const langOptions = [
  { label: '全部语言', value: '' },
  { label: 'Java', value: 'java' },
  { label: 'JavaScript', value: 'javascript' },
  { label: 'Go', value: 'go' },
  { label: 'Python', value: 'python' },
  { label: 'Ruby', value: 'ruby' },
  { label: 'Rust', value: 'rust' },
]

async function load() {
  loading.value = true
  try {
    const page = await dependencyApi.pageComponents({
      pageNo: pagination.query.value.pageNo,
      pageSize: pagination.query.value.pageSize,
      search: search.value || undefined,
      language: language.value || undefined,
    })
    components.value = page.records ?? []
    pagination.setTotal(page.total)
  } catch (e) {
    message.error('加载失败')
  } finally {
    loading.value = false
  }
}

const { tablePagination, handlePageChange, handlePageSizeChange } = useDataTablePagination(
  pagination,
  load,
)

const columns: DataTableColumns<DependencyComponent> = [
  { title: '组件名', key: 'name', width: 200, ellipsis: { tooltip: true } },
  { title: '分组', key: 'groupName', width: 160, ellipsis: { tooltip: true } },
  { title: '版本', key: 'version', width: 120 },
  {
    title: '语言',
    key: 'language',
    width: 100,
    render: (row) => h(NTag, { size: 'small', bordered: false }, () => row.language),
  },
  { title: '许可证', key: 'license', width: 120, ellipsis: { tooltip: true } },
  { title: 'PURL', key: 'purl', ellipsis: { tooltip: true }, minWidth: 200 },
  { title: '流水线', key: 'pipelineName', width: 180, ellipsis: { tooltip: true } },
  { title: '检测时间', key: 'createTime', width: 160 },
]

function onSearch() {
  pagination.resetPage()
  void load()
}

onMounted(load)
</script>

<template>
  <div class="dp-list">
    <header class="dp-header">
      <h3>依赖组件</h3>
      <p class="dp-subtitle">从 DEPENDENCY_ANALYSIS 产出的 SBOM 中解析的所有组件</p>
    </header>

    <section class="dp-filter">
      <n-grid :cols="12" :x-gap="12">
        <n-gi :span="6">
          <n-input
            v-model:value="search"
            placeholder="搜索组件名 / PURL / 分组"
            clearable
            @keydown.enter="onSearch"
          />
        </n-gi>
        <n-gi :span="3">
          <n-select
            v-model:value="language"
            :options="langOptions"
            placeholder="选择语言"
            clearable
            @update:value="onSearch"
          />
        </n-gi>
        <n-gi :span="3">
          <n-button type="primary" @click="onSearch">搜索</n-button>
        </n-gi>
      </n-grid>
    </section>

    <section class="dp-table">
      <n-data-table
        :data="components"
        :columns="columns"
        :loading="loading"
        :bordered="false"
        :single-line="false"
        remote
        :pagination="tablePagination"
        :row-key="(row: DependencyComponent) => String(row.id)"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </section>
  </div>
</template>

<style scoped>
.dp-list {
  padding: 16px;
}
.dp-header {
  margin-bottom: 16px;
}
.dp-header h3 {
  margin: 0 0 4px;
  font-size: 18px;
  font-weight: 600;
}
.dp-subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--wb-muted, #646a73);
}
.dp-filter {
  margin-bottom: 16px;
}
.dp-table {
  background: var(--wb-card-bg, #fff);
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  padding: 16px;
}
</style>