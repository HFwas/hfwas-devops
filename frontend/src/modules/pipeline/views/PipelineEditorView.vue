<script setup lang="ts">
import { ArrowLeft, LayoutTemplate, Play, Save } from '@lucide/vue'
import { useMessage } from 'naive-ui'
import { pipelineApi, pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import JobInspector from '@/modules/pipeline/components/JobInspector.vue'
import TaskPickerDrawer from '@/modules/pipeline/components/TaskPickerDrawer.vue'
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
import { coerceToolchain } from '@/modules/pipeline/graph/toolchainCascade'
import type { EditorJob, EditorStage, JobKind, PipelineCredential, ToolchainOption } from '@/modules/pipeline/types/pipeline'
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
const stack = ref('JAVA_MAVEN')
const runtimeVersion = ref('21')
const toolVersion = ref<string | null>('3.9')
const stages = ref<EditorStage[]>([])
const selectedJobKey = ref<string | null>(null)
const startSelected = ref(false)
const pickerShow = ref(false)
const pickerTarget = ref<StageInsertTarget | null>(null)
const configShow = ref(false)
const currentOption = ref<ToolchainOption | null>(null)

const pipelineId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' && id !== 'new' ? id : ''
})
const isNew = computed(() => !pipelineId.value)
const selectedJob = computed(() => findEditorJob(stages.value, selectedJobKey.value))
const configTitle = computed(() => (startSelected.value || !selectedJob.value ? '流水线设置' : '任务配置'))
const inspectorMode = computed(() => (selectedJob.value && !startSelected.value ? 'job' : 'pipeline'))

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function applyToolchain(option: ToolchainOption) {
  stack.value = option.stack
  runtimeVersion.value = option.runtimeVersion
  toolVersion.value = option.toolVersion ?? null
  currentOption.value = option
}

function onStackChange(value: string) {
  const option = coerceToolchain(toolchains.value, value, runtimeVersion.value, toolVersion.value)
  if (option) applyToolchain(option)
}

function onRuntimeChange(value: string) {
  const option = coerceToolchain(toolchains.value, stack.value, value, toolVersion.value)
  if (option) applyToolchain(option)
}

function onToolChange(value: string | null) {
  const option = coerceToolchain(toolchains.value, stack.value, runtimeVersion.value, value)
  if (option) applyToolchain(option)
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
  configShow.value = false
}

function openConfig() {
  pickerShow.value = false
  configShow.value = true
}

function selectStart() {
  selectedJobKey.value = null
  startSelected.value = true
  openConfig()
}

function selectJob(jobKey: string) {
  selectedJobKey.value = jobKey
  startSelected.value = false
  openConfig()
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
  const job = createEditorJob(kind, currentOption.value)
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

function removeJob(jobKey: string) {
  stages.value = removeEditorJob(stages.value, jobKey)
  if (selectedJobKey.value === jobKey) {
    selectedJobKey.value = null
    startSelected.value = true
  }
}

function applyTemplate() {
  if (!currentOption.value) return
  if (stages.value.length) {
    message.warning('画布已有阶段，请先清空或继续手动编排')
    return
  }
  stages.value = toEditorStages(createTemplateStages(currentOption.value))
  selectedJobKey.value = null
  startSelected.value = false
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
  try {
    const payload = {
      name: name.value.trim(),
      repoUrl: repoUrl.value.trim(),
      gitRef: gitRef.value.trim() || 'main',
      credentialId: credentialId.value,
      stack: stack.value,
      runtimeVersion: runtimeVersion.value,
      toolVersion: toolVersion.value,
      stages: toSaveStages(stages.value),
    }
    const id = isNew.value
      ? await pipelineApi.create(payload)
      : await pipelineApi.update(pipelineId.value, payload)
    message.success('已保存')
    if (thenRun) {
      const run = await pipelineApi.start(id)
      await router.push(`/pipeline/pipelines/${id}/runs/${run.id}`)
      return
    }
    if (isNew.value) {
      await router.replace(`/pipeline/pipelines/${id}/edit`)
    }
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    saving.value = false
  }
}

async function load() {
  loading.value = true
  try {
    const [matrix, creds] = await Promise.all([pipelineApi.toolchains(), pipelineCredentialApi.list()])
    toolchains.value = matrix
    credentials.value = creds
    if (pipelineId.value) {
      const row = await pipelineApi.get(pipelineId.value)
      name.value = row.name
      repoUrl.value = row.repoUrl
      gitRef.value = row.gitRef || 'main'
      credentialId.value = row.credentialId != null ? String(row.credentialId) : null
      const option = coerceToolchain(matrix, row.stack, row.runtimeVersion, row.toolVersion) ?? matrix[0]
      stages.value = toEditorStages(row.stages)
      if (option) applyToolchain(option)
    } else {
      stages.value = []
      const option = coerceToolchain(matrix, 'JAVA_MAVEN', '21', '3.9') ?? matrix[0]
      if (option) applyToolchain(option)
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
      <YunxiaoFlowCanvas
        :stages="stages"
        :selected-job-key="selectedJobKey"
        :start-selected="startSelected"
        @insert-stage="(afterIndex) => openPicker({ type: 'stage', afterIndex })"
        @add-parallel="(stageKey) => openPicker({ type: 'parallel', stageKey })"
        @select-job="selectJob"
        @select-start="selectStart"
        @remove="removeJob"
      />
    </div>
  </n-spin>
  <TaskPickerDrawer v-model:show="pickerShow" :stages="stages" :target="pickerTarget" @pick="onPick" />
  <n-drawer v-model:show="configShow" :width="400" placement="right">
    <n-drawer-content :title="configTitle" closable>
      <JobInspector
        :mode="inspectorMode"
        :job="selectedJob"
        :stages="stages"
        :selected-job-key="selectedJobKey"
        :name="name"
        :repo-url="repoUrl"
        :git-ref="gitRef"
        :credential-id="credentialId"
        :stack="stack"
        :runtime-version="runtimeVersion"
        :tool-version="toolVersion"
        :toolchains="toolchains"
        :credentials="credentials"
        @update:name="name = $event"
        @update:repo-url="repoUrl = $event"
        @update:git-ref="gitRef = $event"
        @update:credential-id="credentialId = $event"
        @update:stack="onStackChange"
        @update:runtime-version="onRuntimeChange"
        @update:tool-version="onToolChange"
        @update:job="patchJob"
        @kind-blocked="message.warning($event)"
        @remove="selectedJobKey && removeJob(selectedJobKey)"
      />
    </n-drawer-content>
  </n-drawer>
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
</style>
