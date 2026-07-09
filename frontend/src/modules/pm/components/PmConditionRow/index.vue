<script setup lang="ts">
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, QueryCondition, QueryOperator } from '@/modules/pm/types'
import { OPERATORS } from '@/modules/pm/types'
import type { EntityId } from '@/modules/pm/utils/id'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  condition: QueryCondition
  projectId?: EntityId
}>()

const emit = defineEmits<{ update: [QueryCondition] }>()

const fieldOptions = computed(() =>
  props.fieldDefs.map((f) => ({
    label: f.fieldName,
    value: f.systemFlag === 1 ? f.fieldKey : `custom.${f.fieldKey}`,
  })),
)

const operatorOptions = computed(() => OPERATORS)

const selectedField = computed(() => {
  const key = props.condition.field?.replace(/^custom\./, '')
  return props.fieldDefs.find((f) => f.fieldKey === key || f.fieldKey === props.condition.field)
})

function patch(partial: Partial<QueryCondition>) {
  emit('update', { ...props.condition, ...partial })
}

function onFieldChange(field: string) {
  patch({ field, value: null, operator: 'EQ' })
}

function onOperatorChange(operator: QueryOperator) {
  if (operator === 'IS_NULL' || operator === 'IS_NOT_NULL') {
    patch({ operator, value: null })
  } else {
    patch({ operator })
  }
}

const hideValue = computed(() =>
  ['IS_NULL', 'IS_NOT_NULL'].includes(props.condition.operator),
)
</script>

<template>
  <div class="condition-row">
    <n-select
      class="field-select"
      :value="condition.field"
      :options="fieldOptions"
      placeholder="字段"
      filterable
      @update:value="onFieldChange"
    />
    <n-select
      class="operator-select"
      :value="condition.operator"
      :options="operatorOptions"
      placeholder="运算符"
      @update:value="onOperatorChange"
    />
    <div v-if="!hideValue && selectedField" class="value-slot">
      <PmFieldRenderer
        :field="selectedField"
        :model-value="condition.value"
        :project-id="projectId"
        mode="query"
        @update:model-value="(v) => patch({ value: v })"
      />
    </div>
    <n-text v-else-if="hideValue" depth="3" class="value-hint">无需填写值</n-text>
  </div>
</template>

<style scoped>
.condition-row {
  display: grid;
  grid-template-columns: minmax(140px, 1.1fr) minmax(110px, 0.8fr) minmax(160px, 1.4fr);
  gap: 8px;
  align-items: center;
  width: 100%;
}

.field-select,
.operator-select,
.value-slot {
  min-width: 0;
  width: 100%;
}

.value-hint {
  font-size: 12px;
}

@media (max-width: 720px) {
  .condition-row {
    grid-template-columns: 1fr;
  }
}
</style>
