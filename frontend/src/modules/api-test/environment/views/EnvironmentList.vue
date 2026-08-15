<template>
  <div class="environment-list">
    <!-- 头部 -->
    <div class="environment-list__header">
      <h2>环境变量管理</h2>
      <n-button type="primary" @click="openCreateDialog">
        新建环境
      </n-button>
    </div>

    <!-- 表格 -->
    <n-data-table
      :columns="columns"
      :data="pageResult.records"
      :loading="loading"
      :bordered="false"
      :row-key="(row: any) => row.id"
      size="small"
    />

    <!-- 分页 -->
    <div class="environment-list__pagination">
      <n-pagination
        :page="Number(pageResult.current)"
        :page-size="Number(pageResult.size)"
        :item-count="Number(pageResult.total)"
        :page-sizes="[10, 20, 50]"
        show-size-picker
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
      />
    </div>

    <!-- 编辑弹窗 -->
    <environment-form-dialog
      v-model:show="showDialog"
      :environment-id="editingId"
      :project-id="projectId"
      @saved="onSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { NButton, useDialog, useMessage } from 'naive-ui'
import { useRoute } from 'vue-router'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useAuthStore } from '@/modules/user/stores/auth'
import EnvironmentFormDialog from '@/modules/api-test/environment/components/EnvironmentFormDialog.vue'
import type { EnvironmentVO } from '@/modules/api-test/environment/types/environment'

const route = useRoute()
const dialog = useDialog()
const message = useMessage()

const store = useEnvironmentStore()
const authStore = useAuthStore()

const projectId = computed(() => Number(route.query.projectId) || 1)
const userId = computed(() => Number(authStore.user?.id) || 0)
const pageResult = computed(() => store.pageResult)
const loading = computed(() => store.loading)

const showDialog = ref(false)
const editingId = ref<number | null>(null)

const columns = [
  { title: '环境名称', key: 'name', width: 150 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '变量数量', key: 'variableCount', width: 90 },
  { title: '排序', key: 'sortOrder', width: 60 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '更新时间', key: 'updateTime', width: 170 },
  {
    title: '操作',
    key: 'actions',
    width: 160,
    render: (row: EnvironmentVO) => h('div', { style: 'display: flex; gap: 8px;' }, [
      h(NButton, {
        size: 'tiny',
        onClick: () => openEditDialog(row.id),
      }, { default: () => '编辑' }),
      h(NButton, {
        size: 'tiny',
        type: 'error',
        onClick: () => handleDelete(row),
      }, { default: () => '删除' }),
    ]),
  },
]

onMounted(() => {
  loadData()
})

function loadData() {
  store.loadPage({ projectId: projectId.value })
}

function onPageChange(page: number) {
  store.loadPage({ projectId: projectId.value, pageNo: page, pageSize: Number(pageResult.value.size) })
}

function onPageSizeChange(pageSize: number) {
  store.loadPage({ projectId: projectId.value, pageNo: 1, pageSize })
}

function openCreateDialog() {
  editingId.value = null
  showDialog.value = true
}

function openEditDialog(id: number) {
  editingId.value = id
  showDialog.value = true
}

function onSaved() {
  showDialog.value = false
  loadData()
}

function handleDelete(row: EnvironmentVO) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除环境「${row.name}」吗？删除后变量将一并删除。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteEnvironment(row.id)
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
.environment-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

.environment-list__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.environment-list__header h2 {
  margin: 0;
  font-size: 20px;
}

.environment-list__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>