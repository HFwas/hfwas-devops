<script setup lang="ts">
import { computed } from 'vue'
import PmMarkdownEditor from '@/modules/pm/components/PmMarkdownEditor/index.vue'
import { useFieldOptions } from '@/modules/pm/composables/useFieldOptions'
import { useProjectModules } from '@/modules/pm/composables/useProjectModules'
import { useStatusOptions } from '@/modules/pm/composables/useStatusOptions'
import { useUserOptions } from '@/modules/pm/composables/useUserOptions'
import type { FieldDefinition } from '@/modules/pm/types'
import { PRIORITY_OPTIONS } from '@/modules/pm/types'
import { asId, routeId } from '@/modules/pm/utils/id'

const props = defineProps<{
  field: FieldDefinition
  modelValue: unknown
  mode?: 'edit' | 'query' | 'view'
  projectId?: number | string
  typeCode?: string
  restrictStatus?: boolean
  /** 评估 Transition Condition 时传入当前事项 ID */
  workItemId?: number | string
  size?: 'tiny' | 'small' | 'medium' | 'large'
  /** query 模式下的运算符，IN/NOT_IN 时多选 */
  operator?: string
}>()

const emit = defineEmits<{ 'update:modelValue': [unknown] }>()

const controlSize = computed(() => props.size ?? (props.mode === 'query' ? 'small' : 'medium'))
const queryMulti = computed(
  () => props.mode === 'query' && (props.operator === 'IN' || props.operator === 'NOT_IN'),
)

const route = useRoute()
const resolvedProjectId = computed(() => props.projectId ?? (routeId(route.params.projectId) || undefined))
const resolvedTypeCode = computed(() => props.typeCode ?? (typeof route.params.typeCode === 'string' ? route.params.typeCode : undefined))
const isStatusField = computed(() => props.field.fieldKey === 'status' || props.field.fieldType === 'STATUS')
const statusFrom = computed(() => (props.restrictStatus && props.modelValue ? String(props.modelValue) : undefined))
const statusWorkItemId = computed(() => (props.restrictStatus ? props.workItemId : undefined))

const { selectOptions: moduleOptions, labelMap, load: loadModules } = useProjectModules(resolvedProjectId)
const { selectOptions: userOptions, labelMap: userLabelMap, load: loadUsers } = useUserOptions()
const { options: fieldSelectOptions, loading: fieldOptionsLoading, error: optionsError } = useFieldOptions(
  computed(() => (isStatusField.value ? undefined : props.field)),
)
const { selectOptions: statusOptions, labelMap: statusLabelMap, loading: statusLoading } = useStatusOptions(
  resolvedProjectId,
  resolvedTypeCode,
  statusFrom,
  statusWorkItemId,
)

watch(resolvedProjectId, () => loadModules(), { immediate: true })
watch(() => props.field.fieldType, (t) => { if (t === 'USER') loadUsers() }, { immediate: true })

const readonly = computed(() => props.mode === 'view')
const optionsLoading = computed(() => fieldOptionsLoading.value || statusLoading.value)

const value = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const options = computed(() => {
  if (isStatusField.value) return statusOptions.value
  if (props.field.fieldType === 'MODULE') return moduleOptions.value
  if (props.field.fieldType === 'USER') return userOptions.value
  if (props.field.fieldKey === 'priority' || props.field.fieldType === 'PRIORITY') return PRIORITY_OPTIONS
  return fieldSelectOptions.value
})

const isNullOp = computed(() => props.mode === 'query' && (value.value === '__null__' || value.value === '__not_null__'))

const displayText = computed(() => {
  const val = value.value
  if (val == null || val === '') return '-'
  if (props.field.fieldType === 'BOOLEAN') return val ? '是' : '否'
  if (props.field.fieldType === 'MODULE') {
    return labelMap.value[asId(val as string | number)] ?? String(val)
  }
  if (props.field.fieldType === 'USER') {
    const id = asId(val as string | number)
    return userLabelMap.value[id] ?? String(val)
  }
  if (isStatusField.value) {
    return statusLabelMap.value[String(val)] ?? String(val)
  }
  if (['SELECT', 'PRIORITY'].includes(props.field.fieldType)) {
    const opt = options.value.find((o) => o.value === val)
    return opt?.label ?? String(val)
  }
  if (props.field.fieldType === 'MULTI_SELECT' && Array.isArray(val)) {
    return val.map((v) => options.value.find((o) => o.value === v)?.label ?? v).join('、') || '-'
  }
  if (props.field.fieldType === 'DATE' && typeof val === 'number') {
    return new Date(val).toLocaleDateString()
  }
  return String(val)
})
</script>

<template>
  <n-text v-if="readonly">{{ displayText }}</n-text>
  <n-input
    v-else-if="field.fieldType === 'TEXT' || field.fieldType === 'TEXTAREA'"
    v-model:value="value as string"
    :size="controlSize"
    :type="field.fieldType === 'TEXTAREA' ? 'textarea' : 'text'"
    :placeholder="field.fieldName"
    :autosize="field.fieldType === 'TEXTAREA' ? { minRows: mode === 'query' ? 1 : 4 } : undefined"
  />
  <PmMarkdownEditor
    v-else-if="field.fieldType === 'MARKDOWN'"
    v-model="value as string"
    :placeholder="field.fieldName"
  />
  <n-input-number
    v-else-if="field.fieldType === 'NUMBER'"
    v-model:value="value as number"
    :size="controlSize"
    style="width: 100%"
  />
  <n-space v-else-if="['SELECT', 'STATUS', 'PRIORITY', 'MODULE', 'USER'].includes(field.fieldType) && !isNullOp" vertical style="width: 100%">
    <n-select
      v-model:value="value"
      :size="controlSize"
      :options="options"
      :loading="optionsLoading"
      :multiple="queryMulti"
      placeholder=""
      clearable
      filterable
    />
    <n-text v-if="optionsError" type="error" depth="3">{{ optionsError }}</n-text>
  </n-space>
  <n-space v-else-if="field.fieldType === 'MULTI_SELECT'" vertical style="width: 100%">
    <n-select
      v-model:value="value"
      :size="controlSize"
      :options="options"
      :loading="optionsLoading"
      multiple
      placeholder=""
      clearable
    />
    <n-text v-if="optionsError" type="error" depth="3">{{ optionsError }}</n-text>
  </n-space>
  <n-date-picker
    v-else-if="field.fieldType === 'DATE'"
    v-model:value="value as number"
    :size="controlSize"
    type="date"
    style="width: 100%"
  />
  <n-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model:value="value as boolean" size="small" />
  <n-input v-else v-model:value="value as string" :size="controlSize" :placeholder="field.fieldName" />
</template>
