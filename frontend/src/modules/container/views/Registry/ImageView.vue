<script setup lang="ts">
import { Search, RefreshCw } from '@lucide/vue'
import { NButton, NCard, NDataTable, NInput, NSpace, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { h, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { imageApi } from '@/modules/container/api/image'
import type { ImageSearchVO } from '@/modules/container/types/registry'
import { usePagination } from '@/shared/composables/usePagination'
import AppPagination from '@/shared/components/AppPagination.vue'
import { isApiError } from '@/shared/errors/apiError'

const router = useRouter()
const message = useMessage()

const loading = ref(false)
const rows = ref<ImageSearchVO[]>([])
const keyword = ref('')
const pagination = usePagination({ pageSize: 20 })

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

async function load() {
  loading.value = true
  try {
    const page = await imageApi.searchImages(
      keyword.value.trim() || undefined,
      Number(pagination.pageNo.value),
      Number(pagination.pageSize.value),
    )
    rows.value = page.records ?? []
    pagination.setTotal(Number(page.total))
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

function goToRegistry(row: ImageSearchVO) {
  router.push(`/container/registries/${row.registryId}`)
}

function goToRepo(row: ImageSearchVO) {
  const shortName = row.repoName.replace(`${row.projectName}/`, '')
  router.push(
    `/container/registries/${row.registryId}/projects/${encodeURIComponent(row.projectName)}/repos/${encodeURIComponent(shortName)}`
  )
}

const columns: DataTableColumns<ImageSearchVO> = [
  {
    title: '镜像名称',
    key: 'repoName',
    ellipsis: { tooltip: true },
    width: 240,
    render: (row) => h('button', {
      type: 'button',
      class: 'link-btn',
      onClick: () => goToRepo(row),
    }, row.repoName),
  },
  {
    title: '所属仓库',
    key: 'registryName',
    width: 160,
    ellipsis: { tooltip: true },
    render: (row) => h('button', {
      type: 'button',
      class: 'link-btn',
      onClick: () => goToRegistry(row),
    }, row.registryName),
  },
  { title: '项目', key: 'projectName', width: 120, ellipsis: { tooltip: true } },
  { title: 'Tag 数', key: 'artifactCount', width: 80 },
  { title: '拉取次数', key: 'pullCount', width: 90 },
  { title: '更新时间', key: 'updateTime', width: 180 },
]

onMounted(load)
</script>

<template>
  <div class="image-search-page">
    <n-card :bordered="false">
      <template #header>
        <n-space align="center">
          <span>镜像列表</span>
          <n-input
            v-model:value="keyword"
            placeholder="搜索镜像名称"
            clearable
            style="width: 280px"
            @keyup.enter="onSearch"
          >
            <template #prefix>
              <Search :size="14" />
            </template>
          </n-input>
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
        :row-key="(row: ImageSearchVO) => `${row.registryId}/${row.repoName}`"
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
.image-search-page {
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