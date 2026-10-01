<script setup lang="ts">
import { ref, computed } from 'vue'
import { useRouter, useRoute } from 'vue-router'
import { useMessage } from 'naive-ui'
import {
  ArrowLeft,
  Code2,
  Play,
  Save,
  Shield,
  Variable,
} from '@lucide/vue'
import { NButton, NInput, NCard, NModal, NSelect, NSpace, NTabPane, NTabs, NTag } from 'naive-ui'
import { VueFlow, useVueFlow, MarkerType } from '@vue-flow/core'
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import { MiniMap } from '@vue-flow/minimap'
import type { Node, Edge, Connection, NodeMouseEvent } from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@/modules/pipeline/styles/pipeline-theme.css'

import { groupNodes, findNodeMeta } from '@/modules/argo/graph/nodeRegistry'
import type { FlowNodeData, FlowEdgeData } from '@/modules/argo/types/workflow'

// ============================================================
// 编辑器状态
// ============================================================
const router = useRouter()
const route = useRoute()
const message = useMessage()

const workflowName = ref('')
const description = ref('')
const nodes = ref<Node<FlowNodeData>[]>([])
const edges = ref<Edge<FlowEdgeData>[]>([])
const selectedNode = ref<Node<FlowNodeData> | null>(null)
const editorMode = ref<'editor' | 'yaml'>('editor')

const nodeGroups = computed(() => groupNodes())

const { fitView } = useVueFlow({ id: 'editor-flow' })

const defaultEdgeOptions = {
  type: 'smoothstep',
  animated: false,
  style: { stroke: '#94a3b8', strokeWidth: 2 },
  markerEnd: { type: MarkerType.ArrowClosed, color: '#94a3b8' },
}

// ============================================================
// 事件处理
// ============================================================
function onNodeClick(event: NodeMouseEvent) {
  selectedNode.value = event.node as Node<FlowNodeData>
}

function onPaneClick() {
  selectedNode.value = null
}

function onDrop(event: DragEvent) {
  const type = event.dataTransfer?.getData('application/argonodetype')
  if (!type) return

  const meta = findNodeMeta(type)
  if (!meta) return

  const position = { x: event.offsetX - 75, y: event.offsetY - 25 }
  const newNode: Node<FlowNodeData> = {
    id: `node-${Date.now()}`,
    type: 'custom',
    position,
    data: {
      label: meta.label,
      type: meta.type,
      meta,
      config: {
        image: meta.defaultImage || undefined,
        command: meta.defaultCommand || undefined,
      },
    },
  }
  nodes.value = [...nodes.value, newNode]
}

function onDragOver(event: DragEvent) {
  event.preventDefault()
  if (event.dataTransfer) {
    event.dataTransfer.dropEffect = 'move'
  }
}

function handleConnection(connection: Connection) {
  edges.value = [...edges.value, connection as Edge]
}

function goBack() {
  router.push('/argo/workflows')
}

async function handleSave() {
  if (!workflowName.value) {
    message.warning('请输入工作流名称')
    return
  }
  message.success('保存成功（模拟）')
}

function handleRun() {
  message.success('已提交运行（模拟）')
}

function handleAutoLayout() {
  fitView({ padding: 0.2 })
}

// 初始化：默认添加 Start node
const startMeta = findNodeMeta('start')
if (startMeta) {
  nodes.value = [{
    id: 'start',
    type: 'custom',
    position: { x: 300, y: 100 },
    data: {
      label: '开始',
      type: 'start',
      meta: startMeta,
      config: {},
    },
  }]
}
</script>

<template>
  <div class="wf-editor">
    <!-- 顶栏 -->
    <header class="wf-header">
      <div class="wf-header-left">
        <button class="wf-back" @click="goBack">
          <ArrowLeft :size="16" />
        </button>
        <NInput
          v-model:value="workflowName"
          placeholder="工作流名称"
          size="small"
          style="width: 200px"
        />
        <span class="wf-header-hint">{{ description || '未填写描述' }}</span>
      </div>

      <div class="wf-header-right">
        <NButton quaternary size="tiny" @click="editorMode = editorMode === 'editor' ? 'yaml' : 'editor'">
          <template #icon><Code2 :size="13" /></template>
          {{ editorMode === 'editor' ? 'YAML' : '可视化' }}
        </NButton>
        <NButton quaternary size="tiny" @click="handleAutoLayout">
          <template #icon><Variable :size="13" /></template>
          自动布局
        </NButton>
        <NButton secondary size="tiny" @click="handleSave">
          <template #icon><Save :size="13" /></template>
          保存
        </NButton>
        <NButton type="primary" size="tiny" @click="handleRun">
          <template #icon><Play :size="13" /></template>
          运行
        </NButton>
      </div>
    </header>

    <!-- 编辑器主体 -->
    <div class="wf-body" :class="{ 'wf-body--yaml': editorMode === 'yaml' }">
      <!-- 左栏 — 节点面板 -->
      <aside v-if="editorMode === 'editor'" class="wf-palette">
        <div class="wf-palette-header">节点类型</div>
        <div class="wf-palette-body">
          <div v-for="group in nodeGroups" :key="group.group" class="wf-palette-group">
            <div class="wf-palette-group-title">{{ group.label }}</div>
            <div
              v-for="node in group.nodes"
              :key="node.type"
              class="wf-palette-item"
              draggable="true"
              @dragstart="(e: DragEvent) => e.dataTransfer?.setData('application/argonodetype', node.type)"
            >
              <div class="wf-palette-item-icon" :style="{ background: node.bgColor, color: node.color }">
                <component :is="node.icon" :size="16" />
              </div>
              <div class="wf-palette-item-info">
                <div class="wf-palette-item-name">{{ node.label }}</div>
                <div class="wf-palette-item-desc">{{ node.description }}</div>
              </div>
            </div>
          </div>
        </div>
      </aside>

      <!-- 中栏 — 画布 -->
      <main class="wf-canvas">
        <VueFlow
          v-if="editorMode === 'editor'"
          v-model:nodes="nodes"
          v-model:edges="edges"
          :default-edge-options="defaultEdgeOptions"
          fit-view-on-init
          @connect="handleConnection"
          @node-click="onNodeClick"
          @pane-click="onPaneClick"
          @drop="onDrop"
          @dragover="onDragOver"
        >
          <Background variant="dots" :gap="20" :size="1" />

          <template #node-custom="nodeProps">
            <div class="wf-custom-node" :style="{ borderTopColor: (nodeProps.data as any)?.meta?.color || '#3370ff' }">
              <strong>{{ (nodeProps.data as any)?.label }}</strong>
            </div>
          </template>

          <Controls position="bottom-left" :show-interactive="false" />

          <MiniMap
            position="bottom-right"
            :node-color="'#3370ff'"
            :mask-color="'rgba(0,0,0,0.08)'"
            style="border-radius: 8px; border: 1px solid #e5e7eb;"
          />
        </VueFlow>

        <div v-else class="wf-yaml-editor">
          <textarea
            class="wf-yaml-textarea"
            placeholder="在此编辑 Workflow YAML..."
            spellcheck="false"
          />
        </div>
      </main>

      <!-- 右栏 — 配置面板 -->
      <aside v-if="editorMode === 'editor'" class="wf-config">
        <div class="wf-config-header">
          {{ selectedNode?.data?.label ?? '配置' }}
        </div>
        <div v-if="selectedNode" class="wf-config-body">
          <div class="wf-config-section">
            <div class="wf-config-label">节点名称</div>
            <NInput :value="selectedNode?.data?.label ?? ''" size="small" @update:value="(v: string) => { if (selectedNode?.data) selectedNode.data.label = v }" />
          </div>

          <div v-if="selectedNode?.data?.config?.image || selectedNode?.data?.meta?.defaultImage" class="wf-config-section">
            <div class="wf-config-label">镜像</div>
            <NInput :value="selectedNode?.data?.config?.image ?? ''" size="small" placeholder="alpine:3.21" @update:value="(v: string) => { if (selectedNode?.data?.config) selectedNode.data.config.image = v }" />
          </div>

          <div v-if="selectedNode?.data?.config?.command || selectedNode?.data?.meta?.defaultCommand" class="wf-config-section">
            <div class="wf-config-label">命令</div>
            <textarea
              :value="selectedNode?.data?.config?.command ?? ''"
              class="wf-config-textarea"
              rows="4"
              spellcheck="false"
              @input="(e: Event) => { if (selectedNode?.data?.config) selectedNode.data.config.command = (e.target as HTMLTextAreaElement).value }"
            />
          </div>

          <div class="wf-config-section">
            <div class="wf-config-label">重试策略</div>
            <NSelect
              :value="selectedNode?.data?.config?.retry?.limit ?? 0"
              :options="[
                { label: '不重试', value: 0 },
                { label: '重试 1 次', value: 1 },
                { label: '重试 3 次', value: 3 },
                { label: '重试 5 次', value: 5 },
              ]"
              size="small"
              @update:value="(v: number) => { if (selectedNode?.data?.config) { selectedNode.data.config.retry = { limit: v } } }"
            />
          </div>

          <div class="wf-config-section">
            <div class="wf-config-label">超时 (秒)</div>
            <NInput
              :value="String(selectedNode?.data?.config?.timeout ?? 300)"
              size="small"
              @update:value="(v: string) => { if (selectedNode?.data?.config) { selectedNode.data.config.timeout = Number(v) } }"
            />
          </div>
        </div>

        <div v-else class="wf-config-empty">
          <div class="wf-config-empty-icon"><Shield :size="32" /></div>
          <p>点击画布中的节点<br/>即可编辑配置</p>
        </div>
      </aside>
    </div>
  </div>
</template>

<style scoped>
.wf-editor {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 140px);
  margin: -4px -24px -28px;
}

.wf-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 16px;
  background: var(--wb-card-bg, #fff);
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  flex-shrink: 0;
}

.wf-header-left,
.wf-header-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.wf-back {
  display: flex;
  padding: 4px;
  border: none;
  background: transparent;
  color: var(--wb-muted, #6b7280);
  cursor: pointer;
  border-radius: 4px;
}

.wf-back:hover {
  background: var(--wb-chip-bg, #f3f4f6);
  color: var(--wb-text, #111827);
}

.wf-header-hint {
  font-size: 12px;
  color: var(--wb-muted, #6b7280);
}

.wf-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.wf-body--yaml .wf-canvas {
  flex: 1;
}

.wf-palette {
  width: 220px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--wb-card-bg, #fff);
  border-right: 1px solid var(--wb-border, #e5e7eb);
}

.wf-palette-header {
  padding: 10px 12px;
  font-size: 12px;
  font-weight: 600;
  color: var(--wb-text, #111827);
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.wf-palette-body {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.wf-palette-group {
  margin-bottom: 12px;
}

.wf-palette-group-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--wb-muted, #6b7280);
  padding: 4px 8px;
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.wf-palette-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 8px;
  border-radius: 6px;
  cursor: grab;
  transition: background 0.15s;
}

.wf-palette-item:hover {
  background: var(--wb-chip-bg, #f3f4f6);
}

.wf-palette-item:active {
  cursor: grabbing;
}

.wf-palette-item-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 6px;
  flex-shrink: 0;
}

.wf-palette-item-info {
  min-width: 0;
}

.wf-palette-item-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--wb-text, #111827);
}

.wf-palette-item-desc {
  font-size: 11px;
  color: var(--wb-muted, #6b7280);
  white-space: nowrap;
  text-overflow: ellipsis;
  overflow: hidden;
}

.wf-canvas {
  flex: 1;
  position: relative;
  background: #fafbfc;
}

.wf-yaml-textarea {
  width: 100%;
  height: 100%;
  padding: 20px;
  border: none;
  background: #1e1e1e;
  color: #d4d4d4;
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 13px;
  line-height: 1.6;
  resize: none;
  outline: none;
  tab-size: 2;
}

.wf-config {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: var(--wb-card-bg, #fff);
  border-left: 1px solid var(--wb-border, #e5e7eb);
}

.wf-config-header {
  padding: 10px 12px;
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-text, #111827);
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
}

.wf-config-body {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.wf-config-section {
  margin-bottom: 14px;
}

.wf-config-label {
  font-size: 12px;
  font-weight: 500;
  color: var(--wb-text, #111827);
  margin-bottom: 4px;
}

.wf-config-textarea {
  width: 100%;
  padding: 6px 8px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 6px;
  background: var(--wb-chip-bg, #f8fafc);
  font-family: 'JetBrains Mono', 'Fira Code', monospace;
  font-size: 12px;
  line-height: 1.5;
  resize: vertical;
  outline: none;
}

.wf-config-textarea:focus {
  border-color: var(--wb-primary, #3370ff);
}

.wf-config-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  flex: 1;
  color: var(--wb-muted, #6b7280);
  font-size: 13px;
  text-align: center;
  line-height: 1.6;
}

.wf-config-empty-icon {
  margin-bottom: 8px;
  opacity: 0.4;
}

.wf-custom-node {
  min-width: 140px;
  padding: 10px 16px;
  border-radius: 8px;
  border-top: 4px solid #3370ff;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.1);
  font-size: 12px;
  text-align: center;
}
</style>