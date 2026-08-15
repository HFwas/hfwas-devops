<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { storeToRefs } from 'pinia'
import { useMessage } from 'naive-ui'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import ComingSoonPane from '@/modules/api-test/shell/components/ComingSoonPane.vue'

const props = defineProps<{
  collectionId: number
}>()

const emit = defineEmits<{
  run: [collectionId: number]
  history: [collectionId: number]
}>()

const message = useMessage()
const collectionStore = useCollectionStore()
const { currentDetail } = storeToRefs(collectionStore)

const activeTab = ref('overview')
const loading = ref(false)

const detail = computed(() =>
  currentDetail.value?.id === props.collectionId ? currentDetail.value : null,
)

watch(
  () => props.collectionId,
  async (id) => {
    if (!id) return
    if (currentDetail.value?.id === id) return
    loading.value = true
    try {
      await collectionStore.loadDetail(id)
    } catch (e: any) {
      message.error(e?.message || '加载集合详情失败')
    } finally {
      loading.value = false
    }
  },
  { immediate: true },
)

function onRun() {
  emit('run', props.collectionId)
}

function onHistory() {
  emit('history', props.collectionId)
}
</script>

<template>
  <div class="collection-overview" data-testid="collection-overview">
    <n-spin :show="loading">
      <n-tabs v-model:value="activeTab" type="line" size="small" class="collection-overview__tabs">
        <n-tab-pane name="overview" tab="Overview">
          <div class="collection-overview__body">
            <div class="collection-overview__name">{{ detail?.name || '—' }}</div>
            <div v-if="detail?.description" class="collection-overview__desc">
              {{ detail.description }}
            </div>
            <n-empty v-else-if="detail" description="暂无描述" size="small" />
          </div>
        </n-tab-pane>

        <n-tab-pane name="runs" tab="Runs">
          <div class="collection-overview__body collection-overview__runs">
            <n-button
              type="primary"
              size="small"
              data-testid="collection-overview-run"
              @click="onRun"
            >
              Run
            </n-button>
            <n-button
              size="small"
              data-testid="collection-overview-history"
              @click="onHistory"
            >
              History
            </n-button>
          </div>
        </n-tab-pane>

        <n-tab-pane name="authorization" tab="Authorization">
          <ComingSoonPane title="Authorization" />
        </n-tab-pane>

        <n-tab-pane name="scripts" tab="Scripts">
          <ComingSoonPane title="Scripts" />
        </n-tab-pane>

        <n-tab-pane name="variables" tab="Variables">
          <ComingSoonPane title="Variables" />
        </n-tab-pane>
      </n-tabs>
    </n-spin>
  </div>
</template>

<style scoped>
.collection-overview {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  height: 100%;
  overflow: hidden;
  padding: 12px 16px;
  background: var(--wb-card-bg, #fff);
  color: inherit;
}

.collection-overview__tabs {
  height: 100%;
}

.collection-overview__body {
  padding: 12px 0;
}

.collection-overview__name {
  font-size: 18px;
  font-weight: 600;
  line-height: 1.4;
}

.collection-overview__desc {
  margin-top: 8px;
  font-size: 13px;
  line-height: 1.5;
  color: var(--wb-muted, #6b7280);
  white-space: pre-wrap;
}

.collection-overview__runs {
  display: flex;
  gap: 8px;
  align-items: center;
}
</style>
