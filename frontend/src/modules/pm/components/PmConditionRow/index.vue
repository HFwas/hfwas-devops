<script setup lang="ts">
import PmFieldRenderer from '@/modules/pm/components/PmFieldRenderer/index.vue'
import type { FieldDefinition, QueryCondition, QueryOperator } from '@/modules/pm/types'
import { OPERATORS } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  condition: QueryCondition
}>()

const emit = defineEmits<{ update: [QueryCondition] }>()

const fieldOptions = computed(() =>
  props.fieldDefs.map((f) => ({
    label: f.fieldName,
    value: f.systemFlag ? f.fieldKey : `custom.${f.fieldKey}`,
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
  <n-space align="center" wrap>
    <n-select
      :value="condition.field"
      :options="fieldOptions"
      placeholder="字段"
      style="width: 160px"
      @update:value="onFieldChange"
    />
    <n-select
      :value="condition.operator"
      :options="operatorOptions"
      placeholder="运算符"
      style="width: 120px"
      @update:value="onOperatorChange"
    />
    <div v-if="!hideValue && selectedField" style="min-width: 180px">
      <PmFieldRenderer
        :field="selectedField"
        :model-value="condition.value"
        mode="query"
        @update:model-value="(v) => patch({ value: v })"
      />
    </div>
  </n-space>
</template>
