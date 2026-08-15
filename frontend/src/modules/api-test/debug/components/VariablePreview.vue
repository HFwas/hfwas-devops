<template>
  <div class="variable-preview">
    <div class="variable-preview__header">
      <span class="variable-preview__title">变量预览</span>
      <n-tag v-if="environmentName" size="tiny" type="info">
        {{ environmentName }}
      </n-tag>
      <n-tag v-else size="tiny" type="warning">未选择环境</n-tag>
    </div>
    <div class="variable-preview__list" v-if="variables && Object.keys(variables).length > 0">
      <div v-for="(value, key) in variables" :key="key" class="variable-preview__item">
        <code class="variable-preview__name">{{ key }}</code>
        <span class="variable-preview__arrow">→</span>
        <code class="variable-preview__value">{{ value }}</code>
      </div>
    </div>
    <n-empty v-else-if="!environmentAllVariables" description="无变量" />
  </div>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'

const props = defineProps<{
  environmentId: number | null
}>()

const environmentStore = useEnvironmentStore()

const environmentName = computed(() => {
  if (!props.environmentId) return null
  const env = environmentStore.allList.find(e => e.id === props.environmentId)
  return env?.name || null
})

const environmentAllVariables = computed(() => {
  if (!props.environmentId) return null
  return environmentStore.currentDetail?.variables || null
})

const variables = computed(() => {
  if (!environmentAllVariables.value) return {}
  const result: Record<string, string> = {}
  for (const v of environmentAllVariables.value) {
    result[v.name] = v.isSecret ? '******' : v.value
  }
  return result
})
</script>

<style scoped>
.variable-preview {
  padding: 8px;
  background: #fafafa;
  border-radius: 4px;
  border: 1px solid #eee;
}

.variable-preview__header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.variable-preview__title {
  font-size: 13px;
  font-weight: 500;
  color: #666;
}

.variable-preview__list {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.variable-preview__item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 4px;
  font-size: 13px;
}

.variable-preview__name {
  color: #409EFF;
  font-weight: 600;
}

.variable-preview__arrow {
  color: #999;
}

.variable-preview__value {
  color: #333;
}
</style>