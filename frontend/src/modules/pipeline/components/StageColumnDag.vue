<script setup lang="ts">
import { MoreHorizontal, Plus } from '@lucide/vue'
import type { DropdownOption } from 'naive-ui'
import type { EditorStage, PipelineRunJob } from '@/modules/pipeline/types/pipeline'
import { canDeleteJob, canDeleteStage } from '@/modules/pipeline/graph/pipelineGraph'

const props = withDefaults(
  defineProps<{
    stages: EditorStage[]
    mode?: 'edit' | 'run'
    runJobs?: PipelineRunJob[]
    selectedJobId?: string | number | null
  }>(),
  { mode: 'edit', runJobs: () => [], selectedJobId: null },
)

const emit = defineEmits<{
  'add-stage': []
  'add-job': [stageKey: string]
  'remove-stage': [stageKey: string]
  'remove-job': [stageKey: string, jobKey: string]
  'edit-job': [stageKey: string, jobKey: string]
  'rename-stage': [stageKey: string, name: string]
  'select-job': [job: PipelineRunJob]
}>()

const root = ref<HTMLElement | null>(null)
const lines = ref<string[]>([])

function statusOf(stageName: string, jobName: string, jobId?: string | number): string {
  if (props.mode !== 'run') return ''
  return runJobFor(stageName, jobName, jobId)?.status ?? ''
}

function runJobFor(stageName: string, jobName: string, jobId?: string | number): PipelineRunJob | undefined {
  return props.runJobs.find((item) => {
    if (jobId != null && item.jobId != null && String(item.jobId) === String(jobId)) return true
    return item.stageName === stageName && item.jobName === jobName
  })
}

function jobMenu(kind: string): DropdownOption[] {
  const items: DropdownOption[] = [{ label: '改名 / 改命令', key: 'edit' }]
  if (canDeleteJob({ kind })) {
    items.push({ label: '删除', key: 'delete' })
  }
  return items
}

function onJobMenu(key: string | number, stageKey: string, jobKey: string) {
  if (key === 'edit') emit('edit-job', stageKey, jobKey)
  if (key === 'delete') emit('remove-job', stageKey, jobKey)
}

function onCardClick(stage: EditorStage, jobName: string, jobId?: string | number) {
  if (props.mode !== 'run') return
  const job = runJobFor(stage.name, jobName, jobId)
  if (job) emit('select-job', job)
}

function statusClass(status: string): string {
  const value = status.toLowerCase()
  if (value === 'succeeded') return 'is-succeeded'
  if (value === 'failed') return 'is-failed'
  if (value === 'running') return 'is-running'
  if (value === 'cancelled') return 'is-cancelled'
  if (value === 'queued') return 'is-queued'
  return ''
}

function updateLines() {
  const rootEl = root.value
  if (!rootEl) return
  const rootBox = rootEl.getBoundingClientRect()
  const cols = [...rootEl.querySelectorAll<HTMLElement>('[data-stage]')]
  const next: string[] = []
  for (let i = 0; i < cols.length - 1; i++) {
    const fromCards = cols[i].querySelectorAll<HTMLElement>('[data-job]')
    const toCards = cols[i + 1].querySelectorAll<HTMLElement>('[data-job]')
    fromCards.forEach((from) => {
      toCards.forEach((to) => {
        const a = from.getBoundingClientRect()
        const b = to.getBoundingClientRect()
        const x1 = a.right - rootBox.left
        const y1 = a.top + a.height / 2 - rootBox.top
        const x2 = b.left - rootBox.left
        const y2 = b.top + b.height / 2 - rootBox.top
        const mid = (x1 + x2) / 2
        next.push(`M ${x1} ${y1} C ${mid} ${y1}, ${mid} ${y2}, ${x2} ${y2}`)
      })
    })
  }
  lines.value = next
}

let observer: ResizeObserver | null = null

onMounted(() => {
  observer = new ResizeObserver(() => updateLines())
  if (root.value) observer.observe(root.value)
  void nextTick(updateLines)
})

onBeforeUnmount(() => observer?.disconnect())

watch(
  () => [props.stages, props.runJobs],
  () => void nextTick(updateLines),
  { deep: true },
)
</script>

<template>
  <div ref="root" class="dag">
    <svg class="dag-lines" aria-hidden="true">
      <path v-for="(d, index) in lines" :key="index" :d="d" />
    </svg>
    <div class="dag-cols">
      <div v-for="stage in stages" :key="stage.clientKey" class="dag-col" data-stage>
        <div class="dag-col-head">
          <n-input
            v-if="mode === 'edit'"
            :value="stage.name"
            size="small"
            @update:value="(value) => emit('rename-stage', stage.clientKey, value)"
          />
          <strong v-else>{{ stage.name }}</strong>
          <n-button
            v-if="mode === 'edit' && canDeleteStage(stage)"
            size="tiny"
            quaternary
            @click="emit('remove-stage', stage.clientKey)"
          >
            删除列
          </n-button>
        </div>
        <div class="dag-col-body">
          <div
            v-for="job in stage.jobs"
            :key="job.clientKey"
            class="job-card"
            data-job
            :class="[
              statusClass(statusOf(stage.name, job.name, job.id)),
              { 'is-run': mode === 'run', 'is-selected': selectedJobId != null && String(runJobFor(stage.name, job.name, job.id)?.id) === String(selectedJobId) },
            ]"
            @click="onCardClick(stage, job.name, job.id)"
          >
            <div class="job-card-top">
              <span class="job-kind">{{ job.kind }}</span>
              <n-dropdown
                v-if="mode === 'edit'"
                trigger="click"
                :options="jobMenu(job.kind)"
                @select="(key) => onJobMenu(key, stage.clientKey, job.clientKey)"
              >
                <n-button size="tiny" quaternary @click.stop>
                  <template #icon><MoreHorizontal :size="14" /></template>
                </n-button>
              </n-dropdown>
            </div>
            <div class="job-name">{{ job.name }}</div>
            <div v-if="job.kind !== 'CLONE'" class="job-cmd">{{ job.command || '（未填写命令）' }}</div>
            <div v-else class="job-cmd">平台生成 git clone</div>
          </div>
          <n-button
            v-if="mode === 'edit'"
            dashed
            size="small"
            @click="emit('add-job', stage.clientKey)"
          >
            <template #icon><Plus :size="14" /></template>
            任务
          </n-button>
        </div>
      </div>
      <div v-if="mode === 'edit'" class="dag-col dag-col--add">
        <n-button dashed @click="emit('add-stage')">
          <template #icon><Plus :size="14" /></template>
          阶段
        </n-button>
      </div>
    </div>
  </div>
</template>

<style scoped>
.dag {
  position: relative;
  min-height: 280px;
  overflow: auto;
}

.dag-lines {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.dag-lines path {
  fill: none;
  stroke: #94a3b8;
  stroke-width: 1.5;
}

.dag-cols {
  position: relative;
  z-index: 1;
  display: flex;
  align-items: flex-start;
  gap: 28px;
  padding: 8px 4px 16px;
}

.dag-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  width: 220px;
  flex-shrink: 0;
}

.dag-col--add {
  padding-top: 36px;
}

.dag-col-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.dag-col-body {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
  padding: 12px;
  border: 1px dashed var(--wb-border, #e5e7eb);
  border-radius: 10px;
  background: var(--wb-chip-bg, #f8fafc);
}

.job-card {
  display: block;
  width: 100%;
  padding: 10px 12px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  background: var(--wb-card-bg, #fff);
  text-align: left;
  font: inherit;
}

.job-card.is-run {
  cursor: pointer;
}

.job-card.is-running {
  border-color: #3b82f6;
}

.job-card.is-succeeded {
  border-color: #16a34a;
}

.job-card.is-failed {
  border-color: #dc2626;
}

.job-card.is-cancelled,
.job-card.is-queued {
  border-color: #94a3b8;
}

.job-card.is-selected {
  box-shadow: 0 0 0 2px rgba(45, 128, 230, 0.35);
}

.job-card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.job-kind {
  font-size: 11px;
  color: var(--wb-muted, #6b7280);
}

.job-name {
  margin-top: 4px;
  font-size: 13px;
  font-weight: 600;
}

.job-cmd {
  margin-top: 6px;
  color: var(--wb-muted, #6b7280);
  font-size: 12px;
  line-height: 1.4;
  word-break: break-all;
}
</style>
