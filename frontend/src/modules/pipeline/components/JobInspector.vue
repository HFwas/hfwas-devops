<script setup lang="ts">
import { jobKindMeta, requiresCommand } from '@/modules/pipeline/graph/jobCatalog'
import { allEditorJobs, canChangeJobKind, kindSelectOptions } from '@/modules/pipeline/graph/pipelineGraph'
import {
  findToolchain,
  runtimesFor,
  stackLabel,
  toolsFor,
  uniqueStacks,
} from '@/modules/pipeline/graph/toolchainCascade'
import type {
  EditorJob,
  EditorStage,
  JobKind,
  JobParamBinding,
  JobParamValueMode,
  PipelineCredential,
  TaskKindParam,
  TaskKindVO,
  ToolchainOption,
} from '@/modules/pipeline/types/pipeline'
import JobParamBinder from './JobParamBinder.vue'

const props = defineProps<{
  mode: 'pipeline' | 'job'
  job: EditorJob | null
  stages: EditorStage[]
  selectedJobKey?: string | null
  name: string
  repoUrl: string
  gitRef: string
  credentialId: string | null
  toolchains: ToolchainOption[]
  credentials: PipelineCredential[]
  taskKinds?: TaskKindVO[]
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:repoUrl': [value: string]
  'update:gitRef': [value: string]
  'update:credentialId': [value: string | null]
  'update:job': [patch: Partial<EditorJob>]
  'patch-job': [jobKey: string, patch: Partial<EditorJob>]
  'kind-blocked': [message: string]
  remove: []
}>()

const meta = computed(() => jobKindMeta(props.job?.kind))
const showJobCommand = computed(() => requiresCommand(props.job?.kind))
const kindOptions = computed(() => kindSelectOptions(props.stages.flatMap((stage) => stage.jobs), props.selectedJobKey ?? undefined))
const stackOptions = computed(() => uniqueStacks(props.toolchains).map((value) => ({ label: stackLabel(value), value })))
const runtimeOptions = computed(() =>
  props.job?.stack ? runtimesFor(props.toolchains, props.job.stack).map((value) => ({ label: value, value })) : [],
)
const toolOptions = computed(() =>
  props.job?.stack && props.job.runtimeVersion
    ? toolsFor(props.toolchains, props.job.stack, props.job.runtimeVersion).map((value) => ({ label: value, value }))
    : [],
)
const showTool = computed(() => toolOptions.value.length > 0)

/** 需要语言/版本/工具选择的 job 类型：代码构建类 */
function requiresToolchain(kind?: string | null): boolean {
  return kind === 'BUILD' || kind === 'TEST' || kind === 'PACKAGE' || kind === 'PUBLISH'
}

const showToolchain = computed(() => props.job != null && requiresToolchain(props.job.kind))

const credentialOptions = computed(() =>
  props.credentials.map((item) => ({ label: `${item.name}（${item.kind}）`, value: String(item.id) })),
)
const showSourceFields = computed(() => props.mode === 'pipeline' || props.job?.kind === 'CLONE')

const kindParams = computed<TaskKindParam[]>(() => {
  const kind = props.job?.kind
  if (!kind) return []
  const found = (props.taskKinds || []).find((item) => item.kindValue === kind)
  return found?.params ? found.params : []
})

const binderParams = computed(() => {
  if (props.job?.kind === 'CLONE') {
    return kindParams.value.filter((item) => item.paramKey !== 'GIT_REF')
  }
  return kindParams.value
})

const cloneJob = computed(() => allEditorJobs(props.stages).find((item) => item.kind === 'CLONE') ?? null)

const gitRefDef = computed(() => {
  const found = (props.taskKinds || []).find((item) => item.kindValue === 'CLONE')
  const params = found?.params ? found.params : []
  return params.find((item) => item.paramKey === 'GIT_REF') ?? null
})

const gitRefMode = computed<JobParamValueMode>(() =>
  cloneJob.value?.paramBindings?.GIT_REF?.mode === 'runtime' ? 'runtime' : 'fixed',
)

function gitRefTypeLabel() {
  const type = gitRefDef.value?.paramType
  if (type === 'select') return '枚举'
  if (type === 'api_select') return '远程接口'
  return '静态值'
}

function applyClonePatch(patch: Partial<EditorJob>) {
  const job = cloneJob.value
  if (!job) return
  if (job.clientKey === props.selectedJobKey) {
    emit('update:job', patch)
    return
  }
  emit('patch-job', job.clientKey, patch)
}

function setGitRefMode(mode: JobParamValueMode) {
  const job = cloneJob.value
  if (!job) return
  applyClonePatch({
    paramBindings: {
      ...(job.paramBindings || {}),
      GIT_REF: { mode },
    },
  })
}

function onBindingsChange(bindings: Record<string, JobParamBinding>) {
  emit('update:job', { paramBindings: bindings })
}

function onKindChange(kind: JobKind) {
  if (!props.selectedJobKey) return
  const blocked = canChangeJobKind(props.stages, props.selectedJobKey, kind)
  if (blocked) {
    emit('kind-blocked', blocked)
    return
  }
  const previous = jobKindMeta(props.job?.kind)
  const next = jobKindMeta(kind)
  const command = props.job?.command ?? ''
  const keep = command.trim() && command !== (previous?.defaultCommand ?? '')
  emit('update:job', {
    kind,
    name: props.job?.name === previous?.label ? (next?.label ?? kind) : props.job?.name,
    command: requiresCommand(kind) ? (keep ? command : next?.defaultCommand ?? '') : '',
    stack: requiresToolchain(kind) ? (props.job?.stack ?? null) : null,
    runtimeVersion: requiresToolchain(kind) ? (props.job?.runtimeVersion ?? null) : null,
    toolVersion: requiresToolchain(kind) ? (props.job?.toolVersion ?? null) : null,
    paramBindings: {},
  })
}

function onJobStackChange(value: string) {
  const runtimes = runtimesFor(props.toolchains, value)
  const runtime = runtimes.includes(props.job?.runtimeVersion ?? '') ? props.job?.runtimeVersion : runtimes[0]
  const tools = runtime ? toolsFor(props.toolchains, value, runtime) : []
  const tool = tools.includes(props.job?.toolVersion ?? '') ? props.job?.toolVersion : (tools[0] ?? null)
  const option = findToolchain(props.toolchains, value, runtime ?? '', tool)
  emit('update:job', {
    stack: value,
    runtimeVersion: runtime ?? null,
    toolVersion: tool,
    command: option?.buildCommand ?? props.job?.command ?? '',
  })
}

function onJobRuntimeChange(value: string) {
  const tools = toolsFor(props.toolchains, props.job?.stack ?? '', value)
  const tool = tools.includes(props.job?.toolVersion ?? '') ? props.job?.toolVersion : (tools[0] ?? null)
  const option = findToolchain(props.toolchains, props.job?.stack ?? '', props.job?.runtimeVersion ?? '', value)
  emit('update:job', {
    runtimeVersion: value,
    toolVersion: tool,
    command: option?.buildCommand ?? props.job?.command ?? '',
  })
}

function onJobToolChange(value: string | null) {
  const option = findToolchain(props.toolchains, props.job?.stack ?? '', props.job?.runtimeVersion ?? '', value)
  emit('update:job', {
    toolVersion: value,
    command: option?.buildCommand ?? props.job?.command ?? '',
  })
}
</script>

<template>
  <div class="job-inspector">
    <template v-if="mode === 'job' && job">
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input :value="job.name" @update:value="(value) => emit('update:job', { name: value })" />
        </n-form-item>
        <n-form-item label="类型">
          <n-select :value="job.kind" :options="kindOptions" @update:value="onKindChange" />
        </n-form-item>

        <template v-if="showToolchain">
          <n-form-item label="技术栈">
            <n-select :value="job.stack" :options="stackOptions" @update:value="onJobStackChange" />
          </n-form-item>
          <n-form-item label="运行时">
            <n-select :value="job.runtimeVersion" :options="runtimeOptions" @update:value="onJobRuntimeChange" />
          </n-form-item>
          <n-form-item v-if="showTool" label="工具版本">
            <n-select :value="job.toolVersion" :options="toolOptions" @update:value="onJobToolChange" />
          </n-form-item>
        </template>

        <n-divider />
        <JobParamBinder
          v-if="binderParams.length > 0 || kindParams.length === 0"
          :kind-params="binderParams"
          :bindings="job.paramBindings || {}"
          @update:bindings="onBindingsChange"
        />
        <p v-else class="job-inspector-hint">
          代码分支请在下方「分支 / SHA」点「设为变量」。其它环境变量请到任务市场配置。
        </p>
        <n-divider />

        <n-form-item v-if="showJobCommand" label="命令">
          <n-input
            :value="job.command"
            type="textarea"
            :rows="8"
            @update:value="(value) => emit('update:job', { command: value })"
          />
        </n-form-item>
        <p v-if="meta?.hint" class="job-inspector-hint">{{ meta.hint }}</p>
      </n-form>

      <n-button block type="error" ghost style="margin-top: 8px" @click="emit('remove')">删除任务</n-button>
    </template>

    <template v-if="mode === 'pipeline'">
      <n-form label-placement="top">
        <n-form-item label="名称">
          <n-input :value="name" placeholder="例如 checkout-ci" @update:value="(value) => emit('update:name', value)" />
        </n-form-item>
      </n-form>
      <p class="job-inspector-hint">
        点击任务卡片，可将任务市场预置的环境变量「写死」或「设为变量」。变量在运行时按任务市场的枚举 / 远程接口 / 静态值选择。
      </p>
    </template>

    <n-divider v-if="showSourceFields" />
    <n-form v-if="showSourceFields" label-placement="top">
      <n-form-item label="仓库 HTTPS">
        <n-input
          :value="repoUrl"
          placeholder="有克隆任务时必填"
          @update:value="(value) => emit('update:repoUrl', value)"
        />
      </n-form-item>
      <n-form-item>
        <template #label>
          <div class="job-inspector-label-row">
            <span>分支 / SHA</span>
            <n-radio-group
              v-if="gitRefDef && cloneJob"
              :value="gitRefMode"
              size="small"
              @update:value="(v: JobParamValueMode) => setGitRefMode(v)"
            >
              <n-radio-button value="fixed">写死</n-radio-button>
              <n-radio-button value="runtime">设为变量</n-radio-button>
            </n-radio-group>
          </div>
        </template>
        <n-input
          :value="gitRef"
          :placeholder="gitRefMode === 'runtime' ? '运行时默认值，如 main' : 'main'"
          @update:value="(value) => emit('update:gitRef', value)"
        />
        <p v-if="gitRefMode === 'runtime'" class="job-inspector-hint" style="margin-top: 6px">
          运行时选择，选项来自任务市场「代码克隆」的 {{ gitRefDef?.paramLabel || 'GIT_REF' }}（{{ gitRefTypeLabel() }}）
        </p>
      </n-form-item>
      <n-form-item label="克隆凭证">
        <n-select
          :value="credentialId"
          clearable
          :options="credentialOptions"
          placeholder="可选"
          @update:value="(value) => emit('update:credentialId', value)"
        />
      </n-form-item>
    </n-form>
  </div>
</template>

<style scoped>
.job-inspector-hint {
  margin: 0 0 12px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--wb-muted, #646a73);
}

.job-inspector-label-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  width: 100%;
}
</style>
