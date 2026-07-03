<script setup lang="ts">
import PmConditionGroup from '@/modules/pm/components/PmConditionGroup/index.vue'
import type { FieldDefinition, QueryConditionGroup, QuerySpec } from '@/modules/pm/types'
import { emptyQuerySpec } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  modelValue: QuerySpec
}>()

const emit = defineEmits<{ 'update:modelValue': [QuerySpec] }>()

const rootGroup = computed({
  get: () => ({
    logic: props.modelValue.logic || 'AND',
    conditions: props.modelValue.conditions || [],
    groups: props.modelValue.groups || [],
  }),
  set: (group) => emit('update:modelValue', { ...props.modelValue, ...group }),
})

function reset() {
  emit('update:modelValue', emptyQuerySpec(props.modelValue.projectId, props.modelValue.typeCode))
}
function onGroupUpdate(g: QueryConditionGroup) {
  emit('update:modelValue', {
    ...props.modelValue,
    logic: g.logic,
    conditions: g.conditions,
    groups: g.groups,
  })
}
</script>

<template>
  <n-card title="查询条件" size="small">
    <PmConditionGroup :field-defs="fieldDefs" :group="rootGroup" @update:group="onGroupUpdate" />
    <template #footer>
      <n-space justify="end">
        <n-button @click="reset">重置</n-button>
      </n-space>
    </template>
  </n-card>
</template>
