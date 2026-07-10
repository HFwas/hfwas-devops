<script setup lang="ts">
import PmConditionGroup from '@/modules/pm/components/PmConditionGroup/index.vue'
import { useProjectModules } from '@/modules/pm/composables/useProjectModules'
import { useStatusOptions } from '@/modules/pm/composables/useStatusOptions'
import { useUserOptions } from '@/modules/pm/composables/useUserOptions'
import type {
  FieldDefinition,
  QueryCondition,
  QueryConditionGroup,
  QuerySpec,
} from '@/modules/pm/types'
import { OPERATORS, PRIORITY_OPTIONS, emptyQuerySpec } from '@/modules/pm/types'
import { asId } from '@/modules/pm/utils/id'

const props = withDefaults(
  defineProps<{
    fieldDefs: FieldDefinition[]
    modelValue: QuerySpec
    expanded?: boolean
  }>(),
  { expanded: false },
)

const emit = defineEmits<{
  'update:modelValue': [QuerySpec]
  'update:expanded': [boolean]
  search: []
}>()

const groupRef = ref<InstanceType<typeof PmConditionGroup> | null>(null)

const projectId = computed(() => props.modelValue.projectId)
const typeCode = computed(() => props.modelValue.typeCode)
const { labelMap: statusLabelMap } = useStatusOptions(projectId, typeCode)
const { labelMap: userLabelMap } = useUserOptions()
const { labelMap: moduleLabelMap } = useProjectModules(projectId)

const searchableFields = computed(() => props.fieldDefs.filter((f) => f.searchable))
const hasSearchable = computed(() => searchableFields.value.length > 0)
const conditionCount = computed(() => props.modelValue.conditions?.length ?? 0)

const rootGroup = computed({
  get: () => ({
    logic: props.modelValue.logic || 'AND',
    conditions: props.modelValue.conditions || [],
    groups: props.modelValue.groups || [],
  }),
  set: (group) => emit('update:modelValue', { ...props.modelValue, ...group }),
})

const fieldByQueryKey = computed(() => {
  const map = new Map<string, FieldDefinition>()
  for (const f of props.fieldDefs) {
    const key = f.systemFlag === 1 ? f.fieldKey : `custom.${f.fieldKey}`
    map.set(key, f)
    map.set(f.fieldKey, f)
  }
  return map
})

const operatorLabelMap = computed(() =>
  Object.fromEntries(OPERATORS.map((o) => [o.value, o.label])),
)

const priorityLabelMap = computed(() =>
  Object.fromEntries(PRIORITY_OPTIONS.map((o) => [o.value, o.label])),
)

const conditionSummaries = computed(() =>
  (props.modelValue.conditions ?? [])
    .map((c, idx) => {
      const text = formatCondition(c)
      return text ? { idx, text } : null
    })
    .filter(Boolean) as Array<{ idx: number; text: string }>,
)

function resolveField(queryField: string): FieldDefinition | undefined {
  return fieldByQueryKey.value.get(queryField)
}

function selectOptionLabel(field: FieldDefinition, raw: string): string {
  const options = field.config?.options as Array<{ label: string; value: string }> | undefined
  const hit = options?.find((o) => String(o.value) === raw)
  return hit?.label ?? raw
}

function formatSingleValue(field: FieldDefinition | undefined, raw: unknown): string {
  if (raw == null || raw === '') return ''
  if (typeof raw === 'boolean') return raw ? '是' : '否'
  const text = String(raw)
  if (!field) return text

  if (field.fieldKey === 'status' || field.fieldType === 'STATUS') {
    return statusLabelMap.value[text] ?? text
  }
  if (field.fieldKey === 'priority' || field.fieldType === 'PRIORITY') {
    return priorityLabelMap.value[text] ?? text
  }
  if (field.fieldType === 'USER') {
    return userLabelMap.value[asId(raw as string | number)] ?? text
  }
  if (field.fieldType === 'MODULE') {
    return moduleLabelMap.value[asId(raw as string | number)] ?? text
  }
  if (field.fieldType === 'SELECT' || field.fieldType === 'MULTI_SELECT') {
    return selectOptionLabel(field, text)
  }
  return text
}

function formatValue(field: FieldDefinition | undefined, value: unknown): string {
  if (value == null || value === '') return ''
  if (Array.isArray(value)) {
    return value
      .map((v) => formatSingleValue(field, v))
      .filter(Boolean)
      .join(',')
  }
  return formatSingleValue(field, value)
}

function formatCondition(c: QueryCondition): string | null {
  if (!c.field) return null
  const def = resolveField(c.field)
  const fieldName = def?.fieldName ?? c.field
  const op = operatorLabelMap.value[c.operator] ?? c.operator
  if (c.operator === 'IS_NULL' || c.operator === 'IS_NOT_NULL') {
    return `${fieldName} ${op}`
  }
  const val = formatValue(def, c.value)
  if (!val) return `${fieldName} ${op}`
  return `${fieldName} ${op} '${val}'`
}

function removeCondition(index: number) {
  const conditions = (props.modelValue.conditions ?? []).filter((_, i) => i !== index)
  emit('update:modelValue', { ...props.modelValue, conditions })
  emit('search')
}

function reset() {
  emit('update:modelValue', emptyQuerySpec(props.modelValue.projectId, props.modelValue.typeCode))
  emit('search')
}

function onGroupUpdate(g: QueryConditionGroup) {
  emit('update:modelValue', {
    ...props.modelValue,
    logic: g.logic,
    conditions: g.conditions,
    groups: g.groups,
  })
}

function onSearch() {
  emit('search')
  emit('update:expanded', false)
}

watch(
  () => props.expanded,
  (open) => {
    if (open && hasSearchable.value && conditionCount.value === 0) {
      nextTick(() => groupRef.value?.addCondition())
    }
  },
)
</script>

<template>
  <div class="pm-query">
    <div v-if="!expanded && conditionSummaries.length" class="pm-query-chips">
      <span v-for="item in conditionSummaries" :key="item.idx" class="pm-chip">
        <span class="pm-chip-text">{{ item.text }}</span>
        <button type="button" class="pm-chip-x" title="移除" @click="removeCondition(item.idx)">
          ×
        </button>
      </span>
      <button type="button" class="pm-chip-clear" @click="reset">清空</button>
    </div>

    <div v-if="expanded" class="pm-query-panel">
      <n-empty
        v-if="!hasSearchable"
        description="当前事项类型未启用搜索字段"
        size="small"
        style="padding: 4px 0"
      >
        <template #extra>
          <n-text depth="3" style="font-size: 12px">
            请在「设置 → 事项配置」中为字段开启「搜索」
          </n-text>
        </template>
      </n-empty>

      <template v-else>
        <div class="pm-query-panel-body">
          <PmConditionGroup
            ref="groupRef"
            :field-defs="searchableFields"
            :group="rootGroup"
            :project-id="modelValue.projectId"
            @update:group="onGroupUpdate"
          />
        </div>
        <div class="pm-query-panel-footer">
          <n-button quaternary size="small" :disabled="!conditionCount" @click="reset">
            重置
          </n-button>
          <n-button type="primary" size="small" @click="onSearch">搜索</n-button>
        </div>
      </template>
    </div>
  </div>
</template>

<style scoped>
.pm-query {
  width: 100%;
}

.pm-query-chips {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--pm-border-soft, #eef0f3);
  background: #fafbfc;
}

.pm-chip {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  max-width: 100%;
  padding: 2px 4px 2px 8px;
  border-radius: 4px;
  background: #fff;
  border: 1px solid var(--pm-border, #e8eaed);
  color: var(--pm-text-secondary, #646a73);
  font-size: 12px;
  line-height: 1.5;
}

.pm-chip-text {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pm-chip-x {
  border: none;
  background: transparent;
  color: var(--pm-text-muted, #8f959e);
  cursor: pointer;
  font-size: 14px;
  line-height: 1;
  padding: 0 4px;
}

.pm-chip-x:hover {
  color: var(--pm-text, #1f2329);
}

.pm-chip-clear {
  border: none;
  background: transparent;
  color: var(--pm-primary, #3370ff);
  cursor: pointer;
  font-size: 12px;
  padding: 0 4px;
}

.pm-query-panel {
  padding: 10px 12px;
  border-bottom: 1px solid var(--pm-border-soft, #eef0f3);
  background: #fafbfc;
}

.pm-query-panel-body {
  margin-bottom: 10px;
}

.pm-query-panel-footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding-top: 10px;
  border-top: 1px solid var(--pm-border-soft, #eef0f3);
}
</style>
