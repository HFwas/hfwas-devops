<template>
  <div class="api-definition-detail">
    <n-spin :show="loading">
      <div v-if="detail" class="api-definition-detail__content">
        <!-- 头部 -->
        <div class="api-definition-detail__header">
          <n-button quaternary @click="goBack">
            <template #icon>
              <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M20 11H7.83l5.59-5.59L12 4l-8 8 8 8 1.41-1.41L7.83 13H20v-2z"/></svg></n-icon>
            </template>
          </n-button>
          <div class="api-definition-detail__header-info">
            <div class="api-definition-detail__header-top">
              <n-tag :type="methodTagType" size="small" style="margin-right: 8px;">
                {{ detail.method }}
              </n-tag>
              <h2 class="api-definition-detail__title">{{ detail.name }}</h2>
              <n-tag :type="statusTagType" size="small" style="margin-left: 8px;">
                {{ statusLabel }}
              </n-tag>
              <span class="api-definition-detail__version">v{{ detail.version }}</span>
            </div>
            <div class="api-definition-detail__path">
              <code>{{ detail.path }}</code>
            </div>
          </div>
          <div class="api-definition-detail__header-actions">
            <n-button size="small" @click="openEditDialog">编辑</n-button>
            <n-button
              v-if="detail.status === 'DRAFT'"
              size="small"
              type="success"
              @click="handlePublish"
            >
              发布
            </n-button>
            <n-button
              v-if="detail.status === 'PUBLISHED'"
              size="small"
              type="warning"
              @click="handleDeprecate"
            >
              废弃
            </n-button>
            <n-button
              v-if="detail.status === 'PUBLISHED' || detail.status === 'DEPRECATED'"
              size="small"
              @click="handleRevertDraft"
            >
              恢复草稿
            </n-button>
            <n-button size="small" type="error" @click="handleDelete">删除</n-button>
          </div>
        </div>

        <!-- 基本信息 -->
        <n-card title="基本信息" size="small" class="detail-card">
          <n-descriptions :column="3" size="small" bordered>
            <n-descriptions-item label="接口名称">
              {{ detail.name }}
            </n-descriptions-item>
            <n-descriptions-item label="请求方式">
              <n-tag :type="methodTagType" size="tiny">{{ detail.method }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="当前版本">
              v{{ detail.version }}
            </n-descriptions-item>
            <n-descriptions-item label="请求路径">
              <code>{{ detail.path }}</code>
            </n-descriptions-item>
            <n-descriptions-item label="所属分组">
              {{ detail.groupName || '未分组' }}
            </n-descriptions-item>
            <n-descriptions-item label="接口状态">
              <n-tag :type="statusTagType" size="tiny">{{ statusLabel }}</n-tag>
            </n-descriptions-item>
            <n-descriptions-item label="协议" :span="3">
              {{ detail.protocol || 'HTTP' }}
            </n-descriptions-item>
            <n-descriptions-item label="描述" :span="3">
              {{ detail.description || '暂无描述' }}
            </n-descriptions-item>
          </n-descriptions>
        </n-card>

        <!-- 请求参数 -->
        <n-card title="请求参数" size="small" class="detail-card">
          <template v-if="detail.params && detail.params.length > 0">
            <n-tabs type="line" default-value="query">
              <n-tab-pane name="query" tab="Query 参数">
                <n-data-table
                  :columns="paramColumns"
                  :data="filteredParams('query')"
                  :bordered="false"
                  size="small"
                  :max-height="300"
                />
              </n-tab-pane>
              <n-tab-pane name="header" tab="请求头">
                <n-data-table
                  :columns="paramColumns"
                  :data="filteredParams('header')"
                  :bordered="false"
                  size="small"
                  :max-height="300"
                />
              </n-tab-pane>
              <n-tab-pane name="path" tab="路径参数">
                <n-data-table
                  :columns="paramColumns"
                  :data="filteredParams('path')"
                  :bordered="false"
                  size="small"
                  :max-height="300"
                />
              </n-tab-pane>
              <n-tab-pane name="body" tab="请求体">
                <n-data-table
                  :columns="paramColumns"
                  :data="filteredParams('body')"
                  :bordered="false"
                  size="small"
                  :max-height="300"
                />
              </n-tab-pane>
            </n-tabs>
          </template>
          <n-empty v-else description="暂无参数" />
        </n-card>

        <!-- 响应定义 -->
        <n-card title="响应定义" size="small" class="detail-card">
          <template v-if="detail.responses && detail.responses.length > 0">
            <div v-for="(resp, index) in detail.responses" :key="resp.id" class="response-item">
              <n-card :title="`${resp.statusCode} ${resp.contentType}`" size="small" :embedded="true">
                <n-descriptions :column="2" size="small">
                  <n-descriptions-item label="状态码">
                    {{ resp.statusCode }}
                  </n-descriptions-item>
                  <n-descriptions-item label="Content-Type">
                    {{ resp.contentType }}
                  </n-descriptions-item>
                  <n-descriptions-item label="描述" :span="2">
                    {{ resp.description || '暂无描述' }}
                  </n-descriptions-item>
                </n-descriptions>
                <template v-if="resp.bodySchema">
                  <n-divider />
                  <div class="response-item__label">Schema</div>
                  <n-code :code="formatJson(resp.bodySchema)" language="json" />
                </template>
                <template v-if="resp.bodyExample">
                  <n-divider />
                  <div class="response-item__label">示例</div>
                  <n-code :code="formatJson(resp.bodyExample)" language="json" />
                </template>
              </n-card>
            </div>
          </template>
          <n-empty v-else description="暂无响应定义" />
        </n-card>
      </div>
      <n-empty v-else-if="!loading" description="接口不存在" />
    </n-spin>

    <!-- 编辑对话框 -->
    <api-definition-form-dialog
      v-model:show="showEditDialog"
      :definition-id="definitionId"
      :project-id="detail?.projectId || 0"
      @saved="onEditSaved"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NTag, NButton, useDialog, useMessage } from 'naive-ui'
import { useApiDefinitionStore } from '@/modules/api-test/define/stores/definition'
import { useAuthStore } from '@/modules/user/stores/auth'
import { API_STATUS_OPTIONS, HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import type { ApiDefinitionParamVO, ParamType } from '@/modules/api-test/define/types/definition'
import ApiDefinitionFormDialog from '@/modules/api-test/define/views/ApiDefinitionFormDialog.vue'

const route = useRoute()
const router = useRouter()
const dialog = useDialog()
const message = useMessage()

const store = useApiDefinitionStore()
const authStore = useAuthStore()

const definitionId = computed(() => Number(route.params.id))
const userId = computed(() => Number(authStore.user?.id) || 0)

const detail = computed(() => store.currentDetail)
const loading = ref(false)
const showEditDialog = ref(false)

const statusLabel = computed(() => {
  const option = API_STATUS_OPTIONS.find((o) => o.value === detail.value?.status)
  return option?.label || detail.value?.status
})

const methodTagType = computed(() => {
  const method = detail.value?.method
  if (method === 'GET') return 'success'
  if (method === 'POST') return 'primary'
  if (method === 'PUT' || method === 'PATCH') return 'warning'
  if (method === 'DELETE') return 'error'
  return 'default'
})

const statusTagType = computed(() => {
  const status = detail.value?.status
  if (status === 'PUBLISHED') return 'success'
  if (status === 'DEPRECATED') return 'error'
  return 'default'
})

const paramColumns = [
  { title: '参数名称', key: 'name', width: 180 },
  { title: '数据类型', key: 'dataType', width: 100 },
  {
    title: '必填',
    key: 'required',
    width: 60,
    render: (row: ApiDefinitionParamVO) => row.required ? '是' : '否',
  },
  { title: '默认值', key: 'defaultValue', width: 120 },
  { title: '描述', key: 'description', width: 200, ellipsis: { tooltip: true } },
  { title: '示例', key: 'example', width: 150, ellipsis: { tooltip: true } },
]

onMounted(async () => {
  loading.value = true
  try {
    await store.loadDetail(definitionId.value)
  } catch (e: any) {
    message.error(e.message || '加载失败')
  } finally {
    loading.value = false
  }
})

function goBack() {
  router.push('/api-test/definitions')
}

function filteredParams(type: ParamType) {
  return detail.value?.params?.filter((p) => p.paramType === type) || []
}

function openEditDialog() {
  showEditDialog.value = true
}

function onEditSaved() {
  showEditDialog.value = false
  store.loadDetail(definitionId.value)
}

function handlePublish() {
  dialog.info({
    title: '确认发布',
    content: '确定要发布该接口吗？发布后版本号将自动更新。',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.publish(definitionId.value, userId.value)
        message.success('发布成功')
      } catch (e: any) {
        message.error(e.message || '发布失败')
      }
    },
  })
}

function handleDeprecate() {
  dialog.warning({
    title: '确认废弃',
    content: '确定要废弃该接口吗？废弃后不影响已有使用。',
    positiveText: '确定废弃',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deprecate(definitionId.value, userId.value)
        message.success('废弃成功')
      } catch (e: any) {
        message.error(e.message || '废弃失败')
      }
    },
  })
}

function handleRevertDraft() {
  dialog.info({
    title: '确认恢复草稿',
    content: '恢复草稿后接口状态将变为草稿，可重新编辑发布。',
    positiveText: '确定',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.revertDraft(definitionId.value, userId.value)
        message.success('已恢复草稿')
      } catch (e: any) {
        message.error(e.message || '操作失败')
      }
    },
  })
}

function handleDelete() {
  dialog.warning({
    title: '确认删除',
    content: '确定要删除该接口定义吗？删除后无法恢复。',
    positiveText: '确定删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      try {
        await store.deleteDefinition(definitionId.value)
        message.success('删除成功')
        router.push('/api-test/definitions')
      } catch (e: any) {
        message.error(e.message || '删除失败')
      }
    },
  })
}

function formatJson(value: any): string {
  if (!value) return ''
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}
</script>

<style scoped>
.api-definition-detail {
  max-width: 1200px;
  margin: 0 auto;
}

.api-definition-detail__header {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  padding: 16px 0;
  border-bottom: 1px solid #eee;
  margin-bottom: 16px;
}

.api-definition-detail__header-info {
  flex: 1;
}

.api-definition-detail__header-top {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.api-definition-detail__title {
  font-size: 18px;
  font-weight: 600;
  margin: 0;
}

.api-definition-detail__version {
  font-size: 12px;
  color: #999;
  margin-left: 8px;
}

.api-definition-detail__path {
  margin-top: 4px;
}

.api-definition-detail__path code {
  font-size: 13px;
  background: #f5f5f5;
  padding: 2px 8px;
  border-radius: 4px;
  color: #666;
}

.api-definition-detail__header-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}

.detail-card {
  margin-bottom: 12px;
}

.response-item {
  margin-bottom: 8px;
}

.response-item__label {
  font-size: 12px;
  font-weight: 500;
  color: #666;
  margin-bottom: 4px;
}
</style>