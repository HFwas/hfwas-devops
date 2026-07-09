<script setup lang="ts">
import PmConditionRow from '@/modules/pm/components/PmConditionRow/index.vue'
import type { FieldDefinition, QueryCondition, QueryConditionGroup } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  group: QueryConditionGroup
  projectId?: number | string
}>()

const emit = defineEmits<{ 'update:group': [QueryConditionGroup] }>()

const canAdd = computed(() => props.fieldDefs.length > 0)

function updateCondition(index: number, condition: QueryCondition) {
  const conditions = [...props.group.conditions]
  conditions[index] = condition
  emit('update:group', { ...props.group, conditions })
}

function addCondition() {
  if (!canAdd.value) return
  const first = props.fieldDefs[0]
  const field = first.systemFlag === 1 ? first.fieldKey : `custom.${first.fieldKey}`
  emit('update:group', {
    ...props.group,
    conditions: [...props.group.conditions, { field, operator: 'EQ', value: null }],
  })
}

function removeCondition(index: number) {
  const conditions = props.group.conditions.filter((_, i) => i !== index)
  emit('update:group', { ...props.group, conditions })
}
</script>

<template>
  <n-space vertical :size="12" style="width: 100%">
    <n-space align="center" justify="space-between">
      <n-radio-group
        :value="group.logic"
        size="small"
        @update:value="(v) => emit('update:group', { ...group, logic: v })"
      >
        <n-radio-button value="AND">满足全部（且）</n-radio-button>
        <n-radio-button value="OR">满足任一（或）</n-radio-button>
      </n-radio-group>
      <n-button size="small" dashed :disabled="!canAdd" @click="addCondition">添加条件</n-button>
    </n-space>

    <n-empty
      v-if="!group.conditions.length"
      description="尚未添加筛选条件"
      size="small"
      style="padding: 8px 0 4px"
    />

    <div v-else class="condition-list">
      <div v-for="(cond, idx) in group.conditions" :key="idx" class="condition-item">
        <div class="condition-index">
          <n-text depth="3">{{ idx + 1 }}</n-text>
        </div>
        <div class="condition-body">
          <PmConditionRow
            :field-defs="fieldDefs"
            :condition="cond"
            :project-id="projectId"
            @update="(c) => updateCondition(idx, c)"
          />
        </div>
        <n-button quaternary type="error" size="small" @click="removeCondition(idx)">删除</n-button>
      </div>
    </div>
  </n-space>
</template>

<script lang="ts">
export default { name: 'PmConditionGroup' }
</script>

<style scoped>
.condition-list {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.condition-item {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  border: 1px solid var(--n-border-color);
  border-radius: var(--n-border-radius);
  background: var(--n-color-embedded, var(--n-action-color));
}

.condition-index {
  width: 20px;
  padding-top: 6px;
  text-align: center;
  flex-shrink: 0;
}

.condition-body {
  flex: 1;
  min-width: 0;
}
</style>
