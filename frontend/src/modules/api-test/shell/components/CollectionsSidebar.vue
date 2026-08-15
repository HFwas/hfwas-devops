<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useMessage } from 'naive-ui'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import CollectionTree from '@/modules/api-test/collection/components/CollectionTree.vue'
import type {
  CollectionDetailVO,
  CollectionItemVO,
  CollectionVO,
} from '@/modules/api-test/collection/types/collection'
import { debugHistoryApi } from '@/modules/api-test/debug/api/debugHistory'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import type { ApiDebugHistoryVO } from '@/modules/api-test/debug/types/debug'
import { useAuthStore } from '@/modules/user/stores/auth'
import HistorySidebarList from '@/modules/api-test/shell/components/HistorySidebarList.vue'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { createRequestInCollection } from '@/modules/api-test/shell/utils/createRequestInCollection'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'
import { mapHistoryDetailToTab } from '@/modules/api-test/shell/utils/mapHistoryDetailToTab'

const PROJECT_ID = 1

const emit = defineEmits<{
  run: [collectionId: number]
  history: [collectionId: number]
  'import-curl': []
}>()

const message = useMessage()
const collectionStore = useCollectionStore()
const authStore = useAuthStore()
const workspace = useWorkspaceStore()
const debugStore = useDebugStore()
const { pageResult, currentDetail } = storeToRefs(collectionStore)
const { historyEpoch } = storeToRefs(debugStore)

const collections = computed(() => pageResult.value.records || [])
const userId = computed(() => Number(authStore.user?.id) || 0)

const sidebarMode = ref<'collections' | 'history'>('collections')
const historyRecords = ref<ApiDebugHistoryVO[]>([])
const historyLoading = ref(false)

const expandedIds = ref<Set<number>>(new Set())
const detailCache = reactive<Record<number, CollectionDetailVO>>({})
const searchKeyword = ref('')

const showCreateDialog = ref(false)
const creating = ref(false)
const createForm = ref({ name: '', description: '' })

const filteredCollections = computed(() => {
  const kw = searchKeyword.value.trim().toLowerCase()
  if (!kw) return collections.value
  return collections.value.filter((c) => c.name.toLowerCase().includes(kw))
})

const activeCollectionItemId = computed(() => {
  const tab = workspace.activeTab
  if (tab?.source === 'collection' && tab.refId != null) return tab.refId
  return null
})

onMounted(async () => {
  await reloadPage()
})

async function reloadPage() {
  try {
    await collectionStore.loadPage({ projectId: PROJECT_ID, pageNo: 1, pageSize: 200 })
  } catch (e: any) {
    message.error(e?.message || '加载集合失败')
  }
}

async function loadHistoryPage() {
  historyLoading.value = true
  try {
    const page = await debugHistoryApi.page({
      projectId: PROJECT_ID,
      pageNo: 1,
      pageSize: 50,
    })
    historyRecords.value = page.records || []
  } catch (e: any) {
    message.error(e?.message || '加载历史失败')
    historyRecords.value = []
  } finally {
    historyLoading.value = false
  }
}

watch(
  () => [sidebarMode.value, historyEpoch.value] as const,
  async ([mode]) => {
    if (mode === 'history') await loadHistoryPage()
  },
)

function setSidebarMode(mode: 'collections' | 'history') {
  sidebarMode.value = mode
}

function isExpanded(id: number) {
  return expandedIds.value.has(id)
}

function detailFor(id: number): CollectionDetailVO | undefined {
  return detailCache[id]
}

async function ensureDetail(id: number) {
  try {
    const detail = await collectionStore.loadDetail(id)
    detailCache[id] = detail
    return detail
  } catch (e: any) {
    message.error(e?.message || '加载集合详情失败')
    return null
  }
}

/** Sync store currentDetail into local cache (e.g. after scratch save elsewhere) */
watch(currentDetail, (detail) => {
  if (detail != null) {
    detailCache[detail.id] = detail
  }
})

async function onSelectCollection(col: CollectionVO) {
  const next = new Set(expandedIds.value)
  next.add(col.id)
  expandedIds.value = next
  await ensureDetail(col.id)
  workspace.openOrFocusCollectionOverview(col.id, col.name)
}

async function onSelectItem(item: CollectionItemVO) {
  try {
    const { detail, draft } = await loadDefinitionIntoTab(item.definitionId)
    workspace.openOrFocusTab({
      source: 'collection',
      refId: item.id,
      definitionId: item.definitionId,
      collectionId: item.collectionId,
      folderId: item.folderId,
      title: item.name || detail.name,
      method: item.method || detail.method,
      draft,
    })
  } catch (e: any) {
    message.error(e?.message || '加载接口失败')
  }
}

async function onSelectHistory(id: number) {
  try {
    const detail = await debugHistoryApi.detail(id)
    const mapped = mapHistoryDetailToTab(detail)
    const tab = workspace.openScratchTab()
    workspace.patchDraft(tab.id, mapped.draftPatch)
    workspace.setTabMeta(tab.id, { title: mapped.title, method: mapped.method })
    workspace.setTabResult(tab.id, mapped.result)
  } catch (e: any) {
    message.error(e?.message || '加载历史详情失败')
  }
}

function requireUserId(): number | null {
  if (!userId.value) {
    message.warning('请先登录后再操作')
    return null
  }
  return userId.value
}

function openCreateDialog() {
  createForm.value = { name: '', description: '' }
  showCreateDialog.value = true
}

async function submitCreateCollection() {
  const uid = requireUserId()
  if (uid == null) return
  const name = createForm.value.name.trim()
  if (!name) {
    message.warning('请输入集合名称')
    return
  }
  creating.value = true
  try {
    const created = await collectionStore.create(
      { name, description: createForm.value.description.trim() || undefined },
      PROJECT_ID,
      uid,
    )
    showCreateDialog.value = false
    await reloadPage()
    const next = new Set(expandedIds.value)
    next.add(created.id)
    expandedIds.value = next
    await ensureDetail(created.id)
    workspace.openOrFocusCollectionOverview(created.id, created.name)
  } catch (e: any) {
    message.error(e?.message || '创建集合失败')
  } finally {
    creating.value = false
  }
}

async function onAddRequest(collectionId: number, event: Event) {
  event.stopPropagation()
  const uid = requireUserId()
  if (uid == null) return
  try {
    const result = await createRequestInCollection({
      projectId: PROJECT_ID,
      collectionId,
      userId: uid,
    })
    const next = new Set(expandedIds.value)
    next.add(collectionId)
    expandedIds.value = next
    await ensureDetail(collectionId)
    const { detail, draft } = await loadDefinitionIntoTab(result.definitionId)
    workspace.openOrFocusTab({
      source: 'collection',
      refId: result.itemId,
      definitionId: result.definitionId,
      collectionId,
      title: result.name || detail.name,
      method: result.method || detail.method,
      draft,
    })
  } catch (e: any) {
    message.error(e?.message || '新建请求失败')
  }
}

function onRun(collectionId: number, event: Event) {
  event.stopPropagation()
  emit('run', collectionId)
}

function onHistory(collectionId: number, event: Event) {
  event.stopPropagation()
  emit('history', collectionId)
}

function onImportCurl() {
  emit('import-curl')
}
</script>

<template>
  <div class="collections-sidebar" data-testid="collections-sidebar">
    <div class="collections-sidebar__toolbar">
      <div class="collections-sidebar__modes">
        <button
          type="button"
          class="collections-sidebar__mode"
          :class="{ 'collections-sidebar__mode--active': sidebarMode === 'collections' }"
          data-testid="sidebar-mode-collections"
          @click="setSidebarMode('collections')"
        >
          Collections
        </button>
        <button
          type="button"
          class="collections-sidebar__mode"
          :class="{ 'collections-sidebar__mode--active': sidebarMode === 'history' }"
          data-testid="sidebar-mode-history"
          @click="setSidebarMode('history')"
        >
          History
        </button>
      </div>
      <n-input
        v-model:value="searchKeyword"
        size="tiny"
        clearable
        placeholder="搜索"
        class="collections-sidebar__search"
      />
      <n-button
        v-if="sidebarMode === 'collections'"
        size="tiny"
        quaternary
        data-testid="import-curl"
        title="导入 cURL"
        @click="onImportCurl"
      >
        导入
      </n-button>
      <n-button
        v-if="sidebarMode === 'collections'"
        size="tiny"
        quaternary
        data-testid="create-collection"
        title="新建集合"
        @click="openCreateDialog"
      >
        +
      </n-button>
    </div>

    <div class="collections-sidebar__body">
      <HistorySidebarList
        v-if="sidebarMode === 'history'"
        :records="historyRecords"
        :loading="historyLoading"
        :keyword="searchKeyword"
        @select="onSelectHistory"
      />

      <template v-else-if="filteredCollections.length">
        <div
          v-for="col in filteredCollections"
          :key="col.id"
          class="collections-sidebar__section"
        >
          <div
            class="collections-sidebar__row"
            :data-testid="`collection-row-${col.id}`"
            @click="onSelectCollection(col)"
          >
            <span class="collections-sidebar__chevron">{{ isExpanded(col.id) ? '▾' : '▸' }}</span>
            <span class="collections-sidebar__name">{{ col.name }}</span>
            <div class="collections-sidebar__row-actions" @click.stop>
              <n-button
                size="tiny"
                quaternary
                :data-testid="`collection-add-request-${col.id}`"
                title="新建请求"
                @click="onAddRequest(col.id, $event)"
              >
                +
              </n-button>
              <n-button
                size="tiny"
                quaternary
                :data-testid="`collection-menu-run-${col.id}`"
                title="Run"
                @click="onRun(col.id, $event)"
              >
                Run
              </n-button>
              <n-button
                size="tiny"
                quaternary
                :data-testid="`collection-menu-history-${col.id}`"
                title="History"
                @click="onHistory(col.id, $event)"
              >
                Hist
              </n-button>
            </div>
          </div>
          <div v-if="isExpanded(col.id)" class="collections-sidebar__tree">
            <CollectionTree
              :folders="detailFor(col.id)?.folders || []"
              :items="detailFor(col.id)?.items || []"
              :selected-id="activeCollectionItemId"
              @select-item="onSelectItem"
            />
          </div>
        </div>
      </template>

      <div v-else class="collections-sidebar__empty">
        <n-button
          size="small"
          type="primary"
          data-testid="empty-create-collection"
          @click="openCreateDialog"
        >
          创建第一个集合
        </n-button>
      </div>
    </div>

    <n-modal
      v-model:show="showCreateDialog"
      preset="dialog"
      title="新建集合"
      :mask-closable="false"
    >
      <n-form label-placement="left">
        <n-form-item label="名称">
          <n-input v-model:value="createForm.name" placeholder="集合名称" data-testid="create-collection-name" />
        </n-form-item>
        <n-form-item label="描述">
          <n-input
            v-model:value="createForm.description"
            type="textarea"
            placeholder="描述（可选）"
          />
        </n-form-item>
      </n-form>
      <template #action>
        <n-button @click="showCreateDialog = false">取消</n-button>
        <n-button
          type="primary"
          :loading="creating"
          data-testid="create-collection-submit"
          @click="submitCreateCollection"
        >
          创建
        </n-button>
      </template>
    </n-modal>
  </div>
</template>

<style scoped>
.collections-sidebar {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.collections-sidebar__toolbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  padding: var(--api-density-pad-y, 5px) var(--api-density-pad-x, 10px);
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.collections-sidebar__modes {
  display: flex;
  flex-shrink: 0;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 3px;
  overflow: hidden;
}

.collections-sidebar__mode {
  margin: 0;
  padding: 2px 8px;
  border: 0;
  background: transparent;
  color: var(--wb-text-secondary, #64748b);
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.02em;
  cursor: pointer;
}

.collections-sidebar__mode--active {
  background: var(--wb-chip-bg, #f1f5f9);
  color: var(--wb-text, #0f172a);
}

.collections-sidebar__search {
  flex: 1;
  min-width: 0;
}

.collections-sidebar__body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: var(--api-density-pad-y, 4px) 0;
  display: flex;
  flex-direction: column;
}

.collections-sidebar__section {
  margin-bottom: 2px;
}

.collections-sidebar__row {
  display: flex;
  align-items: center;
  gap: 4px;
  width: 100%;
  min-height: var(--api-row-height, 28px);
  padding: var(--api-density-pad-y, 4px) var(--api-density-pad-x, 8px);
  cursor: pointer;
  font-size: var(--api-font, 13px);
}

.collections-sidebar__row:hover {
  background: var(--wb-chip-bg, #f8fafc);
}

.collections-sidebar__chevron {
  flex-shrink: 0;
  width: 12px;
  color: var(--wb-text-secondary, #64748b);
  font-size: 11px;
}

.collections-sidebar__name {
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}

.collections-sidebar__row-actions {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 0;
  opacity: 0.55;
}

.collections-sidebar__row:hover .collections-sidebar__row-actions {
  opacity: 0.85;
}

.collections-sidebar__tree {
  padding: 0 var(--api-density-pad-x, 4px) var(--api-density-pad-y, 4px) 18px;
}

.collections-sidebar__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px 12px;
}
</style>
