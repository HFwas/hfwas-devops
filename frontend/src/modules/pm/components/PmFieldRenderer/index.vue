<script setup lang="ts">
import { computed } from 'vue'
import PmMarkdownEditor from '@/modules/pm/components/PmMarkdownEditor/index.vue'
import type { FieldDefinition } from '@/modules/pm/types'
import { PRIORITY_OPTIONS, STATUS_OPTIONS } from '@/modules/pm/types'

const props = defineProps<{
  field: FieldDefinition
  modelValue: unknown
  mode?: 'edit' | 'query'
}>()

const emit = defineEmits<{ 'update:modelValue': [unknown] }>()

const value = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const options = computed(() => {
  const cfg = props.field.config?.options as Array<{ label: string; value: string }> | undefined
  if (cfg?.length) return cfg.map((o) => ({ label: o.label, value: o.value }))
  if (props.field.fieldKey === 'status' || props.field.fieldType === 'STATUS') return STATUS_OPTIONS
  if (props.field.fieldKey === 'priority' || props.field.fieldType === 'PRIORITY') return PRIORITY_OPTIONS
  return []
})

const isNullOp = computed(() => props.mode === 'query' && (value.value === '__null__' || value.value === '__not_null__'))
</script>

<template>
  <n-input
    v-if="field.fieldType === 'TEXT' || field.fieldType === 'TEXTAREA'"
    v-model:value="value as string"
    :type="field.fieldType === 'TEXTAREA' ? 'textarea' : 'text'"
    :placeholder="field.fieldName"
    :autosize="field.fieldType === 'TEXTAREA' ? { minRows: 4 } : undefined"
  />
  <PmMarkdownEditor
    v-else-if="field.fieldType === 'MARKDOWN'"
    v-model="value as string"
    :placeholder="field.fieldName"
  />
  <n-input-number
    v-else-if="field.fieldType === 'NUMBER' || field.fieldType === 'USER'"
    v-model:value="value as number"
    style="width: 100%"
  />
  <n-select
    v-else-if="['SELECT', 'STATUS', 'PRIORITY'].includes(field.fieldType) && !isNullOp"
    v-model:value="value"
    :options="options"
    clearable
  />
  <n-select
    v-else-if="field.fieldType === 'MULTI_SELECT'"
    v-model:value="value"
    :options="options"
    multiple
    clearable
  />
  <n-date-picker
    v-else-if="field.fieldType === 'DATE'"
    v-model:value="value as number"
    type="date"
    style="width: 100%"
  />
  <n-switch v-else-if="field.fieldType === 'BOOLEAN'" v-model:value="value as boolean" />
  <n-input v-else v-model:value="value as string" :placeholder="field.fieldName" />
</template>
