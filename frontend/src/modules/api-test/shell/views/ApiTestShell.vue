<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import ModuleRail from '@/modules/api-test/shell/components/ModuleRail.vue'
import ResourcePanel from '@/modules/api-test/shell/components/ResourcePanel.vue'
import RequestTabBar from '@/modules/api-test/shell/components/RequestTabBar.vue'
import RequestWorkspace from '@/modules/api-test/shell/components/RequestWorkspace.vue'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { ShellModule } from '@/modules/api-test/shell/types/workspace'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'

const SHELL_MODULES: readonly ShellModule[] = [
  'apis',
  'collections',
  'environments',
  'docs',
  'specs',
  'mocks',
]

const SIDEBAR_MIN = 180
const SIDEBAR_MAX = 480

const route = useRoute()
const message = useMessage()
const workspace = useWorkspaceStore()
const { sidebarWidth, tabs } = storeToRefs(workspace)

const treeLoaded = ref(false)
let openedDefKey: string | null = null

function isShellModule(value: unknown): value is ShellModule {
  return typeof value === 'string' && (SHELL_MODULES as readonly string[]).includes(value)
}

function applyQueryModule() {
  const raw = route.query.module
  const value = Array.isArray(raw) ? raw[0] : raw
  if (isShellModule(value)) {
    workspace.setModule(value)
  }
}

function parseDefQuery(): number | null {
  const raw = route.query.def
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value === '') return null
  const id = Number(value)
  if (!Number.isInteger(id) || id <= 0) return null
  return id
}

async function openDefFromQuery() {
  if (!treeLoaded.value) return
  const id = parseDefQuery()
  if (id == null) return
  const key = String(id)
  if (openedDefKey === key) return
  openedDefKey = key
  try {
    const { detail, draft } = await loadDefinitionIntoTab(id)
    workspace.openOrFocusTab({
      source: 'definition',
      refId: id,
      definitionId: id,
      title: detail.name,
      method: detail.method,
      draft,
    })
  } catch (e: any) {
    openedDefKey = null
    message.error(e?.message || '加载接口失败')
  }
}

function onTreeLoaded() {
  treeLoaded.value = true
  void openDefFromQuery()
}

onMounted(applyQueryModule)
watch(() => route.query, applyQueryModule, { deep: true })
watch(() => route.query.def, () => {
  void openDefFromQuery()
})

let stopSidebarResize: (() => void) | null = null

function onSidebarResizeStart(event: MouseEvent) {
  event.preventDefault()
  stopSidebarResize?.()

  const startX = event.clientX
  const startWidth = sidebarWidth.value

  function onMove(moveEvent: MouseEvent) {
    const next = Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, startWidth + moveEvent.clientX - startX))
    workspace.setLayout({ sidebarWidth: next })
  }

  function onUp() {
    window.removeEventListener('mousemove', onMove)
    window.removeEventListener('mouseup', onUp)
    document.body.style.removeProperty('user-select')
    document.body.style.removeProperty('cursor')
    stopSidebarResize = null
  }

  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  window.addEventListener('mousemove', onMove)
  window.addEventListener('mouseup', onUp)
  stopSidebarResize = onUp
}

onUnmounted(() => stopSidebarResize?.())
</script>

<template>
  <div class="api-test-shell">
    <ModuleRail />

    <aside class="api-test-shell__sidebar" :style="{ width: `${sidebarWidth}px` }">
      <ResourcePanel @loaded="onTreeLoaded" />
    </aside>
    <div
      class="api-test-shell__resizer"
      title="拖拽调整侧栏宽度"
      @mousedown="onSidebarResizeStart"
    />

    <div class="api-test-shell__main">
      <RequestTabBar />
      <div class="api-test-shell__workspace">
        <RequestWorkspace v-if="tabs.length" />
        <n-empty v-else description="从左侧打开接口" />
      </div>
    </div>
  </div>
</template>

<style scoped>
.api-test-shell {
  display: flex;
  height: calc(100vh - 56px);
  overflow: hidden;
  background: var(--wb-card-bg, #fff);
}

.api-test-shell__sidebar {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  background: var(--wb-card-bg, #fff);
}

.api-test-shell__resizer {
  flex-shrink: 0;
  width: 4px;
  height: 100%;
  background: var(--wb-border, #e5e7eb);
  cursor: col-resize;
}

.api-test-shell__resizer:hover {
  background: #4098fc;
}

.api-test-shell__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
}

.api-test-shell__workspace {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: hidden;
}

.api-test-shell__workspace > .request-workspace {
  align-self: stretch;
  width: 100%;
}
</style>
