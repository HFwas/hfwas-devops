<script setup lang="ts">
import { useMessage } from 'naive-ui'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import StageColumnDag from '@/modules/pipeline/components/StageColumnDag.vue'
import { stackSummary } from '@/modules/pipeline/graph/pipelineGraph'
import type { EditorStage, PipelineRun, PipelineRunJob } from '@/modules/pipeline/types/pipeline'
import { isApiError } from '@/shared/errors/apiError'

const router = useRouter()
const route = useRoute()
const message = useMessage()
const loading = ref(false)
const run = ref<PipelineRun | null>(null)
const stages = ref<EditorStage[]>([])
const selected = ref<PipelineRunJob | null>(null)
let timer: number | null = null

const pipelineId = computed(() => String(route.params.id ?? ''))
const runId = computed(() => String(route.params.runId ?? ''))

function errorMessage(e: unknown): string {
  if (isApiError(e)) return e.message
  return e instanceof Error ? e.message : '操作失败'
}

function statusType(status?: string | null) {
  if (status === 'SUCCEEDED') return 'success'
  if (status === 'FAILED') return 'error'
  if (status === 'RUNNING') return 'info'
  if (status === 'CANCELLED') return 'warning'
  return 'default'
}

function isTerminal(status?: string | null) {
  return status === 'SUCCEEDED' || status === 'FAILED' || status === 'CANCELLED'
}

function groupStages(jobs: PipelineRunJob[]): EditorStage[] {
  const names: string[] = []
  const map = new Map<string, PipelineRunJob[]>()
  for (const job of jobs) {
    const key = job.stageName || '未命名'
    if (!map.has(key)) {
      names.push(key)
      map.set(key, [])
    }
    map.get(key)!.push(job)
  }
  return names.map((name, index) => ({
    clientKey: `stage-${index}`,
    name,
    sortOrder: index,
    jobs: (map.get(name) ?? []).map((job, jobIndex) => ({
      id: job.jobId ?? job.id,
      clientKey: `job-${job.id}`,
      name: job.jobName,
      kind: job.kind,
      command: job.command,
      sortOrder: jobIndex,
    })),
  }))
}

async function load() {
  loading.value = true
  try {
    const data = await pipelineApi.getRun(pipelineId.value, runId.value)
    run.value = data
    stages.value = groupStages(data.jobs ?? [])
    if (selected.value) {
      selected.value = data.jobs.find((item) => String(item.id) === String(selected.value?.id)) ?? data.jobs[0] ?? null
    } else {
      selected.value = data.jobs[0] ?? null
    }
  } catch (e) {
    message.error(errorMessage(e))
  } finally {
    loading.value = false
  }
}

async function cancel() {
  try {
    run.value = await pipelineApi.cancel(pipelineId.value, runId.value)
    message.success('已取消')
  } catch (e) {
    message.error(errorMessage(e))
  }
}

function stopTimer() {
  if (timer != null) {
    window.clearInterval(timer)
    timer = null
  }
}

onMounted(async () => {
  await load()
  timer = window.setInterval(() => {
    if (isTerminal(run.value?.status)) {
      stopTimer()
      return
    }
    void load()
  }, 2000)
})

onBeforeUnmount(stopTimer)
</script>

<template>
  <n-space vertical size="large" style="padding: 20px 24px 28px">
    <n-page-header :title="run?.pipelineName || '运行详情'" @back="router.push(`/pipeline/pipelines/${pipelineId}`)">
      <template #extra>
        <n-space align="center">
          <n-tag v-if="run" :type="statusType(run.status)" :bordered="false">{{ run.status }}</n-tag>
          <n-button v-if="run && !isTerminal(run.status)" @click="cancel">取消</n-button>
        </n-space>
      </template>
    </n-page-header>

    <n-descriptions v-if="run" :column="3" label-placement="left" size="small">
      <n-descriptions-item label="运行编号">{{ run.id }}</n-descriptions-item>
      <n-descriptions-item label="Git">{{ run.gitRef }}</n-descriptions-item>
      <n-descriptions-item label="镜像">{{ run.image }}</n-descriptions-item>
      <n-descriptions-item label="工具链">
        {{ stackSummary(run.stack, run.runtimeVersion, run.toolVersion) }}
      </n-descriptions-item>
      <n-descriptions-item v-if="run.errorMessage" label="失败原因" :span="2">
        {{ run.errorMessage }}
      </n-descriptions-item>
    </n-descriptions>

    <n-spin :show="loading && !run">
      <div class="run-body">
        <StageColumnDag
          mode="run"
          :stages="stages"
          :run-jobs="run?.jobs ?? []"
          :selected-job-id="selected?.id"
          @select-job="selected = $event"
        />
        <n-card title="日志" size="small" class="log-card">
          <pre class="log-text">{{ selected?.logText || '选择卡片查看日志' }}</pre>
        </n-card>
      </div>
    </n-spin>
  </n-space>
</template>

<style scoped>
.run-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 360px;
  gap: 16px;
  align-items: start;
}

.log-card {
  min-height: 280px;
}

.log-text {
  margin: 0;
  max-height: 480px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 12px;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .run-body {
    grid-template-columns: 1fr;
  }
}
</style>
