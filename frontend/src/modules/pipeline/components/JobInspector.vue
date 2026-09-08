<script setup lang="ts">
import { jobKindMeta, requiresCommand } from '@/modules/pipeline/graph/jobCatalog'
import { canChangeJobKind, kindSelectOptions } from '@/modules/pipeline/graph/pipelineGraph'
import {
  coerceToolchain,
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
  stack: string
  runtimeVersion: string
  toolVersion: string | null
  toolchains: ToolchainOption[]
  credentials: PipelineCredential[]
}>()

const emit = defineEmits<{
  'update:name': [value: string]
  'update:repoUrl': [value: string]
  'update:gitRef': [value: string]
  'update:credentialId': [value: string | null]
  'update:stack': [value: string]
  'update:runtimeVersion': [value: string]
  'update:toolVersion': [value: string | null]
  'update:job': [patch: Partial<EditorJob>]
  'kind-blocked': [message: string]
  remove: []
}>()

const meta = computed(() => jobKindMeta(props.job?.kind))
const showJobCommand = computed(() => requiresCommand(props.job?.kind))
const kindOptions = computed(() => kindSelectOptions(props.stages.flatMap((stage) => stage.jobs), props.selectedJobKey ?? undefined))
const stackOptions = computed(() => uniqueStacks(props.toolchains).map((value) => ({ label: stackLabel(value), value })))
const runtimeOptions = computed(() =>
  runtimesFor(props.toolchains, props.stack).map((value) => ({ label: value, value })),
)
const toolOptions = computed(() =>
  toolsFor(props.toolchains, props.stack, props.runtimeVersion).map((value) => ({ label: value, value })),
)
const showTool = computed(() => toolOptions.value.length > 0)
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
  })
}

function onStackChange(value: string) {
  emit('update:stack', value)
  const option = coerceToolchain(props.toolchains, value, props.runtimeVersion, props.toolVersion)
  if (option) {
    emit('update:runtimeVersion', option.runtimeVersion)
    emit('update:toolVersion', option.toolVersion ?? null)
  }
}

function onRuntimeChange(value: string) {
  emit('update:runtimeVersion', value)
  const option = coerceToolchain(props.toolchains, props.stack, value, props.toolVersion)
  if (option) emit('update:toolVersion', option.toolVersion ?? null)
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
        <n-form-item label="技术栈">
          <n-select :value="stack" :options="stackOptions" @update:value="onStackChange" />
        </n-form-item>
        <n-form-item label="运行时">
          <n-select :value="runtimeVersion" :options="runtimeOptions" @update:value="onRuntimeChange" />
        </n-form-item>
        <n-form-item v-if="showTool" label="工具版本">
          <n-select :value="toolVersion" :options="toolOptions" @update:value="(value) => emit('update:toolVersion', value)" />
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
