<script setup lang="ts">
import { Play, Plus, Power, X } from '@lucide/vue'
import type { EditorJob, EditorStage } from '@/modules/pipeline/types/pipeline'
import { jobKindMeta, jobKindLabel } from '@/modules/pipeline/graph/jobCatalog'
import { jobKindIcon } from '@/modules/pipeline/graph/jobIcons'
import { jobKindTone, runStatusLabel, runStatusTagType } from '@/modules/pipeline/status'
import { jobRunDuration } from '@/modules/pipeline/status'

const props = withDefaults(
  defineProps<{
    stages: EditorStage[]
    selectedJobKey?: string | null
    startSelected?: boolean
    editable?: boolean
    now?: number
    paramCountByJobId?: Record<string, number>
  }>(),
  { selectedJobKey: null, startSelected: false, editable: true, now: 0, paramCountByJobId: () => ({}) },
)

const emit = defineEmits<{
  'insert-stage': [afterIndex: number]
  'add-parallel': [stageKey: string]
  'select-job': [jobKey: string]
  'select-start': []
  'view-log': [jobKey: string]
  'open-terminal': [jobKey: string]
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

function toneClass(kind?: string | null): string {
  return `tone-${jobKindTone(kind)}`
}

function paramCount(job: EditorJob) {
  const byId = job.id != null ? props.paramCountByJobId[String(job.id)] : 0
  if (byId) return byId
  return props.paramCountByJobId[job.clientKey] ?? 0
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
        <div class="yx-wire-bar" />
        <button
          v-if="editable"
          type="button"
          class="yx-plus"
          title="插入阶段"
          @click.stop="emit('insert-stage', -1)"
        >
          <Plus :size="16" />
        </button>
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
            <!-- 卡片头部 -->
            <div class="yx-job-head">
              <span class="yx-index-badge">{{ jobIndex(stageIndex, jobIdx) }}</span>
              <div class="yx-job-icon-outer">
                <span class="yx-job-icon" :class="toneClass(job.kind)">
                  <component :is="jobKindIcon(job.kind)" :size="editable ? 14 : 16" />
                </span>
              </div>
              <div class="yx-job-info">
                <span class="yx-job-label" :title="job.name">{{ job.name }}</span>
                <span class="yx-job-kind-tag">{{ jobKindLabel(job.kind) }}</span>
              </div>
              <!-- 执行模式：状态标签 -->
              <n-tag
                v-if="!editable"
                size="tiny"
                :bordered="false"
                :type="runStatusTagType(job.status)"
                style="margin-left: auto"
              >
                {{ runStatusLabel(job.status) }}
              </n-tag>
              <!-- 编辑模式：删除按钮 -->
              <button
                v-else
                type="button"
                class="yx-job-del"
                title="删除"
                @click.stop="emit('remove', job.clientKey)"
              >
                <X :size="12" />
              </button>
            </div>

            <!-- 描述 -->
            <p v-if="jobKindMeta(job.kind)?.description" class="yx-job-desc">
              {{ jobKindMeta(job.kind)?.description }}
            </p>

            <!-- 底部元数据 -->
            <div class="yx-job-foot">
              <span class="yx-job-kind">类型: {{ job.kind }}</span>
              <span v-if="paramCount(job)" class="yx-job-param-badge">{{ paramCount(job) }} 个变量</span>
              <span v-if="jobDuration(job) && !editable" class="yx-job-dur">{{ jobDuration(job) }}</span>
            </div>

            <!-- 操作按钮（运行态） -->
            <div v-if="!editable" class="yx-job-actions">
              <button
                type="button"
                class="yx-job-action-btn"
                @click.stop="emit('view-log', job.clientKey)"
              >
                查看日志
              </button>
              <button
                type="button"
                class="yx-job-action-btn"
                @click.stop="emit('open-terminal', job.clientKey)"
              >
                终端
              </button>
            </div>
          </article>

          <button
            v-if="editable"
            type="button"
            class="yx-parallel"
            title="在本列追加并行任务，将与上方任务同时执行"
            @click.stop="emit('add-parallel', stage.clientKey)"
          >
            <Plus :size="16" />
            <span>增加并行任务</span>
          </button>
        </div>

        <div class="yx-wire">
          <div class="yx-wire-bar" />
          <button
            v-if="editable"
            type="button"
            class="yx-plus"
            title="插入阶段"
            @click.stop="emit('insert-stage', stageIndex)"
          >
            <Plus :size="16" />
          </button>
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
/* ========== Canvas Base ========== */
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
  padding: 56px 40px 80px 28px;
}

.yx-hint {
  margin: -48px 0 0 40px;
  font-size: 12px;
  color: var(--wb-muted, #646a73);
}

/* ========== Endpoints (Start / End) ========== */
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

/* ========== Wires between stages ========== */
.yx-wire {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  width: 88px;
  height: 76px;
}

.yx-wire-bar {
  position: relative;
  flex: 1;
  height: 4px;
  border-radius: 2px;
  background: var(--wb-primary, #3370ff);
  box-shadow: 0 0 6px color-mix(in srgb, var(--wb-primary) 30%, transparent);
}

.yx-wire-bar::after {
  content: '';
  position: absolute;
  right: -2px;
  top: -6px;
  border: 7px solid transparent;
  border-right: 0;
  border-left-color: var(--wb-primary, #3370ff);
  filter: drop-shadow(1px 0 2px color-mix(in srgb, var(--wb-primary) 25%, transparent));
}

.yx-plus {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  margin: 0 4px;
  border: 2px solid var(--wb-primary);
  border-radius: 50%;
  background: #fff;
  color: var(--wb-primary);
  cursor: pointer;
  transition: all 0.15s;
  box-shadow: 0 1px 4px color-mix(in srgb, var(--wb-primary) 20%, transparent);
}

.yx-plus:hover {
  background: var(--wb-primary);
  color: #fff;
  box-shadow: 0 2px 8px color-mix(in srgb, var(--wb-primary) 35%, transparent);
}

/* ========== Stage Columns ========== */
.yx-col {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 224px;
  padding: 10px;
  border: 1.5px solid color-mix(in srgb, var(--wb-primary) 35%, transparent);
  border-radius: var(--wb-radius-sm);
  background: color-mix(in srgb, var(--wb-primary) 4%, transparent);
}

/* ========== Job Card ========== */
.yx-job {
  display: flex;
  flex-direction: column;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 8px;
  padding: 12px;
  background: var(--wb-card-bg, #fff);
  cursor: pointer;
  transition: box-shadow 0.15s;
}

.yx-job:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}

.yx-job.is-selected {
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--wb-primary) 18%, transparent);
}

/* Status border colors (execution mode) */
.yx-job.is-ok {
  border-color: var(--wb-success);
}
.yx-job.is-fail {
  border-color: var(--wb-error);
}
.yx-job.is-run {
  border-color: var(--wb-primary);
  animation: yx-pulse 1.8s ease-in-out infinite;
}
.yx-job.is-wait {
  border-color: var(--wb-warning);
}
.yx-job.is-idle {
  border-color: var(--wb-border, #e5e7eb);
}

/* Card Head */
.yx-job-head {
  display: flex;
  align-items: center;
  gap: 8px;
}

.yx-index-badge {
  flex-shrink: 0;
  font-size: 10px;
  font-weight: 700;
  color: var(--wb-muted, #646a73);
  background: var(--wb-th, #f0f2f5);
  padding: 1px 5px;
  border-radius: 3px;
  line-height: 1.5;
}

/* Tone Icon */
.yx-job-icon-outer {
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.yx-job-icon {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border-radius: 50%;
  flex-shrink: 0;
}

/* Tone Colors */
.tone-blue {
  background: #eff6ff;
  color: #2563eb;
}
.tone-violet {
  background: #f5f3ff;
  color: #7c3aed;
}
.tone-green {
  background: #ecfdf5;
  color: #059669;
}
.tone-amber {
  background: #fffbeb;
  color: #d97706;
}
.tone-cyan {
  background: #ecfeff;
  color: #0891b2;
}
.tone-rose {
  background: #fff1f2;
  color: #e11d48;
}

/* Job Label & Kind Tag */
.yx-job-info {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 1px;
}

.yx-job-label {
  overflow: hidden;
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-ink, #1f2329);
  text-overflow: ellipsis;
  white-space: nowrap;
}

.yx-job-kind-tag {
  font-size: 10px;
  color: var(--wb-muted, #646a73);
  white-space: nowrap;
}

/* Description */
.yx-job-desc {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.4;
  color: var(--wb-muted, #646a73);
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 1;
  overflow: hidden;
}

/* Footer Meta */
.yx-job-foot {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 8px;
  gap: 8px;
}

.yx-job-kind {
  font-size: 10px;
  color: var(--wb-muted, #646a73);
}

.yx-job-dur {
  font-size: 11px;
  color: var(--wb-muted, #646a73);
  flex-shrink: 0;
}

.yx-job-param-badge {
  margin-left: auto;
  flex-shrink: 0;
  padding: 0 6px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--wb-primary, #3370ff) 12%, #fff);
  font-size: 10px;
  font-weight: 600;
  color: var(--wb-primary, #3370ff);
}

.yx-job.is-ok .yx-job-dur {
  color: var(--wb-success);
}
.yx-job.is-fail .yx-job-dur {
  color: var(--wb-error);
}

/* Actions Bar */
.yx-job-actions {
  display: flex;
  gap: 8px;
  margin-top: 8px;
  padding-top: 6px;
  border-top: 1px solid var(--wb-border-soft, #eef0f3);
}

.yx-job-action-btn {
  display: inline-flex;
  align-items: center;
  padding: 0;
  border: none;
  background: transparent;
  font: inherit;
  font-size: 12px;
  color: var(--wb-primary, #3370ff);
  cursor: pointer;
}

.yx-job-action-btn:hover {
  text-decoration: underline;
}

/* Delete Button */
.yx-job-del {
  display: flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  margin-left: auto;
  border: none;
  border-radius: 4px;
  background: transparent;
  color: var(--wb-muted);
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s, color 0.15s;
}

.yx-job:hover .yx-job-del {
  opacity: 1;
}

.yx-job-del:hover {
  color: var(--wb-error);
  background: var(--wb-error-soft);
}

/* Stage Add Parallel Button — dashed drop-zone, high contrast */
.yx-parallel {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  width: 100%;
  min-height: 48px;
  padding: 8px 12px;
  border: 1.5px dashed var(--wb-primary, #3370ff);
  border-radius: 8px;
  background: color-mix(in srgb, var(--wb-primary, #3370ff) 8%, #fff);
  font: inherit;
  font-size: 13px;
  font-weight: 600;
  color: var(--wb-primary, #3370ff);
  cursor: pointer;
  transition: background 0.15s, border-color 0.15s, box-shadow 0.15s;
}

.yx-parallel:hover {
  background: color-mix(in srgb, var(--wb-primary, #3370ff) 16%, #fff);
  border-style: solid;
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--wb-primary, #3370ff) 18%, transparent);
}

/* Running Pulse Animation */
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