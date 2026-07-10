<script setup lang="ts">
import PmWorkItemActivity from '@/modules/pm/components/PmWorkItemActivity/index.vue'
import PmWorkItemComments from '@/modules/pm/components/PmWorkItemComments/index.vue'
import PmWorkItemFieldSidebar from '@/modules/pm/components/PmWorkItemFieldSidebar/index.vue'
import PmMarkdownPreview from '@/modules/pm/components/PmMarkdownPreview/index.vue'
import PmTransitionDialog from '@/modules/pm/components/PmTransitionDialog/index.vue'
import { pmStatusApi, pmWorkItemApi } from '@/modules/pm/api'
import { useFieldSchemaStore } from '@/modules/pm/stores'
import type { PmWorkItem } from '@/modules/pm/types'
import { TYPE_META } from '@/modules/pm/types'
import { formatDateTime } from '@/modules/pm/utils/comment'
import { routeId } from '@/modules/pm/utils/id'
import { useMessage } from 'naive-ui'

interface WorkItemLink {
  id: string
  sourceId: string
  targetId: string
  linkType: string
}

const route = useRoute()
const router = useRouter()
const message = useMessage()
const fieldStore = useFieldSchemaStore()

const projectId = computed(() => routeId(route.params.projectId))
const itemId = computed(() => routeId(route.params.itemId))
const activeTab = ref(String(route.query.tab ?? 'activity'))

const item = ref<PmWorkItem | null>(null)
const links = ref<WorkItemLink[]>([])
const loading = ref(true)
const loadError = ref('')
const deleting = ref(false)
const saving = ref(false)
const commentCount = ref(0)
const activityRef = ref<InstanceType<typeof PmWorkItemActivity> | null>(null)
const linkTargetId = ref<string | null>(null)
const linkType = ref('relates_to')
const persistedStatus = ref('')
const statusLabelMap = ref<Record<string, string>>({})

const transitionDialog = ref({
  show: false,
  transitionId: '',
  transitionName: '',
  fromStatus: '',
  fromStatusName: '',
  toStatusName: '',
})

const typeCode = computed(() => item.value?.typeCode ?? 'task')
const typeLabel = computed(() => TYPE_META[typeCode.value]?.label ?? '事项')
const fieldDefs = computed(() => fieldStore.getSchema(projectId.value, typeCode.value))
const listPath = computed(() => `/pm/projects/${projectId.value}/items/${typeCode.value}`)

async function loadStatusLabels() {
  if (!item.value) return
  const options = await pmStatusApi.options(projectId.value, item.value.typeCode)
  const map: Record<string, string> = {}
  for (const s of options) map[s.statusCode] = s.statusName
  statusLabelMap.value = map
}

async function load() {
  loading.value = true
  loadError.value = ''
  try {
    const data = await pmWorkItemApi.getById(itemId.value)
    if (!data) {
      throw new Error('事项不存在或已删除')
    }
    item.value = data
    persistedStatus.value = data.status ?? ''
    await fieldStore.loadSchema(projectId.value, data.typeCode)
    await loadStatusLabels()
    links.value = (await pmWorkItemApi.listLinks(itemId.value)).map((link) => ({
      id: String(link.id),
      sourceId: String(link.sourceId),
      targetId: String(link.targetId),
      linkType: link.linkType,
    }))
    commentCount.value = await pmWorkItemApi.countComments(itemId.value)
  } catch (e) {
    item.value = null
    loadError.value = e instanceof Error ? e.message : '加载失败'
    message.error(loadError.value)
  } finally {
    loading.value = false
  }
}

async function persistItem() {
  if (!item.value) return
  saving.value = true
  try {
    await pmWorkItemApi.save(item.value)
    persistedStatus.value = item.value.status ?? ''
    activityRef.value?.reload()
  } catch (e) {
    message.error(e instanceof Error ? e.message : '保存失败')
    await load()
  } finally {
    saving.value = false
  }
}

let saveTimer: ReturnType<typeof setTimeout> | undefined
function scheduleSave() {
  clearTimeout(saveTimer)
  saveTimer = setTimeout(() => void persistItem(), 400)
}

async function onSidebarChange() {
  if (!item.value) return
  const nextStatus = item.value.status ?? ''
  const prevStatus = persistedStatus.value
  if (nextStatus && prevStatus && nextStatus !== prevStatus) {
    item.value = { ...item.value, status: prevStatus }
    try {
      const allowed = await pmStatusApi.allowed(projectId.value, item.value.typeCode, prevStatus, item.value.id)
      const option = (allowed.transitions ?? []).find((t) => t.toStatus === nextStatus)
      if (!option) {
        message.warning('不允许流转到该状态')
        await load()
        return
      }
      const meta = await pmStatusApi.transitionMeta(
        projectId.value,
        item.value.typeCode,
        option.id,
        prevStatus,
      )
      const required = meta.requiredFields ?? []
      if (required.length) {
        transitionDialog.value = {
          show: true,
          transitionId: option.id,
          transitionName: option.name || meta.name || '',
          fromStatus: prevStatus,
          fromStatusName: statusLabelMap.value[prevStatus] ?? prevStatus,
          toStatusName: option.toStatusName || statusLabelMap.value[nextStatus] || nextStatus,
        }
        return
      }
      saving.value = true
      await pmWorkItemApi.transition(item.value.id!, { transitionId: option.id })
      message.success(`已执行「${option.name || option.toStatusName}」`)
      await load()
      activityRef.value?.reload()
    } catch (e) {
      message.error(e instanceof Error ? e.message : '状态流转失败')
      await load()
    } finally {
      saving.value = false
    }
    return
  }
  scheduleSave()
}

async function removeItem() {
  deleting.value = true
  try {
    await pmWorkItemApi.delete(itemId.value)
    message.success('已删除')
    router.push(listPath.value)
  } catch (e) {
    message.error(e instanceof Error ? e.message : '删除失败')
  } finally {
    deleting.value = false
  }
}

async function addLink() {
  if (!linkTargetId.value) return
  await pmWorkItemApi.addLink(itemId.value, linkTargetId.value, linkType.value)
  message.success('关联已添加')
  linkTargetId.value = null
  await load()
}

function goBack() {
  router.push({ path: listPath.value, query: { type: typeCode.value } })
}

function onCommentCountUpdate(count: number) {
  commentCount.value = count
  activityRef.value?.reload()
}

watch(activeTab, (tab) => {
  router.replace({ query: { ...route.query, tab } })
})

watch(
  () => route.query.tab,
  (tab) => {
    if (typeof tab === 'string' && tab !== activeTab.value) activeTab.value = tab
  },
)

watch(itemId, load, { immediate: true })
</script>

<template>
  <n-spin :show="loading">
    <template v-if="item">
      <div class="detail-page">
        <div class="detail-body">
          <n-card class="detail-main" :bordered="true" size="small">
            <div class="item-header">
              <n-space align="center" justify="space-between" style="width: 100%">
                <n-space align="center" :size="12">
                  <n-button text @click="goBack">← 返回</n-button>
                  <n-text depth="3">{{ item.itemKey ?? `#${item.itemNo ?? item.id}` }}</n-text>
                  <n-text strong>{{ item.title }}</n-text>
                  <n-text depth="3">{{ typeLabel }}</n-text>
                  <n-text depth="3">更新于 {{ formatDateTime(item.updateTime) }}</n-text>
                  <n-text v-if="saving" depth="3">保存中...</n-text>
                </n-space>
                <n-popconfirm @positive-click="removeItem">
                  <template #trigger>
                    <n-button type="error" secondary size="small" :loading="deleting">删除</n-button>
                  </template>
                  确定删除该事项吗？
                </n-popconfirm>
              </n-space>
            </div>
            <n-divider style="margin: 12px 0" />
            <n-tabs v-model:value="activeTab" type="line" animated>
              <n-tab-pane name="activity" tab="动态">
                <PmWorkItemActivity ref="activityRef" :work-item-id="itemId" />
              </n-tab-pane>
              <n-tab-pane name="comments" :tab="`评论 (${commentCount})`">
                <PmWorkItemComments
                  embedded
                  :work-item-id="itemId"
                  @update:count="onCommentCountUpdate"
                />
              </n-tab-pane>
              <n-tab-pane name="detail" tab="详情">
                <n-space vertical :size="16">
                  <div>
                    <n-text strong>描述</n-text>
                    <div style="margin-top: 8px">
                      <PmMarkdownPreview :content="item.description" />
                    </div>
                  </div>
                  <n-divider />
                  <div>
                    <n-text strong>事项关联</n-text>
                    <n-list bordered style="margin-top: 8px">
                      <n-list-item v-for="link in links" :key="link.id">
                        {{ link.linkType }} → #{{ String(link.sourceId) === String(itemId) ? link.targetId : link.sourceId }}
                      </n-list-item>
                      <n-empty v-if="!links.length" description="暂无关联" size="small" />
                    </n-list>
                    <n-space style="margin-top: 12px">
                      <n-input v-model:value="linkTargetId" placeholder="目标事项 ID" style="width: 200px" />
                      <n-select
                        v-model:value="linkType"
                        :options="[
                          { label: '关联', value: 'relates_to' },
                          { label: '阻塞', value: 'blocks' },
                          { label: '重复', value: 'duplicates' },
                        ]"
                        style="width: 120px"
                      />
                      <n-button @click="addLink">添加关联</n-button>
                    </n-space>
                  </div>
                </n-space>
              </n-tab-pane>
            </n-tabs>
          </n-card>

          <n-card class="detail-sidebar" title="基础字段" size="small">
            <PmWorkItemFieldSidebar
              v-if="item"
              v-model:model-value="item"
              :field-defs="fieldDefs"
              @change="onSidebarChange"
            />
          </n-card>
        </div>
      </div>

      <PmTransitionDialog
        v-model:show="transitionDialog.show"
        :project-id="projectId"
        :type-code="typeCode"
        :item="item"
        :transition-id="transitionDialog.transitionId"
        :transition-name="transitionDialog.transitionName"
        :from-status="transitionDialog.fromStatus"
        :from-status-name="transitionDialog.fromStatusName"
        :to-status-name="transitionDialog.toStatusName"
        @success="() => { load(); activityRef?.reload() }"
      />
    </template>

    <n-result
      v-else-if="!loading && loadError"
      status="error"
      title="无法加载事项"
      :description="loadError"
    >
      <template #footer>
        <n-button @click="goBack">返回列表</n-button>
      </template>
    </n-result>
  </n-spin>
</template>

<style scoped>
.detail-page {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.item-header {
  padding: 0 4px;
}

.detail-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  align-items: start;
}

.detail-main {
  min-height: 520px;
}

.detail-sidebar {
  position: sticky;
  top: 12px;
}

@media (max-width: 960px) {
  .detail-body {
    grid-template-columns: 1fr;
  }

  .detail-sidebar {
    position: static;
  }
}
</style>
