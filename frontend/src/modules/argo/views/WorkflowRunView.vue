<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NTag, NSpace, NSpin, NTabs, NTabPane, useMessage } from 'naive-ui'
import { ArrowLeft, RefreshCw } from '@lucide/vue'
import { VueFlow, MarkerType } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { MiniMap } from '@vue-flow/minimap'
import type { Node, Edge, NodeMouseEvent } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import type { WorkflowRun, NodeStatus, WorkflowRunLog, WorkflowEvent } from '@/modules/argo/types/workflow'
import { getWorkflowRun, getWorkflowRunLogs, getWorkflowRunEvents } from '@/modules/argo/api/workflowApi'
import '@/modules/pipeline/styles/pipeline-theme.css'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const run = ref<WorkflowRun | null>(null)
const logs = ref<WorkflowRunLog[]>([])
const events = ref<WorkflowEvent[]>([])
const activeTab = ref<'logs' | 'events' | 'yaml'>('logs')
const selectedNodeId = ref<string | null>(null)
const loading = ref(false)
const logContent = ref('')
const flowNodes = ref<Node[]>([])
const flowEdges = ref<Edge[]>([])
const runId = route.params.runId as string
const wfName = route.params.name as string

function nodeBg(phase: string): string {
  if (phase === 'Running') return '#eff6ff'
  if (phase === 'Succeeded') return '#ecfdf5'
  if (phase === 'Failed' || phase === 'Error') return '#fef2f2'
  return '#f8fafc'
}

function nodeBorder(phase: string): string {
  if (phase === 'Running') return '#3b82f6'
  if (phase === 'Succeeded') return '#10b981'
  if (phase === 'Failed' || phase === 'Error') return '#ef4444'
  return '#e2e8f0'
}

function buildGraph() {
  if (!run.value) return
  const nodes: Node[] = []
  const edges: Edge[] = []

  for (const [id, ns] of Object.entries(run.value.nodeStatus)) {
    nodes.push({
      id,
      type: 'default',
      position: { x: 0, y: 0 }, // auto-layout
      data: { label: `${ns.displayName || ns.name}\n${ns.phase}` },
      style: {
        background: nodeBg(ns.phase),
        border: `2px solid ${nodeBorder(ns.phase)}`,
        borderRadius: 8,
        padding: '10px 16px',
        color: '#1e293b',
        fontSize: 12,
        fontWeight: 500,
        width: 160,
      },
    })

    for (const child of ns.children || []) {
      edges.push({
        id: `${id}-${child}`,
        source: id,
        target: child,
        type: 'smoothstep',
        style: { stroke: '#94a3b8', strokeWidth: 2 },
        markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8' },
      })
    }
  }

  flowNodes.value = nodes
  flowEdges.value = edges
}

async function selectNode(ev: NodeMouseEvent) {
  const nodeId = ev.node.id
  selectedNodeId.value = nodeId
  activeTab.value = 'logs'
  try {
    const log = await getWorkflowRunLogs(runId, nodeId)
    logs.value = log
    logContent.value = log.map((l) => l.logs).join('\n')
  } catch {
    logContent.value = '// 日志不可用'
  }
}

async function loadEvents() {
  try {
    events.value = await getWorkflowRunEvents(runId)
  } catch {
    events.value = []
  }
}

async function load() {
  loading.value = true
  try {
    run.value = await getWorkflowRun(runId)
    buildGraph()
  } catch (e: unknown) {
    message.error(e instanceof Error ? e.message : '加载失败')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  load()
  loadEvents()
})
</script>

<template>
  <div class="pl-page" style="min-height: auto; padding-bottom: 0">
    <div class="pl-hero">
      <div class="pl-hero-main">
        <button class="pl-back" @click="router.push(`/argo/workflows/${wfName}`)">
          <ArrowLeft :size="14" /> 返回
        </button>
        <h1 class="pl-hero-title" style="font-size: 16px">
          运行: {{ wfName }}
          <NTag v-if="run" :type="run.status === 'Succeeded' ? 'success' : run.status === 'Running' ? 'info' : 'error'" size="small" style="margin-left: 8px">
            {{ run.status }}
          </NTag>
        </h1>
      </div>
      <div class="pl-hero-extra">
        <NButton quaternary size="tiny" @click="load">
          <template #icon><RefreshCw :size="12" /></template>
          刷新
        </NButton>
      </div>
    </div>

    <!-- DAG 运行状态 -->
    <NCard size="small" class="pl-card" style="margin-bottom: 16px">
      <div style="height: 300px; background: #fafbfc; border-radius: 6px; overflow: hidden">
        <NSpin :show="loading">
          <VueFlow
            v-model:nodes="flowNodes"
            v-model:edges="flowEdges"
            :min-zoom="0.1"
            :max-zoom="2"
            fit-view-on-init
            @node-click="selectNode"
          >
            <Background variant="dots" :gap="20" :size="1" />
            <MiniMap
              position="bottom-right"
              :mask-color="'rgba(0,0,0,0.06)'"
              style="border-radius: 6px; border: 1px solid #e5e7eb"
            />
          </VueFlow>
        </NSpin>
      </div>
    </NCard>

    <!-- 底部面板 -->
    <NCard size="small" class="pl-card">
      <NTabs v-model:value="activeTab" type="line" size="small">
        <NTabPane name="logs" tab="日志">
          <pre v-if="logContent" class="wf-log-viewer">{{ logContent }}</pre>
          <div v-else style="padding: 20px; text-align: center; color: var(--wb-muted)">
            点击画布中的节点查看日志
          </div>
        </NTabPane>
        <NTabPane name="events" tab="事件">
          <div v-if="events.length === 0" style="padding: 20px; text-align: center; color: var(--wb-muted)">
            暂无事件
          </div>
          <div v-else class="wf-events">
            <div v-for="ev in events" :key="ev.id" class="wf-event-item">
              <NTag size="tiny" style="margin-right: 8px; flex-shrink: 0">{{ ev.type }}</NTag>
              <span style="flex: 1">{{ ev.message }}</span>
              <span style="font-size: 11px; color: var(--wb-muted); flex-shrink: 0">{{ new Date(ev.timestamp).toLocaleTimeString() }}</span>
            </div>
          </div>
        </NTabPane>
        <NTabPane name="yaml" tab="YAML">
          <pre class="wf-log-viewer">{{ JSON.stringify(run, null, 2) || '// 加载中' }}</pre>
        </NTabPane>
      </NTabs>
    </NCard>
  </div>
</template>

<style scoped>
.wf-log-viewer {
  max-height: 300px;
  overflow: auto;
  margin: 0;
  padding: 12px;
  background: var(--wb-chip-bg, #f8fafc);
  border-radius: 6px;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}

.wf-events {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 300px;
  overflow-y: auto;
}

.wf-event-item {
  display: flex;
  align-items: center;
  padding: 6px 10px;
  background: var(--wb-chip-bg, #f8fafc);
  border-radius: 6px;
  font-size: 12px;
  gap: 8px;
}
</style>