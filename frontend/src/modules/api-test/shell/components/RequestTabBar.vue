<script setup lang="ts">
import { Plus, X } from '@lucide/vue'
import { storeToRefs } from 'pinia'
import { useDialog } from 'naive-ui'
import { HTTP_METHOD_OPTIONS } from '@/modules/api-test/define/types/definition'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import type { RequestTab } from '@/modules/api-test/shell/types/workspace'

const workspace = useWorkspaceStore()
const { tabs, activeTabId } = storeToRefs(workspace)
const dialog = useDialog()

function methodColor(method: string): string {
  return HTTP_METHOD_OPTIONS.find((o) => o.value === method)?.color || '#909399'
}

function onClose(event: MouseEvent, tab: RequestTab) {
  event.stopPropagation()
  if (!tab.dirty) {
    workspace.closeTab(tab.id)
    return
  }
  dialog.warning({
    title: '未保存的更改',
    content: '关闭后未保存的更改将丢失，确定关闭吗？',
    positiveText: '确定关闭',
    negativeText: '取消',
    onPositiveClick: () => {
      workspace.closeTab(tab.id)
    },
  })
}
</script>

<template>
  <div class="request-tab-bar" role="tablist">
    <button
      v-for="tab in tabs"
      :key="tab.id"
      type="button"
      role="tab"
      class="request-tab-bar__tab"
      :class="{ 'is-active': tab.id === activeTabId }"
      :aria-selected="tab.id === activeTabId"
      :data-testid="`tab-${tab.id}`"
      :title="tab.title"
      @click="workspace.setActiveTab(tab.id)"
    >
      <span
        v-if="tab.method"
        class="request-tab-bar__method"
        :style="{ color: methodColor(tab.method) }"
      >
        {{ tab.method }}
      </span>
      <span class="request-tab-bar__title">{{ tab.title }}</span>
      <span v-if="tab.dirty" class="request-tab-bar__dirty">●</span>
      <span
        class="request-tab-bar__close"
        :data-testid="`tab-close-${tab.id}`"
        @click="onClose($event, tab)"
      >
        <X :size="12" />
      </span>
    </button>
    <button
      type="button"
      class="request-tab-bar__add"
      data-testid="tab-add"
      title="新建请求"
      aria-label="新建请求"
      @click="workspace.openScratchTab()"
    >
      <Plus :size="14" />
    </button>
  </div>
</template>

<style scoped>
.request-tab-bar {
  display: flex;
  flex-shrink: 0;
  align-items: stretch;
  min-height: var(--api-row-height, 28px);
  overflow-x: auto;
  border-bottom: 1px solid var(--wb-border, #e5e7eb);
  color: inherit;
  background: var(--wb-card-bg, #fff);
}

.request-tab-bar__tab {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  gap: 4px;
  max-width: 220px;
  min-height: var(--api-row-height, 28px);
  padding: 0 var(--api-density-pad-x, 10px);
  border: none;
  border-right: 1px solid var(--wb-border, #e5e7eb);
  background: transparent;
  color: var(--wb-muted, #6b7280);
  cursor: pointer;
  font-size: 12px;
}

.request-tab-bar__tab.is-active {
  background: var(--wb-chip-bg, #f8fafc);
  color: inherit;
  font-weight: 500;
  box-shadow: inset 0 -2px 0 var(--api-test-accent, #4098fc);
}

.request-tab-bar__method {
  flex-shrink: 0;
  font-weight: 700;
  font-size: 11px;
}

.request-tab-bar__title {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.request-tab-bar__dirty {
  flex-shrink: 0;
  color: var(--api-test-accent, #4098fc);
  font-size: 10px;
  line-height: 1;
}

.request-tab-bar__close {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 16px;
  height: 16px;
  border-radius: 4px;
  color: var(--wb-muted, #6b7280);
}

.request-tab-bar__close:hover {
  background: var(--wb-chip-bg, #e5e7eb);
  color: inherit;
}

.request-tab-bar__add {
  display: inline-flex;
  flex-shrink: 0;
  align-items: center;
  justify-content: center;
  width: var(--api-row-height, 28px);
  min-height: var(--api-row-height, 28px);
  border: none;
  background: transparent;
  color: var(--wb-muted, #6b7280);
  cursor: pointer;
}

.request-tab-bar__add:hover {
  color: var(--api-test-accent-strong, #2d80e6);
}

html.dark .request-tab-bar__add:hover {
  color: var(--api-test-accent-strong, #82c4ff);
}
</style>
