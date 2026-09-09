<script setup lang="ts">
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { getToken } from '@/shared/keycloak'
import { useMessage } from 'naive-ui'

const props = withDefaults(
  defineProps<{
    show: boolean
    pipelineId: string
    runId: string
    jobId: string
    jobName: string
  }>(),
  { show: false },
)

const emit = defineEmits<{
  close: []
}>()

const message = useMessage()

// ---- 状态 ----
type TerminalState = 'loading' | 'selecting' | 'connecting' | 'connected' | 'disconnected' | 'error'
const state = ref<TerminalState>('loading')
const errorMsg = ref('')
const containers = ref<ContainerInfo[]>([])
const selectedContainer = ref('')
const connectingContainer = ref('')

// xterm
let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let ws: WebSocket | null = null
let pingTimer: number | null = null
let readerTimer: number | null = null

const terminalEl = ref<HTMLDivElement | null>(null)
const containerPickerEl = ref<HTMLDivElement | null>(null)

// ---- 类型 ----
interface ContainerInfo {
  name: string
  state: 'running' | 'terminated' | 'waiting' | 'unknown'
  exitCode?: number | null
  hasShell: boolean
  recommendedMode: 'exec' | 'ephemeral' | 'debug_pod' | 'unavailable'
  unavailableReason?: string | null
}

interface PodContainersVO {
  namespace: string
  podName: string
  podExists: 'true' | 'false' | 'unknown'
  workspaceKind: 'pvc' | 'emptydir' | 'unknown'
  workspacePath: string
  containers: ContainerInfo[]
  defaultContainer: string
}

// ---- 方法 ----
async function loadContainers() {
  state.value = 'loading'
  errorMsg.value = ''
  try {
    const vo = await pipelineApi.getContainers(props.pipelineId, props.runId, props.jobId)
    containers.value = vo.containers.filter(c => c.recommendedMode !== 'unavailable')
    if (containers.value.length === 0) {
      // 全部 unavailable
      const allUnavailable = vo.containers.length > 0
      if (allUnavailable) {
        errorMsg.value = vo.containers[0].unavailableReason || '当前容器无法进入终端'
        state.value = 'error'
      } else {
        errorMsg.value = '暂无可用容器'
        state.value = 'error'
      }
      return
    }
    selectedContainer.value = vo.defaultContainer || containers.value[0].name
    if (containers.value.length === 1) {
      // 只有一个容器，自动连接
      startConnect(selectedContainer.value)
    } else {
      state.value = 'selecting'
      // 下一帧聚焦选择器
      nextTick(() => {
        containerPickerEl.value?.querySelector<HTMLElement>('.pt-container-item.is-default')?.focus()
      })
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '获取容器列表失败'
    state.value = 'error'
  }
}

function startConnect(containerName: string) {
  selectedContainer.value = containerName
  connectingContainer.value = containerName
  state.value = 'connecting'
  connectWebSocket(containerName)
}

async function connectWebSocket(containerName: string) {
  try {
    const token = await getToken()
    if (!token) {
      errorMsg.value = '登录已过期，请重新登录'
      state.value = 'error'
      return
    }

    // 构建 WebSocket URL
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/api/ws/exec/${props.pipelineId}/${props.runId}/${props.jobId}?container=${encodeURIComponent(containerName)}`

    // JWT 通过 Sec-WebSocket-Protocol 传递
    ws = new WebSocket(wsUrl, [token, 'pipeline-exec'])

    ws.onopen = () => {
      state.value = 'connected'
      initTerminal()
      startPing()
    }

    ws.onmessage = (event) => {
      if (event.data instanceof Blob) {
        // 二进制帧：PTY 输出
        event.data.arrayBuffer().then((buf) => {
          const decoder = new TextDecoder()
          const text = decoder.decode(buf)
          terminal?.write(text)
        })
      } else {
        // JSON 文本帧：控制消息
        try {
          const msg = JSON.parse(event.data)
          switch (msg.type) {
            case 'connected':
              break
            case 'ping':
              ws?.send(JSON.stringify({ type: 'pong' }))
              break
            case 'error':
              errorMsg.value = msg.message || '连接错误'
              disconnect()
              break
            case 'progress':
              terminal?.writeln(`\x1b[33m${msg.message}\x1b[0m`)
              break
          }
        } catch {
          // 非 JSON 文本直接输出
          terminal?.write(event.data)
        }
      }
    }

    ws.onclose = (event) => {
      stopPing()
      if (state.value === 'connected' || state.value === 'connecting') {
        if (event.code !== 1000 && event.reason) {
          errorMsg.value = `连接断开: ${event.reason}`
        } else {
          errorMsg.value = '连接已断开'
        }
        state.value = 'disconnected'
      }
    }

    ws.onerror = () => {
      if (state.value === 'connecting') {
        errorMsg.value = 'WebSocket 连接失败'
        state.value = 'error'
      }
    }
  } catch (e: any) {
    errorMsg.value = e?.message || '连接失败'
    state.value = 'error'
  }
}

function initTerminal() {
  if (!terminalEl.value) return

  terminal = new Terminal({
    cursorBlink: true,
    cursorStyle: 'block',
    fontSize: 13,
    fontFamily: 'Menlo, Monaco, "Courier New", monospace',
    theme: {
      background: '#1d2129',
      foreground: '#e5e7eb',
      selectionBackground: '#4a5568',
      cursor: '#e5e7eb',
      black: '#1d2129',
      red: '#f56c6c',
      green: '#67c23a',
      yellow: '#e6a23c',
      blue: '#409eff',
      magenta: '#b37feb',
      cyan: '#4fc3f7',
      white: '#e5e7eb',
    },
    allowTransparency: false,
    rows: 30,
    cols: 80,
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)

  terminal.open(terminalEl.value)

  // 延迟适配
  setTimeout(() => fitAddon?.fit(), 100)

  // 监听窗口 resize
  window.addEventListener('resize', onResize)

  // 用户输入 → WebSocket
  terminal.onData((data: string) => {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'input', data }))
    }
  })

  // resize 消息
  terminal.onResize(({ cols, rows }) => {
    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'resize', cols, rows }))
    }
  })

  terminal.focus()
}

function onResize() {
  try {
    fitAddon?.fit()
    if (terminal && fitAddon) {
      const dims = fitAddon.proposeDimensions()
      if (dims && ws?.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({ type: 'resize', cols: dims.cols, rows: dims.rows }))
      }
    }
  } catch {
    // ignore resize errors
  }
}

function startPing() {
  stopPing()
  // 应用级 ping 由服务端发起，客户端只需响应
  // 这里只设置一个兜底的心跳检测
  pingTimer = window.setInterval(() => {
    if (ws?.readyState !== WebSocket.OPEN) {
      stopPing()
    }
  }, 30000)
}

function stopPing() {
  if (pingTimer != null) {
    clearInterval(pingTimer)
    pingTimer = null
  }
}

function disconnect() {
  stopPing()
  if (ws) {
    ws.onclose = null
    ws.onerror = null
    ws.onmessage = null
    if (ws.readyState === WebSocket.OPEN || ws.readyState === WebSocket.CONNECTING) {
      ws.close(1000, '用户断开')
    }
    ws = null
  }
  destroyTerminal()
}

function destroyTerminal() {
  window.removeEventListener('resize', onResize)
  if (terminal) {
    terminal.dispose()
    terminal = null
  }
  fitAddon = null
}

function retry() {
  disconnect()
  loadContainers()
}

function handleClose() {
  disconnect()
  emit('close')
}

// 监听 show 变化
watch(
  () => props.show,
  (val) => {
    if (val) {
      loadContainers()
    } else {
      disconnect()
      state.value = 'loading'
      errorMsg.value = ''
    }
  },
)

onBeforeUnmount(() => {
  disconnect()
})

function stateLabel(stateVal: string, containerName: string): string {
  switch (stateVal) {
    case 'loading':
      return '获取 Pod 信息…'
    case 'selecting':
      return '选择容器'
    case 'connecting':
      return `正在连接 ${containerName}…`
    case 'connected':
      return `已连接 ${containerName}`
    case 'disconnected':
      return '连接已断开'
    case 'error':
      return '连接失败'
    default:
      return ''
  }
}

function modeLabel(mode: string): string {
  switch (mode) {
    case 'exec':
      return '直连'
    case 'ephemeral':
      return '注入调试'
    case 'debug_pod':
      return '调试 Pod'
    default:
      return mode
  }
}

function stateColor(stateVal: string): string {
  switch (stateVal) {
    case 'running':
      return 'var(--wb-success, #67c23a)'
    case 'terminated':
      return 'var(--wb-warning, #e6a23c)'
    case 'waiting':
      return 'var(--wb-primary, #409eff)'
    default:
      return 'var(--wb-muted, #909399)'
  }
}
</script>

<template>
  <n-drawer :show="show" :width="780" placement="right" @update:show="(val: boolean) => !val && handleClose()">
    <n-drawer-content closable :native-scrollbar="false" @close="handleClose">
      <template #header>
        <div class="pt-drawer-head">
          <strong>{{ jobName }} 终端</strong>
          <n-tag v-if="state !== 'loading' && state !== 'selecting'" size="tiny" :bordered="false" :type="state === 'connected' ? 'success' : 'warning'">
            {{ stateLabel(state, selectedContainer) }}
          </n-tag>
        </div>
      </template>

      <!-- 加载中 -->
      <div v-if="state === 'loading'" class="pt-center">
        <n-spin size="medium" />
        <p class="pt-hint">正在获取容器信息…</p>
      </div>

      <!-- 选择容器 -->
      <div v-else-if="state === 'selecting'" ref="containerPickerEl" class="pt-select">
        <p class="pt-select-title">选择要进入的容器</p>
        <div class="pt-container-list">
          <button
            v-for="c in containers"
            :key="c.name"
            type="button"
            class="pt-container-item"
            :class="{ 'is-default': c.name === selectedContainer }"
            @click="selectedContainer = c.name"
            @dblclick="startConnect(c.name)"
          >
            <div class="pt-container-head">
              <span class="pt-container-name">{{ c.name }}</span>
              <span class="pt-container-state" :style="{ color: stateColor(c.state) }">{{ c.state }}</span>
            </div>
            <div class="pt-container-meta">
              <span class="pt-container-mode">模式: {{ modeLabel(c.recommendedMode) }}</span>
              <span v-if="c.hasShell !== undefined" class="pt-container-shell">{{ c.hasShell ? '有 Shell' : '无 Shell' }}</span>
            </div>
          </button>
        </div>
        <n-button type="primary" :disabled="!selectedContainer" @click="startConnect(selectedContainer)">
          连接
        </n-button>
      </div>

      <!-- 连接中 -->
      <div v-else-if="state === 'connecting'" class="pt-center">
        <n-spin size="medium" />
        <p class="pt-hint">正在连接 {{ connectingContainer }}…</p>
      </div>

      <!-- 已连接 -->
      <div v-else-if="state === 'connected'" class="pt-term-wrap">
        <div ref="terminalEl" class="pt-term" />
        <div class="pt-toolbar">
          <n-button size="tiny" @click="disconnect">断开</n-button>
          <n-button size="tiny" @click="retry">重连</n-button>
        </div>
      </div>

      <!-- 已断开 -->
      <div v-else-if="state === 'disconnected'" class="pt-center">
        <n-result status="info" title="连接已断开" :description="errorMsg || '连接已断开'">
          <template #footer>
            <n-button @click="retry">重新连接</n-button>
          </template>
        </n-result>
      </div>

      <!-- 错误 -->
      <div v-else class="pt-center">
        <n-result status="error" title="连接失败" :description="errorMsg">
          <template #footer>
            <n-button @click="retry">重试</n-button>
          </template>
        </n-result>
      </div>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.pt-drawer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 14px;
}

.pt-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  gap: 12px;
}

.pt-hint {
  margin: 0;
  font-size: 13px;
  color: var(--wb-muted, #909399);
}

/* ---- Container Picker ---- */
.pt-select {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 24px;
}

.pt-select-title {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
}

.pt-container-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.pt-container-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding: 12px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  background: var(--wb-card-bg, #fff);
  cursor: pointer;
  transition: box-shadow 0.15s, border-color 0.15s;
  text-align: left;
  font: inherit;
}

.pt-container-item:hover {
  border-color: var(--wb-primary, #409eff);
  box-shadow: 0 1px 6px rgba(64, 158, 255, 0.12);
}

.pt-container-item.is-default {
  border-color: var(--wb-primary, #409eff);
  background: color-mix(in srgb, var(--wb-primary) 6%, transparent);
}

.pt-container-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.pt-container-name {
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
}

.pt-container-state {
  font-size: 12px;
  font-weight: 500;
}

.pt-container-meta {
  display: flex;
  gap: 12px;
  font-size: 11px;
  color: var(--wb-muted, #909399);
}

.pt-container-shell {
  color: var(--wb-primary, #409eff);
}

/* ---- Terminal ---- */
.pt-term-wrap {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
}

.pt-term {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 8px;
}

.pt-toolbar {
  display: flex;
  gap: 8px;
  padding: 8px 0 0;
  flex-shrink: 0;
}

/* xterm 样式覆盖 */
.pt-term :deep(.xterm) {
  height: 100%;
  padding: 8px;
}

.pt-term :deep(.xterm-viewport) {
  scrollbar-width: thin;
  scrollbar-color: #4a5568 transparent;
}
</style>