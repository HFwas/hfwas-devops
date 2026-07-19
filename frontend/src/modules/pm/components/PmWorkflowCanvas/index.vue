<script setup lang="ts">
import { Background } from '@vue-flow/background'
import { Controls } from '@vue-flow/controls'
import {
  Handle,
  MarkerType,
  Position,
  VueFlow,
  type Connection,
  type Edge,
  type EdgeChange,
  type EdgeMouseEvent,
  type Node,
  type NodeDragEvent,
  type NodeMouseEvent,
} from '@vue-flow/core'
import '@vue-flow/core/dist/style.css'
import '@vue-flow/core/dist/theme-default.css'
import '@vue-flow/controls/dist/style.css'
import {
  emptyTransitionConditions,
  isTransitionConditionsEmpty,
  statusTagColor,
  type StatusDefinition,
  type Transition,
} from '@/modules/pm/types'

const props = defineProps<{
  statuses: StatusDefinition[]
}>()

const emit = defineEmits<{
  'update:statuses': [StatusDefinition[]]
  'edit-transition': [payload: { fromCode: string; transitionId: string }]
  'edit-status': [statusCode: string]
}>()

type StatusNodeData = {
  status: StatusDefinition
  color: string
  index: number
}

function defaultPosition(status: StatusDefinition, index: number): { x: number; y: number } {
  if (status.layoutX != null && status.layoutY != null) {
    return { x: Number(status.layoutX), y: Number(status.layoutY) }
  }
  return {
    x: 80 + (index % 4) * 220,
    y: 80 + Math.floor(index / 4) * 140 + (index % 2) * 20,
  }
}

function edgeMeta(t: Transition) {
  const cond = !isTransitionConditionsEmpty(t.conditions)
  const validators = (t.validators ?? []).some((v) => (v.fieldKeys?.length ?? 0) > 0)
  const actions = (t.postFunctions?.length ?? 0) > 0
  return { accent: cond || validators || actions }
}

function buildNodes(list: StatusDefinition[]): Node<StatusNodeData>[] {
  return list.map((status, index) => ({
    id: status.statusCode,
    type: 'status',
    position: defaultPosition(status, index),
    data: {
      status,
      color: statusTagColor(status, index),
      index,
    },
    draggable: true,
  }))
}

function buildEdges(list: StatusDefinition[]): Edge[] {
  const edges: Edge[] = []
  for (const from of list) {
    for (const t of from.transitions ?? []) {
      if (!t?.id || !t.toStatus) continue
      const meta = edgeMeta(t)
      edges.push({
        id: t.id,
        source: from.statusCode,
        target: t.toStatus,
        label: t.name || `→ ${t.toStatus}`,
        type: 'smoothstep',
        animated: meta.accent,
        markerEnd: MarkerType.ArrowClosed,
        style: meta.accent
          ? { stroke: 'var(--n-primary-color)', strokeWidth: 2 }
          : { strokeWidth: 1.5 },
        labelStyle: { fontSize: 11, fill: 'var(--n-text-color)' },
        labelBgStyle: { fill: 'var(--n-color)', fillOpacity: 0.92 },
        labelBgPadding: [4, 6] as [number, number],
        labelBgBorderRadius: 4,
        data: {
          fromCode: from.statusCode,
          transitionId: t.id,
        },
        selectable: true,
        deletable: true,
      })
    }
  }
  return edges
}

const nodes = ref<Node<StatusNodeData>[]>([])
const edges = ref<Edge[]>([])
let syncing = false

function syncFromProps() {
  syncing = true
  nodes.value = buildNodes(props.statuses)
  edges.value = buildEdges(props.statuses)
  nextTick(() => {
    syncing = false
  })
}

watch(
  () => props.statuses,
  () => syncFromProps(),
  { deep: true, immediate: true },
)

function patchStatuses(mutator: (list: StatusDefinition[]) => void) {
  const next = props.statuses.map((s) => ({
    ...s,
    transitions: (s.transitions ?? []).map((t) => ({
      ...t,
      conditions: t.conditions
        ? {
            logic: t.conditions.logic,
            conditions: [...(t.conditions.conditions ?? [])],
            groups: [...(t.conditions.groups ?? [])],
          }
        : emptyTransitionConditions(),
      validators: (t.validators ?? []).map((v) => ({ ...v, fieldKeys: [...(v.fieldKeys ?? [])] })),
      postFunctions: [...(t.postFunctions ?? [])],
    })),
  }))
  mutator(next)
  emit('update:statuses', next)
}

function onConnect(connection: Connection) {
  const source = connection.source
  const target = connection.target
  if (!source || !target || source === target) return
  const to = props.statuses.find((s) => s.statusCode === target)
  if (!to) return
  patchStatuses((list) => {
    const from = list.find((s) => s.statusCode === source)
    if (!from) return
    if (!from.transitions) from.transitions = []
    from.transitions.push({
      id: crypto.randomUUID(),
      name: `→ ${to.statusName}`,
      toStatus: target,
      conditions: emptyTransitionConditions(),
      validators: [],
      postFunctions: [],
    })
  })
}

function onEdgesChange(changes: EdgeChange[]) {
  if (syncing) return
  const removed = changes.filter((c) => c.type === 'remove')
  if (!removed.length) return
  const ids = new Set(removed.map((c) => c.id))
  patchStatuses((list) => {
    for (const status of list) {
      status.transitions = (status.transitions ?? []).filter((t) => !ids.has(t.id))
    }
  })
}

function onNodeDragStop(event: NodeDragEvent) {
  const code = event.node.id
  const { x, y } = event.node.position
  patchStatuses((list) => {
    const status = list.find((s) => s.statusCode === code)
    if (!status) return
    status.layoutX = Math.round(x)
    status.layoutY = Math.round(y)
  })
}

function onEdgeClick(event: EdgeMouseEvent) {
  const fromCode = String(event.edge.data?.fromCode ?? event.edge.source)
  const transitionId = String(event.edge.data?.transitionId ?? event.edge.id)
  emit('edit-transition', { fromCode, transitionId })
}

function onNodeDoubleClick(event: NodeMouseEvent) {
  emit('edit-status', event.node.id)
}
</script>

<template>
  <div class="pm-workflow-canvas">
    <VueFlow
      v-model:nodes="nodes"
      v-model:edges="edges"
      :default-edge-options="{ type: 'smoothstep' }"
      :min-zoom="0.4"
      :max-zoom="1.6"
      fit-view-on-init
      delete-key-code="Backspace"
      class="canvas"
      @connect="onConnect"
      @edges-change="onEdgesChange"
      @node-drag-stop="onNodeDragStop"
      @edge-click="onEdgeClick"
      @node-double-click="onNodeDoubleClick"
    >
      <Background :gap="16" />
      <Controls position="bottom-left" />

      <template #node-status="{ data }">
        <div
          class="status-node"
          :class="{
            initial: data.status.isInitial === 1,
            final: data.status.isFinal === 1,
          }"
          :style="{ borderColor: data.color }"
        >
          <Handle type="target" :position="Position.Left" class="handle" />
          <div class="status-node-body">
            <span class="status-dot" :style="{ background: data.color }" />
            <div class="status-text">
              <div class="status-name">{{ data.status.statusName }}</div>
              <div class="status-code">{{ data.status.statusCode }}</div>
            </div>
          </div>
          <div v-if="data.status.isInitial === 1 || data.status.isFinal === 1" class="status-flags">
            <span v-if="data.status.isInitial === 1" class="flag">初始</span>
            <span v-if="data.status.isFinal === 1" class="flag">终态</span>
          </div>
          <Handle type="source" :position="Position.Right" class="handle" />
        </div>
      </template>
    </VueFlow>
  </div>
</template>

<style scoped>
.pm-workflow-canvas {
  height: 520px;
  border: 1px solid var(--n-border-color);
  border-radius: var(--n-border-radius);
  overflow: hidden;
  background: var(--n-color-embedded, var(--n-action-color));
}

.canvas {
  width: 100%;
  height: 100%;
}

.status-node {
  min-width: 140px;
  padding: 10px 12px;
  border-radius: 8px;
  border: 2px solid;
  background: var(--n-color);
  box-shadow: 0 1px 2px color-mix(in srgb, var(--n-text-color) 8%, transparent);
}

.status-node-body {
  display: flex;
  align-items: center;
  gap: 8px;
}

.status-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  flex-shrink: 0;
}

.status-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--n-text-color);
  line-height: 1.2;
}

.status-code {
  font-size: 11px;
  color: var(--n-text-color-3);
  margin-top: 2px;
}

.status-flags {
  display: flex;
  gap: 4px;
  margin-top: 6px;
}

.flag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 999px;
  background: var(--n-color-embedded, var(--n-action-color));
  color: var(--n-text-color-2);
}

.handle {
  width: 8px;
  height: 8px;
  background: var(--n-primary-color);
  border: 2px solid var(--n-color);
}
</style>
