<script setup lang="ts">
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import '@xterm/xterm/css/xterm.css'
import { NButton, NInput, NModal, NSelect, NSpace, NTag, useMessage } from 'naive-ui'
import { getToken } from '@/shared/keycloak'
import { podApi } from '@/modules/container/api/pod'
import { onBeforeUnmount, ref, watch, nextTick, computed } from 'vue'

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

// File transfer state
const showUploadDialog = ref(false)
const showDownloadDialog = ref(false)
const uploading = ref(false)
const downloading = ref(false)
const uploadDestPath = ref('')
const downloadFilePath = ref('')
const selectedFile = ref<File | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)

// Current working directory tracking (detected from user's cd commands)
const currentDir = ref('/')
let inputBuffer = ''

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
    // Track input buffer for cd command detection
    if (data === '\r') {
      const line = inputBuffer.trim()
      if (line.startsWith('cd ')) {
        currentDir.value = resolvePath(currentDir.value, line.slice(3).trim())
      } else if (line === 'cd' || line === 'cd ~') {
        // cd with no args or ~ goes to home, keep as-is
      }
      inputBuffer = ''
    } else if (data === '\x7f') {
      inputBuffer = inputBuffer.slice(0, -1)
    } else if (data.startsWith('\x1b')) {
      // Escape sequence (arrow keys etc.) — reset buffer, user is navigating
      inputBuffer = ''
    } else if (data.length === 1 && data.charCodeAt(0) >= 0x20) {
      inputBuffer += data
    }

    if (ws?.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({ type: 'input', data }))
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

// ==================== Path Helpers ====================

/** Resolve a cd target relative to the current directory */
function resolvePath(current: string, target: string): string {
  if (target === '~' || target === '' || target === '-') {
    return current // home / prev — can't track reliably
  }
  if (target.startsWith('~/')) {
    return normalizePath('/' + target.slice(2)) // ~ as /
  }
  if (target.startsWith('/')) {
    return normalizePath(target)
  }
  return normalizePath(current + '/' + target)
}

/** Normalize a path: resolve . and .., remove double slashes, no trailing slash */
function normalizePath(path: string): string {
  const parts = path.split('/').filter(Boolean)
  const result: string[] = []
  for (const part of parts) {
    if (part === '.') continue
    if (part === '..') {
      if (result.length > 0) result.pop()
      continue
    }
    result.push(part)
  }
  return '/' + result.join('/')
}

// ==================== File Transfer ====================

function openUploadDialog() {
  // Try to detect the current directory from the terminal prompt
  detectCurrentDirFromBuffer()
  // Default to tracked directory, falling back to /tmp/
  uploadDestPath.value = (currentDir.value.endsWith('/') ? currentDir.value : currentDir.value + '/') || '/tmp/'
  selectedFile.value = null
  showUploadDialog.value = true
}

/** Read the terminal buffer to detect the current directory from the shell prompt */
function detectCurrentDirFromBuffer() {
  if (!terminal) return
  const buffer = terminal.buffer.active
  const cursorY = buffer.cursorY
  // Search backwards from the cursor line for a prompt with a path
  for (let i = Math.min(cursorY, buffer.length - 1); i >= Math.max(0, cursorY - 3); i--) {
    const line = buffer.getLine(i)?.translateToString() || ''
    // Match patterns like hostname:/path$, user@host:/path#, /path $
    const m = line.match(/:(\/[^\s$#]*)[$#]/)
    if (m && m[1]) {
      currentDir.value = normalizePath(m[1])
      return
    }
    // Fallback: match standalone absolute path before $ or #
    const m2 = line.match(/(\/[^\s$#]+)[$#]/)
    if (m2 && m2[1]) {
      currentDir.value = normalizePath(m2[1])
      return
    }
  }
}

function onFileSelected(event: Event) {
  const input = event.target as HTMLInputElement
  if (input.files && input.files.length > 0) {
    selectedFile.value = input.files[0]
  }
}

function triggerFilePicker() {
  fileInputRef.value?.click()
}

async function handleUpload() {
  if (!selectedFile.value) {
    message.warning('请选择要上传的文件')
    return
  }
  if (!uploadDestPath.value.trim()) {
    message.warning('请输入目标路径')
    return
  }
  if (!selectedContainer.value) {
    message.warning('请先选择容器')
    return
  }

  uploading.value = true
  try {
    await podApi.uploadFile(
      props.clusterId,
      props.namespace,
      props.podName,
      selectedContainer.value,
      uploadDestPath.value.trim(),
      selectedFile.value,
    )
    message.success(`文件已上传到 ${uploadDestPath.value}${selectedFile.value.name}`)
    showUploadDialog.value = false
    selectedFile.value = null
  } catch (e: any) {
    message.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function openDownloadDialog() {
  downloadFilePath.value = ''
  showDownloadDialog.value = true
}

async function handleDownload() {
  if (!downloadFilePath.value.trim()) {
    message.warning('请输入容器内文件路径')
    return
  }
  if (!selectedContainer.value) {
    message.warning('请先选择容器')
    return
  }

  downloading.value = true
  try {
    const { blob, filename } = await podApi.downloadFile(
      props.clusterId,
      props.namespace,
      props.podName,
      selectedContainer.value,
      downloadFilePath.value.trim(),
    )

    // Trigger browser download
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url
    a.download = filename
    document.body.appendChild(a)
    a.click()
    document.body.removeChild(a)
    URL.revokeObjectURL(url)

    message.success(`文件已下载: ${filename}`)
    showDownloadDialog.value = false
  } catch (e: any) {
    message.error(e?.message || '下载失败')
  } finally {
    downloading.value = false
  }
}
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
          <n-button size="tiny" quaternary @click="openUploadDialog" :disabled="uploading">
            上传文件
          </n-button>
          <n-button size="tiny" quaternary @click="openDownloadDialog" :disabled="downloading">
            下载文件
          </n-button>
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

    <!-- Upload dialog -->
    <n-modal v-model:show="showUploadDialog" title="上传文件到容器" :mask-closable="false" preset="card" style="width: 480px">
      <n-space vertical>
        <div class="upload-file-row">
          <n-button @click="triggerFilePicker" :disabled="uploading">选择文件</n-button>
          <span class="upload-file-name">{{ selectedFile?.name || '未选择文件' }}</span>
        </div>
        <input ref="fileInputRef" type="file" style="display: none" @change="onFileSelected" />
        <n-input
          v-model:value="uploadDestPath"
          placeholder="目标目录，默认 /tmp/"
          :disabled="uploading"
        >
          <template #prefix>目标路径</template>
        </n-input>
        <n-space justify="end">
          <n-button @click="showUploadDialog = false" :disabled="uploading">取消</n-button>
          <n-button type="primary" @click="handleUpload" :loading="uploading" :disabled="!selectedFile">
            上传
          </n-button>
        </n-space>
      </n-space>
    </n-modal>

    <!-- Download dialog -->
    <n-modal v-model:show="showDownloadDialog" title="从容器下载文件" :mask-closable="false" preset="card" style="width: 480px">
      <n-space vertical>
        <n-input
          v-model:value="downloadFilePath"
          placeholder="请输入容器内文件路径，如 /tmp/myfile.log"
          :disabled="downloading"
        >
          <template #prefix>文件路径</template>
        </n-input>
        <n-space justify="end">
          <n-button @click="showDownloadDialog = false" :disabled="downloading">取消</n-button>
          <n-button type="primary" @click="handleDownload" :loading="downloading" :disabled="!downloadFilePath.trim()">
            下载
          </n-button>
        </n-space>
      </n-space>
    </n-modal>
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

.upload-file-row {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0;
}

.upload-file-name {
  font-size: 13px;
  color: var(--wb-muted, #909399);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>