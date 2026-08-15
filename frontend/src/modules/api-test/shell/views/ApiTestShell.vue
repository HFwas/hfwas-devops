<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import ModuleRail from '@/modules/api-test/shell/components/ModuleRail.vue'
import ResourcePanel from '@/modules/api-test/shell/components/ResourcePanel.vue'
import RequestTabBar from '@/modules/api-test/shell/components/RequestTabBar.vue'
import RequestWorkspace from '@/modules/api-test/shell/components/RequestWorkspace.vue'
import CollectionRunDrawer from '@/modules/api-test/shell/components/CollectionRunDrawer.vue'
import EnvironmentSelector from '@/modules/api-test/debug/components/EnvironmentSelector.vue'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { ShellModule } from '@/modules/api-test/shell/types/workspace'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'
import {
  clampSidebarWidth,
  persistLayout,
  readStoredLayout,
} from '@/modules/api-test/shell/utils/layoutPersist'

const SHELL_MODULES: readonly ShellModule[] = [
  'apis',
  'collections',
  'environments',
  'docs',
  'specs',
  'mocks',
]

const route = useRoute()
const message = useMessage()
const workspace = useWorkspaceStore()
const envStore = useEnvironmentStore()
const { sidebarWidth, responseHeight, tabs } = storeToRefs(workspace)
const { selectedEnvironmentId } = storeToRefs(envStore)

const treeLoaded = ref(false)
const runDrawerShow = ref(false)
const runCollectionId = ref<number | null>(null)
const runDrawerMode = ref<'run' | 'history'>('history')
const runNonce = ref(0)
let openedDefKey: string | null = null
let openedRunsKey: string | null = null

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

function parseCollectionIdQuery(): number | null {
  const raw = route.query.collectionId
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value === '') return null
  const id = Number(value)
  if (!Number.isInteger(id) || id <= 0) return null
  return id
}

function hasRunsQuery(): boolean {
  const raw = route.query.runs
  const value = Array.isArray(raw) ? raw[0] : raw
  return value === '1'
}

function applyCollectionQuery() {
  const id = parseCollectionIdQuery()
  if (id == null || !hasRunsQuery()) return
  const key = String(id)
  if (openedRunsKey === key) return
  openedRunsKey = key
  runCollectionId.value = id
  runDrawerMode.value = 'history'
  runDrawerShow.value = true
}

function onCollectionRun(id: number) {
  runCollectionId.value = id
  runDrawerMode.value = 'run'
  runDrawerShow.value = true
  runNonce.value += 1
}

function onCollectionHistory(id: number) {
  runCollectionId.value = id
  runDrawerMode.value = 'history'
  runDrawerShow.value = true
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

function onEnvironmentChange(id: number | null) {
  envStore.selectEnvironment(id)
}

onMounted(() => {
  const stored = readStoredLayout()
  if (stored.sidebarWidth != null || stored.responseHeight != null) {
    workspace.setLayout(stored)
  }
  applyQueryModule()
  applyCollectionQuery()
  void envStore.loadAll(1)
})
watch(() => route.query, () => {
  applyQueryModule()
  applyCollectionQuery()
}, { deep: true })
watch(() => route.query.def, () => {
  void openDefFromQuery()
})
watch([sidebarWidth, responseHeight], ([width, height]) => {
  persistLayout({ sidebarWidth: width, responseHeight: height })
})

let stopSidebarResize: (() => void) | null = null

function onSidebarResizeStart(event: PointerEvent) {
  if (event.button !== 0) return
  event.preventDefault()
  stopSidebarResize?.()

  const startX = event.clientX
  const startWidth = sidebarWidth.value

  function onMove(moveEvent: PointerEvent) {
    workspace.setLayout({
      sidebarWidth: clampSidebarWidth(startWidth + moveEvent.clientX - startX),
    })
  }

  function onUp() {
    window.removeEventListener('pointermove', onMove)
    window.removeEventListener('pointerup', onUp)
    document.body.style.removeProperty('user-select')
    document.body.style.removeProperty('cursor')
    stopSidebarResize = null
  }

  document.body.style.userSelect = 'none'
  document.body.style.cursor = 'col-resize'
  window.addEventListener('pointermove', onMove)
  window.addEventListener('pointerup', onUp)
  stopSidebarResize = onUp
}

onUnmounted(() => stopSidebarResize?.())
</script>

<template>
  <div class="api-test-shell">
    <ModuleRail />

    <aside class="api-test-shell__sidebar" :style="{ width: `${sidebarWidth}px` }">
      <ResourcePanel
        @loaded="onTreeLoaded"
        @run="onCollectionRun"
        @history="onCollectionHistory"
      />
    </aside>
    <div
      class="api-test-shell__resizer"
      data-testid="sidebar-resizer"
      title="拖拽调整侧栏宽度"
      @pointerdown="onSidebarResizeStart"
    />

    <div class="api-test-shell__main">
      <div class="api-test-shell__header" data-testid="shell-env-header">
        <EnvironmentSelector
          :project-id="1"
          :environment-id="selectedEnvironmentId"
          @update:environment-id="onEnvironmentChange"
        />
      </div>
      <RequestTabBar />
      <div class="api-test-shell__workspace">
        <RequestWorkspace v-if="tabs.length" />
        <n-empty v-else description="从左侧打开接口" />
      </div>
    </div>

    <CollectionRunDrawer
      v-model:show="runDrawerShow"
      :collection-id="runCollectionId"
      :mode="runDrawerMode"
      :run-nonce="runNonce"
    />
  </div>
</template>

<style scoped>
.api-test-shell {
  --api-test-accent: #4098fc;
  --api-test-accent-strong: #2d80e6;
  --api-test-accent-soft: rgba(64, 152, 252, 0.12);
  display: flex;
  height: calc(100vh - 56px);
  overflow: hidden;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

html.dark .api-test-shell {
  --api-test-accent: #5eb0ff;
  --api-test-accent-strong: #82c4ff;
  --api-test-accent-soft: rgba(94, 176, 255, 0.2);
}

.api-test-shell__sidebar {
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.api-test-shell__resizer {
  flex-shrink: 0;
  width: 4px;
  height: 100%;
  background: var(--wb-border, #e5e7eb);
  cursor: col-resize;
  touch-action: none;
}

.api-test-shell__resizer:hover {
  background: var(--api-test-accent, #4098fc);
}

.api-test-shell__main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
  height: 100%;
  color: inherit;
  background: var(--wb-page-bg, #f5f7fb);
}

.api-test-shell__header {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: flex-end;
  padding: 8px 12px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.api-test-shell__workspace {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: hidden;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.api-test-shell__workspace > .request-workspace {
  align-self: stretch;
  width: 100%;
}
</style>
