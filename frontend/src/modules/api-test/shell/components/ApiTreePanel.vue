<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed, onMounted, ref } from 'vue'
import { useDialog, useMessage } from 'naive-ui'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useApiDefinitionStore } from '@/modules/api-test/define/stores/definition'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import type { ApiGroupCreateDTO, ApiGroupVO } from '@/modules/api-test/define/types/group'
import ApiWorkspaceSidebar from '@/modules/api-test/define/components/ApiWorkspaceSidebar.vue'
import ApiDefinitionFormDialog from '@/modules/api-test/define/views/ApiDefinitionFormDialog.vue'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'

const PROJECT_ID = 1

const emit = defineEmits<{
  loaded: []
}>()

const message = useMessage()
const dialog = useDialog()
const authStore = useAuthStore()
const definitionStore = useApiDefinitionStore()
const groupStore = useApiGroupStore()
const workspace = useWorkspaceStore()
const { activeTab } = storeToRefs(workspace)

const userId = computed(() => Number(authStore.user?.id) || 0)
const definitions = computed(() => definitionStore.pageResult.records || [])
const loading = computed(() => definitionStore.loading)
const projectId = PROJECT_ID

const selectedId = computed(() => {
  const tab = activeTab.value
  if (tab?.source === 'definition' && tab.refId != null) return tab.refId
  return null
})

const showFormDialog = ref(false)
const editingId = ref<number | null>(null)

const showGroupDialog = ref(false)
const groupSaving = ref(false)
const groupDialogTitle = ref('新建分组')
const groupFormRef = ref<{ validate: () => Promise<void> } | null>(null)
const groupForm = ref<ApiGroupCreateDTO>({
  projectId,
  name: '',
  sortOrder: 0,
  description: '',
})
const groupRules = {
  name: { required: true, message: '请输入分组名称', trigger: 'blur' },
}
const editingGroup = ref<ApiGroupVO | null>(null)
const createGroupParentId = ref<number | null>(null)

onMounted(async () => {
  try {
    await loadData()
  } catch (e: any) {
    message.error(e?.message || '加载接口列表失败')
  } finally {
    emit('loaded')
  }
})

async function loadData() {
  await Promise.all([
    definitionStore.loadPage({ projectId, pageNo: 1, pageSize: 500 }),
    groupStore.loadTree(projectId),
  ])
}

async function onSelectDefinition(id: number | null) {
  if (id == null) return
  try {
    const { detail, draft } = await loadDefinitionIntoTab(id)
    workspace.openOrFocusTab({
      source: 'definition',
      refId: id,
      definitionId: id,
      title: detail.name,
      method: detail.method,
      draft,
    })
  } catch (e: any) {
    message.error(e?.message || '加载接口失败')
  }
}

function openCreateDialog() {
  editingId.value = null
  showFormDialog.value = true
}

function onFormSaved() {
  showFormDialog.value = false
  loadData()
}

function openCreateGroupDialog(parentId?: number | null) {
  createGroupParentId.value = parentId ?? null
  editingGroup.value = null
  groupDialogTitle.value = '新建分组'
  groupForm.value = {
    projectId,
    name: '',
    sortOrder: 0,
    description: '',
    parentId: parentId ?? undefined,
  }
  showGroupDialog.value = true
}

function openEditGroupDialog(group: ApiGroupVO) {
  editingGroup.value = group
  createGroupParentId.value = null
  groupDialogTitle.value = '编辑分组'
  groupForm.value = {
    projectId,
    name: group.name,
    sortOrder: group.sortOrder || 0,
    description: group.description || '',
  }
  showGroupDialog.value = true
}

async function onSaveGroup() {
  try {
    await groupFormRef.value?.validate()
  } catch {
    return
  }

  groupSaving.value = true
  try {
    if (editingGroup.value) {
      await groupStore.update(editingGroup.value.id, {
        name: groupForm.value.name,
        sortOrder: groupForm.value.sortOrder,
        description: groupForm.value.description,
      }, userId.value)
      message.success('更新成功')
    } else {
      await groupStore.create({
        ...groupForm.value,
        parentId: createGroupParentId.value ?? undefined,
        projectId,
      }, userId.value)
      message.success('创建成功')
    }
    showGroupDialog.value = false
    await groupStore.loadTree(projectId)
  } catch (e: any) {
    message.error(e?.message || '操作失败')
  } finally {
    groupSaving.value = false
  }
}

function handleDeleteGroup(group: ApiGroupVO) {
  dialog.warning({
    title: '确认删除',
    content: `确定删除分组「${group.name}」吗？该分组下的所有接口将变为未分组状态。`,
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await groupStore.deleteGroup(group.id)
        message.success('删除成功')
        await groupStore.loadTree(projectId)
      } catch (e: any) {
        message.error(e?.message || '删除失败')
      }
    },
  })
}
</script>

<template>
  <div class="api-tree-panel">
    <ApiWorkspaceSidebar
      :definitions="definitions"
      :loading="loading"
      :selected-id="selectedId"
      :project-id="projectId"
      @select="onSelectDefinition"
      @create-definition="openCreateDialog"
      @create-group="openCreateGroupDialog"
      @edit-group="openEditGroupDialog"
      @delete-group="handleDeleteGroup"
      @refresh="loadData"
    />

    <ApiDefinitionFormDialog
      v-model:show="showFormDialog"
      :definition-id="editingId"
      :project-id="projectId"
      @saved="onFormSaved"
    />

    <n-modal v-model:show="showGroupDialog" :title="groupDialogTitle" preset="card" style="width: 420px">
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
          <n-button @click="showGroupDialog = false">取消</n-button>
          <n-button type="primary" :loading="groupSaving" @click="onSaveGroup">确定</n-button>
        </n-space>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.api-tree-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.api-tree-panel :deep(.workspace-sidebar) {
  width: 100%;
  min-width: 0;
  border-right: none;
}
</style>
