<script setup lang="ts">
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import { NButton, NSelect, NSpace, NTag, useMessage } from 'naive-ui'
import { getToken } from '@/shared/keycloak'
import { onBeforeUnmount, ref, watch, nextTick } from 'vue'

const props = defineProps<{
  clusterId: string
  namespace: string
  podName: string
  containers: { name: string; state: string }[]
}>()

const message = useMessage()

type TermState = 'idle' | 'connecting' | 'connected' | 'disconnected' | 'error'
const state = ref<TermState>('idle')
const errorMsg = ref('')
const selectedContainer = ref('')

let terminal: Terminal | null = null
let fitAddon: FitAddon | null = null
let ws: WebSocket | null = null
let pingTimer: number | null = null

const terminalEl = ref<HTMLDivElement | null>(null)

const containerOptions = computed(() =>
  props.containers.map(c => ({ label: `${c.name} (${c.state})`, value: c.name }))
)

// Auto-select the first running container
watch(containerOptions, (opts) => {
  if (opts.length > 0 && !selectedContainer.value) {
    // Prefer a running container
    const running = props.containers.find(c => c.state === 'running')
    selectedContainer.value = running?.name || props.containers[0].name
  }
}, { immediate: true })

async function connect() {
  if (!selectedContainer.value) {
    message.warning('请选择一个容器')
    return
  }

  disconnect()
  state.value = 'connecting'
  errorMsg.value = ''

  try {
    const token = await getToken()
    if (!token) {
      errorMsg.value = '登录已过期，请重新登录'
      state.value = 'error'
      return
    }

    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const wsUrl = `${protocol}//${window.location.host}/api/ws/container/shell/${props.clusterId}/${props.namespace}/${props.podName}?container=${encodeURIComponent(selectedContainer.value)}`

    ws = new WebSocket(wsUrl, [token, 'container-shell'])

    ws.onopen = () => {
      state.value = 'connected'
      nextTick(() => {
        initTerminal()
        startPing()
      })
    }

    ws.onmessage = (event) => {
      if (event.data instanceof Blob) {
        event.data.arrayBuffer().then((buf) => {
          const decoder = new TextDecoder()
          const text = decoder.decode(buf)
          terminal?.write(text)
        })
      } else {
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
          }
        } catch {
          terminal?.write(event.data)
        }
      }
    }

    ws.onclose = () => {
      stopPing()
      if (state.value === 'connected' || state.value === 'connecting') {
        state.value = 'disconnected'
        errorMsg.value = '连接已断开'
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
    rows: 24,
    cols: 80,
  })

  fitAddon = new FitAddon()
  terminal.loadAddon(fitAddon)
  terminal.open(terminalEl.value)

  nextTick(() => fitAddon?.fit())

  window.addEventListener('resize', onResize)

  terminal.onData((data: string) => {
    console.log('[terminal] onData received:', JSON.stringify(data))
    if (ws?.readyState === WebSocket.OPEN) {
      const msg = JSON.stringify({ type: 'input', data })
      console.log('[terminal] sending to WS:', msg)
      ws.send(msg)
    } else {
      console.warn('[terminal] WS not open, state:', ws?.readyState)
    }
  })

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
    // ignore
  }
}

function startPing() {
  stopPing()
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

onBeforeUnmount(() => {
  disconnect()
})
</script>

<template>
  <div class="pod-shell">
    <!-- Container selector & connection bar -->
    <div v-if="state !== 'connected'" class="shell-toolbar">
      <n-space align="center">
        <n-select
          v-model:value="selectedContainer"
          :options="containerOptions"
          placeholder="选择容器"
          style="width: 280px"
          size="small"
        />
        <n-button size="small" type="primary" @click="connect" :disabled="!selectedContainer || state === 'connecting'">
          {{ state === 'connecting' ? '连接中...' : '进入控制台' }}
        </n-button>
      </n-space>
      <n-tag v-if="state === 'connecting'" type="warning" size="small">正在连接...</n-tag>
      <n-tag v-if="state === 'disconnected'" type="warning" size="small">已断开</n-tag>
      <n-tag v-if="state === 'error'" type="error" size="small">{{ errorMsg }}</n-tag>
    </div>

    <!-- Terminal -->
    <div v-if="state === 'connected'" class="shell-terminal-wrap">
      <div ref="terminalEl" class="shell-terminal" />
      <div class="shell-actions">
        <n-space>
          <n-button size="tiny" quaternary @click="disconnect">断开</n-button>
          <n-button size="tiny" quaternary @click="connect">重连</n-button>
        </n-space>
        <n-tag size="tiny" type="success">{{ selectedContainer }}</n-tag>
      </div>
    </div>

    <!-- Idle state -->
    <div v-if="state === 'idle'" class="shell-hint">
      选择容器后点击"进入控制台"以建立终端连接
    </div>
  </div>
</template>

<style scoped>
.pod-shell {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-height: 400px;
}

.shell-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

.shell-terminal-wrap {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 350px;
}

.shell-terminal {
  flex: 1;
  min-height: 0;
  overflow: hidden;
  border-radius: 6px;
}

.shell-terminal :deep(.xterm) {
  height: 100%;
  padding: 8px;
}

.shell-terminal :deep(.xterm-viewport) {
  scrollbar-width: thin;
  scrollbar-color: #4a5568 transparent;
}

.shell-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 0 0;
}

.shell-hint {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 1;
  min-height: 200px;
  color: var(--wb-muted, #909399);
  font-size: 13px;
}
</style>