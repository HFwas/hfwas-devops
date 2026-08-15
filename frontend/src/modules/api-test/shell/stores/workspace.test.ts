import { createPinia, setActivePinia } from 'pinia'
import { beforeEach, describe, expect, it } from 'vitest'
import { useWorkspaceStore } from './workspace'
import { emptyDraft } from '../types/workspace'

describe('useWorkspaceStore', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('openOrFocusTab reuses same source+refId', () => {
    const store = useWorkspaceStore()
    const a = store.openOrFocusTab({
      source: 'definition',
      refId: 10,
      definitionId: 10,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })
    const b = store.openOrFocusTab({
      source: 'definition',
      refId: 10,
      definitionId: 10,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })
    expect(a.id).toBe(b.id)
    expect(store.tabs).toHaveLength(1)
    expect(store.activeTabId).toBe(a.id)
  })

  it('openScratchTab adds blank dirty=false tab', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    expect(tab.source).toBe('scratch')
    expect(tab.dirty).toBe(false)
    expect(store.tabs).toHaveLength(1)
  })

  it('patchDraft marks dirty', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    store.patchDraft(tab.id, { url: 'https://example.com' })
    expect(store.tabs[0].dirty).toBe(true)
    expect(store.tabs[0].draft.url).toBe('https://example.com')
  })

  it('closeTab removes tab and activates neighbor', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openScratchTab()
    store.setActiveTab(t2.id)
    store.closeTab(t1.id)
    expect(store.tabs.map((t) => t.id)).toEqual([t2.id])
    expect(store.activeTabId).toBe(t2.id)
  })

  it('setTabResult isolates per tab', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openScratchTab()
    store.setTabResult(t1.id, { durationMs: 12, status: 'SUCCESS', requestUrl: '/', requestMethod: 'GET' } as any)
    expect(store.tabs.find((t) => t.id === t1.id)?.result?.durationMs).toBe(12)
    expect(store.tabs.find((t) => t.id === t2.id)?.result).toBeNull()
  })

  it('open multiple tabs activates the last one', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openOrFocusTab({
      source: 'definition',
      refId: 1,
      definitionId: 1,
      title: 'API 1',
      method: 'GET',
      draft: emptyDraft({ url: '/api/1' }),
    })
    const t3 = store.openOrFocusTab({
      source: 'definition',
      refId: 2,
      definitionId: 2,
      title: 'API 2',
      method: 'POST',
      draft: emptyDraft({ url: '/api/2', method: 'POST' }),
    })
    expect(store.tabs).toHaveLength(3)
    expect(store.activeTabId).toBe(t3.id)
  })

  it('closeTab on non-active tab does not change activeTabId', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openScratchTab()
    const t3 = store.openScratchTab()
    store.setActiveTab(t2.id)
    store.closeTab(t1.id)
    expect(store.activeTabId).toBe(t2.id)
    expect(store.tabs).toHaveLength(2)
  })

  it('closeTab on active tab switches to previous', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    const t2 = store.openScratchTab()
    store.setActiveTab(t2.id)
    store.closeTab(t2.id)
    expect(store.activeTabId).toBe(t1.id)
    expect(store.tabs).toHaveLength(1)
  })

  it('closeTab on last tab sets activeTabId to null', () => {
    const store = useWorkspaceStore()
    const t1 = store.openScratchTab()
    store.closeTab(t1.id)
    expect(store.activeTabId).toBeNull()
    expect(store.tabs).toHaveLength(0)
  })

  it('patchDraft with method update marks dirty', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    store.patchDraft(tab.id, { method: 'POST' })
    expect(store.tabs[0].dirty).toBe(true)
    expect(store.tabs[0].draft.method).toBe('POST')
  })

  it('markClean resets dirty flag', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    store.patchDraft(tab.id, { url: '/changed' })
    expect(store.tabs[0].dirty).toBe(true)
    store.markClean(tab.id)
    expect(store.tabs[0].dirty).toBe(false)
  })

  it('setActiveTab ignores non-existent tab id', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    store.setActiveTab('non-existent')
    expect(store.activeTabId).toBe(tab.id)
  })

  it('setModule switches active module', () => {
    const store = useWorkspaceStore()
    expect(store.activeModule).toBe('apis')
    store.setModule('environments')
    expect(store.activeModule).toBe('environments')
    store.setModule('collections')
    expect(store.activeModule).toBe('collections')
  })

  it('setLayout updates sidebar and response heights', () => {
    const store = useWorkspaceStore()
    store.setLayout({ sidebarWidth: 320, responseHeight: 400 })
    expect(store.sidebarWidth).toBe(320)
    expect(store.responseHeight).toBe(400)
  })

  it('setLayout with partial update', () => {
    const store = useWorkspaceStore()
    store.setLayout({ sidebarWidth: 300 })
    expect(store.sidebarWidth).toBe(300)
    expect(store.responseHeight).toBe(320)
  })

  it('activeTab is null when no tabs', () => {
    const store = useWorkspaceStore()
    expect(store.activeTab).toBeNull()
  })

  it('activeTab returns the active tab', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    expect(store.activeTab?.id).toBe(tab.id)
  })

  it('openOrFocusTab without refId always creates new tab', () => {
    const store = useWorkspaceStore()
    const t1 = store.openOrFocusTab({
      source: 'scratch',
      title: 'New',
      method: 'GET',
      draft: emptyDraft(),
    })
    const t2 = store.openOrFocusTab({
      source: 'scratch',
      title: 'New',
      method: 'GET',
      draft: emptyDraft(),
    })
    expect(t1.id).not.toBe(t2.id)
    expect(store.tabs).toHaveLength(2)
  })

  it('setTabResult with null clears result', () => {
    const store = useWorkspaceStore()
    const tab = store.openScratchTab()
    store.setTabResult(tab.id, { durationMs: 100, status: 'SUCCESS', requestUrl: '/', requestMethod: 'GET' } as any)
    expect(store.tabs[0].result).not.toBeNull()
    store.setTabResult(tab.id, null)
    expect(store.tabs[0].result).toBeNull()
  })
})