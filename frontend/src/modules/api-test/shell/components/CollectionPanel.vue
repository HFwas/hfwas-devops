<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import CollectionTree from '@/modules/api-test/collection/components/CollectionTree.vue'
import type { CollectionItemVO } from '@/modules/api-test/collection/types/collection'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { loadDefinitionIntoTab } from '@/modules/api-test/shell/utils/loadDefinitionDraft'

const PROJECT_ID = 1

const emit = defineEmits<{
  run: [collectionId: number]
  history: [collectionId: number]
}>()

const route = useRoute()
const message = useMessage()
const collectionStore = useCollectionStore()
const workspace = useWorkspaceStore()
const { pageResult, currentDetail } = storeToRefs(collectionStore)

const selectedCollectionId = ref<number | null>(null)
const collections = computed(() => pageResult.value.records || [])
const drilled = computed(() => selectedCollectionId.value != null && currentDetail.value?.id === selectedCollectionId.value)

onMounted(async () => {
  try {
    await collectionStore.loadPage({ projectId: PROJECT_ID, pageNo: 1, pageSize: 200 })
  } catch (e: any) {
    message.error(e?.message || '加载集合失败')
  }
})

watch(() => route.query.collectionId, (raw) => {
  const id = parsePositiveInt(raw)
  if (id != null) void drillInto(id)
}, { immediate: true })

function parsePositiveInt(raw: unknown): number | null {
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value === '') return null
  const id = Number(value)
  if (!Number.isInteger(id) || id <= 0) return null
  return id
}

async function drillInto(id: number) {
  selectedCollectionId.value = id
  try {
    await collectionStore.loadDetail(id)
  } catch (e: any) {
    selectedCollectionId.value = null
    message.error(e?.message || '加载集合详情失败')
  }
}

function onBack() {
  selectedCollectionId.value = null
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

function onRun() {
  if (selectedCollectionId.value == null) return
  emit('run', selectedCollectionId.value)
}

function onHistory() {
  if (selectedCollectionId.value == null) return
  emit('history', selectedCollectionId.value)
}
</script>

<template>
  <div class="collection-panel" data-testid="collection-panel">
    <template v-if="!drilled">
      <div class="collection-panel__toolbar">
        <span class="collection-panel__title">集合</span>
      </div>
      <div class="collection-panel__list">
        <button
          v-for="col in collections"
          :key="col.id"
          type="button"
          class="collection-panel__item"
          :data-testid="`collection-item-${col.id}`"
          @click="drillInto(col.id)"
        >
          {{ col.name }}
        </button>
        <n-empty v-if="!collections.length" description="暂无集合" size="small" />
      </div>
    </template>

    <template v-else>
      <div class="collection-panel__toolbar">
        <n-button size="tiny" data-testid="collection-back" @click="onBack">返回</n-button>
        <span class="collection-panel__title">{{ currentDetail?.name }}</span>
        <div class="collection-panel__actions">
          <n-button size="tiny" type="primary" data-testid="collection-run" @click="onRun">
            Run
          </n-button>
          <n-button size="tiny" data-testid="collection-history" @click="onHistory">
            History
          </n-button>
        </div>
      </div>
      <div class="collection-panel__tree">
        <CollectionTree
          :folders="currentDetail?.folders || []"
          :items="currentDetail?.items || []"
          @select-item="onSelectItem"
        />
      </div>
    </template>
  </div>
</template>

<style scoped>
.collection-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.collection-panel__toolbar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.collection-panel__title {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.collection-panel__actions {
  display: flex;
  flex-shrink: 0;
  gap: 6px;
}

.collection-panel__list {
  flex: 1;
  overflow: auto;
  padding: 4px 0;
}

.collection-panel__item {
  display: flex;
  width: 100%;
  padding: 6px 12px;
  border: none;
  background: transparent;
  color: inherit;
  cursor: pointer;
  font-size: 13px;
  text-align: left;
}

.collection-panel__item:hover {
  background: var(--wb-chip-bg, #f8fafc);
}

.collection-panel__tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 4px 0;
}
</style>
