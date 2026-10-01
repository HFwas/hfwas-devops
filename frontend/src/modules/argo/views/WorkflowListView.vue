<script setup lang="ts">
import { Play, Plus, RefreshCw, Search, Trash2 } from '@lucide/vue'
import { NButton, NCard, NEmpty, NInput, NSelect, NSpace, NSpin, NTag, useMessage } from 'naive-ui'
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import type { WorkflowSummary } from '@/modules/argo/types/workflow'
import { pageWorkflows, submitWorkflow, deleteWorkflow } from '@/modules/argo/api/workflowApi'
import '@/modules/pipeline/styles/pipeline-theme.css'

const router = useRouter()
const message = useMessage()
const loading = ref(false)
const keyword = ref('')
const statusFilter = ref<'all' | 'Running' | 'Succeeded' | 'Failed'>('all')
const workflows = ref<WorkflowSummary[]>([])
const total = ref(0)
const pageNo = ref(1)
const pageSize = 20

const statusOptions = [
  { label: '全部', value: 'all' },
  { label: '运行中', value: 'Running' },
  { label: '已完成', value: 'Succeeded' },
  { label: '失败', value: 'Failed' },
]

async function fetch() {
  loading.value = true
  try {
    const res = await pageWorkflows({
      pageNo: pageNo.value,
      pageSize,
      keyword: keyword.value || undefined,
      status: statusFilter.value === 'all' ? undefined : statusFilter.value,
    })
    workflows.value = res.records
    total.value = res.total
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

function goNew() {
  router.push('/argo/workflows/new')
}

function goDetail(name: string) {
  router.push(`/argo/workflows/${name}`)
}

async function handleRun(name: string) {
  try {
    await submitWorkflow(name)
    message.success('已提交运行')
    fetch()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '运行失败')
  }
}

async function handleDelete(name: string) {
  try {
    await deleteWorkflow(name)
    message.success('已删除')
    fetch()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

const statusIcon = (s: string) => {
  if (s === 'Running') return '🔵'
  if (s === 'Succeeded') return '✅'
  if (s === 'Failed') return '❌'
  if (s === 'Pending') return '⏳'
  if (s === 'Error') return '💥'
  return '⚪'
}

const statusType = (s: string) => {
  if (s === 'Running') return 'info' as const
  if (s === 'Succeeded') return 'success' as const
  if (s === 'Failed' || s === 'Error') return 'error' as const
  return 'default' as const
}

onMounted(fetch)
</script>

<template>
  <div class="pl-toolbar">
    <NInput
      v-model:value="keyword"
      placeholder="搜索工作流名称..."
      clearable
      style="width: 280px"
      @keyup.enter="fetch"
    >
      <template #prefix>
        <Search :size="14" />
      </template>
    </NInput>

    <NSelect
      v-model:value="statusFilter"
      :options="statusOptions"
      style="width: 120px"
      @update:value="fetch"
    />

    <NSpace style="margin-left: auto">
      <NButton quaternary @click="fetch">
        <template #icon><RefreshCw :size="14" /></template>
        刷新
      </NButton>
      <NButton type="primary" @click="goNew">
        <template #icon><Plus :size="14" /></template>
        新建工作流
      </NButton>
    </NSpace>
  </div>

  <NSpin :show="loading">
    <div v-if="workflows.length === 0" class="pl-empty">
      <NEmpty description="暂无工作流" />
    </div>

    <div v-else class="pl-grid">
      <div
        v-for="wf in workflows"
        :key="wf.name"
        class="pl-tile"
        @click="goDetail(wf.name)"
      >
        <div class="pl-tile-top">
          <div class="pl-tile-icon" :class="statusType(wf.status)">
            <span style="font-size: 18px">{{ statusIcon(wf.status) }}</span>
          </div>
          <div class="pl-tile-status">
            <NTag :type="statusType(wf.status)" size="small">
              {{ wf.status }}
            </NTag>
          </div>
        </div>

        <div class="pl-tile-body">
          <div class="pl-tile-name">{{ wf.name }}</div>
          <div class="pl-tile-meta">
            <span>⏱ {{ wf.duration || '-' }}</span>
            <span>🎯 {{ wf.entrypoint || '-' }}</span>
            <span>📅 {{ wf.startedAt ? new Date(wf.startedAt).toLocaleString() : '-' }}</span>
          </div>
        </div>

        <div class="pl-tile-actions" @click.stop>
          <NButton size="tiny" quaternary @click="handleRun(wf.name)">
            <template #icon><Play :size="13" /></template>
            运行
          </NButton>
          <NButton size="tiny" quaternary @click="handleDelete(wf.name)">
            <template #icon><Trash2 :size="13" /></template>
            删除
          </NButton>
        </div>
      </div>
    </div>

    <div v-if="total > pageSize" class="pl-footer">
      <NPagination
        v-model:page="pageNo"
        :page-size="pageSize"
        :item-count="total"
        @update:page="fetch"
      />
    </div>
  </NSpin>
</template>

<style scoped>
.pl-empty {
  display: flex;
  justify-content: center;
  padding: 60px 0;
}

.pl-tile-icon.info {
  background: #eff6ff;
  color: #2563eb;
}
.pl-tile-icon.success {
  background: #ecfdf5;
  color: #059669;
}
.pl-tile-icon.error {
  background: #fef2f2;
  color: #dc2626;
}
</style>