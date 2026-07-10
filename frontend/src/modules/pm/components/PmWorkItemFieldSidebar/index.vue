<script setup lang="ts">
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, PmWorkItem } from '@/modules/pm/types'
import { systemFieldProp } from '@/modules/pm/types'

const SIDEBAR_EXCLUDED = new Set(['type_code', 'description'])

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  modelValue: PmWorkItem
}>()

const emit = defineEmits<{ 'update:modelValue': [PmWorkItem]; change: [] }>()

const sidebarFields = computed(() =>
  props.fieldDefs.filter((f) => !SIDEBAR_EXCLUDED.has(f.fieldKey) && f.fieldType !== 'MARKDOWN' && f.fieldType !== 'TEXTAREA'),
)

function systemValue(key: string) {
  const prop = systemFieldProp(key)
  return (props.modelValue as Record<string, unknown>)[prop]
}

function customValue(key: string) {
  return props.modelValue.customFields?.[key]
}

function updateSystem(key: string, val: unknown) {
  const prop = systemFieldProp(key)
  emit('update:modelValue', { ...props.modelValue, [prop]: val })
  emit('change')
}

function updateCustom(key: string, val: unknown) {
  emit('update:modelValue', {
    ...props.modelValue,
    customFields: { ...(props.modelValue.customFields || {}), [key]: val },
  })
  emit('change')
}
</script>

<template>
  <n-scrollbar style="max-height: calc(100vh - 220px)">
    <n-form label-placement="left" label-width="88" size="small">
      <n-form-item v-for="field in sidebarFields" :key="field.fieldKey" :label="field.fieldName">
        <PmFieldRenderer
          :field="field"
          mode="edit"
          :project-id="modelValue.projectId"
          :type-code="modelValue.typeCode"
          :work-item-id="modelValue.id"
          :restrict-status="field.fieldKey === 'status'"
          :model-value="field.systemFlag === 1 ? systemValue(field.fieldKey) : customValue(field.fieldKey)"
          @update:model-value="(v) => (field.systemFlag === 1 ? updateSystem(field.fieldKey, v) : updateCustom(field.fieldKey, v))"
        />
      </n-form-item>
    </n-form>
    <n-empty v-if="!sidebarFields.length" description="暂无字段" size="small" />
  </n-scrollbar>
</template>
