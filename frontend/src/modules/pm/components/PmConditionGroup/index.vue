<script setup lang="ts">
import PmConditionRow from '@/modules/pm/components/PmConditionRow/index.vue'
import type { FieldDefinition, QueryCondition, QueryConditionGroup, QuerySpec } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  group: QueryConditionGroup
  projectId?: number
}>()

const emit = defineEmits<{ 'update:group': [QueryConditionGroup] }>()

function updateCondition(index: number, condition: QueryCondition) {
  const conditions = [...props.group.conditions]
  conditions[index] = condition
  emit('update:group', { ...props.group, conditions })
}

function addCondition() {
  emit('update:group', {
    ...props.group,
    conditions: [...props.group.conditions, { field: props.fieldDefs[0]?.fieldKey || 'title', operator: 'EQ', value: null }],
  })
}

function removeCondition(index: number) {
  const conditions = props.group.conditions.filter((_, i) => i !== index)
  emit('update:group', { ...props.group, conditions })
}
</script>

<template>
  <n-space vertical :size="8" style="width: 100%">
    <n-radio-group
      :value="group.logic"
      size="small"
      @update:value="(v) => emit('update:group', { ...group, logic: v })"
    >
      <n-radio-button value="AND">且</n-radio-button>
      <n-radio-button value="OR">或</n-radio-button>
    </n-radio-group>
    <n-space v-for="(cond, idx) in group.conditions" :key="idx" align="center">
      <PmConditionRow
        :field-defs="fieldDefs"
        :condition="cond"
        :project-id="projectId"
        @update="(c) => updateCondition(idx, c)"
      />
      <n-button quaternary type="error" @click="removeCondition(idx)">删除</n-button>
    </n-space>
    <n-button dashed block @click="addCondition">添加条件</n-button>
  </n-space>
</template>

<script lang="ts">
export default { name: 'PmConditionGroup' }
</script>
