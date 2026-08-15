<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import CollectionRunResult from '@/modules/api-test/collection/components/CollectionRunResult.vue'
import type { CollectionRunDetailVO, CollectionRunVO } from '@/modules/api-test/collection/types/collection'

const props = withDefaults(defineProps<{
  show: boolean
  collectionId?: number | null
  mode?: 'run' | 'history'
}>(), {
  collectionId: null,
  mode: 'history',
})

const emit = defineEmits<{
  'update:show': [value: boolean]
}>()

const route = useRoute()
const message = useMessage()
const authStore = useAuthStore()
const collectionStore = useCollectionStore()
const envStore = useEnvironmentStore()
const { runHistory, executing } = storeToRefs(collectionStore)

const userId = computed(() => Number(authStore.user?.id) || 0)
const loading = ref(false)
const runDetail = ref<CollectionRunDetailVO | null>(null)
const selectedRunId = ref<number | null>(null)
let honoredRunsKey: string | null = null

const historyRecords = computed(() => runHistory.value.records || [])

function parsePositiveInt(raw: unknown): number | null {
  const value = Array.isArray(raw) ? raw[0] : raw
  if (typeof value !== 'string' || value === '') return null
  const id = Number(value)
  if (!Number.isInteger(id) || id <= 0) return null
  return id
}

function queryRuns(): boolean {
  const raw = route.query.runs
  const value = Array.isArray(raw) ? raw[0] : raw
  return value === '1'
}

const queryCollectionId = computed(() => parsePositiveInt(route.query.collectionId))

const effectiveCollectionId = computed(() => props.collectionId ?? queryCollectionId.value)

async function loadHistory(collectionId: number) {
  loading.value = true
  try {
    await collectionStore.loadRunHistory(collectionId, { pageNo: 1, pageSize: 20 })
  } catch (e: any) {
    message.error(e?.message || '加载运行历史失败')
  } finally {
    loading.value = false
  }
}

async function executeRun(collectionId: number) {
  loading.value = true
  runDetail.value = null
  try {
    const result = await collectionStore.runCollection(
      collectionId,
      envStore.selectedEnvironmentId ?? undefined,
      userId.value,
    )
    if (result?.id) {
      selectedRunId.value = result.id
      runDetail.value = await collectionStore.loadRunDetail(result.id)
    }
  } catch (e: any) {
    message.error(e?.message || '执行集合失败')
  } finally {
    loading.value = false
  }
}

async function selectHistoryRun(runId: number) {
  selectedRunId.value = runId
  loading.value = true
  try {
    runDetail.value = await collectionStore.loadRunDetail(runId)
  } catch (e: any) {
    message.error(e?.message || '加载运行详情失败')
  } finally {
    loading.value = false
  }
}

watch(() => [props.show, props.mode, props.collectionId] as const, async ([show, mode, id]) => {
  if (!show) return
  const collectionId = id ?? queryCollectionId.value
  if (collectionId == null) return
  if (mode === 'run') {
    await executeRun(collectionId)
  } else {
    runDetail.value = null
    selectedRunId.value = null
    await loadHistory(collectionId)
  }
}, { immediate: true })

watch(() => [queryCollectionId.value, queryRuns()] as const, async ([id, runs]) => {
  if (id == null || !runs) return
  const key = String(id)
  if (honoredRunsKey === key) return
  honoredRunsKey = key
  emit('update:show', true)
  runDetail.value = null
  selectedRunId.value = null
  await loadHistory(id)
}, { immediate: true })

function onUpdateShow(value: boolean) {
  emit('update:show', value)
}

function statusLabel(status: string): string {
  if (status === 'COMPLETED' || status === 'SUCCESS') return '完成'
  if (status === 'FAILED' || status === 'FAILURE') return '失败'
  if (status === 'RUNNING') return '运行中'
  return status
}

function runRowClass(run: CollectionRunVO) {
  return run.id === selectedRunId.value ? 'is-active' : undefined
}
</script>

<template>
  <n-drawer :show="show" :width="720" placement="right" @update:show="onUpdateShow">
    <n-drawer-content :title="mode === 'run' ? '集合运行' : '运行历史'" closable>
      <n-spin :show="loading || executing">
        <div v-if="mode === 'history' || historyRecords.length" class="run-drawer__history">
          <button
            v-for="run in historyRecords"
            :key="run.id"
            type="button"
            class="run-drawer__history-item"
            :class="runRowClass(run)"
            :data-testid="`run-history-item-${run.id}`"
            @click="selectHistoryRun(run.id)"
          >
            <span class="run-drawer__history-name">{{ run.name }}</span>
            <span class="run-drawer__history-meta">{{ statusLabel(run.status) }} · {{ run.createTime }}</span>
          </button>
          <n-empty
            v-if="mode === 'history' && !historyRecords.length && !loading"
            description="暂无运行记录"
            size="small"
          />
        </div>
        <CollectionRunResult v-if="runDetail" :run-detail="runDetail" />
        <n-empty
          v-else-if="mode === 'history' && historyRecords.length"
          description="选择一条运行记录"
          size="small"
        />
      </n-spin>
    </n-drawer-content>
  </n-drawer>
</template>

<style scoped>
.run-drawer__history {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 12px;
  max-height: 220px;
  overflow: auto;
}

.run-drawer__history-item {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  width: 100%;
  padding: 8px 10px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 6px;
  background: transparent;
  color: inherit;
  cursor: pointer;
  text-align: left;
}

.run-drawer__history-item:hover {
  background: var(--wb-chip-bg, #f8fafc);
}

.run-drawer__history-item.is-active {
  border-color: #4098fc;
  background: rgba(64, 152, 252, 0.12);
}

.run-drawer__history-name {
  font-size: 13px;
  font-weight: 600;
}

.run-drawer__history-meta {
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}
</style>
