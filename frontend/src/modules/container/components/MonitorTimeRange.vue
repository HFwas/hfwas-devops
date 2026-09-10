<script setup lang="ts">
import type { MonitorRange } from '@/modules/container/types/monitor'
import { RANGE_STEP_MAP } from '@/modules/container/types/monitor'

const props = defineProps<{
  value: MonitorRange
}>()

const emit = defineEmits<{
  'update:value': [range: MonitorRange]
}>()

const options: { value: MonitorRange; label: string }[] = Object.entries(RANGE_STEP_MAP).map(([value, config]) => ({
  value: value as MonitorRange,
  label: config.label,
}))
</script>

<template>
  <n-space align="center" style="margin-bottom: 12px">
    <n-radio-group :value="value" @update:value="(v: MonitorRange) => emit('update:value', v)">
      <n-radio-button
        v-for="opt in options"
        :key="opt.value"
        :value="opt.value"
        :label="opt.label"
      />
    </n-radio-group>
    <slot />
  </n-space>
</template>