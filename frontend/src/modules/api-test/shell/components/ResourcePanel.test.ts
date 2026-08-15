import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import ResourcePanel from './ResourcePanel.vue'

describe('ResourcePanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  function mountPanel() {
    return mount(ResourcePanel, {
      global: {
        stubs: {
          ApiTreePanel: { template: '<div data-testid="api-tree-panel" />' },
          EnvironmentPanel: { template: '<div data-testid="environment-panel" />' },
          CollectionPanel: { template: '<div data-testid="collection-panel" />' },
          PlaceholderPanel: true,
          NEmpty: {
            props: ['description'],
            template: '<div data-testid="empty">{{ description }}</div>',
          },
        },
      },
    })
  }

  it('shows EnvironmentPanel when activeModule is environments', () => {
    const workspace = useWorkspaceStore()
    workspace.setModule('environments')
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="environment-panel"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('环境面板将在后续任务接入')
  })

  it('does not show EnvironmentPanel on apis module', () => {
    const workspace = useWorkspaceStore()
    workspace.setModule('apis')
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="environment-panel"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="api-tree-panel"]').exists()).toBe(true)
  })

  it('shows CollectionPanel when activeModule is collections', () => {
    const workspace = useWorkspaceStore()
    workspace.setModule('collections')
    const wrapper = mountPanel()
    expect(wrapper.find('[data-testid="collection-panel"]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('集合面板将在后续任务接入')
  })
})
