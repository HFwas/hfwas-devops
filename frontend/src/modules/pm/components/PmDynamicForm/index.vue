<script setup lang="ts">
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, PmWorkItem } from '@/modules/pm/types'
import { systemFieldProp } from '@/modules/pm/types'

const FULL_WIDTH_TYPES = new Set(['TEXTAREA', 'MARKDOWN'])

const props = withDefaults(defineProps<{
  fieldDefs: FieldDefinition[]
  modelValue: Partial<PmWorkItem>
  mode?: 'create' | 'edit' | 'all'
}>(), {
  mode: 'all',
})

const emit = defineEmits<{ 'update:modelValue': [Partial<PmWorkItem>]; submit: [] }>()

const form = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const visibleFields = computed(() => {
  if (props.mode === 'create') {
    return props.fieldDefs.filter((f) => f.showInCreate)
  }
  return props.fieldDefs.filter((f) => f.fieldKey !== 'type_code')
})

const systemFields = computed(() => visibleFields.value.filter((f) => f.systemFlag === 1))
const customFields = computed(() => visibleFields.value.filter((f) => f.systemFlag !== 1))

function systemValue(key: string) {
  const prop = systemFieldProp(key)
  return (form.value as Record<string, unknown>)[prop]
}

function setSystem(key: string, val: unknown) {
  const prop = systemFieldProp(key)
  emit('update:modelValue', { ...form.value, [prop]: val })
}

function customValue(key: string) {
  return form.value.customFields?.[key]
}

function setCustom(key: string, val: unknown) {
  emit('update:modelValue', {
    ...form.value,
    customFields: { ...(form.value.customFields || {}), [key]: val },
  })
}

function isFullWidth(field: FieldDefinition) {
  return FULL_WIDTH_TYPES.has(field.fieldType)
}
</script>

<template>
  <n-form label-placement="top">
    <n-grid :cols="2" :x-gap="16">
      <n-gi v-for="field in systemFields" :key="field.fieldKey" :span="isFullWidth(field) ? 2 : 1">
        <n-form-item :label="field.fieldName" :required="field.requiredFlag === 1">
          <PmFieldRenderer
            :field="field"
            :model-value="systemValue(field.fieldKey)"
            @update:model-value="(v) => setSystem(field.fieldKey, v)"
          />
        </n-form-item>
      </n-gi>
      <n-gi v-for="field in customFields" :key="'c-' + field.fieldKey" :span="isFullWidth(field) ? 2 : 1">
        <n-form-item :label="field.fieldName" :required="field.requiredFlag === 1">
          <PmFieldRenderer
            :field="field"
            :model-value="customValue(field.fieldKey)"
            @update:model-value="(v) => setCustom(field.fieldKey, v)"
          />
        </n-form-item>
      </n-gi>
    </n-grid>
    <n-space justify="end" style="margin-top: 12px">
      <n-button type="primary" @click="emit('submit')">保存</n-button>
    </n-space>
  </n-form>
</template>
