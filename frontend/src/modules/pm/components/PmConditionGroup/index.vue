<script setup lang="ts">
import PmConditionRow from '@/modules/pm/components/PmConditionRow/index.vue'
import type { FieldDefinition, QueryCondition, QueryConditionGroup } from '@/modules/pm/types'

const props = withDefaults(
  defineProps<{
    fieldDefs: FieldDefinition[]
    group: QueryConditionGroup
    projectId?: number | string
    /** 是否展示且/或逻辑切换 */
    showLogic?: boolean
    /** 条件行是否带「当」前缀 */
    showWhen?: boolean
  }>(),
  { showLogic: true, showWhen: true },
)

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

defineExpose({ addCondition })
</script>

<template>
  <div class="condition-group">
    <div v-if="showLogic && group.conditions.length > 1" class="logic-bar">
      <n-radio-group
        :value="group.logic"
        size="small"
        @update:value="(v) => emit('update:group', { ...group, logic: v })"
      >
        <n-radio-button value="AND">满足全部（且）</n-radio-button>
        <n-radio-button value="OR">满足任一（或）</n-radio-button>
      </n-radio-group>
    </div>

    <div v-if="group.conditions.length" class="condition-list">
      <div v-for="(cond, idx) in group.conditions" :key="idx" class="condition-item">
        <n-button
          class="remove-btn"
          quaternary
          circle
          size="tiny"
          type="error"
          title="删除条件"
          @click="removeCondition(idx)"
        >
          −
        </n-button>
        <PmConditionRow
          class="condition-body"
          :field-defs="fieldDefs"
          :condition="cond"
          :project-id="projectId"
          :show-when="showWhen"
          @update="(c) => updateCondition(idx, c)"
        />
      </div>
    </div>

    <n-button
      class="add-btn"
      text
      type="primary"
      size="small"
      :disabled="!canAdd"
      @click="addCondition"
    >
      + 添加条件
    </n-button>
  </div>
</template>

<script lang="ts">
export default { name: 'PmConditionGroup' }
</script>

<style scoped>
.condition-group {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 100%;
}

.logic-bar {
  margin-bottom: 2px;
}

.condition-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.condition-item {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  min-width: 0;
}

.remove-btn {
  flex-shrink: 0;
  margin-top: 2px;
  font-size: 15px;
  font-weight: 500;
  line-height: 1;
  color: var(--pm-text-muted, #9ca3af) !important;
}

.condition-body {
  flex: 1;
  min-width: 0;
}

.add-btn {
  align-self: flex-start;
  padding-left: 2px;
  color: var(--pm-accent, #64748b) !important;
}
</style>
