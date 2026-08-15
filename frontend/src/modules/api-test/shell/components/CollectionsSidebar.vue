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
import { useAuthStore } from '@/modules/user/stores/auth'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { createRequestInCollection } from '@/modules/api-test/shell/utils/createRequestInCollection'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'

const PROJECT_ID = 1

const emit = defineEmits<{
  run: [collectionId: number]
  history: [collectionId: number]
}>()

const message = useMessage()
const collectionStore = useCollectionStore()
const authStore = useAuthStore()
const workspace = useWorkspaceStore()
const { pageResult, currentDetail } = storeToRefs(collectionStore)

const collections = computed(() => pageResult.value.records || [])
const userId = computed(() => Number(authStore.user?.id) || 0)

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
      title: item.name || detail.name,
      method: item.method || detail.method,
      draft,
    })
  } catch (e: any) {
    message.error(e?.message || '加载接口失败')
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
</script>

<template>
  <div class="collections-sidebar" data-testid="collections-sidebar">
    <div class="collections-sidebar__toolbar">
      <span class="collections-sidebar__title">COLLECTIONS</span>
      <n-input
        v-model:value="searchKeyword"
        size="tiny"
        clearable
        placeholder="搜索"
        class="collections-sidebar__search"
      />
      <n-button
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
      <template v-if="filteredCollections.length">
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

.collections-sidebar__title {
  flex-shrink: 0;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.04em;
  color: var(--wb-text-secondary, #64748b);
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
