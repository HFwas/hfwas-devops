<script setup lang="ts">
import PmConditionGroup from '@/modules/pm/components/PmConditionGroup/index.vue'
import type { FieldDefinition, QueryConditionGroup, QuerySpec } from '@/modules/pm/types'
import { emptyQuerySpec } from '@/modules/pm/types'

const props = defineProps<{
  fieldDefs: FieldDefinition[]
  modelValue: QuerySpec
}>()

const emit = defineEmits<{
  'update:modelValue': [QuerySpec]
  search: []
}>()

const searchableFields = computed(() => props.fieldDefs.filter((f) => f.searchable))
const hasSearchable = computed(() => searchableFields.value.length > 0)
const conditionCount = computed(
  () => (props.modelValue.conditions?.length ?? 0) + (props.modelValue.groups?.length ?? 0),
)

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
</script>

<template>
  <n-card size="small" class="pm-query-card" :bordered="true">
    <template #header>
      <n-space align="center" justify="space-between" style="width: 100%">
        <n-space align="center" :size="8">
          <n-text strong>筛选条件</n-text>
          <n-tag v-if="conditionCount" size="small" :bordered="false" type="info">
            {{ conditionCount }} 条
          </n-tag>
        </n-space>
        <n-text depth="3" style="font-size: 12px">
          {{ hasSearchable ? '组合条件后点击查询' : '暂无可用搜索字段' }}
        </n-text>
      </n-space>
    </template>

    <n-empty
      v-if="!hasSearchable"
      description="当前事项类型未启用搜索字段"
      size="small"
      style="padding: 12px 0"
    >
      <template #extra>
        <n-text depth="3" style="font-size: 12px">
          请在「设置 → 事项配置」中为字段开启「搜索」展示
        </n-text>
      </template>
    </n-empty>

    <PmConditionGroup
      v-else
      :field-defs="searchableFields"
      :group="rootGroup"
      :project-id="modelValue.projectId"
      @update:group="onGroupUpdate"
    />

    <template #footer>
      <n-space justify="end">
        <n-button quaternary :disabled="!hasSearchable && !conditionCount" @click="reset">重置</n-button>
        <n-button type="primary" :disabled="!hasSearchable" @click="emit('search')">查询</n-button>
      </n-space>
    </template>
  </n-card>
</template>

<style scoped>
.pm-query-card :deep(.n-card-header) {
  padding: 12px 16px;
}

.pm-query-card :deep(.n-card__content) {
  padding: 12px 16px 8px;
}

.pm-query-card :deep(.n-card__footer) {
  padding: 10px 16px 12px;
  border-top: 1px solid var(--n-border-color);
}
</style>
