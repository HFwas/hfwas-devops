<script setup lang="ts">
import { storeToRefs } from 'pinia'
import { computed } from 'vue'
import PlaceholderPanel from '@/modules/api-test/shell/components/PlaceholderPanel.vue'
import ApiTreePanel from '@/modules/api-test/shell/components/ApiTreePanel.vue'
import CollectionPanel from '@/modules/api-test/shell/components/CollectionPanel.vue'
import EnvironmentPanel from '@/modules/api-test/shell/components/EnvironmentPanel.vue'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { ShellModule } from '@/modules/api-test/shell/types/workspace'

const emit = defineEmits<{
  loaded: []
  run: [collectionId: number]
  history: [collectionId: number]
}>()

const workspace = useWorkspaceStore()
const { activeModule } = storeToRefs(workspace)

function isPlaceholder(module: ShellModule): module is 'docs' | 'specs' | 'mocks' {
  return module === 'docs' || module === 'specs' || module === 'mocks'
}

const placeholderModule = computed(() => {
  const module = activeModule.value
  return isPlaceholder(module) ? module : null
})

const fillPanel = computed(() =>
  activeModule.value === 'apis'
  || activeModule.value === 'environments'
  || activeModule.value === 'collections',
)
</script>

<template>
  <div class="resource-panel" :class="{ 'resource-panel--fill': fillPanel }">
    <ApiTreePanel v-show="activeModule === 'apis'" @loaded="emit('loaded')" />
    <EnvironmentPanel v-if="activeModule === 'environments'" />
    <CollectionPanel
      v-else-if="activeModule === 'collections'"
      @run="emit('run', $event)"
      @history="emit('history', $event)"
    />
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

.resource-panel--fill {
  align-items: stretch;
  justify-content: flex-start;
  overflow: hidden;
  padding: 0;
}
</style>
