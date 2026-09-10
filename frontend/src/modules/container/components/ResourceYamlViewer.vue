<script setup lang="ts">
import { NButton, NSpace } from 'naive-ui'
import { Copy, Download } from '@lucide/vue'

const props = defineProps<{
  content: string
  language?: string
  maxHeight?: string
}>()

function copyContent() {
  navigator.clipboard.writeText(props.content).catch(() => {})
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
  <div class="yaml-viewer">
    <div class="yaml-toolbar">
      <n-space>
        <n-button size="tiny" quaternary @click="copyContent">
          <template #icon><Copy :size="14" /></template>
          复制
        </n-button>
        <n-button size="tiny" quaternary @click="downloadContent">
          <template #icon><Download :size="14" /></template>
          下载
        </n-button>
      </n-space>
    </div>
    <pre class="yaml-content" :style="{ maxHeight: maxHeight || '500px' }">{{ content }}</pre>
  </div>
</template>

<style scoped>
.yaml-viewer {
  border: 1px solid var(--wb-border);
  border-radius: 6px;
  overflow: hidden;
}
.yaml-toolbar {
  display: flex;
  justify-content: flex-end;
  padding: 4px 8px;
  background: #f5f5f5;
  border-bottom: 1px solid var(--wb-border);
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
</style>