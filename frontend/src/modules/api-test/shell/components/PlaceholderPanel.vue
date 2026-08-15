<script setup lang="ts">
import ComingSoonPane from '@/modules/api-test/shell/components/ComingSoonPane.vue'
import type { ShellModule } from '@/modules/api-test/shell/types/workspace'

const TITLES: Record<'docs' | 'specs' | 'mocks', string> = {
  docs: 'Docs',
  specs: 'Specs',
  mocks: 'Mocks',
}

defineProps<{
  module: Extract<ShellModule, 'docs' | 'specs' | 'mocks'>
}>()
</script>

<template>
  <ComingSoonPane :title="TITLES[module]" subtitle="即将支持" />
</template>
