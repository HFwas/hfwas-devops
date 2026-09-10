<script setup lang="ts">
import { NButton, NSelect, NSpace, NTag, NSwitch, useMessage } from 'naive-ui'
import type { SelectOption } from 'naive-ui'
import { getToken } from '@/shared/keycloak'
import { onBeforeUnmount, ref, watch, nextTick, computed } from 'vue'

const props = defineProps<{
  clusterId: string
  namespace: string
  podName: string
  containers: { name: string; state: string }[]
}>()

const message = useMessage()

type StreamState = 'disconnected' | 'connecting' | 'connected' | 'error'
const state = ref<StreamState>('disconnected')
const errorMsg = ref('')
const logLines = ref<string[]>([])
const selectedContainer = ref('')
const autoFollow = ref(true)
const lineCount = ref(0)

let ws: WebSocket | null = null
let reconnectTimer: ReturnType<typeof setTimeout> | null = null

const logContainerRef = ref<HTMLDivElement | null>(null)
let isAtBottom = true
let userScrolledUp = false

const MAX_LINES = 5000

const containerOptions = computed<SelectOption[]>(() => {
  if (!props.containers.length) return []
  return [
    { label: '(所有容器)', value: '' },
    ...props.containers.map(c => ({ label: `${c.name} (${c.state})`, value: c.name })),
  ]
})

// Auto-select first container
watch(containerOptions, (opts) => {
  if (opts.length > 1 && !selectedContainer.value) {
    selectedContainer.value = props.containers.find(c => c.state === 'running')?.name || props.containers[0].name
    connect()
  }
}, { immediate: true })

const statusLabel = computed(() => {
  switch (state.value) {
    case 'connected': return `已连接 · ${lineCount.value} 行`
    case 'connecting': return '连接中...'
    case 'error': return errorMsg.value || '连接失败'
    default: return '未连接'
  }
})

const statusType = computed(() => {
  switch (state.value) {
    case 'connected': return 'success' as const
    case 'connecting': return 'warning' as const
    case 'error': return 'error' as const
    default: return 'default' as const
  }
})

function connect() {
  disconnect()
  state.value = 'connecting'
  errorMsg.value = ''
  logLines.value = []
  lineCount.value = 0

  connectWs()
}

async function connectWs() {
  try {
    const token = await getToken()
    if (!token) {
      errorMsg.value = '登录已过期'
      state.value = 'error'
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const params = new URLSearchParams({
      namespace: props.namespace,
      pod: props.podName,
    })
    if (selectedContainer.value) {
      params.set('container', selectedContainer.value)
    }
    const wsUrl = `${protocol}//${window.location.host}/api/ws/container/logs/${props.clusterId}?${params.toString()}`

    ws = new WebSocket(wsUrl, [token, 'container-logs'])

    ws.onopen = () => {
      state.value = 'connected'
      isAtBottom = true
      userScrolledUp = false
    }

    ws.onmessage = (event) => {
      if (event.data instanceof Blob) {
        event.data.arrayBuffer().then((buf) => {
          const decoder = new TextDecoder()
          const text = decoder.decode(buf)
          appendLog(text)
        })
      } else {
        appendLog(event.data)
      }
    }

    ws.onclose = () => {
      if (state.value === 'connected') {
        state.value = 'disconnected'
        // Auto-reconnect
        scheduleReconnect()
      } else if (state.value === 'connecting') {
        state.value = 'error'
        errorMsg.value = '连接失败'
      }
    }

    ws.onerror = () => {
      if (state.value === 'connecting') {
        state.value = 'error'
        errorMsg.value = 'WebSocket 连接失败'
      }
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '连接失败'
    state.value = 'error'
  }
}

function appendLog(text: string) {
  // Split by newlines and add each line
  const lines = text.split('\n')
  // If there's content after the last newline, keep it as a partial line
  // Actually, since we receive stream data, lines may be split across chunks
  // We handle this by checking if the last character is a newline
  if (lines.length === 0) return

  // The last element might be empty if text ends with \n, that's fine
  for (let i = 0; i < lines.length; i++) {
    // Skip empty lines at the end (caused by trailing newline)
    if (i === lines.length - 1 && lines[i] === '' && text.endsWith('\n')) continue

    logLines.value.push(lines[i])

    // Trim to max lines
    if (logLines.value.length > MAX_LINES) {
      logLines.value.splice(0, logLines.value.length - MAX_LINES)
    }
  }

  lineCount.value = logLines.value.length

  // Auto-follow: scroll to bottom if user hasn't scrolled up
  if (autoFollow.value && !userScrolledUp) {
    nextTick(scrollToBottom)
  }
}

function scrollToBottom() {
  if (logContainerRef.value) {
    logContainerRef.value.scrollTop = logContainerRef.value.scrollHeight
  }
}

function onScroll() {
  if (!logContainerRef.value) return
  const el = logContainerRef.value
  const threshold = 50 // px from bottom
  isAtBottom = el.scrollHeight - el.scrollTop - el.clientHeight < threshold
  userScrolledUp = !isAtBottom
}

function disconnect() {
  if (reconnectTimer !== null) {
    clearTimeout(reconnectTimer)
    reconnectTimer = null
  }
  if (ws) {
    ws.onclose = null
    ws.onerror = null
    ws.onmessage = null
    if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
      ws.close(1000, '用户断开')
    }
    ws = null
  }
  state.value = 'disconnected'
}

function scheduleReconnect() {
  if (reconnectTimer !== null) clearTimeout(reconnectTimer)
  reconnectTimer = setTimeout(() => {
    if (state.value === 'disconnected') {
      connectWs()
    }
  }, 3000)
}

function onContainerChange(container: string | null) {
  selectedContainer.value = container ?? ''
  connect()
}

onBeforeUnmount(() => {
  disconnect()
})
</script>

<template>
  <div class="pod-log-stream">
    <!-- Toolbar -->
    <div class="log-toolbar">
      <n-select
        :value="selectedContainer"
        :options="containerOptions"
        placeholder="选择容器"
        style="width: 200px"
        size="small"
        clearable
        @update:value="onContainerChange"
      />
      <n-button v-if="state === 'connected'" size="tiny" quaternary @click="disconnect">断开</n-button>
      <n-button v-else size="tiny" quaternary @click="connect">连接</n-button>
      <div class="log-follow-toggle">
        <n-switch :value="autoFollow" size="small" @update:value="(v: boolean) => { autoFollow = v; if (v) { userScrolledUp = false; nextTick(scrollToBottom) } }" />
        <span class="log-follow-label">自动跟随</span>
      </div>
      <n-tag :type="statusType" size="tiny" class="log-status-tag">{{ statusLabel }}</n-tag>
    </div>

    <!-- Log display -->
    <div ref="logContainerRef" class="log-container" @scroll="onScroll">
      <div v-if="logLines.length === 0 && state === 'connected'" class="log-empty">等待日志输出...</div>
      <div v-else-if="logLines.length === 0 && state === 'disconnected'" class="log-empty">点击「连接」开始接收日志</div>
      <div v-else-if="logLines.length === 0 && state === 'error'" class="log-empty">{{ errorMsg }}</div>
      <div v-else class="log-lines">
        <div v-for="(line, idx) in logLines" :key="idx" class="log-line" :class="{ 'log-line-highlight': idx === logLines.length - 1 && state === 'connected' }">
          <span class="log-line-num">{{ idx + 1 }}</span>
          <span class="log-line-text">{{ line }}</span>
        </div>
      </div>
    </div>

    <!-- Scroll indicator -->
    <div v-if="userScrolledUp && state === 'connected'" class="log-scroll-hint" @click="scrollToBottom">
      ↓ 新日志 {{ autoFollow ? '· 点击返回底部' : '' }}
    </div>
  </div>
</template>

<style scoped>
.pod-log-stream {
  display: flex;
  flex-direction: column;
  gap: 6px;
  min-height: 400px;
}

.log-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.log-follow-toggle {
  display: flex;
  align-items: center;
  gap: 4px;
}

.log-follow-label {
  font-size: 12px;
  color: var(--wb-muted, #909399);
}

.log-status-tag {
  margin-left: auto;
}

.log-container {
  flex: 1;
  min-height: 350px;
  max-height: 600px;
  overflow: auto;
  background: #1d2129;
  border-radius: 6px;
  font-family: 'Menlo', 'Monaco', 'Courier New', monospace;
  font-size: 12px;
  line-height: 1.6;
  position: relative;
}

.log-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 350px;
  color: #6b7280;
  font-size: 13px;
}

.log-lines {
  padding: 8px 0;
}

.log-line {
  display: flex;
  gap: 0;
  padding: 0 12px;
  white-space: pre;
  min-height: 1.6em;
}

.log-line:hover {
  background: rgba(255, 255, 255, 0.03);
}

.log-line-highlight {
  background: rgba(255, 255, 255, 0.02);
}

.log-line-num {
  color: #4a5568;
  text-align: right;
  padding-right: 12px;
  min-width: 48px;
  flex-shrink: 0;
  user-select: none;
}

.log-line-text {
  color: #d4d4d4;
  white-space: pre-wrap;
  word-break: break-all;
  flex: 1;
}

.log-scroll-hint {
  position: relative;
  text-align: center;
  padding: 4px;
  font-size: 11px;
  color: var(--wb-primary, #409eff);
  background: var(--wb-card-bg, #fff);
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 4px;
  cursor: pointer;
  transition: background 0.15s;
}

.log-scroll-hint:hover {
  background: var(--wb-chip-bg, #f8fafc);
}
</style>