<script setup lang="ts">
import { Play, Plus, Power, X } from '@lucide/vue'
import type { EditorJob, EditorStage } from '@/modules/pipeline/types/pipeline'
import { jobRunDuration, runStatusLabel } from '@/modules/pipeline/status'

const props = withDefaults(
  defineProps<{
    stages: EditorStage[]
    selectedJobKey?: string | null
    startSelected?: boolean
    editable?: boolean
    now?: number
  }>(),
  { selectedJobKey: null, startSelected: false, editable: true, now: 0 },
)

const emit = defineEmits<{
  'insert-stage': [afterIndex: number]
  'add-parallel': [stageKey: string]
  'select-job': [jobKey: string]
  'select-start': []
  'view-log': [jobKey: string]
  remove: [jobKey: string]
}>()

function jobIndex(stageIndex: number, jobIndexInStage: number) {
  return `${stageIndex + 1}-${jobIndexInStage + 1}`
}

function jobDuration(job: EditorJob) {
  return jobRunDuration(job, props.now || Date.now())
}

function statusClass(job: EditorJob) {
  const status = job.status
  if (!status) return ''
  if (status === 'SUCCEEDED') return 'is-ok'
  if (status === 'FAILED') return 'is-fail'
  if (status === 'RUNNING') return 'is-run'
  if (status === 'WAITING_APPROVAL') return 'is-wait'
  if (status === 'QUEUED' || status === 'CANCELLED') return 'is-idle'
  return ''
}
</script>

<template>
  <div class="yx-canvas" @click.self="editable && emit('select-start')">
    <div class="yx-track">
      <button
        type="button"
        class="yx-endpoint"
        :class="{ 'is-selected': startSelected }"
        @click.stop="emit('select-start')"
      >
        <span class="yx-endpoint-icon is-start">
          <Play :size="16" fill="currentColor" />
        </span>
        <span>开始</span>
      </button>

      <div class="yx-wire">
        <span class="yx-wire-line" />
        <button
          v-if="editable"
          type="button"
          class="yx-plus"
          title="插入阶段"
          @click.stop="emit('insert-stage', -1)"
        >
          <Plus :size="14" />
        </button>
        <span class="yx-wire-line is-arrow" />
      </div>

      <template v-for="(stage, stageIndex) in stages" :key="stage.clientKey">
        <div class="yx-col">
          <article
            v-for="(job, jobIdx) in stage.jobs"
            :key="job.clientKey"
            class="yx-job"
            :class="[{ 'is-selected': selectedJobKey === job.clientKey }, statusClass(job)]"
            @click.stop="emit('select-job', job.clientKey)"
          >
            <div class="yx-job-main">
              <span class="yx-job-index">{{ jobIndex(stageIndex, jobIdx) }}</span>
              <span class="yx-job-name" :title="job.name">{{ job.name }}</span>
              <button
                v-if="editable"
                type="button"
                class="yx-job-del"
                title="删除"
                @click.stop="emit('remove', job.clientKey)"
              >
                <X :size="12" />
              </button>
            </div>
            <div v-if="!editable" class="yx-job-foot">
              <button type="button" class="yx-job-log" @click.stop="emit('view-log', job.clientKey)">
                日志
              </button>
              <span class="yx-job-dur">{{ jobDuration(job) || runStatusLabel(job.status) }}</span>
            </div>
            <button
              v-if="editable"
              type="button"
              class="yx-job-add"
              title="增加并行任务"
              @click.stop="emit('add-parallel', stage.clientKey)"
            >
              <Plus :size="12" />
            </button>
          </article>
          <button
            v-if="editable"
            type="button"
            class="yx-parallel"
            @click.stop="emit('add-parallel', stage.clientKey)"
          >
            增加并行阶段
          </button>
        </div>

        <div class="yx-wire">
          <span class="yx-wire-line" />
          <button
            v-if="editable"
            type="button"
            class="yx-plus"
            title="插入阶段"
            @click.stop="emit('insert-stage', stageIndex)"
          >
            <Plus :size="14" />
          </button>
          <span class="yx-wire-line is-arrow" />
        </div>
      </template>

      <div class="yx-endpoint is-end">
        <span class="yx-endpoint-icon is-stop">
          <Power :size="16" />
        </span>
        <span>结束</span>
      </div>
    </div>
    <p v-if="editable && !stages.length" class="yx-hint">点击连线上的 + 选择任务，插入流水线阶段</p>
  </div>
</template>

<style scoped>
.yx-canvas {
  flex: 1;
  min-height: 0;
  min-width: 0;
  overflow: auto;
  background: var(--wb-page-bg, #f5f7fb);
}

.yx-track {
  display: flex;
  align-items: flex-start;
  min-width: max-content;
  padding: 56px 40px 80px;
}

.yx-hint {
  margin: -48px 0 0 40px;
  font-size: 12px;
  color: var(--wb-muted, #646a73);
}

.yx-endpoint {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  gap: 8px;
  height: 56px;
  margin-top: 10px;
  padding: 0 14px 0 10px;
  border: 1px solid var(--wb-border);
  border-radius: var(--wb-radius-sm);
  background: var(--wb-card-bg);
  box-shadow: var(--wb-shadow-1);
  font: inherit;
  font-size: 13px;
  color: var(--wb-ink);
  cursor: pointer;
}

.yx-endpoint.is-end {
  cursor: default;
}

.yx-endpoint.is-selected {
  border-color: var(--wb-primary);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--wb-primary) 18%, transparent);
}

.yx-endpoint-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: var(--wb-radius);
}

.yx-endpoint-icon.is-start {
  color: var(--wb-primary);
  background: var(--wb-primary-soft);
}

.yx-endpoint-icon.is-stop {
  color: var(--wb-error);
  background: var(--wb-error-soft);
}

.yx-wire {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  width: 72px;
  height: 76px;
}

.yx-wire-line {
  flex: 1;
  height: 0;
  border-top: 1.5px dashed var(--wb-primary);
}

.yx-wire-line.is-arrow {
  position: relative;
}

.yx-wire-line.is-arrow::after {
  content: '';
  position: absolute;
  top: -5px;
  right: -1px;
  border: 5px solid transparent;
  border-right: 0;
  border-left-color: var(--wb-primary);
}

.yx-plus {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  border: 1px solid var(--wb-primary);
  border-radius: 50%;
  background: var(--wb-card-bg);
  color: var(--wb-primary);
  cursor: pointer;
}

.yx-plus:hover {
  background: var(--wb-primary);
  color: var(--wb-card-bg);
}

.yx-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 196px;
  padding: 8px;
  border: 1px solid color-mix(in srgb, var(--wb-primary) 45%, transparent);
  border-radius: var(--wb-radius-sm);
  background: color-mix(in srgb, var(--wb-primary) 4%, transparent);
}

.yx-job {
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: var(--wb-card-bg);
  border: 1px solid transparent;
  border-radius: var(--wb-radius-sm);
  box-shadow: var(--wb-shadow-1);
  cursor: pointer;
}

.yx-job.is-ok {
  border-color: var(--wb-success);
}
.yx-job.is-fail {
  border-color: var(--wb-error);
}
.yx-job.is-run {
  border-color: var(--wb-primary);
}
.yx-job.is-wait {
  border-color: var(--wb-warning);
}
.yx-job.is-selected {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--wb-primary) 18%, transparent);
}

.yx-job-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  height: 28px;
  padding: 0 10px 6px;
}

.yx-job-log {
  border: none;
  padding: 0;
  background: transparent;
  font: inherit;
  font-size: 12px;
  color: var(--wb-primary);
  cursor: pointer;
}

.yx-job-log:hover {
  text-decoration: underline;
}

.yx-job-dur {
  font-size: 12px;
  color: var(--wb-muted);
}

.yx-job.is-ok .yx-job-dur {
  color: var(--wb-success);
}
.yx-job.is-fail .yx-job-dur {
  color: var(--wb-error);
}

.yx-job-main {
  display: flex;
  align-items: stretch;
  min-height: 48px;
}

.yx-job-index {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 40px;
  background: var(--wb-primary);
  font-size: 12px;
  font-weight: 600;
  color: var(--wb-card-bg);
}

.yx-job.is-ok .yx-job-index {
  background: var(--wb-success);
}
.yx-job.is-fail .yx-job-index {
  background: var(--wb-error);
}
.yx-job.is-run .yx-job-index {
  background: var(--wb-primary);
  animation: yx-pulse 1.2s ease-in-out infinite;
}
.yx-job.is-wait .yx-job-index {
  background: var(--wb-warning);
}
.yx-job.is-idle .yx-job-index {
  background: var(--wb-muted);
}

.yx-job-name {
  flex: 1;
  min-width: 0;
  padding: 12px 8px;
  overflow: hidden;
  font-size: 13px;
  font-weight: 500;
  color: var(--wb-ink);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yx-job-del {
  display: none;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  border: none;
  background: transparent;
  color: var(--wb-muted);
  cursor: pointer;
}

.yx-job:hover .yx-job-del {
  display: inline-flex;
}

.yx-job-del:hover {
  color: var(--wb-error);
}

.yx-job-add {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 24px;
  border: none;
  border-top: 1px solid var(--wb-border-soft);
  background: var(--wb-card-bg);
  color: var(--wb-primary);
  cursor: pointer;
}

.yx-job-add:hover {
  background: var(--wb-primary-soft);
}

.yx-parallel {
  height: 36px;
  border: none;
  border-radius: var(--wb-radius-sm);
  background: var(--wb-primary);
  font: inherit;
  font-size: 13px;
  color: var(--wb-card-bg);
  cursor: pointer;
}

.yx-parallel:hover {
  background: var(--wb-primary-hover);
}

@keyframes yx-pulse {
  0%,
  100% {
    opacity: 1;
  }
  50% {
    opacity: 0.55;
  }
}
</style>
