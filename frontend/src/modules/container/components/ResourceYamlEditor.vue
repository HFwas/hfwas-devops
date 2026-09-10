<script setup lang="ts">
import { NButton, NSpace, useMessage } from 'naive-ui'
import { Copy, Download, Edit3, Eye, Save, X } from '@lucide/vue'

const props = defineProps<{
  content: string
  loading?: boolean
  editable?: boolean
  maxHeight?: string
}>()

const emit = defineEmits<{
  save: [yaml: string]
}>()

const message = useMessage()
const editing = ref(false)
const editContent = ref('')

watch(() => props.content, (val) => {
  if (!editing.value) editContent.value = val
})

function startEdit() {
  editContent.value = props.content
  editing.value = true
}

function cancelEdit() {
  editing.value = false
  editContent.value = ''
}

function saveEdit() {
  emit('save', editContent.value)
  editing.value = false
}

function copyContent() {
  navigator.clipboard.writeText(editing.value ? editContent.value : props.content).catch(() => {})
}

function downloadContent() {
  const blob = new Blob([props.content], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = 'resource.yaml'
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="yaml-editor">
    <div class="yaml-toolbar">
      <NSpace>
        <n-button size="tiny" quaternary @click="copyContent">
          <template #icon><Copy :size="14" /></template>
          复制
        </n-button>
        <n-button size="tiny" quaternary @click="downloadContent">
          <template #icon><Download :size="14" /></template>
          下载
        </n-button>
        <n-button
          v-if="editable && !editing"
          size="tiny"
          type="primary"
          quaternary
          @click="startEdit"
        >
          <template #icon><Edit3 :size="14" /></template>
          编辑
        </n-button>
        <template v-if="editing">
          <n-button size="tiny" type="primary" @click="saveEdit">
            <template #icon><Save :size="14" /></template>
            保存
          </n-button>
          <n-button size="tiny" quaternary @click="cancelEdit">
            <template #icon><X :size="14" /></template>
            取消
          </n-button>
        </template>
      </NSpace>
      <span v-if="loading" class="yaml-loading">加载中...</span>
    </div>
    <textarea
      v-if="editing"
      v-model="editContent"
      class="yaml-textarea"
      spellcheck="false"
      wrap="off"
    />
    <pre
      v-else
      class="yaml-content"
      :style="{ maxHeight: maxHeight || '500px' }"
    >{{ content || '暂无内容' }}</pre>
  </div>
</template>

<style scoped>
.yaml-editor {
  border: 1px solid var(--wb-border);
  border-radius: 6px;
  overflow: hidden;
}

.yaml-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 4px 8px;
  background: #f5f5f5;
  border-bottom: 1px solid var(--wb-border);
}

.yaml-loading {
  font-size: 12px;
  color: var(--wb-muted);
}

.yaml-content {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  font-size: 12px;
  line-height: 1.5;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}

.yaml-textarea {
  display: block;
  width: 100%;
  min-height: 400px;
  padding: 12px;
  border: none;
  border-radius: 0;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'SF Mono', 'Monaco', 'Menlo', 'Consolas', monospace;
  font-size: 12px;
  line-height: 1.5;
  tab-size: 2;
  resize: vertical;
  outline: none;
}
</style>