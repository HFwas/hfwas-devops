<script setup lang="ts">
import PmTransitionDialog from '@/modules/pm/components/PmTransitionDialog/index.vue'
import { pmMetaApi, pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import { useStatusOptions } from '@/modules/pm/composables/useStatusOptions'
import type { PmWorkItem, StatusDefinition } from '@/modules/pm/types'
import { TYPE_META } from '@/modules/pm/types'
import { routeId } from '@/modules/pm/utils/id'
import { useMessage } from 'naive-ui'

const route = useRoute()
const message = useMessage()
const projectId = computed(() => routeId(route.params.projectId))
const typeCode = computed(() => String(route.params.typeCode))
const pageTitle = computed(() => `${TYPE_META[typeCode.value]?.label ?? ''}看板`)
const board = ref<Record<string, PmWorkItem[]>>({})
const allStatuses = ref<StatusDefinition[]>([])
const moveOptionsMap = ref<Record<string, Array<{ label: string; key: string }>>>({})

const transitionDialog = ref({
  show: false,
  item: null as PmWorkItem | null,
  transitionId: '',
  transitionName: '',
  fromStatus: '',
  fromStatusName: '',
  toStatusName: '',
})

const columns = computed(() =>
  allStatuses.value.map((s) => ({
    key: s.statusCode,
    title: s.statusName,
    items: board.value[s.statusCode] || [],
  })),
)

const { labelMap: statusLabelMap } = useStatusOptions(projectId, typeCode)

async function loadStatuses() {
  allStatuses.value = await pmStatusApi.options(projectId.value, typeCode.value)
}

async function loadBoard() {
  board.value = await pmMetaApi.board(projectId.value, typeCode.value)
}

async function load() {
  await loadStatuses()
  await loadBoard()
}

async function prepareMoveOptions(item: PmWorkItem, fromStatus: string) {
  const result = await pmStatusApi.allowed(projectId.value, typeCode.value, fromStatus, item.id)
  const options = (result.transitions ?? [])
    .filter((t) => t.toStatus !== fromStatus)
    .map((t) => ({ label: t.name || `→ ${t.toStatusName}`, key: t.id }))
  if (item.id != null) {
    moveOptionsMap.value[String(item.id)] = options
  }
  return options
}

async function moveItem(item: PmWorkItem, transitionId: string, fromStatus: string) {
  try {
    const allowed = await pmStatusApi.allowed(projectId.value, typeCode.value, fromStatus, item.id)
    const option = (allowed.transitions ?? []).find((t) => t.id === transitionId)
    if (!option) {
      message.warning('该流转已不可用，请刷新后重试')
      return
    }
    const meta = await pmStatusApi.transitionMeta(
      projectId.value,
      typeCode.value,
      transitionId,
      fromStatus,
    )
    const required = meta.requiredFields ?? []
    if (required.length) {
      transitionDialog.value = {
        show: true,
        item,
        transitionId,
        transitionName: option.name || meta.name || '',
        fromStatus,
        fromStatusName: statusLabelMap.value[fromStatus] ?? fromStatus,
        toStatusName: option.toStatusName,
      }
      return
    }
    await pmWorkItemApi.transition(item.id!, { transitionId })
    message.success(`已执行「${option.name || option.toStatusName}」`)
    await loadBoard()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '状态流转失败')
  }
}

watch(typeCode, load)
onMounted(load)
</script>

<template>
  <n-space vertical size="large">
    <n-page-header :title="pageTitle" subtitle="按状态分列查看与流转事项" />
    <n-scrollbar x-scrollable>
      <n-space align="start" :size="16" style="min-width: 900px">
        <n-card
          v-for="col in columns"
          :key="col.key"
          :title="col.title"
          size="small"
          style="width: 240px; min-height: 400px"
        >
          <n-space vertical>
            <n-card
              v-for="item in col.items"
              :key="item.id"
              size="small"
              hoverable
            >
              <n-text strong>{{ item.title }}</n-text>
              <n-space size="small" style="margin-top: 8px">
                <n-tag size="small" :bordered="false">{{ TYPE_META[item.typeCode]?.label }}</n-tag>
                <n-dropdown
                  :options="item.id != null ? (moveOptionsMap[String(item.id)] ?? []) : []"
                  @select="(key) => moveItem(item, key as string, col.key)"
                >
                  <n-button size="tiny" @click="prepareMoveOptions(item, col.key)">移动</n-button>
                </n-dropdown>
              </n-space>
            </n-card>
          </n-space>
        </n-card>
      </n-space>
    </n-scrollbar>

    <PmTransitionDialog
      v-model:show="transitionDialog.show"
      :project-id="projectId"
      :type-code="typeCode"
      :item="transitionDialog.item"
      :transition-id="transitionDialog.transitionId"
      :transition-name="transitionDialog.transitionName"
      :from-status="transitionDialog.fromStatus"
      :from-status-name="transitionDialog.fromStatusName"
      :to-status-name="transitionDialog.toStatusName"
      @success="loadBoard"
    />
  </n-space>
</template>
