<template>
  <n-tag :type="tagType" :bordered="false" size="tiny">
    {{ label }}
  </n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  severity: string
}>()

const tagType = computed<'success' | 'info' | 'warning' | 'error'>(() => {
  switch (props.severity?.toLowerCase()) {
    case 'critical':
      return 'error'
    case 'high':
      return 'error'
    case 'medium':
      return 'warning'
    case 'low':
      return 'info'
    default:
      return 'success'
  }
})

const label = computed(() => {
  switch (props.severity?.toLowerCase()) {
    case 'critical':
      return '🔴 严重'
    case 'high':
      return '🔴 高危'
    case 'medium':
      return '🟡 中危'
    case 'low':
      return '🔵 低危'
    case 'none':
      return '🟢 安全'
    default:
      return '⚪ 未知'
  }
})
</script>