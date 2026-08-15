<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import PlaceholderPanel from '@/modules/api-test/shell/components/PlaceholderPanel.vue'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { ShellModule } from '@/modules/api-test/shell/types/workspace'

const workspace = useWorkspaceStore()
const { activeModule } = storeToRefs(workspace)

function isPlaceholder(module: ShellModule): module is 'docs' | 'specs' | 'mocks' {
  return module === 'docs' || module === 'specs' || module === 'mocks'
}

const placeholderModule = computed(() => {
  const module = activeModule.value
  return isPlaceholder(module) ? module : null
})
</script>

<template>
  <div class="resource-panel">
    <n-empty v-if="activeModule === 'apis'" description="接口树将在下一任务接入" />
    <n-empty v-else-if="activeModule === 'collections'" description="集合面板将在后续任务接入" />
    <n-empty v-else-if="activeModule === 'environments'" description="环境面板将在后续任务接入" />
    <PlaceholderPanel v-else-if="placeholderModule" :module="placeholderModule" />
  </div>
</template>

<style scoped>
.resource-panel {
  display: flex;
  flex: 1;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  min-height: 0;
  overflow: auto;
  padding: 16px 12px;
}
</style>
