<template>
  <div class="collection-detail">
    <!-- 顶部导航 -->
    <n-page-header @back="goBack">
      <template #title>
        <span>{{ detail?.name || '集合详情' }}</span>
      </template>
      <template #extra>
        <n-button size="small" @click="loadDetail" :loading="loading">刷新</n-button>
      </template>
    </n-page-header>

    <n-spin :show="loading">
      <!-- 基本信息 -->
      <n-card title="基本信息" size="small" class="detail-section">
        <n-descriptions :column="2" size="small">
          <n-descriptions-item label="名称">{{ detail?.name }}</n-descriptions-item>
          <n-descriptions-item label="描述">{{ detail?.description || '-' }}</n-descriptions-item>
        </n-descriptions>
        <template #action>
          <n-button size="tiny" @click="openEditDialog">编辑信息</n-button>
        </template>
      </n-card>

      <!-- 操作区域 -->
      <n-card title="操作" size="small" class="detail-section">
        <div class="action-bar">
          <n-button type="primary" :loading="executing" @click="handleRun">
            <template #icon>
              <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></n-icon>
            </template>
            执行集合
          </n-button>
          <n-select
            v-model:value="runEnvironmentId"
            :options="environmentOptions"
            placeholder="选择环境（可选）"
            clearable
            size="small"
            style="width: 200px"
          />
          <n-button size="small" @click="handleAddItem">添加接口</n-button>
          <n-button size="small" @click="handleAddFolder">新建文件夹</n-button>
        </div>
      </n-card>

      <!-- 树形结构 + 集合项列表 -->
      <n-grid :cols="2" :x-gap="12">
        <!-- 左侧：树形结构 -->
        <n-gi>
          <n-card title="接口树" size="small" class="detail-section">
            <collection-tree
              :folders="detail?.folders || []"
              :items="detail?.items || []"
              @select-item="onSelectItem"
              @select-folder="onSelectFolder"
            />
          </n-card>
        </n-gi>

        <!-- 右侧：集合项列表 -->
        <n-gi>
          <n-card :title="selectedItem ? '集合项详情' : '集合项列表'" size="small" class="detail-section">
            <template v-if="selectedItem">
              <n-descriptions :column="1" size="small" bordered>
                <n-descriptions-item label="接口名称">{{ selectedItem.name || `${selectedItem.method} ${selectedItem.path}` }}</n-descriptions-item>
                <n-descriptions-item label="请求方式">{{ selectedItem.method }}</n-descriptions-item>
                <n-descriptions-item label="请求路径">{{ selectedItem.path }}</n-descriptions-item>
                <n-descriptions-item label="启用状态">
                  <n-switch :value="selectedItem.enabled" size="small" @update:value="toggleItemEnabled(selectedItem)" />
                </n-descriptions-item>
                <n-descriptions-item label="所属文件夹">{{ selectedItem.folderId || '根级' }}</n-descriptions-item>
              </n-descriptions>
              <template #action>
                <n-button size="tiny" type="error" @click="handleDeleteItem(selectedItem)">移出集合</n-button>
              </template>
            </template>
            <template v-else>
              <collection-item-list
                :items="currentItems"
                :loading="false"
                @delete="handleDeleteItem"
                @toggle-enabled="toggleItemEnabled"
              />
            </template>
          </n-card>
        </n-gi>
      </n-grid>

      <!-- 运行结果 -->
      <n-card v-if="lastRunResult" title="最近执行结果" size="small" class="detail-section">
        <collection-run-result :run-detail="lastRunResult" />
      </n-card>
    </n-spin>

    <!-- 编辑集合弹窗 -->
    <n-modal v-model:show="showEditDialog" preset="dialog" title="编辑集合" :mask-closable="false">
      <n-form ref="editFormRef" :model="editFormData" :rules="formRules" label-placement="left">
        <n-form-item label="集合名称" path="name">
          <n-input v-model:value="editFormData.name" />
        </n-form-item>
        <n-form-item label="描述" path="description">
          <n-input v-model:value="editFormData.description" type="textarea" />
        </n-form-item>
        <n-form-item label="排序" path="sortOrder">
          <n-input-number v-model:value="editFormData.sortOrder" :min="0" style="width: 100px" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showEditDialog = false">取消</n-button>
        <n-button type="primary" :loading="saving" @click="handleSaveEdit">保存</n-button>
      </template>
    </n-modal>

    <!-- 添加接口弹窗 -->
    <n-modal v-model:show="showAddItemDialog" preset="dialog" title="添加接口到集合" :mask-closable="false">
      <n-form label-placement="left">
        <n-form-item label="目标文件夹">
          <n-select
            v-model:value="addItemFolderId"
            :options="folderOptions"
            placeholder="根级（不选择文件夹）"
            clearable
          />
        </n-form-item>
        <n-form-item label="接口定义">
          <n-select
            v-model:value="addItemDefinitionId"
            :options="definitionOptions"
            placeholder="选择接口定义"
            filterable
            :loading="loadingDefinitions"
          />
        </n-form-item>
        <n-form-item label="覆盖名称">
          <n-input v-model:value="addItemName" placeholder="留空则使用接口定义名称" />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showAddItemDialog = false">取消</n-button>
        <n-button type="primary" :loading="addingItem" @click="handleSaveItem">添加</n-button>
      </template>
    </n-modal>

    <!-- 新建文件夹弹窗 -->
    <n-modal v-model:show="showAddFolderDialog" preset="dialog" title="新建文件夹" :mask-closable="false">
      <n-form ref="folderFormRef" :model="folderFormData" :rules="folderFormRules" label-placement="left">
        <n-form-item label="文件夹名称" path="name">
          <n-input v-model:value="folderFormData.name" />
        </n-form-item>
        <n-form-item label="父文件夹">
          <n-select
            v-model:value="folderFormData.parentId"
            :options="folderOptions"
            placeholder="根级文件夹"
            clearable
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showAddFolderDialog = false">取消</n-button>
        <n-button type="primary" :loading="addingFolder" @click="handleSaveFolder">创建</n-button>
      </template>
    </n-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NInput, NInputNumber, NSelect, NForm, NFormItem, NModal, NPageHeader, NCard, NSpin, NGrid, NGi, NDescriptions, NDescriptionsItem, NSwitch, NIcon, useMessage, useDialog } from 'naive-ui'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useAuthStore } from '@/modules/user/stores/auth'
import CollectionTree from '@/modules/api-test/collection/components/CollectionTree.vue'
import CollectionItemList from '@/modules/api-test/collection/components/CollectionItemList.vue'
import CollectionRunResult from '@/modules/api-test/collection/components/CollectionRunResult.vue'
import type { CollectionItemVO, CollectionRunDetailVO } from '@/modules/api-test/collection/types/collection'
import { apiDefinitionApi } from '@/modules/api-test/define/api/definition'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const store = useCollectionStore()
const environmentStore = useEnvironmentStore()
const authStore = useAuthStore()

const collectionId = computed(() => Number(route.params.id))
const projectId = computed(() => Number(route.query.projectId) || 1)
const userId = computed(() => Number(authStore.user?.id) || 0)
const detail = computed(() => store.currentDetail)
const loading = computed(() => store.loading)
const executing = computed(() => store.executing)

// 运行
const runEnvironmentId = ref<number | null>(null)
const lastRunResult = ref<CollectionRunDetailVO | null>(null)
const environmentOptions = computed(() => {
  return environmentStore.allList.map(env => ({
    label: env.name,
    value: env.id,
  }))
})

// 当前选中的集合项
const selectedItem = ref<CollectionItemVO | null>(null)

// 当前显示的集合项（根级 + 选中文件夹下的）
const currentItems = computed(() => {
  if (selectedFolder.value) {
    return findFolderItems(detail.value?.folders || [], selectedFolder.value.id)
  }
  return detail.value?.items || []
})

const selectedFolder = ref<any>(null)

// 编辑弹窗
const showEditDialog = ref(false)
const saving = ref(false)
const editFormRef = ref<any>(null)
const editFormData = ref({ name: '', description: '', sortOrder: 0 })
const formRules = {
  name: [{ required: true, message: '请输入集合名称', trigger: 'blur' }],
}

// 添加接口弹窗
const showAddItemDialog = ref(false)
const addingItem = ref(false)
const addItemFolderId = ref<number | null>(null)
const addItemDefinitionId = ref<number | null>(null)
const addItemName = ref('')
const loadingDefinitions = ref(false)
const definitionOptions = ref<Array<{ label: string; value: number }>>([])

// 文件夹选项（用于下拉选择）
const folderOptions = computed(() => {
  return flattenFolders(detail.value?.folders || [])
})

// 新建文件夹弹窗
const showAddFolderDialog = ref(false)
const addingFolder = ref(false)
const folderFormRef = ref<any>(null)
const folderFormData = ref({ name: '', parentId: null as number | null })
const folderFormRules = {
  name: [{ required: true, message: '请输入文件夹名称', trigger: 'blur' }],
}

onMounted(async () => {
  await environmentStore.loadAll(projectId.value)
  await loadDetail()
})

async function loadDetail() {
  await store.loadDetail(collectionId.value)
  selectedItem.value = null
  selectedFolder.value = null
}

function goBack() {
  router.push(`/api-test/collections?projectId=${projectId.value}`)
}

function onSelectItem(item: CollectionItemVO) {
  selectedItem.value = item
}

function onSelectFolder(folder: any) {
  selectedFolder.value = folder
  selectedItem.value = null
}

// 编辑
function openEditDialog() {
  editFormData.value = {
    name: detail.value?.name || '',
    description: detail.value?.description || '',
    sortOrder: detail.value?.sortOrder || 0,
  }
  showEditDialog.value = true
}

async function handleSaveEdit() {
  try {
    await editFormRef.value?.validate()
    saving.value = true
    await store.update(collectionId.value, editFormData.value, userId.value)
    message.success('更新成功')
    showEditDialog.value = false
    await loadDetail()
  } catch (e: any) {
    if (e.message) message.error(e.message)
  } finally {
    saving.value = false
  }
}

// 添加接口
async function handleAddItem() {
  loadingDefinitions.value = true
  showAddItemDialog.value = true
  addItemFolderId.value = null
  addItemDefinitionId.value = null
  addItemName.value = ''
  try {
    // 加载项目下的接口定义
    const result = await apiDefinitionApi.page({ projectId: projectId.value, pageSize: 200 })
    definitionOptions.value = (result.records || []).map((def: any) => ({
      label: `${def.method} ${def.path} - ${def.name}`,
      value: def.id,
    }))
  } catch {
    definitionOptions.value = []
  } finally {
    loadingDefinitions.value = false
  }
}

async function handleSaveItem() {
  if (!addItemDefinitionId.value) {
    message.warning('请选择接口定义')
    return
  }
  addingItem.value = true
  try {
    await store.addItem(collectionId.value, {
      folderId: addItemFolderId.value,
      definitionId: addItemDefinitionId.value,
      name: addItemName.value || undefined,
      enabled: true,
    }, userId.value)
    message.success('添加成功')
    showAddItemDialog.value = false
    await loadDetail()
  } catch (e: any) {
    message.error(e.message || '添加失败')
  } finally {
    addingItem.value = false
  }
}

// 删除集合项
function handleDeleteItem(item: CollectionItemVO) {
  dialog.warning({
    title: '确认',
    content: '确定将该接口移出集合？',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteItem(collectionId.value, item.id)
        message.success('已移出')
        selectedItem.value = null
        await loadDetail()
      } catch (e: any) {
        message.error(e.message || '操作失败')
      }
    },
  })
}

// 切换启用状态
async function toggleItemEnabled(item: CollectionItemVO) {
  try {
    await store.updateItem(collectionId.value, item.id, {
      definitionId: item.definitionId,
      enabled: !item.enabled,
    })
    await loadDetail()
  } catch (e: any) {
    message.error(e.message || '操作失败')
  }
}

// 新建文件夹
function handleAddFolder() {
  folderFormData.value = { name: '', parentId: null }
  showAddFolderDialog.value = true
}

async function handleSaveFolder() {
  try {
    await folderFormRef.value?.validate()
    addingFolder.value = true
    await store.createFolder(collectionId.value, {
      name: folderFormData.value.name,
      parentId: folderFormData.value.parentId,
    }, userId.value)
    message.success('创建成功')
    showAddFolderDialog.value = false
    await loadDetail()
  } catch (e: any) {
    if (e.message) message.error(e.message)
  } finally {
    addingFolder.value = false
  }
}

// 执行集合
async function handleRun() {
  try {
    const result = await store.runCollection(collectionId.value, runEnvironmentId.value ?? undefined, userId.value)
    message.success('执行完成')
    // 加载执行详情
    if (result?.id) {
      lastRunResult.value = await store.loadRunDetail(result.id)
    }
  } catch (e: any) {
    message.error(e.message || '执行失败')
  }
}

// 工具函数
function flattenFolders(folders: any[]): Array<{ label: string; value: number }> {
  const result: Array<{ label: string; value: number }> = []
  function walk(list: any[], prefix = '') {
    for (const f of list) {
      result.push({ label: `${prefix}${f.name}`, value: f.id })
      if (f.children) walk(f.children, `${prefix}${f.name} / `)
    }
  }
  walk(folders)
  return result
}

function findFolderItems(folders: any[], folderId: number): any[] {
  for (const f of folders) {
    if (f.id === folderId) return f.items || []
    if (f.children) {
      const found = findFolderItems(f.children, folderId)
      if (found.length > 0) return found
    }
  }
  return []
}
</script>

<style scoped>
.collection-detail {
  max-width: 1400px;
  margin: 0 auto;
  padding: 16px;
}

.detail-section {
  margin-bottom: 12px;
}

.action-bar {
  display: flex;
  align-items: center;
  gap: 12px;
}
</style>