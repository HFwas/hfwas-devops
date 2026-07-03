<script setup lang="ts">
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, PmWorkItem } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  modelValue: Partial<PmWorkItem>
}>()

const emit = defineEmits<{ 'update:modelValue': [Partial<PmWorkItem>]; submit: [] }>()

const form = computed({
  get: () => props.modelValue,
  set: (v) => emit('update:modelValue', v),
})

const systemFields = computed(() => props.fieldDefs.filter((f) => f.systemFlag === 1))
const customFields = computed(() => props.fieldDefs.filter((f) => f.systemFlag !== 1))

function systemValue(key: string) {
  return (form.value as Record<string, unknown>)[key]
}

function setSystem(key: string, val: unknown) {
  emit('update:modelValue', { ...form.value, [key]: val })
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
</script>

<template>
  <n-form label-placement="top">
    <n-grid :cols="2" :x-gap="16">
      <n-gi v-for="field in systemFields" :key="field.fieldKey">
        <n-form-item :label="field.fieldName" :required="field.requiredFlag === 1">
          <PmFieldRenderer
            :field="field"
            :model-value="systemValue(field.fieldKey)"
            @update:model-value="(v) => setSystem(field.fieldKey, v)"
          />
        </n-form-item>
      </n-gi>
      <n-gi v-for="field in customFields" :key="'c-' + field.fieldKey">
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
