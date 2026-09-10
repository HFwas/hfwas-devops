<template>
  <n-tag :type="tagType" :bordered="false" size="small">
    {{ statusText }}
  </n-tag>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import type { RegistryStatus } from '../../types/registry'

const props = defineProps<{
  status: RegistryStatus
}>()

const tagType = computed<'success' | 'error' | 'warning'>(() => {
  switch (props.status) {
    case 'Connected':
      return 'success'
    case 'Error':
      return 'error'
    default:
      return 'warning'
  }
})

const statusText = computed(() => {
  switch (props.status) {
    case 'Connected':
      return '在线'
    case 'Error':
      return '错误'
    default:
      return '未知'
  }
})
</script>