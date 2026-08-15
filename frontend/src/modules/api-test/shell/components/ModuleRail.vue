<script setup lang="ts">
import { BookOpen, FileJson, FileStack, FolderTree, Globe, Layers } from '@lucide/vue'
import { storeToRefs } from 'pinia'
import type { Component } from 'vue'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { ShellModule } from '@/modules/api-test/shell/types/workspace'

const MODULES: Array<{ key: ShellModule; label: string; icon: Component }> = [
  { key: 'apis', label: '接口', icon: FileJson },
  { key: 'collections', label: '集合', icon: FolderTree },
  { key: 'environments', label: '环境', icon: Globe },
  { key: 'docs', label: '文档', icon: BookOpen },
  { key: 'specs', label: 'Specs', icon: FileStack },
  { key: 'mocks', label: 'Mocks', icon: Layers },
]

const workspace = useWorkspaceStore()
const { activeModule } = storeToRefs(workspace)
</script>

<template>
  <nav class="module-rail" aria-label="模块">
    <button
      v-for="item in MODULES"
      :key="item.key"
      type="button"
      class="module-rail__btn"
      :class="{ 'is-active': activeModule === item.key }"
      :title="item.label"
      :aria-label="item.label"
      :aria-pressed="activeModule === item.key"
      @click="workspace.setModule(item.key)"
    >
      <component :is="item.icon" :size="18" />
    </button>
  </nav>
</template>

<style scoped>
.module-rail {
  display: flex;
  flex-shrink: 0;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  width: 48px;
  padding: 8px 0;
  border-right: 1px solid var(--wb-border, #e5e7eb);
  background: var(--wb-card-bg, #fff);
}

.module-rail__btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--wb-muted, #6b7280);
  cursor: pointer;
}

.module-rail__btn:hover {
  background: var(--wb-chip-bg, #f8fafc);
  color: #2d80e6;
}

.module-rail__btn.is-active {
  background: rgba(64, 152, 252, 0.12);
  color: #2d80e6;
}
</style>
