<script setup lang="ts">
import { Play, RefreshCw } from '@lucide/vue'
import { NAvatar, NButton, NTag, useDialog, useMessage } from 'naive-ui'
import type { DataTableColumns } from 'naive-ui'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import YunxiaoFlowCanvas from '@/modules/pipeline/components/YunxiaoFlowCanvas.vue'
import { findEditorJob, groupRunJobs, repoShortName } from '@/modules/pipeline/graph/pipelineGraph'
import {
  formatCommit,
  formatDateTime,
  formatDuration,
  formatGitRef,
  runStatusLabel,
  runStatusTagType,
} from '@/modules/pipeline/status'
import type { EditorStage, PipelineRun, PipelineSummary } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import { useDataTablePagination, usePagination } from '@/shared/composables/usePagination'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const dialog = useDialog()
const loading = ref(false)
const historyLoading = ref(false)
const run = ref<PipelineRun | null>(null)
const pipeline = ref<PipelineSummary | null>(null)
const stages = ref<EditorStage[]>([])
const selectedJobKey = ref<string | null>(null)
const logMode = ref<'job' | 'all'>('job')
const logOpen = ref(false)
const mainTab = ref<'graph' | 'history'>('graph')
const history = ref<PipelineRun[]>([])
const historyPagination = usePagination({ pageSize: 10, pageSizes: [10, 20, 50] })
const logEl = ref<HTMLElement | null>(null)
const now = ref(Date.now())
let timer: number | null = null

const pipelineId = computed(() => String(route.params.id ?? ''))
const runId = computed(() => String(route.params.runId ?? ''))
const selectedRunJob = computed(() => {
  const job = findEditorJob(stages.value, selectedJobKey.value)
  if (job?.runJobId == null) return null
  return run.value?.jobs.find((item) => String(item.id) === String(job.runJobId)) ?? null
})
const logTitle = computed(() => {
  if (logMode.value === 'all') return '全程日志'
  return selectedRunJob.value?.jobName ? `${selectedRunJob.value.jobName} 日志` : '任务日志'
})
const logBody = computed(() => {
  if (logMode.value === 'all') {
    const chunks = (run.value?.jobs ?? [])
      .map((job) => {
        const body = job.logText?.trim()
        return body ? `--- ${job.jobName} ---\n${body}` : ''
      })
      .filter(Boolean)
    if (chunks.length) return chunks.join('\n\n')
    return run.value?.errorMessage?.trim() || '暂无输出'
  }
  if (selectedRunJob.value?.logText?.trim()) return selectedRunJob.value.logText
  if (run.value?.errorMessage?.trim()) return run.value.errorMessage
  if (selectedRunJob.value) {
    return `任务状态：${runStatusLabel(selectedRunJob.value.status)}\n暂无输出。若长时间如此，多半是执行集群还在拉镜像。`
  }
  return '选择任务查看日志'
})
const autoRefreshing = computed(() => !isTerminal(run.value?.status))
const pipelineTitle = computed(() => run.value?.pipelineName || pipeline.value?.name || '运行详情')

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function isTerminal(status?: string | null) {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED'
}

function triggerLabel(trigger?: string | null) {
  if (trigger === 'MANUAL') return '页面手动触发'
  return trigger || '—'
}

function applyRunGraph(data: PipelineRun) {
  const next = groupRunJobs(data.jobs ?? [])
  stages.value = next
  if (!selectedJobKey.value || !findEditorJob(next, selectedJobKey.value)) {
    selectedJobKey.value = next[0]?.jobs[0]?.clientKey ?? null
  }
}

async function load() {
  loading.value = true
  try {
    const data = await pipelineApi.getRun(pipelineId.value, runId.value)
    run.value = data
    applyRunGraph(data)
    now.value = Date.now()
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function loadPipeline() {
  try {
    pipeline.value = await pipelineApi.get(pipelineId.value)
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function loadHistory() {
  historyLoading.value = true
  try {
    const page = await pipelineApi.pageRuns(pipelineId.value, historyPagination.query.value)
    history.value = page.records ?? []
    historyPagination.setTotal(page.total)
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    historyLoading.value = false
  }
}

const { tablePagination, handlePageChange, handlePageSizeChange } = useDataTablePagination(
  historyPagination,
  loadHistory,
)

function selectTab(tab: 'graph' | 'history') {
  mainTab.value = tab
  if (tab === 'history') void loadHistory()
}

async function cancel() {
  try {
    run.value = await pipelineApi.cancel(pipelineId.value, runId.value)
    if (run.value) applyRunGraph(run.value)
    message.success('已取消')
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function approve() {
  try {
    run.value = await pipelineApi.approve(pipelineId.value, runId.value)
    if (run.value) applyRunGraph(run.value)
    message.success('已通过')
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function rerun() {
  try {
    const next = await pipelineApi.start(pipelineId.value)
    mainTab.value = 'graph'
    selectedJobKey.value = null
    await router.replace(`/pipeline/pipelines/${pipelineId.value}/runs/${next.id}`)
    await load()
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function confirmDelete() {
  const name = pipelineTitle.value
  dialog.warning({
    title: '删除流水线',
    content: `确认删除「${name}」？删除后不可恢复。`,
    positiveText: '删除',
    negativeText: '取消',
    onPositiveClick: async () => {
      await pipelineApi.delete(pipelineId.value)
      message.success('已删除')
      await router.push('/pipeline/pipelines')
    },
  })
}

function selectJob(jobKey: string) {
  selectedJobKey.value = jobKey
  logMode.value = 'job'
  logOpen.value = true
}

function viewLog(jobKey: string) {
  selectJob(jobKey)
}

function viewAllLogs() {
  logMode.value = 'all'
  logOpen.value = true
}

function openHistoryRun(row: PipelineRun) {
  mainTab.value = 'graph'
  if (String(row.id) === runId.value) return
  void router.push(`/pipeline/pipelines/${pipelineId.value}/runs/${row.id}`)
}

function stopTimer() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

watch(logBody, async () => {
  if (!autoRefreshing.value) return
  await nextTick()
  if (logEl.value) logEl.value.scrollTop = logEl.value.scrollHeight
})

watch(runId, async () => {
  selectedJobKey.value = null
  logOpen.value = false
  await load()
})

watch(pipelineId, () => {
  historyPagination.resetPage()
  history.value = []
  void loadPipeline()
})

onMounted(async () => {
  await Promise.all([load(), loadPipeline()])
  timer = window.setInterval(() => {
    now.value = Date.now()
    if (isTerminal(run.value?.status)) return
    void load()
  }, 2000)
})

onBeforeUnmount(stopTimer)

const historyColumns = computed<DataTableColumns<PipelineRun>>(() => [
  {
    title: '运行记录',
    key: 'id',
    width: 110,
    render: (row) => `#${row.id}`,
  },
  {
    title: '状态',
    key: 'status',
    width: 110,
    render: (row) =>
      h(NTag, { size: 'small', bordered: false, type: runStatusTagType(row.status) }, () => runStatusLabel(row.status)),
  },
  {
    title: '开始时间',
    key: 'startedAt',
    width: 180,
    render: (row) => formatDateTime(row.startedAt),
  },
  {
    title: '持续时间',
    key: 'duration',
    width: 120,
    render: (row) => formatDuration(row.startedAt, row.finishedAt, now.value),
  },
  {
    title: '操作人',
    key: 'triggeredByName',
    width: 160,
    ellipsis: { tooltip: true },
    render: (row) => {
      const name = row.triggeredByName?.trim() || '—'
      const initial = name === '—' ? '?' : name.slice(0, 1)
      return h('span', { class: 'pl-operator' }, [
        h(NAvatar, { size: 22, round: true, class: 'pl-operator-avatar' }, () => initial),
        h('span', name),
      ])
    },
  },
  {
    title: '操作',
    key: 'actions',
    width: 80,
    render: () =>
      h(
        NButton,
        {
          text: true,
          type: 'primary',
          size: 'small',
          onClick: (e: MouseEvent) => {
            e.stopPropagation()
            void rerun()
          },
        },
        () => '重试',
      ),
  },
])
</script>

<template>
  <div class="pl-run">
    <header class="pl-editor-bar">
      <nav class="pl-crumb">
        <button type="button" class="pl-crumb-link" @click="router.push('/pipeline/pipelines')">流水线</button>
        <span class="pl-crumb-sep">/</span>
        <strong class="pl-run-title">{{ pipelineTitle }}</strong>
      </nav>
      <div class="pl-run-tabs" role="tablist">
        <button
          type="button"
          role="tab"
          data-testid="run-tab-graph"
          :class="{ 'is-active': mainTab === 'graph' }"
          :aria-selected="mainTab === 'graph'"
          @click="selectTab('graph')"
        >
          构建过程
        </button>
        <button
          type="button"
          role="tab"
          data-testid="run-tab-history"
          :class="{ 'is-active': mainTab === 'history' }"
          :aria-selected="mainTab === 'history'"
          @click="selectTab('history')"
        >
          运行历史
        </button>
      </div>
      <span class="pl-editor-spacer" />
      <n-button v-if="run?.status === 'WAITING_APPROVAL'" size="small" type="primary" @click="approve">通过</n-button>
      <n-button v-if="run && !isTerminal(run.status)" size="small" @click="cancel">取消</n-button>
      <n-button size="small" type="primary" @click="rerun">
        <template #icon><Play :size="14" /></template>
        运行
      </n-button>
      <n-button size="small" @click="router.push(`/pipeline/pipelines/${pipelineId}/edit`)">编辑</n-button>
      <n-button size="small" type="error" ghost @click="confirmDelete">删除</n-button>
    </header>

    <template v-if="mainTab === 'graph'">
      <div v-if="run && isTerminal(run.status)" class="pl-run-retry">
        <n-button text type="primary" size="small" @click="rerun">重试</n-button>
      </div>
      <section v-if="run" class="pl-run-summary">
        <div class="pl-run-status">
          <n-tag :type="runStatusTagType(run.status)" :bordered="false" size="small">
            {{ runStatusLabel(run.status) }}
          </n-tag>
        </div>
        <dl class="pl-run-meta">
          <div>
            <dt>触发方式</dt>
            <dd>{{ triggerLabel(run.trigger) }}</dd>
          </div>
          <div>
            <dt>执行人</dt>
            <dd>{{ run.triggeredByName || '—' }}</dd>
          </div>
          <div>
            <dt>执行时间</dt>
            <dd>{{ formatDateTime(run.startedAt) }}</dd>
          </div>
          <div>
            <dt>持续时间</dt>
            <dd>{{ formatDuration(run.startedAt, run.finishedAt, now) }}</dd>
          </div>
          <div>
            <dt>代码仓库</dt>
            <dd>{{ repoShortName(pipeline?.repoUrl) }}</dd>
          </div>
          <div>
            <dt>代码分支</dt>
            <dd>{{ formatGitRef(run.gitRef) }}</dd>
          </div>
          <div>
            <dt>代码提交</dt>
            <dd class="is-link" :title="run.commitSha || undefined">{{ formatCommit(run.commitSha) }}</dd>
          </div>
          <div>
            <dt>流水线名称</dt>
            <dd>{{ run.pipelineName || '—' }}</dd>
          </div>
        </dl>
      </section>

      <n-spin :show="loading && !run" class="pl-run-spin">
        <div class="pl-run-graph">
          <div class="pl-run-graph-bar">
            <button type="button" class="pl-run-all-log" @click="viewAllLogs">查看全程日志</button>
          </div>
          <YunxiaoFlowCanvas
            :stages="stages"
            :selected-job-key="selectedJobKey"
            :editable="false"
            :now="now"
            @select-job="selectJob"
            @view-log="viewLog"
          />
        </div>
      </n-spin>
    </template>

    <div v-else class="pl-run-history">
      <n-data-table
        :data="history"
        :columns="historyColumns"
        :loading="historyLoading"
        :bordered="false"
        :single-line="false"
        :flex-height="true"
        remote
        :pagination="tablePagination"
        :row-key="(row: PipelineRun) => String(row.id)"
        :row-class-name="(row: PipelineRun) => (String(row.id) === runId ? 'is-current' : '')"
        :row-props="(row: PipelineRun) => ({
          style: 'cursor: pointer',
          onClick: () => openHistoryRun(row),
        })"
        @update:page="handlePageChange"
        @update:page-size="handlePageSizeChange"
      />
    </div>

    <n-drawer v-model:show="logOpen" class="pl-log-drawer" :width="560" placement="right">
      <n-drawer-content closable :native-scrollbar="false">
        <template #header>
          <div class="pl-log-drawer-head">
            <strong>{{ logTitle }}</strong>
            <n-tag
              v-if="logMode === 'job' && selectedRunJob"
              size="tiny"
              :type="runStatusTagType(selectedRunJob.status)"
              :bordered="false"
            >
              {{ runStatusLabel(selectedRunJob.status) }}
            </n-tag>
            <n-tag v-if="autoRefreshing" size="tiny" type="info" :bordered="false">
              <template #icon><RefreshCw :size="11" /></template>
              自动刷新
            </n-tag>
          </div>
        </template>
        <div class="pl-log-body">
          <p v-if="run?.errorMessage" class="pl-run-error">{{ run.errorMessage }}</p>
          <pre ref="logEl" class="pl-log">{{ logBody }}</pre>
        </div>
      </n-drawer-content>
    </n-drawer>
  </div>
</template>

<style scoped>
.pl-run {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--wb-page-bg, #f5f7fb);
}

.pl-editor-bar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 20px;
  height: 48px;
  padding: 0 16px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.pl-crumb {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 6px;
  min-width: 0;
  font-size: 14px;
  color: var(--wb-muted, #646a73);
}

.pl-crumb-link {
  padding: 0;
  border: none;
  background: none;
  font: inherit;
  color: var(--wb-muted, #646a73);
  cursor: pointer;
}

.pl-crumb-link:hover {
  color: var(--wb-primary, #3370ff);
}

.pl-crumb-sep {
  color: var(--wb-border, #e5e7eb);
}

.pl-run-title {
  overflow: hidden;
  font-size: 14px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-editor-spacer {
  flex: 1;
}

.pl-run-tabs {
  display: flex;
  flex-shrink: 0;
  align-self: stretch;
  gap: 8px;
}

.pl-run-tabs button {
  height: 100%;
  padding: 0 8px;
  border: none;
  border-bottom: 2px solid transparent;
  background: transparent;
  font: inherit;
  font-size: 14px;
  color: var(--wb-muted, #646a73);
  cursor: pointer;
}

.pl-run-tabs button.is-active {
  color: var(--wb-primary, #3370ff);
  font-weight: 500;
  border-bottom-color: var(--wb-primary, #3370ff);
}

.pl-run-retry {
  flex-shrink: 0;
  padding: 8px 16px 0;
}

.pl-run-summary {
  display: flex;
  flex-shrink: 0;
  align-items: flex-start;
  gap: 20px;
  margin: 8px 16px 0;
  padding: 16px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  background: var(--wb-card-bg, #fff);
}

.pl-run-meta {
  display: grid;
  flex: 1;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 8px 16px;
  margin: 0;
}

.pl-run-meta div {
  min-width: 0;
}

.pl-run-meta dt {
  font-size: 12px;
  color: var(--wb-muted, #646a73);
}

.pl-run-meta dd {
  margin: 2px 0 0;
  overflow: hidden;
  font-size: 13px;
  color: var(--wb-ink, #1f2329);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pl-run-meta dd.is-link {
  color: var(--wb-primary, #3370ff);
}

.pl-run-spin {
  flex: 1;
  min-height: 0;
  margin: 12px 16px 16px;
}

.pl-run-spin :deep(.n-spin-content) {
  height: 100%;
}

.pl-run-graph {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: auto;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  background: var(--wb-card-bg, #fff);
}

.pl-run-graph-bar {
  display: flex;
  justify-content: flex-end;
  padding: 8px 16px 0;
}

.pl-run-all-log {
  border: none;
  background: transparent;
  font: inherit;
  font-size: 13px;
  color: var(--wb-primary, #3370ff);
  cursor: pointer;
}

.pl-run-history {
  flex: 1;
  min-height: 0;
  padding: 8px 16px 16px;
  overflow: hidden;
  background: var(--wb-card-bg, #fff);
}

.pl-run-history :deep(.n-data-table) {
  height: 100%;
}

.pl-run-history :deep(tr.is-current td) {
  background: var(--wb-primary-soft, #e8f0ff);
}

.pl-run-history :deep(.pl-operator) {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}

.pl-run-history :deep(.pl-operator-avatar) {
  flex-shrink: 0;
  background: var(--wb-primary, #3370ff);
  font-size: 11px;
  color: #fff;
}

.pl-log-drawer-head {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  font-size: 14px;
}

.pl-log-body {
  display: flex;
  flex-direction: column;
  height: calc(100vh - 120px);
}

.pl-run-error {
  margin: 0 0 8px;
  font-size: 12px;
  color: var(--wb-error, #c24b4b);
}

.pl-log {
  flex: 1;
  min-height: 0;
  margin: 0;
  padding: 14px 16px;
  overflow: auto;
  border-radius: 8px;
  background: #1d2129;
  color: #e5e7eb;
  font-size: 12px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
