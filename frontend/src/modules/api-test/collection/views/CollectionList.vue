<template>
  <div class="collection-list">
    <!-- 头部 -->
    <div class="collection-list__header">
      <h2>接口集合管理</h2>
      <n-button type="primary" @click="openCreateDialog">
        新建集合
      </n-button>
    </div>

    <!-- 搜索 -->
    <div class="collection-list__search">
      <n-input
        v-model:value="searchKeyword"
        placeholder="搜索集合名称"
        clearable
        style="width: 300px"
        @keyup.enter="handleSearch"
      />
      <n-button @click="handleSearch">搜索</n-button>
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
    <div class="collection-list__pagination">
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

    <!-- 创建/编辑弹窗 -->
    <n-modal v-model:show="showDialog" preset="dialog" title="集合" :mask-closable="false">
      <n-form ref="formRef" :model="formData" :rules="formRules" label-placement="left">
        <n-form-item label="集合名称" path="name">
          <n-input v-model:value="formData.name" placeholder="请输入集合名称" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="formData.description" type="textarea" placeholder="请输入集合描述" />
        </n-form-item>
        <n-form-item label="排序" path="sortOrder">
          <n-input-number v-model:value="formData.sortOrder" :min="0" style="width: 100px" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showDialog = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="handleSave">保存</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { NButton, NInput, NInputNumber, NForm, NFormItem, NModal, NDataTable, NPagination, useDialog, useMessage } from 'naive-ui'
import { useRoute, useRouter } from 'vue-router'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import { useAuthStore } from '@/modules/user/stores/auth'
import type { CollectionVO } from '@/modules/api-test/collection/types/collection'

const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const message = useMessage()

const store = useCollectionStore()
const authStore = useAuthStore()

const projectId = computed(() => Number(route.query.projectId) || 1)
const userId = computed(() => Number(authStore.user?.id) || 0)
const pageResult = computed(() => store.pageResult)
const loading = computed(() => store.loading)

const searchKeyword = ref('')
const showDialog = ref(false)
const saving = ref(false)
const editingId = ref<number | null>(null)
const formRef = ref<any>(null)
const formData = ref({
  name: '',
  description: '',
  sortOrder: 0,
})
const formRules = {
  name: [{ required: true, message: '请输入集合名称', trigger: 'blur' }],
}

const columns = [
  { title: '集合名称', key: 'name', width: 150 },
  { title: '描述', key: 'description', ellipsis: { tooltip: true } },
  { title: '文件夹数', key: 'folderCount', width: 80 },
  { title: '接口数', key: 'itemCount', width: 80 },
  { title: '排序', key: 'sortOrder', width: 60 },
  { title: '创建时间', key: 'createTime', width: 170 },
  { title: '更新时间', key: 'updateTime', width: 170 },
  {
    title: '操作',
    key: 'actions',
    width: 200,
    render: (row: CollectionVO) => h('div', { style: 'display: flex; gap: 8px;' }, [
      h(NButton, { size: 'tiny', type: 'primary', onClick: () => router.push(`/api-test/collections/${row.id}?projectId=${projectId.value}`) }, { default: () => '详情' }),
      h(NButton, { size: 'tiny', onClick: () => openEditDialog(row) }, { default: () => '编辑' }),
      h(NButton, { size: 'tiny', type: 'error', onClick: () => handleDelete(row) }, { default: () => '删除' }),
    ]),
  },
]

onMounted(() => {
  loadData()
})

function loadData() {
  store.loadPage({ projectId: projectId.value, keyword: searchKeyword.value || undefined })
}

function handleSearch() {
  store.loadPage({ projectId: projectId.value, keyword: searchKeyword.value || undefined })
}

function onPageChange(page: number) {
  store.loadPage({ projectId: projectId.value, keyword: searchKeyword.value || undefined, pageNo: page, pageSize: Number(pageResult.value.size) })
}

function onPageSizeChange(pageSize: number) {
  store.loadPage({ projectId: projectId.value, keyword: searchKeyword.value || undefined, pageNo: 1, pageSize })
}

function openCreateDialog() {
  editingId.value = null
  formData.value = { name: '', description: '', sortOrder: 0 }
  showDialog.value = true
}

function openEditDialog(row: CollectionVO) {
  editingId.value = row.id
  formData.value = { name: row.name, description: row.description, sortOrder: row.sortOrder }
  showDialog.value = true
}

async function handleSave() {
  try {
    await formRef.value?.validate()
    saving.value = true
    if (editingId.value) {
      await store.update(editingId.value, formData.value, userId.value)
      message.success('更新成功')
    } else {
      await store.create(formData.value, projectId.value, userId.value)
      message.success('创建成功')
    }
    showDialog.value = false
    loadData()
  } catch (e: any) {
    if (e.message) message.error(e.message)
  } finally {
    saving.value = false
  }
}

function handleDelete(row: CollectionVO) {
  dialog.warning({
    title: '确认删除',
    content: `确定要删除集合「${row.name}」吗？关联的文件夹、集合项和执行记录将一并删除。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteCollection(row.id)
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
.collection-list {
  max-width: 1200px;
  margin: 0 auto;
  padding: 16px;
}

.collection-list__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.collection-list__header h2 {
  margin: 0;
  font-size: 20px;
}

.collection-list__search {
  display: flex;
  gap: 8px;
  margin-bottom: 16px;
}

.collection-list__pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 16px;
}
</style>