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
})
