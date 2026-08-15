<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import CollectionsSidebar from '@/modules/api-test/shell/components/CollectionsSidebar.vue'
import CollectionOverviewTab from '@/modules/api-test/shell/components/CollectionOverviewTab.vue'
import RequestTabBar from '@/modules/api-test/shell/components/RequestTabBar.vue'
import RequestWorkspace from '@/modules/api-test/shell/components/RequestWorkspace.vue'
import CollectionRunDrawer from '@/modules/api-test/shell/components/CollectionRunDrawer.vue'
import EnvironmentEditDrawer from '@/modules/api-test/shell/components/EnvironmentEditDrawer.vue'
import EnvironmentSelector from '@/modules/api-test/debug/components/EnvironmentSelector.vue'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'
import {
  clampSidebarWidth,
  persistLayout,
  readStoredLayout,
} from '@/modules/api-test/shell/utils/layoutPersist'

const PROJECT_ID = 1

const route = useRoute()
const message = useMessage()
const workspace = useWorkspaceStore()
const collectionStore = useCollectionStore()
const envStore = useEnvironmentStore()
const { sidebarWidth, responseHeight, activeTab } = storeToRefs(workspace)
const { selectedEnvironmentId } = storeToRefs(envStore)

const runDrawerShow = ref(false)
const runCollectionId = ref<number | null>(null)
const runDrawerMode = ref<'run' | 'history'>('history')
const runNonce = ref(0)
const envDrawerShow = ref(false)
const envDrawerId = ref<number | null>(null)
let openedDefKey: string | null = null
let openedOverviewKey: string | null = null
let openedRunsKey: string | null = null
let openedEnvEditKey: string | null = null

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

/** Deep-link: ?envEdit=<id> opens EnvironmentEditDrawer after env list load */
function parseEnvEditQuery(): number | null {
  const raw = route.query.envEdit
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value === '') return null
  const id = Number(value)
  if (!Number.isInteger(id) || id <= 0) return null
  return id
}

function applyEnvEditQuery() {
  const id = parseEnvEditQuery()
  if (id == null) {
    openedEnvEditKey = null
    return
  }
  const key = String(id)
  if (openedEnvEditKey === key) return
  openedEnvEditKey = key
  envDrawerId.value = id
  envDrawerShow.value = true
}

async function resolveCollectionTitle(id: number): Promise<string> {
  const fromPage = collectionStore.pageResult.records?.find((c) => c.id === id)?.name
  if (fromPage) return fromPage
  if (collectionStore.currentDetail?.id === id && collectionStore.currentDetail.name) {
    return collectionStore.currentDetail.name
  }
  try {
    const detail = await collectionStore.loadDetail(id)
    return detail?.name || `Collection ${id}`
  } catch {
    return `Collection ${id}`
  }
}

async function openOverviewFromQuery(id: number) {
  const key = String(id)
  if (openedOverviewKey === key) return
  openedOverviewKey = key
  const title = await resolveCollectionTitle(id)
  workspace.openOrFocusCollectionOverview(id, title)
}

function applyCollectionQuery() {
  const id = parseCollectionIdQuery()
  if (id == null) {
    openedOverviewKey = null
    openedRunsKey = null
    return
  }

  void openOverviewFromQuery(id)

  if (!hasRunsQuery()) {
    openedRunsKey = null
    return
  }
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

function onEnvironmentChange(id: number | null) {
  envStore.selectEnvironment(id)
}

function onEnvEdit(id: number) {
  envDrawerId.value = id
  envDrawerShow.value = true
}

async function onEnvSaved() {
  envDrawerShow.value = false
  await envStore.loadAll(PROJECT_ID)
}

onMounted(() => {
  const stored = readStoredLayout()
  if (stored.sidebarWidth != null || stored.responseHeight != null) {
    workspace.setLayout(stored)
  }
  applyCollectionQuery()
  void (async () => {
    await envStore.loadAll(PROJECT_ID)
    applyEnvEditQuery()
  })()
  void openDefFromQuery()
})
// Deep-links: ?collectionId= opens overview; +runs=1 also opens run history; ?envEdit=<id> opens EnvironmentEditDrawer
watch(() => route.query, () => {
  applyCollectionQuery()
  applyEnvEditQuery()
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
  <div class="api-test-shell" data-density="compact">
    <aside class="api-test-shell__sidebar" :style="{ width: `${sidebarWidth}px` }">
      <CollectionsSidebar
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
          :project-id="PROJECT_ID"
          :environment-id="selectedEnvironmentId"
          @update:environment-id="onEnvironmentChange"
          @edit="onEnvEdit"
        />
      </div>
      <RequestTabBar />
      <div class="api-test-shell__workspace">
        <CollectionOverviewTab
          v-if="activeTab?.source === 'collectionOverview' && activeTab.refId"
          :collection-id="activeTab.refId"
          @run="onCollectionRun"
          @history="onCollectionHistory"
        />
        <RequestWorkspace
          v-else-if="activeTab && activeTab.source !== 'collectionOverview'"
        />
        <n-empty v-else description="选择集合或新建接口" />
      </div>
    </div>

    <CollectionRunDrawer
      v-model:show="runDrawerShow"
      :collection-id="runCollectionId"
      :mode="runDrawerMode"
      :run-nonce="runNonce"
    />

    <EnvironmentEditDrawer
      v-model:show="envDrawerShow"
      :environment-id="envDrawerId"
      :project-id="PROJECT_ID"
      @saved="onEnvSaved"
    />
  </div>
</template>

<style scoped>
.api-test-shell {
  --api-test-accent: #4098fc;
  --api-test-accent-strong: #2d80e6;
  --api-test-accent-soft: rgba(64, 152, 252, 0.12);
  --api-density-pad-y: 5px;
  --api-density-pad-x: 10px;
  --api-row-height: 28px;
  --api-font-sm: 12px;
  --api-font: 13px;
  --api-method-get: #10b981;
  --api-method-post: #f59e0b;
  --api-method-put: #3b82f6;
  --api-method-patch: #8b5cf6;
  --api-method-delete: #ef4444;
  --api-method-default: #64748b;
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
  --api-method-get: #34d399;
  --api-method-post: #fbbf24;
  --api-method-put: #60a5fa;
  --api-method-patch: #a78bfa;
  --api-method-delete: #f87171;
  --api-method-default: #94a3b8;
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
  padding: var(--api-density-pad-y) var(--api-density-pad-x);
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

.api-test-shell__workspace > .request-workspace,
.api-test-shell__workspace > .collection-overview {
  align-self: stretch;
  width: 100%;
}
</style>
