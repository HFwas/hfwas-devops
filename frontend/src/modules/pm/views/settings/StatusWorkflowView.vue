<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmStatusApi } from '@/modules/pm/api'
import PmTransitionRuleDrawer from '@/modules/pm/components/PmTransitionRuleDrawer/index.vue'
import PmWorkflowCanvas from '@/modules/pm/components/PmWorkflowCanvas/index.vue'
import { useTransitionPostFunctionMeta } from '@/modules/pm/composables/useTransitionPostFunctionMeta'
import { useUserOptions } from '@/modules/pm/composables/useUserOptions'
import { useProjectModules } from '@/modules/pm/composables/useProjectModules'
import { invalidateStatusOptionsCache } from '@/modules/pm/composables/useStatusOptions'
import {
  ANY_STATUS_CODE,
  emptyTransitionConditions,
  isTransitionConditionsEmpty,
  statusTagColor,
  type StatusDefinition,
  type Transition,
  type TransitionConditionSpec,
  type TransitionPostFunction,
  type TransitionValidator,
} from '@/modules/pm/types'
import { useProjectIssueTypes } from '@/modules/pm/composables/useIssueTypes'
import { routeId } from '@/modules/pm/utils/id'
import { summarizeTransitionAction } from '@/modules/pm/utils/transitionActionSummary'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const projectId = computed(() => routeId(route.params.projectId))
const typeCode = ref(String(route.params.typeCode || 'task'))
const viewMode = ref<'graph' | 'path' | 'rules'>('graph')
const loading = ref(false)
const saving = ref(false)
const customized = ref(false)
const statuses = ref<StatusDefinition[]>([])
const showStatusModal = ref(false)
const editingStatus = ref<StatusDefinition | null>(null)
const statusForm = ref({ statusCode: '', statusName: '', isInitial: false, isFinal: false })
const ruleDrawer = ref({
  show: false,
  transitionId: '',
  transitionName: '',
  fromCode: '',
  fromName: '',
  toCode: '',
  toName: '',
  conditions: emptyTransitionConditions() as TransitionConditionSpec,
  validators: [] as TransitionValidator[],
  postFunctions: [] as TransitionPostFunction[],
})
const { types: projectTypes } = useProjectIssueTypes(projectId)
const typeTabs = computed(() =>
  projectTypes.value.map((t) => ({ label: t.name, value: t.code })),
)

const { fieldLabelMap, fieldMetaMap, load: loadMeta } = useTransitionPostFunctionMeta(projectId, typeCode)
const { labelMap: userLabelMap } = useUserOptions()
const { labelMap: moduleLabelMap } = useProjectModules(projectId)

const summaryCtx = computed(() => ({
  fieldLabelMap: fieldLabelMap.value,
  fieldMetaMap: fieldMetaMap.value,
  userLabelMap: userLabelMap.value,
  moduleLabelMap: moduleLabelMap.value,
}))

const regularStatuses = computed(() =>
  statuses.value.filter((s) => s.statusCode !== ANY_STATUS_CODE),
)

const matrixRows = computed(() => {
  const any = statuses.value.find((s) => s.statusCode === ANY_STATUS_CODE)
  return any ? [...regularStatuses.value, any] : regularStatuses.value
})

const configuredTransitions = computed(() => {
  const items: Array<{
    id: string
    name: string
    fromCode: string
    fromName: string
    toCode: string
    toName: string
    conditions: TransitionConditionSpec
    validators: TransitionValidator[]
    actions: TransitionPostFunction[]
  }> = []
  for (const row of matrixRows.value) {
    for (const t of row.transitions ?? []) {
      const to = findStatus(t.toStatus)
      items.push({
        id: t.id,
        name: t.name || `→ ${to?.statusName ?? t.toStatus}`,
        fromCode: row.statusCode,
        fromName: row.statusName,
        toCode: t.toStatus,
        toName: to?.statusName ?? t.toStatus,
        conditions: t.conditions ?? emptyTransitionConditions(),
        validators: [...(t.validators ?? [])],
        actions: [...(t.postFunctions ?? [])],
      })
    }
  }
  return items
})

function cloneTransition(t: Transition): Transition {
  return {
    id: t.id,
    name: t.name,
    toStatus: t.toStatus,
    conditions: {
      logic: t.conditions?.logic || 'AND',
      conditions: (t.conditions?.conditions ?? []).map((c) => ({ ...c })),
      groups: (t.conditions?.groups ?? []).map((g) => ({
        ...g,
        conditions: [...(g.conditions ?? [])],
        groups: [...(g.groups ?? [])],
      })),
    },
    validators: (t.validators ?? []).map((v) => ({
      ...v,
      fieldKeys: [...(v.fieldKeys ?? [])],
    })),
    postFunctions: [...(t.postFunctions ?? [])],
  }
}

function cloneStatuses(list: StatusDefinition[]) {
  return list.map((s) => ({
    ...s,
    layoutX: s.layoutX ?? null,
    layoutY: s.layoutY ?? null,
    transitions: (s.transitions ?? []).map(cloneTransition),
  }))
}

function findTransition(status: StatusDefinition, toCode: string): Transition | undefined {
  return status.transitions?.find((t) => t.toStatus === toCode)
}

function findTransitionById(status: StatusDefinition, id: string): Transition | undefined {
  return status.transitions?.find((t) => t.id === id)
}

function ensureTransition(status: StatusDefinition, toCode: string, toName: string): Transition {
  if (!status.transitions) status.transitions = []
  let t = findTransition(status, toCode)
  if (!t) {
    t = {
      id: crypto.randomUUID(),
      name: `→ ${toName}`,
      toStatus: toCode,
      conditions: emptyTransitionConditions(),
      validators: [],
      postFunctions: [],
    }
    status.transitions.push(t)
  }
  return t
}

function addParallelTransition(fromCode: string, fromName: string, toCode: string, toName: string) {
  const from = findStatus(fromCode)
  if (!from) return
  if (!from.transitions) from.transitions = []
  const t: Transition = {
    id: crypto.randomUUID(),
    name: `→ ${toName}`,
    toStatus: toCode,
    conditions: emptyTransitionConditions(),
    validators: [],
    postFunctions: [],
  }
  from.transitions.push(t)
  openRuleDrawerById(fromCode, fromName, toCode, toName, t.id)
}

function transitionsToTarget(fromCode: string, toCode: string): Transition[] {
  const from = findStatus(fromCode)
  return (from?.transitions ?? []).filter((t) => t.toStatus === toCode)
}

function postFunctionCount(fromCode: string, toCode: string) {
  return transitionsToTarget(fromCode, toCode).reduce((n, t) => n + (t.postFunctions?.length ?? 0), 0)
}

function validatorFieldCount(fromCode: string, toCode: string) {
  return transitionsToTarget(fromCode, toCode).reduce((n, t) => {
    const required = (t.validators ?? []).find((v) => v.type === 'REQUIRED_FIELDS')
    return n + (required?.fieldKeys?.length ?? 0)
  }, 0)
}

function conditionCount(fromCode: string, toCode: string) {
  return transitionsToTarget(fromCode, toCode).reduce((n, t) => {
    if (isTransitionConditionsEmpty(t.conditions)) return n
    return n + (t.conditions?.conditions?.length ?? 0) + (t.conditions?.groups?.length ?? 0)
  }, 0)
}

function transitionCellLabel(fromCode: string, toCode: string, toName: string) {
  const edges = transitionsToTarget(fromCode, toCode)
  if (!edges.length) return '点击启用'
  if (edges.length > 1) return `${edges.length} 条流转`
  const t = edges[0]
  const vCount = validatorFieldCount(fromCode, toCode)
  const aCount = postFunctionCount(fromCode, toCode)
  const cCount = conditionCount(fromCode, toCode)
  if (t.name && t.name !== `→ ${toName}`) return t.name
  if (vCount || aCount || cCount) {
    const parts: string[] = []
    if (cCount) parts.push(`${cCount} 条件`)
    if (vCount) parts.push(`${vCount} 校验`)
    if (aCount) parts.push(`${aCount} 动作`)
    return parts.join(' · ')
  }
  return t.name || '配置规则'
}

function summarizeValidators(validators: TransitionValidator[]) {
  const required = validators.find((v) => v.type === 'REQUIRED_FIELDS')
  const keys = required?.fieldKeys ?? []
  if (!keys.length) return []
  return keys.map((key) => `必填：${fieldLabelMap.value[key] ?? key}`)
}

function summarizeConditions(spec?: TransitionConditionSpec) {
  if (isTransitionConditionsEmpty(spec)) return []
  const labels: string[] = []
  for (const c of spec?.conditions ?? []) {
    const field = c.field?.replace(/^custom\./, '') ?? '?'
    const fieldLabel = fieldLabelMap.value[field] ?? field
    if (c.operator === 'IS_NULL') labels.push(`${fieldLabel} 为空`)
    else if (c.operator === 'IS_NOT_NULL') labels.push(`${fieldLabel} 非空`)
    else if (c.value === '__current_user__') labels.push(`${fieldLabel} = 当前用户`)
    else labels.push(`${fieldLabel} ${c.operator} ${c.value ?? ''}`)
  }
  const groupCount = spec?.groups?.length ?? 0
  if (groupCount) labels.push(`${groupCount} 组条件`)
  return labels
}

function openRuleDrawer(fromCode: string, fromName: string, toCode: string, toName: string) {
  if (!isEnabled(fromCode, toCode)) {
    setTransition(fromCode, toCode, true, toName)
  }
  const from = findStatus(fromCode)
  if (!from) return
  const t = ensureTransition(from, toCode, toName)
  openRuleDrawerById(fromCode, fromName, toCode, toName, t.id)
}

function openRuleDrawerById(
  fromCode: string,
  fromName: string,
  toCode: string,
  toName: string,
  transitionId: string,
) {
  const from = findStatus(fromCode)
  if (!from) return
  const t = findTransitionById(from, transitionId)
  if (!t) return
  ruleDrawer.value = {
    show: true,
    transitionId: t.id,
    transitionName: t.name,
    fromCode,
    fromName,
    toCode,
    toName,
    conditions: {
      logic: t.conditions?.logic || 'AND',
      conditions: (t.conditions?.conditions ?? []).map((c) => ({ ...c })),
      groups: (t.conditions?.groups ?? []).map((g) => ({
        ...g,
        conditions: [...(g.conditions ?? [])],
        groups: [...(g.groups ?? [])],
      })),
    },
    validators: (t.validators ?? []).map((v) => ({
      ...v,
      fieldKeys: [...(v.fieldKeys ?? [])],
    })),
    postFunctions: [...(t.postFunctions ?? [])],
  }
}

function saveRuleDrawer(payload: {
  name: string
  conditions: TransitionConditionSpec
  validators: TransitionValidator[]
  postFunctions: TransitionPostFunction[]
}) {
  const from = findStatus(ruleDrawer.value.fromCode)
  if (!from) return
  let t = findTransitionById(from, ruleDrawer.value.transitionId)
  if (!t) {
    t = ensureTransition(from, ruleDrawer.value.toCode, ruleDrawer.value.toName)
  }
  t.name = payload.name
  t.conditions = payload.conditions
  t.validators = payload.validators
  t.postFunctions = payload.postFunctions
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const data = await pmStatusApi.get(projectId.value, typeCode.value)
    customized.value = !!data.customized
    statuses.value = cloneStatuses(data.statuses ?? [])
    await loadMeta(true)
  } finally {
    loading.value = false
  }
}

function findStatus(code: string) {
  return statuses.value.find((s) => s.statusCode === code)
}

function isEnabled(fromCode: string, toCode: string) {
  if (fromCode === toCode) return false
  const from = findStatus(fromCode)
  return from?.transitions?.some((t) => t.toStatus === toCode) ?? false
}

function setTransition(fromCode: string, toCode: string, enabled: boolean, toName?: string) {
  if (fromCode === toCode) return
  const from = findStatus(fromCode)
  if (!from) return
  if (enabled) {
    const name = toName ?? findStatus(toCode)?.statusName ?? toCode
    ensureTransition(from, toCode, name)
  } else {
    from.transitions = (from.transitions ?? []).filter((t) => t.toStatus !== toCode)
  }
}

function onCellClick(fromCode: string, fromName: string, toCode: string, toName: string) {
  if (fromCode === toCode) return
  openRuleDrawer(fromCode, fromName, toCode, toName)
}

function onCanvasEditTransition(payload: { fromCode: string; transitionId: string }) {
  const from = findStatus(payload.fromCode)
  if (!from) return
  const t = findTransitionById(from, payload.transitionId)
  if (!t) return
  const to = findStatus(t.toStatus)
  openRuleDrawerById(
    from.statusCode,
    from.statusName,
    t.toStatus,
    to?.statusName ?? t.toStatus,
    t.id,
  )
}

function onCanvasEditStatus(statusCode: string) {
  const status = findStatus(statusCode)
  if (!status || status.statusCode === ANY_STATUS_CODE) return
  openEditStatus(status)
}

function onStatusesFromCanvas(list: StatusDefinition[]) {
  statuses.value = list
}

function openAddStatus() {
  editingStatus.value = null
  statusForm.value = { statusCode: '', statusName: '', isInitial: false, isFinal: false }
  showStatusModal.value = true
}

function openEditStatus(status: StatusDefinition) {
  editingStatus.value = status
  statusForm.value = {
    statusCode: status.statusCode,
    statusName: status.statusName,
    isInitial: status.isInitial === 1,
    isFinal: status.isFinal === 1,
  }
  showStatusModal.value = true
}

function saveStatusForm() {
  const code = statusForm.value.statusCode.trim()
  const name = statusForm.value.statusName.trim()
  if (!code || !name) {
    message.warning('请填写状态编码和名称')
    return
  }
  if (code === ANY_STATUS_CODE) {
    message.warning('不能使用保留编码')
    return
  }
  if (editingStatus.value) {
    if (editingStatus.value.statusCode !== code && findStatus(code)) {
      message.warning('状态编码已存在')
      return
    }
    editingStatus.value.statusCode = code
    editingStatus.value.statusName = name
    editingStatus.value.isInitial = statusForm.value.isInitial ? 1 : 0
    editingStatus.value.isFinal = statusForm.value.isFinal ? 1 : 0
  } else {
    if (findStatus(code)) {
      message.warning('状态编码已存在')
      return
    }
    const row: StatusDefinition = {
      statusCode: code,
      statusName: name,
      sortOrder: regularStatuses.value.length + 1,
      isInitial: statusForm.value.isInitial ? 1 : 0,
      isFinal: statusForm.value.isFinal ? 1 : 0,
      transitions: [],
    }
    const anyIndex = statuses.value.findIndex((s) => s.statusCode === ANY_STATUS_CODE)
    if (anyIndex >= 0) statuses.value.splice(anyIndex, 0, row)
    else statuses.value.push(row)
  }
  if (statusForm.value.isInitial) {
    for (const s of regularStatuses.value) {
      if (s.statusCode !== code) s.isInitial = 0
    }
  }
  showStatusModal.value = false
}

function removeStatus(status: StatusDefinition) {
  statuses.value = statuses.value.filter((s) => s !== status)
  for (const s of statuses.value) {
    s.transitions = (s.transitions ?? []).filter((t) => t.toStatus !== status.statusCode)
  }
}

function serializeTransitions(list: Transition[] | undefined): Transition[] {
  return (list ?? []).map((t) => ({
    id: t.id,
    name: t.name,
    toStatus: t.toStatus,
    conditions: isTransitionConditionsEmpty(t.conditions)
      ? emptyTransitionConditions()
      : {
          logic: t.conditions?.logic || 'AND',
          conditions: (t.conditions?.conditions ?? []).map((c) => ({ ...c })),
          groups: (t.conditions?.groups ?? []).map((g) => ({
            ...g,
            conditions: [...(g.conditions ?? [])],
            groups: [...(g.groups ?? [])],
          })),
        },
    validators: (t.validators ?? []).map((v) => ({
      type: v.type,
      fieldKeys: [...(v.fieldKeys ?? [])],
    })),
    postFunctions: [...(t.postFunctions ?? [])],
  }))
}

async function persist() {
  if (!projectId.value) {
    message.warning('项目 ID 无效')
    return
  }
  saving.value = true
  try {
    const regular = statuses.value.filter((s) => s.statusCode !== ANY_STATUS_CODE)
    const any = statuses.value.find((s) => s.statusCode === ANY_STATUS_CODE)
    const payload: StatusDefinition[] = regular.map((s, i) => ({
      statusCode: s.statusCode,
      statusName: s.statusName,
      sortOrder: i + 1,
      isInitial: s.isInitial ?? 0,
      isFinal: s.isFinal ?? 0,
      layoutX: s.layoutX ?? null,
      layoutY: s.layoutY ?? null,
      transitions: serializeTransitions(s.transitions),
    }))
    if (any) {
      payload.push({
        statusCode: ANY_STATUS_CODE,
        statusName: any.statusName,
        sortOrder: 999,
        isInitial: 0,
        isFinal: 0,
        layoutX: any.layoutX ?? null,
        layoutY: any.layoutY ?? null,
        transitions: serializeTransitions(any.transitions),
      })
    }
    await pmStatusApi.save(projectId.value, typeCode.value, payload)
    invalidateStatusOptionsCache(projectId.value, typeCode.value)
    message.success('状态流转已保存')
    await load()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
  } finally {
    saving.value = false
  }
}

async function resetToDefault() {
  saving.value = true
  try {
    await pmStatusApi.reset(projectId.value, typeCode.value)
    invalidateStatusOptionsCache(projectId.value, typeCode.value)
    message.success('已恢复为系统默认配置')
    await load()
  } finally {
    saving.value = false
  }
}

watch(typeCode, (code) => {
  if (route.params.typeCode !== code) {
    router.replace(`/pm/projects/${projectId.value}/settings/workflow/${code}`)
  }
  load()
})
watch(() => route.params.typeCode, (v) => {
  if (typeof v === 'string' && v !== typeCode.value) typeCode.value = v
})
onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header title="状态流转" subtitle="图编辑或矩阵配置状态路径与流转规则（需点击「保存配置」持久化）">
      <template #extra>
        <n-space>
          <n-button v-if="customized" :loading="saving" @click="resetToDefault">恢复默认</n-button>
          <n-button type="primary" :loading="saving" @click="persist">保存配置</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-tabs v-model:value="typeCode" type="segment">
      <n-tab-pane v-for="tab in typeTabs" :key="tab.value" :name="tab.value" :tab="tab.label" />
    </n-tabs>

    <n-alert v-if="customized" type="info" :bordered="false">
      当前项目已启用自定义状态流转；点击「恢复默认」可回退到系统模板。规则抽屉内的修改需再点「保存配置」才会写入服务器。
    </n-alert>

    <n-spin :show="loading">
      <n-card title="状态列表" size="small">
        <template #header-extra>
          <n-button size="small" type="primary" @click="openAddStatus">添加状态</n-button>
        </template>
        <n-space vertical>
          <div
            v-for="(status, index) in regularStatuses"
            :key="status.statusCode"
            class="status-item"
          >
            <n-space align="center">
              <n-tag :bordered="false" :color="{ color: statusTagColor(status, index), textColor: '#fff' }">
                {{ status.statusName }}
              </n-tag>
              <n-text depth="3">{{ status.statusCode }}</n-text>
              <n-tag v-if="status.isInitial === 1" size="small" :bordered="false">初始状态</n-tag>
              <n-tag v-if="status.isFinal === 1" size="small" type="warning" :bordered="false">终态</n-tag>
            </n-space>
            <n-space>
              <n-button size="tiny" quaternary @click="openEditStatus(status)">编辑</n-button>
              <n-popconfirm @positive-click="removeStatus(status)">
                <template #trigger>
                  <n-button size="tiny" quaternary type="error">删除</n-button>
                </template>
                确定删除状态「{{ status.statusName }}」吗？
              </n-popconfirm>
            </n-space>
          </div>
        </n-space>
      </n-card>

      <n-card size="small" style="margin-top: 16px">
        <n-tabs v-model:value="viewMode" type="line">
          <n-tab-pane name="graph" tab="图编辑">
            <n-text depth="3" style="display: block; margin: 12px 0">
              从节点右侧手柄拖到目标节点以新增流转；点击连线配置规则；选中连线后按 Backspace 删除；双击节点编辑状态。修改需点「保存配置」才持久化。
            </n-text>
            <PmWorkflowCanvas
              :statuses="statuses"
              @update:statuses="onStatusesFromCanvas"
              @edit-transition="onCanvasEditTransition"
              @edit-status="onCanvasEditStatus"
            />
          </n-tab-pane>

          <n-tab-pane name="path" tab="矩阵">
            <n-text depth="3" style="display: block; margin: 12px 0">
              勾选启用流转；点击已启用单元格可配置名称、可见条件与自动化动作。同一路径可配置多条流转（规则列表中添加）。
            </n-text>
            <div class="matrix-wrap">
              <table class="matrix-table">
                <thead>
                  <tr>
                    <th class="matrix-corner">
                      <span class="corner-from">开始</span>
                      <span class="corner-to">目标</span>
                    </th>
                    <th v-for="(col, colIndex) in regularStatuses" :key="col.statusCode" class="matrix-head">
                      <n-tag
                        size="small"
                        :bordered="false"
                        :color="{ color: statusTagColor(col, colIndex), textColor: '#fff' }"
                      >
                        {{ col.statusName }}
                      </n-tag>
                    </th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(row, rowIndex) in matrixRows" :key="row.statusCode">
                    <th class="matrix-row-head">
                      <n-space align="center" :size="6">
                        <n-tag
                          v-if="row.statusCode !== ANY_STATUS_CODE"
                          size="small"
                          :bordered="false"
                          :color="{ color: statusTagColor(row, rowIndex), textColor: '#fff' }"
                        >
                          {{ row.statusName }}
                        </n-tag>
                        <n-tag v-else size="small" :bordered="false">{{ row.statusName }}</n-tag>
                      </n-space>
                    </th>
                    <td
                      v-for="col in regularStatuses"
                      :key="`${row.statusCode}-${col.statusCode}`"
                      class="matrix-cell"
                      :class="{ enabled: isEnabled(row.statusCode, col.statusCode), same: row.statusCode === col.statusCode }"
                    >
                      <span v-if="row.statusCode === col.statusCode" class="matrix-dash">—</span>
                      <div v-else class="cell-body">
                        <n-checkbox
                          :checked="isEnabled(row.statusCode, col.statusCode)"
                          @update:checked="(checked) => setTransition(row.statusCode, col.statusCode, checked, col.statusName)"
                          @click.stop
                        />
                        <button
                          type="button"
                          class="cell-config"
                          :class="{ active: isEnabled(row.statusCode, col.statusCode) }"
                          @click="onCellClick(row.statusCode, row.statusName, col.statusCode, col.statusName)"
                        >
                          {{ transitionCellLabel(row.statusCode, col.statusCode, col.statusName) }}
                        </button>
                      </div>
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </n-tab-pane>

          <n-tab-pane name="rules" :tab="`流转规则 (${configuredTransitions.length})`">
            <n-empty v-if="!configuredTransitions.length" description="暂无已启用的流转，请先在「流转路径」中勾选" />
            <n-list v-else bordered style="margin-top: 12px">
              <n-list-item v-for="item in configuredTransitions" :key="item.id">
                <n-space vertical style="width: 100%" :size="8">
                  <n-space align="center" justify="space-between" style="width: 100%">
                    <n-space vertical :size="4">
                      <n-text strong>{{ item.name }}</n-text>
                      <n-space align="center" :size="8">
                        <n-tag size="small">{{ item.fromName }}</n-tag>
                        <n-text depth="3">→</n-text>
                        <n-tag size="small" type="success">{{ item.toName }}</n-tag>
                      </n-space>
                    </n-space>
                    <n-space :size="6">
                      <n-button
                        size="small"
                        quaternary
                        @click="addParallelTransition(item.fromCode, item.fromName, item.toCode, item.toName)"
                      >
                        同路径再加一条
                      </n-button>
                      <n-button
                        size="small"
                        type="primary"
                        quaternary
                        @click="openRuleDrawerById(item.fromCode, item.fromName, item.toCode, item.toName, item.id)"
                      >
                        编辑规则
                      </n-button>
                    </n-space>
                  </n-space>
                  <n-space
                    v-if="summarizeConditions(item.conditions).length || item.validators.length || item.actions.length"
                    :size="6"
                    wrap
                  >
                    <n-tag
                      v-for="(label, idx) in summarizeConditions(item.conditions)"
                      :key="`c-${idx}`"
                      size="small"
                      :bordered="false"
                      type="success"
                    >
                      {{ label }}
                    </n-tag>
                    <n-tag
                      v-for="(label, idx) in summarizeValidators(item.validators)"
                      :key="`v-${idx}`"
                      size="small"
                      :bordered="false"
                      type="warning"
                    >
                      {{ label }}
                    </n-tag>
                    <n-tag
                      v-for="(action, idx) in item.actions"
                      :key="`a-${idx}`"
                      size="small"
                      :bordered="false"
                      type="info"
                    >
                      {{ summarizeTransitionAction(action, summaryCtx) }}
                    </n-tag>
                  </n-space>
                  <n-text v-else depth="3">已启用流转，尚未配置条件、校验或自动化动作</n-text>
                </n-space>
              </n-list-item>
            </n-list>
          </n-tab-pane>
        </n-tabs>
      </n-card>
    </n-spin>

    <n-modal v-model:show="showStatusModal" preset="card" :title="editingStatus ? '编辑状态' : '添加状态'" style="width: 420px">
      <n-form label-placement="top">
        <n-form-item label="状态编码" required>
          <n-input v-model:value="statusForm.statusCode" placeholder="如 in_review" :disabled="!!editingStatus" />
        </n-form-item>
        <n-form-item label="状态名称" required>
          <n-input v-model:value="statusForm.statusName" placeholder="如 评审中" />
        </n-form-item>
        <n-form-item label="初始状态">
          <n-switch v-model:value="statusForm.isInitial" />
        </n-form-item>
        <n-form-item label="终态">
          <n-switch v-model:value="statusForm.isFinal" />
        </n-form-item>
      </n-form>
      <template #footer>
        <n-space justify="end">
          <n-button @click="showStatusModal = false">取消</n-button>
          <n-button type="primary" @click="saveStatusForm">确定</n-button>
        </n-space>
      </template>
    </n-modal>

    <PmTransitionRuleDrawer
      v-model:show="ruleDrawer.show"
      :project-id="projectId"
      :type-code="typeCode"
      :from-status-code="ruleDrawer.fromCode"
      :from-status-name="ruleDrawer.fromName"
      :to-status-code="ruleDrawer.toCode"
      :to-status-name="ruleDrawer.toName"
      :transition-name="ruleDrawer.transitionName"
      :conditions="ruleDrawer.conditions"
      :validators="ruleDrawer.validators"
      :post-functions="ruleDrawer.postFunctions"
      @save="saveRuleDrawer"
    />
  </n-space>
</template>

<style scoped>
.status-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 0;
  border-bottom: 1px solid var(--n-border-color, #efeff5);
}

.matrix-wrap {
  overflow: auto;
}

.matrix-table {
  border-collapse: collapse;
  min-width: 100%;
}

.matrix-table th,
.matrix-table td {
  border: 1px solid var(--n-border-color, #efeff5);
  padding: 8px 10px;
  text-align: center;
  vertical-align: middle;
}

.matrix-corner {
  min-width: 120px;
  background: var(--n-color-embedded, var(--n-action-color));
  position: relative;
  height: 64px;
}

.corner-from,
.corner-to {
  position: absolute;
  font-size: 12px;
  color: var(--n-text-color-3);
}

.corner-from {
  left: 10px;
  bottom: 8px;
}

.corner-to {
  right: 10px;
  top: 8px;
}

.matrix-head,
.matrix-row-head {
  background: var(--n-color-embedded, var(--n-action-color));
  white-space: nowrap;
}

.matrix-cell {
  width: 108px;
  transition: background 0.15s;
}

.matrix-cell.enabled {
  background: color-mix(in srgb, var(--n-primary-color) 8%, transparent);
}

.cell-body {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
}

.cell-config {
  border: none;
  background: transparent;
  color: var(--n-text-color-3);
  font-size: 12px;
  cursor: pointer;
  padding: 0;
}

.cell-config.active {
  color: var(--n-primary-color);
  font-weight: 500;
}

.cell-config:hover {
  text-decoration: underline;
}

.matrix-dash {
  color: var(--n-text-color-disabled);
}
</style>
