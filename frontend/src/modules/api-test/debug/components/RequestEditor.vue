<template>
  <div class="request-editor">
    <!-- URL 和 Method -->
    <div class="request-editor__url-bar">
      <n-select
        v-model:value="method"
        :options="METHOD_OPTIONS"
        style="width: 120px; flex-shrink: 0;"
        size="small"
      />
      <n-input
        v-model:value="url"
        placeholder="请求 URL（支持 {{ var }} 变量占位符）"
        size="small"
        clearable
        style="flex: 1;"
      />
    </div>

    <!-- 参数 Tab -->
    <n-tabs type="line" default-value="query" size="small" class="request-editor__tabs">
      <!-- Query 参数 -->
      <n-tab-pane name="query" tab="Query 参数">
        <key-value-editor
          v-model:pairs="queryParams"
          key-placeholder="参数名"
          value-placeholder="参数值（支持 {{ var }}）"
        />
      </n-tab-pane>

      <!-- 请求头 -->
      <n-tab-pane name="header" tab="请求头">
        <key-value-editor
          v-model:pairs="headers"
          key-placeholder="Header 名称"
          value-placeholder="Header 值（支持 {{ var }}）"
        />
      </n-tab-pane>

      <!-- 请求体 -->
      <n-tab-pane name="body" tab="请求体">
        <div class="request-editor__body-header">
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
          :rows="8"
          placeholder="请求体内容（支持 {{ var }} 变量占位符）"
          style="font-family: monospace;"
        />
      </n-tab-pane>
    </n-tabs>
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import KeyValueEditor from '@/modules/api-test/shared/components/KeyValueEditor.vue'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'

const props = defineProps<{
  url: string
  method: string
  headers: Record<string, string>
  queryParams: Record<string, string>
  body: string
  contentType: string
}>()

const emit = defineEmits<{
  'update:url': [value: string]
  'update:method': [value: string]
  'update:headers': [value: Record<string, string>]
  'update:queryParams': [value: Record<string, string>]
  'update:body': [value: string]
  'update:contentType': [value: string]
}>()

const METHOD_OPTIONS = HTTP_METHOD_OPTIONS.map(o => ({ label: o.label, value: o.value }))

const CONTENT_TYPE_OPTIONS = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/xml', value: 'application/xml' },
  { label: 'application/x-www-form-urlencoded', value: 'application/x-www-form-urlencoded' },
  { label: 'multipart/form-data', value: 'multipart/form-data' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
]

const url = computed({
  get: () => props.url,
  set: (v) => emit('update:url', v),
})

const method = computed({
  get: () => props.method,
  set: (v) => emit('update:method', v),
})

const headers = computed({
  get: () => props.headers,
  set: (v) => emit('update:headers', v),
})

const queryParams = computed({
  get: () => props.queryParams,
  set: (v) => emit('update:queryParams', v),
})

const body = computed({
  get: () => props.body,
  set: (v) => emit('update:body', v),
})

const contentType = computed({
  get: () => props.contentType,
  set: (v) => emit('update:contentType', v),
})
</script>

<style scoped>
.request-editor__url-bar {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.request-editor__tabs {
  margin-top: 8px;
}

.request-editor__body-header {
  margin-bottom: 8px;
}
</style>