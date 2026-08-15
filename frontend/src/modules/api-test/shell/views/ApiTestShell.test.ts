import { describe, expect, it, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'

const routeQuery: Record<string, string | undefined> = {}

const { listAllMock } = vi.hoisted(() => ({
  listAllMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(),
      error: vi.fn(),
      warning: vi.fn(),
    }),
  }
})

vi.mock('@/modules/api-test/environment/api/environment', () => ({
  environmentApi: {
    listAll: (...args: unknown[]) => listAllMock(...args),
    detail: vi.fn(),
    update: vi.fn(),
    page: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
  },
}))

import ApiTestShell from './ApiTestShell.vue'

describe('ApiTestShell environment header', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    listAllMock.mockReset()
    listAllMock.mockResolvedValue([])
    routeQuery.module = undefined
    routeQuery.def = undefined
    routeQuery.collectionId = undefined
    routeQuery.runs = undefined
  })

  function mountShell() {
    return mount(ApiTestShell, {
      global: {
        stubs: {
          ModuleRail: true,
          ResourcePanel: {
            name: 'ResourcePanel',
            emits: ['loaded', 'run', 'history'],
            template:
              '<div data-testid="resource-panel">'
              + '<button data-testid="emit-run" type="button" @click="$emit(\'run\', 9)" />'
              + '<button data-testid="emit-history" type="button" @click="$emit(\'history\', 9)" />'
              + '</div>',
          },
          RequestTabBar: true,
          RequestWorkspace: true,
          NEmpty: true,
          CollectionRunDrawer: {
            name: 'CollectionRunDrawer',
            props: ['show', 'collectionId', 'mode', 'runNonce'],
            emits: ['update:show'],
            template:
              '<div v-if="show" data-testid="run-drawer">{{ collectionId }}:{{ mode }}:{{ runNonce }}</div>',
          },
          EnvironmentSelector: {
            name: 'EnvironmentSelector',
            props: ['projectId', 'environmentId'],
            emits: ['update:environmentId'],
            template:
              '<button data-testid="header-env-select" @click="$emit(\'update:environmentId\', 5)">{{ environmentId }}</button>',
          },
        },
      },
    })
  }

  it('loads environments for project 1 and places selector above the tab bar', async () => {
    const wrapper = mountShell()
    await flushPromises()
    expect(listAllMock).toHaveBeenCalledWith(1)
    wrapper.get('[data-testid="header-env-select"]')
    const mainHtml = wrapper.get('.api-test-shell__main').html()
    expect(mainHtml.indexOf('shell-env-header')).toBeLessThan(mainHtml.indexOf('request-tab-bar-stub'))
  })

  it('header selector updates selectedEnvironmentId', async () => {
    const wrapper = mountShell()
    await flushPromises()
    await wrapper.get('[data-testid="header-env-select"]').trigger('click')
    expect(useEnvironmentStore().selectedEnvironmentId).toBe(5)
  })

  it('activates environments module from ?module=environments on mount', async () => {
    routeQuery.module = 'environments'
    mountShell()
    await flushPromises()
    expect(useWorkspaceStore().activeModule).toBe('environments')
  })

  it('opens the run drawer from ResourcePanel run/history without changing route', async () => {
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('[data-testid="run-drawer"]').exists()).toBe(false)

    await wrapper.get('[data-testid="emit-run"]').trigger('click')
    expect(wrapper.get('[data-testid="run-drawer"]').text()).toBe('9:run:1')

    await wrapper.get('[data-testid="emit-history"]').trigger('click')
    expect(wrapper.get('[data-testid="run-drawer"]').text()).toBe('9:history:1')
  })

  it('increments runNonce on a second Run while the drawer stays open in run mode', async () => {
    const wrapper = mountShell()
    await flushPromises()
    await wrapper.get('[data-testid="emit-run"]').trigger('click')
    expect(wrapper.get('[data-testid="run-drawer"]').text()).toBe('9:run:1')
    await wrapper.get('[data-testid="emit-run"]').trigger('click')
    expect(wrapper.get('[data-testid="run-drawer"]').text()).toBe('9:run:2')
  })

  it('opens history drawer from ?collectionId= and ?runs=1', async () => {
    routeQuery.module = 'collections'
    routeQuery.collectionId = '9'
    routeQuery.runs = '1'
    const wrapper = mountShell()
    await flushPromises()
    expect(useWorkspaceStore().activeModule).toBe('collections')
    expect(wrapper.get('[data-testid="run-drawer"]').text()).toBe('9:history:0')
  })

  it('restores sidebarWidth and responseHeight from sessionStorage via setLayout', async () => {
    sessionStorage.setItem('api-test.sidebarWidth', '360')
    sessionStorage.setItem('api-test.responseHeight', '240')
    mountShell()
    await flushPromises()
    const store = useWorkspaceStore()
    expect(store.sidebarWidth).toBe(360)
    expect(store.responseHeight).toBe(240)
  })

  it('clamps restored sidebar width to 200–480', async () => {
    sessionStorage.setItem('api-test.sidebarWidth', '80')
    mountShell()
    await flushPromises()
    expect(useWorkspaceStore().sidebarWidth).toBe(200)
  })

  it('persists layout sizes when setLayout changes values', async () => {
    mountShell()
    await flushPromises()
    useWorkspaceStore().setLayout({ sidebarWidth: 320, responseHeight: 280 })
    await flushPromises()
    expect(sessionStorage.getItem('api-test.sidebarWidth')).toBe('320')
    expect(sessionStorage.getItem('api-test.responseHeight')).toBe('280')
  })

  it('clamps sidebar drag to 200–480 and persists the result', async () => {
    const wrapper = mountShell()
    await flushPromises()
    await wrapper.get('[data-testid="sidebar-resizer"]').trigger('pointerdown', { button: 0, clientX: 280 })
    window.dispatchEvent(new PointerEvent('pointermove', { clientX: 280 + 400 }))
    window.dispatchEvent(new PointerEvent('pointerup'))
    await flushPromises()
    expect(useWorkspaceStore().sidebarWidth).toBe(480)
    expect(sessionStorage.getItem('api-test.sidebarWidth')).toBe('480')
  })
})
