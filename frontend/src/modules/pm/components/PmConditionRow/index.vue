<script setup lang="ts">
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, QueryCondition, QueryOperator } from '@/modules/pm/types'
import { OPERATORS } from '@/modules/pm/types'
import type { EntityId } from '@/modules/pm/utils/id'

const props = withDefaults(
  defineProps<{
    fieldDefs: FieldDefinition[]
    condition: QueryCondition
    projectId?: EntityId
    /** 行首「当」前缀，列表筛选默认开启 */
    showWhen?: boolean
  }>(),
  { showWhen: true },
)

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
  } else if (operator === 'IN' || operator === 'NOT_IN') {
    const cur = props.condition.value
    const next = Array.isArray(cur) ? cur : cur == null || cur === '' ? [] : [cur]
    patch({ operator, value: next })
  } else {
    const cur = props.condition.value
    patch({ operator, value: Array.isArray(cur) ? (cur[0] ?? null) : cur })
  }
}

const hideValue = computed(() =>
  ['IS_NULL', 'IS_NOT_NULL'].includes(props.condition.operator),
)
</script>

<template>
  <div class="condition-row">
    <n-text v-if="showWhen" depth="3" class="when-prefix">当</n-text>
    <n-select
      class="field-select"
      size="small"
      :value="condition.field"
      :options="fieldOptions"
      placeholder="字段"
      filterable
      @update:value="onFieldChange"
    />
    <n-select
      class="operator-select"
      size="small"
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
        :operator="condition.operator"
        mode="query"
        @update:model-value="(v) => patch({ value: v })"
      />
    </div>
    <n-text v-else-if="hideValue" depth="3" class="value-hint">无需填写值</n-text>
  </div>
</template>

<style scoped>
.condition-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}

.when-prefix {
  flex-shrink: 0;
  font-size: 13px;
  line-height: 28px;
}

.field-select {
  width: 140px;
  flex-shrink: 0;
}

.operator-select {
  width: 120px;
  flex-shrink: 0;
}

.value-slot {
  flex: 1;
  min-width: 160px;
}

.value-hint {
  font-size: 12px;
  line-height: 28px;
}

@media (max-width: 720px) {
  .field-select,
  .operator-select,
  .value-slot {
    width: 100%;
    min-width: 0;
  }
}
</style>
