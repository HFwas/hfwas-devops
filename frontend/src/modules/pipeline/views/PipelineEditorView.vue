<script setup lang="ts">
import { ArrowLeft, LayoutTemplate, Play, Save } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { pipelineApi, pipelineCredentialApi, pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import JobInspector from '@/modules/pipeline/components/JobInspector.vue'
import TaskPickerDrawer from '@/modules/pipeline/components/TaskPickerDrawer.vue'
import RunParamDialog from '@/modules/pipeline/components/RunParamDialog.vue'
import YunxiaoFlowCanvas from '@/modules/pipeline/components/YunxiaoFlowCanvas.vue'
import { requiresCommand } from '@/modules/pipeline/graph/jobCatalog'
import {
  addParallelJob,
  allEditorJobs,
  canAddKindToStage,
  createEditorJob,
  createTemplateStages,
  findEditorJob,
  hasClone,
  insertStageAt,
  patchEditorJob,
  removeEditorJob,
  toEditorStages,
  toSaveStages,
  type StageInsertTarget,
} from '@/modules/pipeline/graph/pipelineGraph'
import type { EditorJob, EditorStage, JobKind, PipelineCredential, TaskKindVO, ToolchainOption } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'
import '@/modules/pipeline/styles/pipeline-theme.css'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const saving = ref(false)
const toolchains = ref<ToolchainOption[]>([])
const credentials = ref<PipelineCredential[]>([])
const name = ref('')
const repoUrl = ref('')
const gitRef = ref('main')
const credentialId = ref<string | null>(null)
const stages = ref<EditorStage[]>([])
const selectedJobKey = ref<string | null>(null)
const startSelected = ref(true)
const pickerShow = ref(false)
const pickerTarget = ref<StageInsertTarget | null>(null)
const runParamShow = ref(false)
const taskKinds = ref<TaskKindVO[]>([])

const pipelineId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' && id !== 'new' ? id : ''
})
const isNew = computed(() => !pipelineId.value)
const selectedJob = computed(() => findEditorJob(stages.value, selectedJobKey.value))
const configTitle = computed(() => {
  if (startSelected.value || !selectedJob.value) return '流水线设置'
  return selectedJob.value.name || '任务配置'
})
const inspectorMode = computed(() => (selectedJob.value && !startSelected.value ? 'job' : 'pipeline'))
const paramCountByJobId = computed(() => {
  const map: Record<string, number> = {}
  for (const job of allEditorJobs(stages.value)) {
    const bindings = job.paramBindings || {}
    const count = Object.values(bindings).filter((item) => item && item.mode === 'runtime').length
    if (!count) continue
    if (job.id != null) map[String(job.id)] = count
    map[job.clientKey] = count
  }
  return map
})

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function openPicker(target: StageInsertTarget) {
  if (target.type === 'parallel') {
    const stage = stages.value.find((item) => item.clientKey === target.stageKey)
    if (stage?.jobs.some((job) => job.kind === 'APPROVAL')) {
      message.warning('审批任务必须独占一列')
      return
    }
  }
  pickerTarget.value = target
  pickerShow.value = true
}

function selectStart() {
  selectedJobKey.value = null
  startSelected.value = true
}

function selectJob(jobKey: string) {
  selectedJobKey.value = jobKey
  startSelected.value = false
}

function onPick(kind: JobKind) {
  const target = pickerTarget.value
  if (!target) return
  const stageKey = target.type === 'parallel' ? target.stageKey : null
  const blocked = canAddKindToStage(stages.value, stageKey, kind)
  if (blocked) {
    message.warning(blocked)
    return
  }
  const job = createEditorJob(kind)
  if (target.type === 'stage') {
    stages.value = insertStageAt(stages.value, target.afterIndex, job)
  } else {
    stages.value = addParallelJob(stages.value, target.stageKey, job)
  }
  pickerShow.value = false
  pickerTarget.value = null
  selectJob(job.clientKey)
}

function patchJob(patch: Partial<EditorJob>) {
  if (!selectedJobKey.value) return
  stages.value = patchEditorJob(stages.value, selectedJobKey.value, patch)
}

function patchJobAt(jobKey: string, patch: Partial<EditorJob>) {
  stages.value = patchEditorJob(stages.value, jobKey, patch)
}

function removeJob(jobKey: string) {
  stages.value = removeEditorJob(stages.value, jobKey)
  if (selectedJobKey.value === jobKey) {
    selectedJobKey.value = null
    startSelected.value = true
  }
}

function applyTemplate() {
  if (stages.value.length) {
    message.warning('画布已有阶段，请先清空或继续手动编排')
    return
  }
  stages.value = toEditorStages(createTemplateStages())
  selectedJobKey.value = null
  startSelected.value = false
}

function restoreSelection(jobId?: EditorJob['id']) {
  if (jobId == null) return
  const found = allEditorJobs(stages.value).find((job) => String(job.id) === String(jobId))
  if (found) {
    selectedJobKey.value = found.clientKey
    startSelected.value = false
  }
}

async function reloadDefinition(id: string, keepJobId?: EditorJob['id']) {
  const row = await pipelineApi.get(id)
  name.value = row.name
  repoUrl.value = row.repoUrl
  gitRef.value = row.gitRef || 'main'
  credentialId.value = row.credentialId != null ? String(row.credentialId) : null
  stages.value = toEditorStages(row.stages)
  restoreSelection(keepJobId)
}

async function save(thenRun = false) {
  if (!name.value.trim()) {
    message.warning('名称不能为空')
    return
  }
  if (!stages.value.length) {
    message.warning('请至少添加一个阶段')
    return
  }
  for (const job of allEditorJobs(stages.value)) {
    if (!job.name.trim()) {
      message.warning('任务名称不能为空')
      return
    }
    if (requiresCommand(job.kind) && !String(job.command ?? '').trim()) {
      message.warning(`任务「${job.name}」需要命令`)
      return
    }
  }
  if (hasClone(stages.value) && !repoUrl.value.trim()) {
    message.warning('有克隆任务时仓库地址不能为空')
    return
  }
  saving.value = true
  const keepJobId = selectedJob.value?.id
  try {
    const payload = {
      name: name.value.trim(),
      repoUrl: repoUrl.value.trim(),
      gitRef: gitRef.value.trim() || 'main',
      credentialId: credentialId.value,
      stages: toSaveStages(stages.value),
    }
    const id = isNew.value
      ? await pipelineApi.create(payload)
      : await pipelineApi.update(pipelineId.value, payload)
    message.success('已保存')

    if (isNew.value) {
      await router.replace(`/pipeline/pipelines/${id}/edit`)
    }
    await reloadDefinition(String(id), keepJobId)

    if (thenRun) {
      try {
        const defaultParams = await pipelineApi.getDefaultParams(id)
        if (defaultParams && defaultParams.length > 0) {
          runParamShow.value = true
          return
        }
      } catch {
        // 出错时直接运行
      }
      const run = await pipelineApi.start(id)
      await router.push(`/pipeline/pipelines/${id}/runs/${run.id}`)
    }
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

async function doRunAfterSave(runtimeParams?: Record<string, string>) {
  const id = (isNew.value ? route.params.id : pipelineId.value) as string
  try {
    const run = await pipelineApi.start(id, runtimeParams)
    await router.push(`/pipeline/pipelines/${id}/runs/${run.id}`)
  } catch (e) {
    message.error(errorMessage(e))
  }
}

async function load() {
  loading.value = true
  try {
    const [matrix, creds, kinds] = await Promise.all([
      pipelineApi.toolchains(),
      pipelineCredentialApi.list(),
      pipelineTaskKindApi.list(),
    ])
    toolchains.value = matrix
    credentials.value = creds
    taskKinds.value = kinds
    if (pipelineId.value) {
      const row = await pipelineApi.get(pipelineId.value)
      name.value = row.name
      repoUrl.value = row.repoUrl
      gitRef.value = row.gitRef || 'main'
      credentialId.value = row.credentialId != null ? String(row.credentialId) : null
      stages.value = toEditorStages(row.stages)
    } else {
      stages.value = []
    }
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

onMounted(load)
</script>

<template>
  <n-spin :show="loading" class="pl-editor-spin">
    <div class="pl-editor">
      <header class="pl-editor-bar">
        <n-button text size="small" class="pl-back" @click="router.push('/pipeline/pipelines')">
          <template #icon><ArrowLeft :size="14" /></template>
          流水线
        </n-button>
        <n-input v-model:value="name" placeholder="流水线名称" size="small" style="width: 220px" />
        <n-button size="small" quaternary @click="applyTemplate">
          <template #icon><LayoutTemplate :size="14" /></template>
          插入常用模板
        </n-button>
        <span class="pl-editor-spacer" />
        <n-button size="small" :loading="saving" @click="save(false)">
          <template #icon><Save :size="14" /></template>
          保存
        </n-button>
        <n-button size="small" type="primary" :loading="saving" @click="save(true)">
          <template #icon><Play :size="14" /></template>
          保存并运行
        </n-button>
      </header>
      <div class="pl-editor-body">
        <YunxiaoFlowCanvas
          :stages="stages"
          :selected-job-key="selectedJobKey"
          :start-selected="startSelected"
          :param-count-by-job-id="paramCountByJobId"
          @insert-stage="(afterIndex) => openPicker({ type: 'stage', afterIndex })"
          @add-parallel="(stageKey) => openPicker({ type: 'parallel', stageKey })"
          @select-job="selectJob"
          @select-start="selectStart"
          @remove="removeJob"
        />
        <aside class="pl-editor-inspector">
          <header class="pl-editor-inspector-head">{{ configTitle }}</header>
          <div class="pl-editor-inspector-body">
            <JobInspector
              :mode="inspectorMode"
              :job="selectedJob"
              :stages="stages"
              :selected-job-key="selectedJobKey"
              :name="name"
              :repo-url="repoUrl"
              :git-ref="gitRef"
              :credential-id="credentialId"
              :toolchains="toolchains"
              :credentials="credentials"
              :task-kinds="taskKinds"
              @update:name="name = $event"
              @update:repo-url="repoUrl = $event"
              @update:git-ref="gitRef = $event"
              @update:credential-id="credentialId = $event"
              @update:job="patchJob"
              @patch-job="(jobKey: string, patch: Partial<EditorJob>) => patchJobAt(jobKey, patch)"
              @kind-blocked="message.warning($event)"
              @remove="selectedJobKey && removeJob(selectedJobKey)"
            />
          </div>
        </aside>
      </div>
    </div>
  </n-spin>
  <TaskPickerDrawer v-model:show="pickerShow" :stages="stages" :target="pickerTarget" @pick="onPick" />
  <RunParamDialog
    v-model:show="runParamShow"
    :pipeline-id="(pipelineId || route.params.id) as string"
    @run="(params: Record<string, string>) => doRunAfterSave(params)"
  />
</template>

<style scoped>
.pl-editor-spin {
  height: calc(100vh - 56px);
}

.pl-editor-spin :deep(.n-spin-content) {
  height: 100%;
}

.pl-editor {
  display: flex;
  flex-direction: column;
  height: 100%;
  background: var(--wb-page-bg, #f5f7fb);
}

.pl-editor-bar {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 10px;
  height: 48px;
  padding: 0 12px 0 8px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.pl-editor-spacer {
  flex: 1;
}

.pl-editor-body {
  display: flex;
  flex: 1;
  min-height: 0;
}

.pl-editor-inspector {
  display: flex;
  flex-direction: column;
  width: 360px;
  flex-shrink: 0;
  border-left: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.pl-editor-inspector-head {
  flex-shrink: 0;
  padding: 12px 16px;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  font-size: 14px;
  font-weight: 600;
}

.pl-editor-inspector-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 12px 16px 20px;
}
</style>
