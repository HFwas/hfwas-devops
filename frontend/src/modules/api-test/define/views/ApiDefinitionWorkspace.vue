<template>
  <div class="api-workspace">
    <!-- 左侧面板 -->
    <api-workspace-sidebar
      :definitions="definitions"
      :loading="loading"
      :selected-id="selectedDefinitionId"
      :project-id="currentProjectId"
      @select="onSelectDefinition"
      @create-definition="openCreateDialog"
      @create-group="openCreateGroupDialog"
      @edit-group="openEditGroupDialog"
      @delete-group="handleDeleteGroup"
      @refresh="loadData"
    />

    <!-- 右侧内容区 -->
    <div class="api-workspace__main">
      <!-- 未选中时显示空状态 -->
      <div v-if="!selectedDefinitionId" class="api-workspace__empty">
        <n-empty description="从左侧选择一个接口开始调试">
          <template #extra>
            <n-button size="small" @click="openCreateDialog">新建接口</n-button>
          </template>
        </n-empty>
      </div>

      <template v-else>
        <!-- 请求编辑器 -->
        <div class="api-workspace__request">
          <api-workspace-request
            ref="requestRef"
            :definition-id="selectedDefinitionId"
            :executing="executing"
            @send="handleSend"
            @saved="loadData"
          />
        </div>

        <!-- 分隔线（可拖拽？后续优化） -->
        <div class="api-workspace__divider"></div>

        <!-- 响应面板 -->
        <div class="api-workspace__response" :style="{ flex: responseFlex }">
          <api-workspace-response :result="currentResult" />
        </div>
      </template>
    </div>

    <!-- 新建/编辑接口对话框 -->
    <api-definition-form-dialog
      v-model:show="showFormDialog"
      :definition-id="editingId"
      :project-id="currentProjectId"
      @saved="onFormSaved"
    />

    <!-- 创建分组对话框 -->
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

<script setup lang="ts">
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useMessage, useDialog } from 'naive-ui'
import { useApiDefinitionStore } from '@/modules/api-test/define/stores/definition'
import { useApiGroupStore } from '@/modules/api-test/define/stores/group'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import type { ApiGroupVO, ApiGroupCreateDTO } from '@/modules/api-test/define/types/group'
import ApiWorkspaceSidebar from '@/modules/api-test/define/components/ApiWorkspaceSidebar.vue'
import ApiWorkspaceRequest from '@/modules/api-test/define/components/ApiWorkspaceRequest.vue'
import ApiWorkspaceResponse from '@/modules/api-test/define/components/ApiWorkspaceResponse.vue'
import ApiDefinitionFormDialog from '@/modules/api-test/define/views/ApiDefinitionFormDialog.vue'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const dialog = useDialog()

const store = useApiDefinitionStore()
const groupStore = useApiGroupStore()
const debugStore = useDebugStore()
const authStore = useAuthStore()
const environmentStore = useEnvironmentStore()

const currentProjectId = computed(() => 1) // 暂时写死
const userId = computed(() => Number(authStore.user?.id) || 0)

// 数据
const definitions = computed(() => store.pageResult.records || [])
const loading = computed(() => store.loading)
const executing = computed(() => debugStore.executing)
const currentResult = computed(() => debugStore.currentResult)

// 选中状态
const selectedDefinitionId = ref<number | null>(null)
const responseFlex = ref('1')

// 请求编辑器引用
const requestRef = ref<InstanceType<typeof ApiWorkspaceRequest> | null>(null)

// 从路由参数初始化选中
watch(() => route.params.id, (id) => {
  if (id) {
    selectedDefinitionId.value = Number(id)
  }
}, { immediate: true })

// 对话框
const showFormDialog = ref(false)
const editingId = ref<number | null>(null)

// 分组对话框
const showGroupDialog = ref(false)
const groupSaving = ref(false)
const groupDialogTitle = ref('新建分组')
const groupFormRef = ref<any>(null)
const groupForm = ref<ApiGroupCreateDTO>({
  projectId: currentProjectId.value,
  name: '',
  sortOrder: 0,
  description: '',
})
const groupRules = {
  name: { required: true, message: '请输入分组名称', trigger: 'blur' },
  placeholder: {},
}
const editingGroup = ref<ApiGroupVO | null>(null)
const createGroupParentId = ref<number | null>(null)

onMounted(async () => {
  await Promise.all([
    loadData(),
    groupStore.loadTree(currentProjectId.value),
    environmentStore.loadAll(currentProjectId.value),
  ])
})

async function loadData() {
  await store.loadPage({ projectId: currentProjectId.value })
}

// 选中 API
function onSelectDefinition(id: number | null) {
  selectedDefinitionId.value = id
  if (id) {
    router.replace(`/api-test/definitions/${id}`)
  } else {
    router.replace('/api-test/definitions')
  }
}

// 发送请求
async function handleSend(data: {
  url: string
  method: string
  headers: Record<string, string>
  queryParams: Record<string, string>
  body: string
  contentType: string
  preRequestScript: string
  postResponseScript: string
}) {
  if (!data.url) {
    message.warning('请输入请求 URL')
    return
  }

  try {
    await debugStore.execute({
      projectId: currentProjectId.value,
      definitionId: selectedDefinitionId.value ?? undefined,
      url: data.url,
      method: data.method,
      headers: data.headers,
      queryParams: data.queryParams,
      body: data.body || undefined,
      contentType: data.contentType || undefined,
      preRequestScript: data.preRequestScript || undefined,
      postResponseScript: data.postResponseScript || undefined,
    })
    message.success('调试完成')
  } catch (e: any) {
    message.error(e.message || '请求失败')
  }
}

// 新建接口
function openCreateDialog() {
  editingId.value = null
  showFormDialog.value = true
}

function onFormSaved() {
  showFormDialog.value = false
  loadData()
  groupStore.loadTree(currentProjectId.value)
}

// 分组管理
function openCreateGroupDialog(parentId?: number | null) {
  createGroupParentId.value = parentId ?? null
  editingGroup.value = null
  groupDialogTitle.value = '新建分组'
  groupForm.value = {
    projectId: currentProjectId.value,
    name: '',
    sortOrder: 0,
    description: '',
    parentId: parentId ?? undefined,
  } as any
  showGroupDialog.value = true
}

function openEditGroupDialog(group: ApiGroupVO) {
  editingGroup.value = group
  createGroupParentId.value = null
  groupDialogTitle.value = '编辑分组'
  groupForm.value = {
    projectId: currentProjectId.value,
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
        projectId: currentProjectId.value,
      }, userId.value)
      message.success('创建成功')
    }
    showGroupDialog.value = false
    await groupStore.loadTree(currentProjectId.value)
  } catch (e: any) {
    message.error(e.message || '操作失败')
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
        await groupStore.loadTree(currentProjectId.value)
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}
</script>

<style scoped>
.api-workspace {
  display: flex;
  height: 100%;
  overflow: hidden;
}

.api-workspace__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-width: 0;
}

.api-workspace__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
}

.api-workspace__request {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: auto;
  min-height: 300px;
}

.api-workspace__divider {
  height: 4px;
  background: #f0f0f0;
  cursor: row-resize;
  flex-shrink: 0;
}

.api-workspace__response {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  min-height: 200px;
}
</style>