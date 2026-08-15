import { describe, expect, it, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'

const { dialogWarning } = vi.hoisted(() => ({
  dialogWarning: vi.fn(),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual('naive-ui')
  return {
    ...actual,
    useDialog: () => ({
      warning: dialogWarning,
    }),
    useMessage: () => ({
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
    }),
  }
})

import RequestTabBar from './RequestTabBar.vue'

describe('RequestTabBar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    dialogWarning.mockReset()
  })

  it('renders method, title and dirty marker', () => {
    const workspace = useWorkspaceStore()
    const tab = workspace.openOrFocusTab({
      source: 'definition',
      refId: 1,
      definitionId: 1,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })
    workspace.patchDraft(tab.id, { url: '/login?x=1' })

    const wrapper = mount(RequestTabBar)
    expect(wrapper.text()).toContain('POST')
    expect(wrapper.text()).toContain('Login')
    expect(wrapper.text()).toContain('●')
  })

  it('hides method chip when method is empty', () => {
    useWorkspaceStore().openOrFocusCollectionOverview(1, 'Demo')
    const wrapper = mount(RequestTabBar)
    expect(wrapper.find('.request-tab-bar__method').exists()).toBe(false)
    expect(wrapper.text()).toContain('Demo')
  })

  it('plus button opens a scratch tab', async () => {
    const workspace = useWorkspaceStore()
    const wrapper = mount(RequestTabBar)
    await wrapper.get('[data-testid="tab-add"]').trigger('click')
    expect(workspace.tabs).toHaveLength(1)
    expect(workspace.tabs[0].source).toBe('scratch')
  })

  it('clicking a tab activates it', async () => {
    const workspace = useWorkspaceStore()
    const t1 = workspace.openScratchTab()
    const t2 = workspace.openScratchTab()
    workspace.setActiveTab(t1.id)

    const wrapper = mount(RequestTabBar)
    await wrapper.get(`[data-testid="tab-${t2.id}"]`).trigger('click')
    expect(workspace.activeTabId).toBe(t2.id)
  })

  it('closes a clean tab immediately', async () => {
    const workspace = useWorkspaceStore()
    const tab = workspace.openScratchTab()
    const wrapper = mount(RequestTabBar)
    await wrapper.get(`[data-testid="tab-close-${tab.id}"]`).trigger('click')
    expect(workspace.tabs).toHaveLength(0)
    expect(dialogWarning).not.toHaveBeenCalled()
  })

  it('confirms before closing a dirty tab', async () => {
    const workspace = useWorkspaceStore()
    const tab = workspace.openScratchTab()
    workspace.patchDraft(tab.id, { url: 'https://example.com' })

    const wrapper = mount(RequestTabBar)
    await wrapper.get(`[data-testid="tab-close-${tab.id}"]`).trigger('click')
    expect(workspace.tabs).toHaveLength(1)
    expect(dialogWarning).toHaveBeenCalled()
    const arg = dialogWarning.mock.calls[0][0]
    arg.onPositiveClick()
    expect(workspace.tabs).toHaveLength(0)
  })
})
