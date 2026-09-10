<script setup lang="ts">
import { NTag } from 'naive-ui'
import type { ClusterStatus } from '@/modules/container/types/cluster'

const props = defineProps<{
  status: ClusterStatus | string
  size?: 'small' | 'tiny' | 'medium'
}>()

const tagTypeMap: Record<string, 'success' | 'warning' | 'error' | 'default' | 'info'> = {
  Connected: 'success',
  Running: 'success',
  Degraded: 'warning',
  Pending: 'warning',
  Disconnected: 'error',
  Failed: 'error',
  Unknown: 'default',
  Succeeded: 'info',
}
</script>

<template>
  <n-tag :type="tagTypeMap[props.status] || 'default'" :size="props.size || 'small'">
    {{ props.status }}
  </n-tag>
</template>