<script setup lang="ts">
import JsonEditor from '@/modules/api-test/debug/components/JsonEditor.vue'
import type { BodyMode } from '@/modules/api-test/debug/utils/bodyMode'
import KeyValueEditor from '@/modules/api-test/shared/components/KeyValueEditor.vue'
import type { KeyValuePair } from '@/modules/api-test/shared/types/keyValue'

const MODE_OPTIONS: Array<{ label: string; value: BodyMode }> = [
  { label: 'none', value: 'none' },
  { label: 'json', value: 'json' },
  { label: 'raw', value: 'raw' },
  { label: 'form-data', value: 'form-data' },
  { label: 'urlencoded', value: 'urlencoded' },
]

const RAW_TYPE_OPTIONS = [
  { label: 'text/plain', value: 'text/plain' },
  { label: 'text/html', value: 'text/html' },
  { label: 'application/xml', value: 'application/xml' },
  { label: 'application/json', value: 'application/json' },
]

const props = defineProps<{
  bodyMode: BodyMode
  body: string
  contentType: string
  formFields: KeyValuePair[]
}>()

const emit = defineEmits<{
  'update:bodyMode': [value: BodyMode]
  'update:body': [value: string]
  'update:contentType': [value: string]
  'update:formFields': [value: KeyValuePair[]]
}>()

function formatJson() {
  try {
    emit('update:body', JSON.stringify(JSON.parse(props.body), null, 2))
  } catch {
    // keep invalid json as-is
  }
}
</script>

<template>
  <div class="body-editor" data-testid="body-editor">
    <div class="body-editor__toolbar">
      <n-radio-group
        :value="bodyMode"
        size="small"
        @update:value="(v: BodyMode) => emit('update:bodyMode', v)"
      >
        <n-radio-button v-for="opt in MODE_OPTIONS" :key="opt.value" :value="opt.value">
          {{ opt.label }}
        </n-radio-button>
      </n-radio-group>
      <n-button v-if="bodyMode === 'json'" size="tiny" quaternary @click="formatJson">
        格式化
      </n-button>
      <n-select
        v-if="bodyMode === 'raw'"
        :value="contentType"
        :options="RAW_TYPE_OPTIONS"
        size="tiny"
        style="width: 200px;"
        @update:value="(v: string) => emit('update:contentType', v)"
      />
    </div>

    <n-empty v-if="bodyMode === 'none'" description="该请求不发送 Body" size="small" />

    <json-editor
      v-else-if="bodyMode === 'json'"
      :model-value="body"
      :rows="10"
      @update:model-value="(v) => emit('update:body', v)"
    />

    <n-input
      v-else-if="bodyMode === 'raw'"
      :value="body"
      type="textarea"
      :rows="10"
      placeholder="原始请求体"
      style="font-family: monospace; font-size: 13px;"
      @update:value="(v: string) => emit('update:body', v)"
    />

    <div v-else>
      <key-value-editor
        :pairs="formFields"
        :show-type="bodyMode === 'form-data'"
        key-placeholder="字段名"
        value-placeholder="字段值"
        @update:pairs="(v) => emit('update:formFields', v)"
      />
      <p v-if="bodyMode === 'form-data'" class="body-editor__hint">
        File 行本期不发送（后端 body 仍是文本）。请用 Text 字段。
      </p>
    </div>
  </div>
</template>

<style scoped>
.body-editor {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.body-editor__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.body-editor__hint {
  margin: 6px 0 0;
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}
</style>
