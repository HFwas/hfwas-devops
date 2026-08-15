<template>
  <div class="workspace-request">
    <!-- URL 栏 -->
    <div class="workspace-request__url-bar">
      <n-select
        v-model:value="method"
        :options="METHOD_OPTIONS"
        style="width: 110px; flex-shrink: 0;"
        size="small"
      />
      <n-input
        v-model:value="url"
        placeholder="请求 URL（支持 {{ var }} 变量占位符）"
        size="small"
        clearable
        style="flex: 1;"
      />
      <n-button
        type="primary"
        :loading="executing"
        :disabled="!url"
        @click="$emit('send', getRequestData())"
      >
        <template #icon>
          <n-icon><svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="currentColor"><path d="M8 5v14l11-7z"/></svg></n-icon>
        </template>
        发送
      </n-button>
      <n-button
        quaternary
        size="small"
        @click="showCurlImport = true"
      >
        导入 cURL
      </n-button>
      <n-button
        quaternary
        size="small"
        @click="handleSave"
        :disabled="!definitionId"
      >
        保存
      </n-button>
    </div>

    <!-- 参数 Tab -->
    <n-tabs type="line" default-value="params" size="small" class="workspace-request__tabs" :value="activeTab" @update:value="activeTab = $event">
      <!-- Query 参数 -->
      <n-tab-pane name="params" tab="参数">
        <key-value-editor
          v-model:pairs="queryParams"
          key-placeholder="参数名"
          value-placeholder="参数值（支持 {{ var }}）"
        />
      </n-tab-pane>

      <!-- 请求头 -->
      <n-tab-pane name="headers" tab="请求头">
        <key-value-editor
          v-model:pairs="headers"
          key-placeholder="Header 名称"
          value-placeholder="Header 值（支持 {{ var }}）"
        />
      </n-tab-pane>

      <!-- 请求体 -->
      <n-tab-pane name="body" tab="请求体">
        <div class="workspace-request__body-header">
          <n-select
            v-model:value="contentType"
            :options="CONTENT_TYPE_OPTIONS"
            size="small"
            style="width: 200px;"
          />
        </div>
        <n-input
          v-model:value="body"
          type="textarea"
          :rows="10"
          placeholder="请求体内容（支持 {{ var }} 变量占位符）"
          style="font-family: monospace; font-size: 13px;"
        />
      </n-tab-pane>

      <!-- 脚本 -->
      <n-tab-pane name="scripts" tab="脚本">
        <div class="workspace-request__script-section">
          <div class="workspace-request__script-label">前置脚本（发送前执行）</div>
          <script-editor v-model="preRequestScript" />
        </div>
        <div class="workspace-request__script-section">
          <div class="workspace-request__script-label">后置脚本（响应后执行）</div>
          <script-editor v-model="postResponseScript" />
        </div>
      </n-tab-pane>
    </n-tabs>

    <!-- cURL 导入对话框 -->
    <curl-import-dialog
      v-model:show="showCurlImport"
      @imported="onCurlImported"
    />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, watch } from 'vue'
import { useMessage } from 'naive-ui'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import { useApiDefinitionStore } from '@/modules/api-test/define/stores/definition'
import type { ApiDefinitionDetailVO } from '@/modules/api-test/define/types/definition'
import KeyValueEditor from '@/modules/api-test/shared/components/KeyValueEditor.vue'
import ScriptEditor from '@/modules/api-test/debug/components/ScriptEditor.vue'
import CurlImportDialog from '@/modules/api-test/debug/components/CurlImportDialog.vue'
import type { CurlParseResultVO } from '@/modules/api-test/debug/types/curl'

const props = defineProps<{
  definitionId: number | null
  executing: boolean
}>()

const emit = defineEmits<{
  'send': [data: {
    url: string
    method: string
    headers: Record<string, string>
    queryParams: Record<string, string>
    body: string
    contentType: string
    preRequestScript: string
    postResponseScript: string
  }]
  'saved': []
}>()

const message = useMessage()
const store = useApiDefinitionStore()

const METHOD_OPTIONS = HTTP_METHOD_OPTIONS.map(o => ({ label: o.label, value: o.value }))

const CONTENT_TYPE_OPTIONS = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/xml', value: 'application/xml' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
  { label: 'multipart/form-data', value: 'multipart/form-data' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
]

const activeTab = ref('params')

// 请求参数
const url = ref('')
const method = ref('GET')
const headers = ref<Record<string, string>>({})
const queryParams = ref<Record<string, string>>({})
const body = ref('')
const contentType = ref('application/json')
const preRequestScript = ref('')
const postResponseScript = ref('')

// cURL 导入
const showCurlImport = ref(false)

function onCurlImported(results: CurlParseResultVO[]) {
  const result = results[0]
  if (!result) return
  url.value = result.url || ''
  method.value = result.method || 'GET'
  headers.value = result.headers || {}
  body.value = result.body || ''
  contentType.value = result.contentType || 'application/json'
  activeTab.value = 'params'
  message.success(results.length > 1 ? `已导入首条（共 ${results.length} 条，请在接口测试壳层使用批量导入）` : 'cURL 导入成功')
}

// 监听 API 切换
watch(() => props.definitionId, async (newId) => {
  if (newId) {
    try {
      const detail = await store.loadDetail(newId)
      if (detail) {
        applyDetail(detail)
      }
    } catch (e: any) {
      message.error(e.message || '加载接口详情失败')
    }
  } else {
    resetForm()
  }
}, { immediate: true })

function applyDetail(detail: ApiDefinitionDetailVO) {
  url.value = detail.path || ''
  method.value = detail.method
  headers.value = {}
  queryParams.value = {}
  body.value = ''
  contentType.value = detail.contentType || 'application/json'
  preRequestScript.value = ''
  postResponseScript.value = ''

  // 解析参数
  if (detail.params) {
    for (const param of detail.params) {
      if (param.paramType === 'query') {
        queryParams.value[param.name] = param.defaultValue || ''
      } else if (param.paramType === 'header') {
        headers.value[param.name] = param.defaultValue || ''
      } else if (param.paramType === 'body') {
        body.value = param.defaultValue || ''
      }
    }
  }
}

function resetForm() {
  url.value = ''
  method.value = 'GET'
  headers.value = {}
  queryParams.value = {}
  body.value = ''
  contentType.value = 'application/json'
  preRequestScript.value = ''
  postResponseScript.value = ''
}

function getRequestData() {
  return {
    url: url.value,
    method: method.value,
    headers: { ...headers.value },
    queryParams: { ...queryParams.value },
    body: body.value,
    contentType: contentType.value,
    preRequestScript: preRequestScript.value,
    postResponseScript: postResponseScript.value,
  }
}

async function handleSave() {
  if (!props.definitionId) {
    message.warning('请先选择一个接口')
    return
  }
  try {
    // 构建参数列表
    const params: any[] = [
      ...Object.entries(queryParams.value).map(([name, value]) => ({
        paramType: 'query' as const,
        name,
        defaultValue: value || '',
        dataType: 'string' as const,
        required: false,
        description: '',
        sortOrder: 0,
      })),
      ...Object.entries(headers.value).map(([name, value]) => ({
        paramType: 'header' as const,
        name,
        defaultValue: value || '',
        dataType: 'string' as const,
        required: false,
        description: '',
        sortOrder: 0,
      })),
    ]
    if (body.value) {
      params.push({
        paramType: 'body' as const,
        name: 'body',
        defaultValue: body.value,
        dataType: 'string' as const,
        required: false,
        description: '',
        sortOrder: 0,
      })
    }

    await store.update(props.definitionId, {
      name: store.currentDetail?.name || '',
      path: url.value,
      method: method.value as any,
      params,
      contentType: contentType.value,
    }, 0)
    message.success('保存成功')
    emit('saved')
  } catch (e: any) {
    message.error(e.message || '保存失败')
  }
}
</script>

<style scoped>
.workspace-request {
  display: flex;
  flex-direction: column;
  height: 100%;
}

.workspace-request__url-bar {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 12px 16px 0;
}

.workspace-request__tabs {
  flex: 1;
  padding: 0 16px;
  overflow: auto;
}

.workspace-request__body-header {
  margin-bottom: 8px;
}

.workspace-request__script-section {
  margin-bottom: 16px;
}

.workspace-request__script-label {
  font-size: 12px;
  font-weight: 500;
  color: #666;
  margin-bottom: 4px;
}
</style>