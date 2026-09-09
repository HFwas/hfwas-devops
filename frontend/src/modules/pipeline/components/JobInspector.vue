<script setup lang="ts">
import { jobKindMeta, requiresCommand } from '@/modules/pipeline/graph/jobCatalog'
import { canChangeJobKind, kindSelectOptions } from '@/modules/pipeline/graph/pipelineGraph'
import {
  coerceToolchain,
  findToolchain,
  runtimesFor,
  stackLabel,
  toolsFor,
  uniqueStacks,
} from '@/modules/pipeline/graph/toolchainCascade'
import type { EditorJob, EditorStage, JobKind, PipelineCredential, ToolchainOption } from '@/modules/pipeline/types/pipeline'

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
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:repoUrl': [value: string]
  'update:gitRef': [value: string]
  'update:credentialId': [value: string | null]
  'update:job': [patch: Partial<EditorJob>]
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
  return kind === 'BUILD' || kind === 'TEST' || kind === 'CUSTOM' || kind === 'PACKAGE' || kind === 'PUBLISH'
}

const showToolchain = computed(() => props.job != null && requiresToolchain(props.job.kind))

const credentialOptions = computed(() =>
  props.credentials.map((item) => ({ label: `${item.name}（${item.kind}）`, value: String(item.id) })),
)
const showSourceFields = computed(() => props.mode === 'pipeline' || props.job?.kind === 'CLONE')

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
  const option = findToolchain(props.toolchains, props.job?.stack ?? '', value, tool)
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

        <!-- 代码构建类任务：语言、版本、工具选择 -->
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
      <n-form-item label="分支 / SHA">
        <n-input :value="gitRef" placeholder="main" @update:value="(value) => emit('update:gitRef', value)" />
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
</style>
