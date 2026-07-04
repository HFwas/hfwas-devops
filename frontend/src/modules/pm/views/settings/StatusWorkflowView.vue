<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pmStatusApi } from '@/modules/pm/api'
import {
  ANY_STATUS_CODE,
  TYPE_META,
  WORK_ITEM_TYPE_CODES,
  statusTagColor,
  type StatusDefinition,
} from '@/modules/pm/types'
import { invalidateStatusOptionsCache } from '@/modules/pm/composables/useStatusOptions'
import { routeId } from '@/modules/pm/utils/id'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const projectId = computed(() => routeId(route.params.projectId))
const typeCode = ref(String(route.params.typeCode || 'task'))
const loading = ref(false)
const saving = ref(false)
const customized = ref(false)
const statuses = ref<StatusDefinition[]>([])
const showStatusModal = ref(false)
const editingStatus = ref<StatusDefinition | null>(null)
const statusForm = ref({ statusCode: '', statusName: '', isInitial: false, isFinal: false })

const typeTabs = WORK_ITEM_TYPE_CODES.map((code) => ({
  label: TYPE_META[code].label,
  value: code,
}))

const regularStatuses = computed(() =>
  statuses.value.filter((s) => s.statusCode !== ANY_STATUS_CODE),
)

const matrixRows = computed(() => {
  const any = statuses.value.find((s) => s.statusCode === ANY_STATUS_CODE)
  return any ? [...regularStatuses.value, any] : regularStatuses.value
})

function cloneStatuses(list: StatusDefinition[]) {
  return list.map((s) => ({
    ...s,
    transitions: [...(s.transitions ?? [])],
  }))
}

async function load() {
  if (!projectId.value) return
  loading.value = true
  try {
    const data = await pmStatusApi.get(projectId.value, typeCode.value)
    customized.value = !!data.customized
    statuses.value = cloneStatuses(data.statuses ?? [])
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
  return from?.transitions?.includes(toCode) ?? false
}

function setTransition(fromCode: string, toCode: string, enabled: boolean) {
  if (fromCode === toCode) return
  const from = findStatus(fromCode)
  if (!from) return
  const set = new Set(from.transitions ?? [])
  if (enabled) set.add(toCode)
  else set.delete(toCode)
  from.transitions = [...set]
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
    s.transitions = (s.transitions ?? []).filter((t) => t !== status.statusCode)
  }
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
      transitions: [...(s.transitions ?? [])],
    }))
    if (any) {
      payload.push({
        statusCode: ANY_STATUS_CODE,
        statusName: any.statusName,
        sortOrder: 999,
        isInitial: 0,
        isFinal: 0,
        transitions: [...(any.transitions ?? [])],
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
    <n-page-header
      title="状态流转"
      subtitle="配置事项状态及允许的流转路径，类似 Jira Workflow 状态矩阵"
    >
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
      当前项目已启用自定义状态流转；点击「恢复默认」可回退到系统模板。
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

      <n-card title="流转矩阵" size="small" style="margin-top: 16px">
        <n-text depth="3" style="display: block; margin-bottom: 12px">
          勾选表示允许从「开始状态」流转到「目标状态」；「任何状态」行表示全局允许的流转。
        </n-text>
        <div class="matrix-wrap">
          <table class="matrix-table">
            <thead>
              <tr>
                <th class="matrix-corner">
                  <span class="corner-from">开始状态</span>
                  <span class="corner-to">目标状态</span>
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
                    <n-tag v-if="row.isInitial === 1" size="tiny" :bordered="false">初始</n-tag>
                  </n-space>
                </th>
                <td
                  v-for="col in regularStatuses"
                  :key="`${row.statusCode}-${col.statusCode}`"
                  class="matrix-cell"
                >
                  <span v-if="row.statusCode === col.statusCode" class="matrix-dash">—</span>
                  <n-checkbox
                    v-else
                    :checked="isEnabled(row.statusCode, col.statusCode)"
                    @update:checked="(checked) => setTransition(row.statusCode, col.statusCode, checked)"
                  />
                </td>
              </tr>
            </tbody>
          </table>
        </div>
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
  padding: 10px 12px;
  text-align: center;
  vertical-align: middle;
}

.matrix-corner {
  min-width: 140px;
  background: #fafafa;
  position: relative;
  height: 72px;
}

.corner-from,
.corner-to {
  position: absolute;
  font-size: 12px;
  color: #666;
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
  background: #fafafa;
  white-space: nowrap;
}

.matrix-cell {
  width: 88px;
}

.matrix-dash {
  color: #ccc;
}
</style>
