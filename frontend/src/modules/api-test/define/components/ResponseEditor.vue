<template>
  <div class="response-editor">
    <div class="response-editor__toolbar">
      <n-button size="tiny" @click="addResponse">添加响应定义</n-button>
    </div>

    <div v-for="(resp, index) in responses" :key="index" class="response-editor__item">
      <n-card :title="`响应 ${index + 1}`" size="small" :segmented="true">
        <template #header-extra>
          <n-button size="tiny" text type="error" @click="removeResponse(index)">删除</n-button>
        </template>
        <n-grid :cols="2" :x-gap="16">
          <n-grid-item>
            <n-form-item :label="`状态码`">
              <n-input-number
                :value="resp.statusCode || 200"
                :min="100"
                :max="599"
                @update:value="(v) => updateResponse(index, 'statusCode', v)"
              />
            </n-form-item>
          </n-grid-item>
          <n-grid-item>
            <n-form-item label="Content-Type">
              <n-select
                :value="resp.contentType || 'application/json'"
                :options="contentTypeOptions"
                @update:value="(v) => updateResponse(index, 'contentType', v)"
              />
            </n-form-item>
          </n-grid-item>
        </n-grid>
        <n-form-item label="描述">
          <n-input
            :value="resp.description"
            placeholder="响应描述（可选）"
            @update:value="(v) => updateResponse(index, 'description', v)"
          />
        </n-form-item>
        <n-form-item label="响应体 Schema（JSON）">
          <n-input
            :value="formatJson(resp.bodySchema)"
            type="textarea"
            :rows="4"
            placeholder='{"type": "object", "properties": {}}'
            @update:value="(v) => updateResponse(index, 'bodySchema', parseJson(v))"
          />
        </n-form-item>
        <n-form-item label="响应示例（JSON）">
          <n-input
            :value="formatJson(resp.bodyExample)"
            type="textarea"
            :rows="4"
            placeholder='{"code": 0, "data": {}}'
            @update:value="(v) => updateResponse(index, 'bodyExample', parseJson(v))"
          />
        </n-form-item>
      </n-card>
    </div>

    <div v-if="responses.length === 0" class="response-editor__empty">
      <n-empty description="暂无响应定义">
        <template #extra>
          <n-button size="small" @click="addResponse">添加响应</n-button>
        </template>
      </n-empty>
    </div>
  </div>
</template>

<script setup lang="ts">
import { watch } from 'vue'
import type { ApiDefinitionResponseDTO } from '@/modules/api-test/define/types/definition'

const props = defineProps<{
  responses: ApiDefinitionResponseDTO[]
}>()

const emit = defineEmits<{
  'update:responses': [responses: ApiDefinitionResponseDTO[]]
}>()

const contentTypeOptions = [
  { label: 'application/json', value: 'application/json' },
  { label: 'application/xml', value: 'application/xml' },
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
  { label: 'application/octet-stream', value: 'application/octet-stream' },
  { label: 'multipart/form-data', value: 'multipart/form-data' },
]

function addResponse() {
  const newResponses = [...props.responses]
  newResponses.push({
    statusCode: 200,
    contentType: 'application/json',
    description: '',
    bodySchema: null,
    bodyExample: null,
  })
  emit('update:responses', newResponses)
}

function removeResponse(index: number) {
  const newResponses = [...props.responses]
  newResponses.splice(index, 1)
  emit('update:responses', newResponses)
}

function updateResponse(index: number, key: string, value: any) {
  const newResponses = [...props.responses]
  newResponses[index] = { ...newResponses[index], [key]: value }
  emit('update:responses', newResponses)
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

function parseJson(value: string): any {
  if (!value) return null
  try {
    return JSON.parse(value)
  } catch {
    return value
  }
}
</script>

<style scoped>
.response-editor {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.response-editor__toolbar {
  display: flex;
  gap: 8px;
}

.response-editor__empty {
  padding: 24px 0;
}
</style>