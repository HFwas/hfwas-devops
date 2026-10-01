<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NTag, NSpace, useMessage } from 'naive-ui'
import { ArrowLeft, Play, Trash2, Edit3, History } from '@lucide/vue'
import type { WorkflowDetail, WorkflowRun } from '@/modules/argo/types/workflow'
import { getWorkflow, submitWorkflow, deleteWorkflow, getWorkflowRuns } from '@/modules/argo/api/workflowApi'
import '@/modules/pipeline/styles/pipeline-theme.css'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const detail = ref<WorkflowDetail | null>(null)
const runs = ref<WorkflowRun[]>([])
const loading = ref(false)
const name = route.params.name as string

const statusType = (s: string) => {
  if (s === 'Running') return 'info' as const
  if (s === 'Succeeded') return 'success' as const
  if (s === 'Failed' || s === 'Error') return 'error' as const
  return 'default' as const
}

function goEdit() {
  router.push(`/argo/workflows/${name}/edit`)
}

function goRun(runId: string) {
  router.push(`/argo/workflows/${name}/runs/${runId}`)
}

async function handleRun() {
  try {
    await submitWorkflow(name)
    message.success('已提交运行')
    await load()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '运行失败')
  }
}

async function handleDelete() {
  try {
    await deleteWorkflow(name)
    message.success('已删除')
    router.push('/argo/workflows')
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '删除失败')
  }
}

async function load() {
  loading.value = true
  try {
    detail.value = await getWorkflow(name)
    const res = await getWorkflowRuns(name, { pageNo: 1, pageSize: 10 })
    runs.value = res.records
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <div>
    <!-- 头部 -->
    <div class="pl-hero">
      <div class="pl-hero-main">
        <button class="pl-back" @click="router.push('/argo/workflows')">
          <ArrowLeft :size="14" /> 返回
        </button>
        <h1 class="pl-hero-title">{{ name }}</h1>
        <p class="pl-hero-desc">
          入口: {{ detail?.entrypoint }} &nbsp;|&nbsp;
          状态: <NTag :type="detail ? statusType(detail.status) : 'default'" size="small">{{ detail?.status }}</NTag>
        </p>
      </div>
      <div class="pl-hero-extra">
        <NButton secondary size="small" @click="goEdit">
          <template #icon><Edit3 :size="13" /></template>
          编辑
        </NButton>
        <NButton type="primary" size="small" @click="handleRun">
          <template #icon><Play :size="13" /></template>
          运行
        </NButton>
        <NButton quaternary size="small" @click="handleDelete">
          <template #icon><Trash2 :size="13" /></template>
          删除
        </NButton>
      </div>
    </div>

    <!-- 基本信息 -->
    <div class="pl-meta-grid" style="margin-bottom: 16px">
      <div class="pl-meta-item">
        <div class="pl-meta-label">模板数</div>
        <div class="pl-meta-value">{{ detail?.templates.length ?? '-' }}</div>
      </div>
      <div class="pl-meta-item">
        <div class="pl-meta-label">运行次数</div>
        <div class="pl-meta-value">{{ runs.length }}</div>
      </div>
      <div class="pl-meta-item">
        <div class="pl-meta-label">参数</div>
        <div class="pl-meta-value">{{ detail?.parameters.length ?? 0 }} 个</div>
      </div>
    </div>

    <!-- 运行历史 -->
    <NCard title="运行历史" size="small" class="pl-card">
      <template #header-extra>
        <NTag size="small"><History :size="12" /></NTag>
      </template>

      <div v-if="runs.length === 0" style="padding: 24px 0; text-align: center; color: var(--wb-muted)">
        暂无运行记录
      </div>

      <div v-else class="pl-grid">
        <div
          v-for="run in runs"
          :key="run.id"
          class="pl-tile"
          @click="goRun(run.id)"
        >
          <div class="pl-tile-top">
            <div class="pl-tile-icon" :class="run.status === 'Running' ? 'info' : run.status === 'Succeeded' ? 'success' : 'error'">
              <span>{{ run.status === 'Running' ? '🔵' : run.status === 'Succeeded' ? '✅' : '❌' }}</span>
            </div>
            <div class="pl-tile-status">
              <NTag :type="statusType(run.status)" size="small">{{ run.status }}</NTag>
            </div>
          </div>
          <div class="pl-tile-body">
            <div class="pl-tile-meta">
              <span>⏱ {{ run.duration || '-' }}</span>
              <span>📅 {{ run.startedAt ? new Date(run.startedAt).toLocaleString() : '-' }}</span>
            </div>
          </div>
        </div>
      </div>
    </NCard>
  </div>
</template>