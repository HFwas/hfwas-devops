<template>
  <div class="collection-run-history">
    <n-page-header @back="goBack">
      <template #title>
        <span>运行历史</span>
      </template>
    </n-page-header>

    <!-- 运行历史列表 -->
    <n-data-table
      :columns="columns"
      :data="runHistory.records"
      :loading="loading"
      :bordered="false"
      :row-key="(row: any) => row.id"
      size="small"
    />

    <!-- 分页 -->
    <div class="run-history__pagination">
      <n-pagination
        :page="Number(runHistory.current)"
        :page-size="Number(runHistory.size)"
        :item-count="Number(runHistory.total)"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </div>

    <!-- 运行详情弹窗 -->
    <n-modal v-model:show="showDetailDialog" preset="card" title="运行详情" style="width: 90%; max-width: 1000px;" :mask-closable="false">
      <collection-run-result :run-detail="currentRunDetail" />
      <template #action>
        <n-button @click="showDetailDialog = false">关闭</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { NButton, NTag, NDataTable, NPagination, NModal, NPageHeader, useMessage, useDialog } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import CollectionRunResult from '@/modules/api-test/collection/components/CollectionRunResult.vue'
import type { CollectionRunVO } from '@/modules/api-test/collection/types/collection'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const store = useCollectionStore()

const collectionId = computed(() => Number(route.params.id))
const runHistory = computed(() => store.runHistory)
const currentRunDetail = computed(() => store.currentRunDetail)
const loading = computed(() => store.loading)

const showDetailDialog = ref(false)

const columns = [
  { title: '执行名称', key: 'name', ellipsis: { tooltip: true } },
  {
    title: '状态',
    key: 'status',
    width: 80,
    render: (row: CollectionRunVO) => h(NTag, {
      size: 'small',
      type: row.status === 'COMPLETED' ? 'success' : row.status === 'FAILED' ? 'error' : 'info',
    }, { default: () => row.status === 'COMPLETED' ? '完成' : row.status === 'FAILED' ? '失败' : '运行中' }),
  },
  { title: '总数', key: 'totalCount', width: 50 },
  { title: '通过', key: 'passedCount', width: 50 },
  { title: '失败', key: 'failedCount', width: 50 },
  { title: '错误', key: 'errorCount', width: 50 },
  { title: '耗时(ms)', key: 'durationMs', width: 80 },
  { title: '触发方式', key: 'triggerMode', width: 80 },
  { title: '执行时间', key: 'createTime', width: 170 },
  {
    title: '操作',
    key: 'actions',
    width: 120,
    render: (row: CollectionRunVO) => h('div', { style: 'display: flex; gap: 8px;' }, [
      h(NButton, { size: 'tiny', onClick: () => handleViewDetail(row.id) }, { default: () => '详情' }),
      h(NButton, { size: 'tiny', type: 'error', onClick: () => handleDelete(row) }, { default: () => '删除' }),
    ]),
  },
]

onMounted(() => {
  loadData()
})

function loadData() {
  store.loadRunHistory(collectionId.value)
}

function goBack() {
  router.push(`/api-test/collections/${collectionId.value}?projectId=${route.query.projectId}`)
}

function onPageChange(page: number) {
  store.loadRunHistory(collectionId.value, { pageNo: page, pageSize: Number(runHistory.value.size) })
}

function onPageSizeChange(pageSize: number) {
  store.loadRunHistory(collectionId.value, { pageNo: 1, pageSize })
}

async function handleViewDetail(runId: number) {
  await store.loadRunDetail(runId)
  showDetailDialog.value = true
}

function handleDelete(row: CollectionRunVO) {
  dialog.warning({
    title: '确认删除',
    content: '确定要删除该运行记录？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteRun(row.id)
        message.success('删除成功')
        loadData()
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}
</script>

<style scoped>
.collection-run-history {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

.run-history__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>