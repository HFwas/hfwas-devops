<template>
  <div class="api-definition-list">
    <!-- 左侧分组树 -->
    <div class="api-definition-list__sidebar">
      <div class="api-definition-list__sidebar-header">
        <span class="api-definition-list__sidebar-title">接口分组</span>
        <n-button size="tiny" quaternary @click="showCreateGroupDialog = true">
          <template #icon>
            <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M11 11V5h2v6h6v2h-6v6h-2v-6H5v-2z"/></svg></n-icon>
          </template>
        </n-button>
      </div>
      <n-input
        v-model:value="groupFilter"
        placeholder="搜索分组"
        size="tiny"
        clearable
        class="api-definition-list__sidebar-search"
      />
      <div class="api-definition-list__sidebar-tree">
        <n-spin :show="groupLoading">
          <api-group-tree
            :project-id="currentProjectId"
            @select="onGroupSelect"
          />
        </n-spin>
      </div>
    </div>

    <!-- 右侧列表 -->
    <div class="api-definition-list__main">
      <!-- 工具栏 -->
      <div class="api-definition-list__toolbar">
        <div class="api-definition-list__toolbar-left">
          <n-button type="primary" @click="openCreateDialog">
            <template #icon>
              <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M11 11V5h2v6h6v2h-6v6h-2v-6H5v-2z"/></svg></n-icon>
            </template>
            新建接口
          </n-button>
        </div>
        <div class="api-definition-list__toolbar-right">
          <n-input
            v-model:value="searchKeyword"
            placeholder="搜索接口名称/路径"
            clearable
            style="width: 240px"
            @keyup.enter="onSearch"
          />
          <n-select
            v-model:value="filterMethod"
            :options="HTTP_METHOD_OPTIONS as any"
            placeholder="请求方式"
            clearable
            style="width: 120px"
            @update:value="onSearch"
          />
          <n-select
            v-model:value="filterStatus"
            :options="API_STATUS_OPTIONS as any"
            placeholder="接口状态"
            clearable
            style="width: 120px"
            @update:value="onSearch"
          />
          <n-button @click="onSearch">搜索</n-button>
          <n-button quaternary @click="resetFilters">重置</n-button>
        </div>
      </div>

      <!-- 表格 -->
      <n-data-table
        :columns="columns"
        :data="pageResult.records"
        :loading="loading"
        :pagination="pagination"
        :bordered="false"
        :single-line="false"
        size="small"
        @update:page="onPageChange"
        @update:page-size="onPageSizeChange"
      />

      <!-- 创建/编辑对话框 -->
      <api-definition-form-dialog
        v-model:show="showFormDialog"
        :definition-id="editingId"
        :project-id="currentProjectId"
        @saved="onFormSaved"
      />

      <!-- 创建分组对话框 -->
      <n-modal v-model:show="showCreateGroupDialog" title="新建分组" preset="card" style="width: 420px">
        <n-form ref="groupFormRef" :model="groupForm" :rules="groupRules" label-placement="top">
          <n-form-item label="分组名称" path="name">
            <n-input v-model:value="groupForm.name" placeholder="请输入分组名称" />
          </n-form-item>
          <n-form-item label="排序" path="sortOrder">
            <n-input-number v-model:value="groupForm.sortOrder" :min="0" placeholder="排序序号" />
          </n-form-item>
          <n-form-item label="描述" path="description">
            <n-input v-model:value="groupForm.description" type="textarea" placeholder="分组描述（可选）" />
          </n-form-item>
        </n-form>
        <template #footer>
          <n-space justify="end">
            <n-button @click="showCreateGroupDialog = false">取消</n-button>
            <n-button type="primary" :loading="groupCreating" @click="onCreateGroup">确定</n-button>
          </n-space>
        </template>
      </n-modal>
    </div>
  </div>
</template>

<script setup lang="ts">
import { h, ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import { NButton, NIcon, NSpace, NTag, useDialog, useMessage } from 'naive-ui'
import { useApiDefinitionStore } from '@/modules/api-test/define/stores/definition'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import { useAuthStore } from '@/modules/user/stores/auth'
import { API_STATUS_OPTIONS, HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import type { ApiDefinitionVO } from '@/modules/api-test/define/types/definition'
import ApiGroupTree from '@/modules/api-test/define/components/ApiGroupTree.vue'
import ApiDefinitionFormDialog from '@/modules/api-test/define/views/ApiDefinitionFormDialog.vue'
import type { ApiGroupCreateDTO } from '@/modules/api-test/define/types/group'

const router = useRouter()
const dialog = useDialog()
const message = useMessage()

const store = useApiDefinitionStore()
const groupStore = useApiGroupStore()
const authStore = useAuthStore()

const currentProjectId = computed(() => 1) // 暂时写死，后续从项目上下文获取
const userId = computed(() => Number(authStore.user?.id) || 0)

const { pageResult, loading } = store

// 筛选状态
const searchKeyword = ref('')
const filterMethod = ref<string | null>(null)
const filterStatus = ref<string | null>(null)
const groupFilter = ref('')
const groupLoading = ref(false)

// 对话框状态
const showFormDialog = ref(false)
const editingId = ref<number | null>(null)
const showCreateGroupDialog = ref(false)
const groupCreating = ref(false)

// 分组表单
const groupForm = ref<ApiGroupCreateDTO>({
  projectId: currentProjectId.value,
  name: '',
  sortOrder: 0,
  description: '',
})
const groupRules = {
  name: { required: true, message: '请输入分组名称', trigger: 'blur' },
}

// 表格列定义
const columns: any[] = [
  {
    title: '接口名称',
    key: 'name',
    width: 200,
    ellipsis: { tooltip: true },
    render: (row: ApiDefinitionVO) =>
      h('a', {
        class: 'link-text',
        onClick: () => router.push(`/api-test/definitions/${row.id}`),
      }, row.name),
  },
  {
    title: '请求路径',
    key: 'path',
    width: 280,
    ellipsis: { tooltip: true },
  },
  {
    title: '请求方式',
    key: 'method',
    width: 100,
    render: (row: ApiDefinitionVO) => {
      const option = HTTP_METHOD_OPTIONS.find((o) => o.value === row.method)
      return h(NTag, { size: 'small', color: { color: option?.color, textColor: '#fff' } }, { default: () => row.method })
    },
  },
  {
    title: '状态',
    key: 'status',
    width: 90,
    render: (row: ApiDefinitionVO) => {
      const option = API_STATUS_OPTIONS.find((o) => o.value === row.status)
      return h(NTag, { size: 'small', type: row.status === 'PUBLISHED' ? 'success' : row.status === 'DEPRECATED' ? 'error' : 'default' }, { default: () => option?.label })
    },
  },
  {
    title: '版本',
    key: 'version',
    width: 80,
  },
  {
    title: '分组',
    key: 'groupName',
    width: 120,
    ellipsis: { tooltip: true },
  },
  {
    title: '更新时间',
    key: 'updateTime',
    width: 170,
  },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    fixed: 'right' as const,
    render: (row: ApiDefinitionVO) =>
      h(NSpace, {}, {
        default: () => [
          h(NButton, { size: 'small', quaternary: true, onClick: () => router.push(`/api-test/definitions/${row.id}`) }, { default: () => '查看' }),
          h(NButton, { size: 'small', quaternary: true, onClick: () => openEditDialog(row.id) }, { default: () => '编辑' }),
          h(NButton, { size: 'small', quaternary: true, type: 'error', onClick: () => handleDelete(row.id, row.name) }, { default: () => '删除' }),
        ],
      }),
  },
]

// 分页配置
const pagination = computed(() => ({
  page: Number(pageResult.current) || 1,
  pageSize: Number(pageResult.size) || 20,
  total: Number(pageResult.total) || 0,
  pageSizes: [10, 20, 50, 100],
  showSizePicker: true,
  showQuickJumper: true,
}))

onMounted(async () => {
  await Promise.all([
    store.loadPage({ projectId: currentProjectId.value }),
    groupStore.loadTree(currentProjectId.value),
  ])
})

function onGroupSelect(groupId: number | null) {
  store.loadPage({ groupId: groupId ?? undefined })
}

function onSearch() {
  store.loadPage({
    keyword: searchKeyword.value || undefined,
    method: filterMethod.value as any || undefined,
    status: filterStatus.value as any || undefined,
  })
}

function resetFilters() {
  searchKeyword.value = ''
  filterMethod.value = null
  filterStatus.value = null
  store.loadPage({ projectId: currentProjectId.value, keyword: undefined, method: undefined, status: undefined })
}

function onPageChange(page: number) {
  store.changePage(page, Number(pageResult.size) || 20)
}

function onPageSizeChange(size: number) {
  store.changePage(1, size)
}

function openCreateDialog() {
  editingId.value = null
  showFormDialog.value = true
}

function openEditDialog(id: number) {
  editingId.value = id
  showFormDialog.value = true
}

function onFormSaved() {
  showFormDialog.value = false
  store.loadPage()
}

function handleDelete(id: number, name: string) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除接口「${name}」吗？删除后不可恢复。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteDefinition(id)
        message.success('删除成功')
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

async function onCreateGroup() {
  groupCreating.value = true
  try {
    await groupStore.create(groupForm.value, userId.value)
    message.success('创建成功')
    showCreateGroupDialog.value = false
    groupForm.value = { projectId: currentProjectId.value, name: '', sortOrder: 0, description: '' }
  } catch (e: any) {
    message.error(e.message || '创建失败')
  } finally {
    groupCreating.value = false
  }
}
</script>

<style scoped>
.api-definition-list {
  display: flex;
  height: 100%;
  gap: 12px;
}

.api-definition-list__sidebar {
  width: 240px;
  flex-shrink: 0;
  background: #fff;
  border-radius: 6px;
  border: 1px solid #eee;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.api-definition-list__sidebar-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 12px 8px;
}

.api-definition-list__sidebar-title {
  font-size: 14px;
  font-weight: 500;
}

.api-definition-list__sidebar-search {
  padding: 0 12px 8px;
}

.api-definition-list__sidebar-tree {
  flex: 1;
  overflow-y: auto;
  padding: 0 8px 8px;
}

.api-definition-list__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.api-definition-list__toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 0;
  flex-wrap: wrap;
  gap: 8px;
}

.api-definition-list__toolbar-right {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

:deep(.link-text) {
  color: #2080f0;
  cursor: pointer;
  text-decoration: none;
}

:deep(.link-text:hover) {
  color: #409eff;
}
</style>