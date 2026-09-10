<script setup lang="ts">
defineProps<{
  title: string
  value: string | number
  loading?: boolean
  unit?: string
}>()
</script>

<template>
  <n-card :bordered="false" size="small">
    <n-spin :show="loading" size="small">
      <n-statistic :title="title" :value="value" :precision="Number(value) !== 0 && Math.abs(Number(value)) < 0.01 ? 2 : 1">
        <template v-if="unit" #suffix>
          <span style="font-size: 13px; opacity: 0.6">{{ unit }}</span>
        </template>
      </n-statistic>
    </n-spin>
  </n-card>
</template>