<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pipelineApi, pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import StageColumnDag from '@/modules/pipeline/components/StageColumnDag.vue'
import {
  addJob,
  addStage,
  createDefaultGraph,
  defaultCommandForKind,
  hasClone,
  kindSelectOptions,
  refreshDefaultCommands,
  removeJob,
  removeStage,
  requiresCommand,
  toEditorStages,
  toSaveStages,
} from '@/modules/pipeline/graph/pipelineGraph'
import { coerceToolchain, runtimesFor, stackLabel, toolsFor, uniqueStacks } from '@/modules/pipeline/graph/toolchainCascade'
import type {
  EditorJob,
  EditorStage,
  JobKind,
  PipelineCredential,
  ToolchainOption,
} from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'

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
const currentOption = ref<ToolchainOption | null>(null)
const showJobModal = ref(false)
const editingJob = ref<{ stageKey: string; job: EditorJob } | null>(null)
const jobForm = ref({ name: '', kind: 'CUSTOM' as JobKind, command: '' })

const pipelineId = computed(() => {
  const id = route.params.id
  return typeof id === 'string' && id !== 'new' ? id : ''
})
const isNew = computed(() => !pipelineId.value)

const stackOptions = computed(() => uniqueStacks(toolchains.value).map((value) => ({ label: stackLabel(value), value })))
const runtimeOptions = computed(() =>
  runtimesFor(toolchains.value, stack.value).map((value) => ({ label: value, value })),
)
const toolOptions = computed(() =>
  toolsFor(toolchains.value, stack.value, runtimeVersion.value).map((value) => ({ label: value, value })),
)
const showTool = computed(() => toolOptions.value.length > 0)
const credentialOptions = computed(() =>
  credentials.value.map((item) => ({ label: `${item.name}（${item.kind}）`, value: String(item.id) })),
)
const jobKindOptions = computed(() => kindSelectOptions(stages.value, editingJob.value?.job.clientKey))
const showJobCommand = computed(() => requiresCommand(jobForm.value.kind))
const jobCommandHint = computed(() => {
  if (jobForm.value.kind === 'LINT') return '填写 Semgrep 参数；要跑 Sonar 时取消注释并填写 Host 与 Token。'
  if (jobForm.value.kind === 'IMAGE') return '填写 DEST / IMAGE_PLATFORMS / DOCKERFILE；可选 COSIGN_PRIVATE_KEY。'
  if (jobForm.value.kind === 'APPROVAL') return '运行到此处会暂停，需在运行页点通过。'
  if (jobForm.value.kind === 'CLONE') return 'Clone 命令由平台生成，不需要填写。'
  return ''
})

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function applyOption(option: ToolchainOption, refreshCommands: boolean) {
  const previous = currentOption.value
  stack.value = option.stack
  runtimeVersion.value = option.runtimeVersion
  toolVersion.value = option.toolVersion ?? null
  if (!stages.value.length) {
    stages.value = createDefaultGraph(option)
  } else if (refreshCommands) {
    stages.value = refreshDefaultCommands(stages.value, previous, option)
  }
  currentOption.value = option
}

function onStackChange(value: string) {
  const option = coerceToolchain(toolchains.value, value, runtimeVersion.value, toolVersion.value)
  if (option) applyOption(option, true)
}

function onRuntimeChange(value: string) {
  const option = coerceToolchain(toolchains.value, stack.value, value, toolVersion.value)
  if (option) applyOption(option, true)
}

function onToolChange(value: string) {
  const option = coerceToolchain(toolchains.value, stack.value, runtimeVersion.value, value)
  if (option) applyOption(option, true)
}

function openJob(stageKey: string, jobKey: string) {
  const stage = stages.value.find((item) => item.clientKey === stageKey)
  const job = stage?.jobs.find((item) => item.clientKey === jobKey)
  if (!job) return
  editingJob.value = { stageKey, job }
  jobForm.value = {
    name: job.name,
    kind: (job.kind as JobKind) || 'CUSTOM',
    command: job.command ?? '',
  }
  showJobModal.value = true
}

function onKindChange(kind: JobKind) {
  const previous = jobForm.value.kind
  const previousDefault = defaultCommandForKind(previous, currentOption.value)
  const nextDefault = defaultCommandForKind(kind, currentOption.value)
  const command = jobForm.value.command
  jobForm.value.kind = kind
  if (!requiresCommand(kind)) {
    jobForm.value.command = ''
    return
  }
  if (!command.trim() || command === previousDefault) {
    jobForm.value.command = nextDefault
  }
}

function saveJob() {
  const editing = editingJob.value
  if (!editing) return
  if (!jobForm.value.name.trim()) {
    message.warning('任务名称不能为空')
    return
  }
  if (requiresCommand(jobForm.value.kind) && !jobForm.value.command.trim()) {
    message.warning('任务需要命令')
    return
  }
  stages.value = stages.value.map((stage) => {
    if (stage.clientKey !== editing.stageKey) return stage
    return {
      ...stage,
      jobs: stage.jobs.map((job) =>
        job.clientKey === editing.job.clientKey
          ? {
              ...job,
              name: jobForm.value.name.trim(),
              kind: jobForm.value.kind,
              command: requiresCommand(jobForm.value.kind) ? jobForm.value.command : '',
            }
          : job,
      ),
    }
  })
  showJobModal.value = false
}

async function save(thenRun = false) {
  if (!name.value.trim()) {
    message.warning('名称不能为空')
    return
  }
  if (hasClone(stages.value) && !repoUrl.value.trim()) {
    message.warning('有 clone 任务时仓库地址不能为空')
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
      await router.replace(`/pipeline/pipelines/${id}`)
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
      const option =
        coerceToolchain(matrix, row.stack, row.runtimeVersion, row.toolVersion) ?? matrix[0]
      stages.value = toEditorStages(row.stages, option)
      if (option) applyOption(option, false)
    } else {
      const option = coerceToolchain(matrix, 'JAVA_MAVEN', '21', '3.9') ?? matrix[0]
      if (option) applyOption(option, false)
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
  <n-spin :show="loading">
    <n-space vertical size="large" style="padding: 20px 24px 28px">
      <n-page-header :title="isNew ? '新建流水线' : '编辑流水线'" @back="router.push('/pipeline/pipelines')">
        <template #extra>
          <n-space>
            <n-button :loading="saving" @click="save(false)">保存</n-button>
            <n-button type="primary" :loading="saving" @click="save(true)">保存并运行</n-button>
          </n-space>
        </template>
      </n-page-header>

      <n-form label-placement="left" label-width="96" class="meta-form">
        <n-grid :cols="2" :x-gap="16">
          <n-gi>
            <n-form-item label="名称">
              <n-input v-model:value="name" placeholder="例如 checkout-ci" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="仓库 HTTPS">
              <n-input v-model:value="repoUrl" placeholder="有 clone 时必填，例如 https://github.com/org/repo.git" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="分支 / SHA">
              <n-input v-model:value="gitRef" placeholder="main" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="克隆凭证">
              <n-select v-model:value="credentialId" clearable :options="credentialOptions" placeholder="可选" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="技术栈">
              <n-select :value="stack" :options="stackOptions" @update:value="onStackChange" />
            </n-form-item>
          </n-gi>
          <n-gi>
            <n-form-item label="运行时">
              <n-select :value="runtimeVersion" :options="runtimeOptions" @update:value="onRuntimeChange" />
            </n-form-item>
          </n-gi>
          <n-gi v-if="showTool">
            <n-form-item label="工具版本">
              <n-select :value="toolVersion" :options="toolOptions" @update:value="onToolChange" />
            </n-form-item>
          </n-gi>
        </n-grid>
      </n-form>

      <StageColumnDag
        :stages="stages"
        mode="edit"
        @add-stage="stages = addStage(stages)"
        @add-job="(key) => (stages = addJob(stages, key))"
        @remove-stage="(key) => (stages = removeStage(stages, key))"
        @remove-job="(stageKey, jobKey) => (stages = removeJob(stages, stageKey, jobKey))"
        @edit-job="openJob"
        @rename-stage="(key, value) => (stages = stages.map((item) => (item.clientKey === key ? { ...item, name: value } : item)))"
      />

      <p class="hint">使用镜像内工具执行命令，不跑 mvnw / nvm / pyenv / Go auto-toolchain。</p>
    </n-space>
  </n-spin>

  <n-modal v-model:show="showJobModal" preset="card" title="编辑任务" style="width: 480px">
    <n-form label-placement="top">
      <n-form-item label="名称">
        <n-input v-model:value="jobForm.name" />
      </n-form-item>
      <n-form-item label="类型">
        <n-select :value="jobForm.kind" :options="jobKindOptions" @update:value="onKindChange" />
      </n-form-item>
      <n-form-item v-if="showJobCommand" label="命令">
        <n-input v-model:value="jobForm.command" type="textarea" :rows="6" />
      </n-form-item>
      <p v-if="jobCommandHint" class="hint">{{ jobCommandHint }}</p>
    </n-form>
    <template #footer>
      <n-space justify="end">
        <n-button @click="showJobModal = false">取消</n-button>
        <n-button type="primary" @click="saveJob">确定</n-button>
      </n-space>
    </template>
  </n-modal>
</template>

<style scoped>
.hint {
  margin: 0;
  color: var(--wb-muted, #6b7280);
  font-size: 12px;
}

.meta-form {
  padding: 8px 4px 0;
}
</style>
