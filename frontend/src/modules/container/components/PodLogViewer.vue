<script setup lang="ts">
import { NButton, NSelect, NSpace, NSwitch } from 'naive-ui'
import { Pause, Play, Download, Trash2 } from '@lucide/vue'

const props = defineProps<{
  containers: string[]
  activeContainer: string
  logContent: string
  autoScroll: boolean
  tailing: boolean
}>()

const emit = defineEmits<{
  'update:active-container': [value: string]
  'update:auto-scroll': [value: boolean]
  toggleTail: []
  clear: []
  download: []
}>()

const containerOptions = computed(() =>
  props.containers.map((c) => ({ label: c, value: c }))
)

function downloadLog() {
  const blob = new Blob([props.logContent], { type: 'text/plain' })
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = `${props.activeContainer || 'pod'}-logs.txt`
  a.click()
  URL.revokeObjectURL(url)
}
</script>

<template>
  <div class="log-viewer">
    <div class="log-toolbar">
      <n-space align="center" size="small">
        <n-select
          :value="activeContainer"
          :options="containerOptions"
          size="tiny"
          style="width: 160px"
          @update:value="emit('update:active-container', $event)"
        />
        <span class="label">自动滚动</span>
        <n-switch :value="autoScroll" size="small" @update:value="emit('update:auto-scroll', $event)" />
        <n-button size="tiny" quaternary @click="emit('toggleTail')">
          <template #icon>
            <component :is="tailing ? Pause : Play" :size="14" />
          </template>
          {{ tailing ? '暂停' : '继续' }}
        </n-button>
        <n-button size="tiny" quaternary @click="emit('clear')">
          <template #icon><Trash2 :size="14" /></template>
          清空
        </n-button>
        <n-button size="tiny" quaternary @click="downloadLog">
          <template #icon><Download :size="14" /></template>
          下载
        </n-button>
      </n-space>
    </div>
    <pre ref="logPreRef" class="log-content">{{ logContent || '(暂无日志)' }}</pre>
  </div>
</template>

<script lang="ts">
export default {
  inheritAttrs: false,
}
</script>

<style scoped>
.log-viewer {
  border: 1px solid var(--wb-border);
  border-radius: 6px;
  overflow: hidden;
}
.log-toolbar {
  display: flex;
  align-items: center;
  padding: 4px 8px;
  background: #f5f5f5;
  border-bottom: 1px solid var(--wb-border);
}
.label {
  font-size: 12px;
  color: #666;
}
.log-content {
  background: #1e1e1e;
  color: #d4d4d4;
  padding: 12px;
  font-size: 12px;
  line-height: 1.5;
  overflow: auto;
  max-height: 500px;
  white-space: pre-wrap;
  word-break: break-all;
  margin: 0;
}
</style>