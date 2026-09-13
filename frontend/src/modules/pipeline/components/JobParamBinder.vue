<script setup lang="ts">
import type { JobParamBinding, JobParamValueMode, TaskKindParam } from '@/modules/pipeline/types/pipeline'

const props = defineProps<{
  kindParams: TaskKindParam[]
  bindings: Record<string, JobParamBinding>
}>()

const emit = defineEmits<{
  'update:bindings': [value: Record<string, JobParamBinding>]
}>()

function modeOf(key: string): JobParamValueMode {
  return props.bindings[key]?.mode === 'runtime' ? 'runtime' : 'fixed'
}

function valueOf(param: TaskKindParam): string {
  const binding = props.bindings[param.paramKey]
  if (binding?.value != null && binding.value !== '') return binding.value
  return param.defaultValue || ''
}

function patch(key: string, next: JobParamBinding) {
  emit('update:bindings', { ...props.bindings, [key]: next })
}

function setMode(param: TaskKindParam, mode: JobParamValueMode) {
  patch(param.paramKey, { mode, value: valueOf(param) })
}

function setValue(param: TaskKindParam, value: string) {
  patch(param.paramKey, { mode: modeOf(param.paramKey), value })
}

function typeLabel(type: string) {
  if (type === 'select') return '枚举'
  if (type === 'api_select') return '远程接口'
  return '静态值'
}
</script>

<template>
  <div class="job-param-binder">
    <div class="job-param-header">
      <span class="job-param-title">运行参数</span>
    </div>
    <p class="job-param-hint">
      变量在任务市场预置。点「设为变量」后，运行时按任务市场的枚举 / 远程接口 / 静态值选择。
    </p>

    <div v-if="kindParams.length === 0" class="job-param-empty">
      该任务类型尚未在任务市场配置环境变量。请到「任务市场」编辑对应 Task 后添加。
    </div>
    <div v-else class="job-param-items">
      <div v-for="p in kindParams" :key="p.paramKey" class="job-param-row">
        <div class="job-param-row-head">
          <span class="job-param-key">{{ p.paramKey }}</span>
          <span class="job-param-label">{{ p.paramLabel }}</span>
          <n-tag size="tiny" :bordered="false">{{ typeLabel(p.paramType) }}</n-tag>
        </div>
        <div class="job-param-row-body">
          <n-radio-group :value="modeOf(p.paramKey)" size="small" @update:value="(v: JobParamValueMode) => setMode(p, v)">
            <n-radio-button value="fixed">写死</n-radio-button>
            <n-radio-button value="runtime">设为变量</n-radio-button>
          </n-radio-group>
        </div>
        <n-input
          v-if="modeOf(p.paramKey) === 'fixed'"
          size="small"
          :value="valueOf(p)"
          :placeholder="p.placeholder || p.defaultValue || '固定值'"
          @update:value="(v: string) => setValue(p, v)"
        />
        <p v-else class="job-param-runtime-hint">
          运行时选择，选项来自任务市场（{{ typeLabel(p.paramType) }}）
        </p>
      </div>
    </div>
  </div>
</template>

<style scoped>
.job-param-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.job-param-title {
  font-size: 13px;
  font-weight: 600;
}

.job-param-hint,
.job-param-empty,
.job-param-runtime-hint {
  margin: 0 0 8px;
  font-size: 12px;
  line-height: 1.5;
  color: var(--wb-muted, #888);
}

.job-param-items {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.job-param-row {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 8px;
  border: 1px solid var(--wb-border, #e5e7eb);
  border-radius: 6px;
}

.job-param-row-head {
  display: flex;
  align-items: center;
  gap: 6px;
}

.job-param-key {
  font-size: 12px;
  font-weight: 600;
  font-family: monospace;
  color: var(--wb-primary, #3370ff);
}

.job-param-label {
  font-size: 12px;
}

.job-param-row-body {
  display: flex;
}
</style>
