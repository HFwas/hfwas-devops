import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import type { ApiDebugResultVO } from '@/modules/api-test/debug/types/debug'
import {
  emptyDraft,
  type OpenTabInput,
  type RequestDraft,
  type RequestTab,
  type ShellModule,
} from '@/modules/api-test/shell/types/workspace'

function newTabId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `tab-${Date.now()}-${Math.random()}`
}

export const useWorkspaceStore = defineStore('apiTestWorkspace', () => {
  const activeModule = ref<ShellModule>('apis')
  const tabs = ref<RequestTab[]>([])
  const activeTabId = ref<string | null>(null)
  const sidebarWidth = ref(280)
  const responseHeight = ref(320)

  const activeTab = computed(() => tabs.value.find((t) => t.id === activeTabId.value) ?? null)

  function setModule(module: ShellModule) {
    activeModule.value = module
  }

  function openOrFocusTab(input: OpenTabInput): RequestTab {
    if (input.refId != null) {
      const existing = tabs.value.find((t) => t.source === input.source && t.refId === input.refId)
      if (existing) {
        activeTabId.value = existing.id
        return existing
      }
    }

    const tab: RequestTab = {
      id: newTabId(),
      source: input.source,
      refId: input.refId,
      definitionId: input.definitionId,
      title: input.title,
      method: input.method,
      dirty: false,
      draft: input.draft,
      result: null,
      loadError: input.loadError,
    }
    tabs.value.push(tab)
    activeTabId.value = tab.id
    return tab
  }

  function openScratchTab(): RequestTab {
    return openOrFocusTab({
      source: 'scratch',
      title: 'Untitled Request',
      method: 'GET',
      draft: emptyDraft(),
    })
  }

  function setActiveTab(tabId: string) {
    if (tabs.value.some((t) => t.id === tabId)) {
      activeTabId.value = tabId
    }
  }

  function closeTab(tabId: string) {
    const index = tabs.value.findIndex((t) => t.id === tabId)
    if (index === -1) return

    tabs.value.splice(index, 1)

    if (activeTabId.value !== tabId) return

    if (tabs.value.length === 0) {
      activeTabId.value = null
      return
    }

    const nextIndex = Math.min(index, tabs.value.length - 1)
    activeTabId.value = tabs.value[nextIndex].id
  }

  function patchDraft(tabId: string, partial: Partial<RequestDraft>) {
    const tab = tabs.value.find((t) => t.id === tabId)
    if (!tab) return
    tab.draft = { ...tab.draft, ...partial }
    tab.dirty = true
  }

  function markClean(tabId: string) {
    const tab = tabs.value.find((t) => t.id === tabId)
    if (!tab) return
    tab.dirty = false
  }

  function setTabResult(tabId: string, result: ApiDebugResultVO | null) {
    const tab = tabs.value.find((t) => t.id === tabId)
    if (!tab) return
    tab.result = result
  }

  function setLayout(partial: { sidebarWidth?: number; responseHeight?: number }) {
    if (partial.sidebarWidth != null) {
      sidebarWidth.value = partial.sidebarWidth
    }
    if (partial.responseHeight != null) {
      responseHeight.value = partial.responseHeight
    }
  }

  return {
    activeModule,
    tabs,
    activeTabId,
    sidebarWidth,
    responseHeight,
    activeTab,
    setModule,
    openOrFocusTab,
    openScratchTab,
    closeTab,
    setActiveTab,
    patchDraft,
    markClean,
    setTabResult,
    setLayout,
  }
})
