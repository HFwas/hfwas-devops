import { describe, expect, it, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'

const routeQuery: Record<string, string | undefined> = {}

const { listAllMock, collectionDetailMock } = vi.hoisted(() => ({
  listAllMock: vi.fn(),
  collectionDetailMock: vi.fn(),
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

vi.mock('@/modules/api-test/collection/api/collection', () => ({
  collectionApi: {
    page: vi.fn().mockResolvedValue({ records: [], total: 0, size: 200, current: 1, pages: 0 }),
    detail: (...args: unknown[]) => collectionDetailMock(...args),
    run: vi.fn(),
    runHistory: vi.fn(),
    runDetail: vi.fn(),
  },
}))

import ApiTestShell from './ApiTestShell.vue'

describe('ApiTestShell environment header', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    sessionStorage.clear()
    listAllMock.mockReset()
    listAllMock.mockResolvedValue([])
    collectionDetailMock.mockReset()
    collectionDetailMock.mockResolvedValue({
      id: 9,
      projectId: 1,
      name: 'Payments',
      description: '',
      sortOrder: 0,
      folders: [],
      items: [],
    })
    routeQuery.module = undefined
    routeQuery.def = undefined
    routeQuery.collectionId = undefined
    routeQuery.runs = undefined
    routeQuery.envEdit = undefined
  })

  function mountShell() {
    return mount(ApiTestShell, {
      global: {
        stubs: {
          CollectionsSidebar: {
            name: 'CollectionsSidebar',
            emits: ['run', 'history', 'import-curl'],
            template:
              '<div data-testid="collections-sidebar">'
              + '<button data-testid="emit-run" type="button" @click="$emit(\'run\', 9)" />'
              + '<button data-testid="emit-history" type="button" @click="$emit(\'history\', 9)" />'
              + '<button data-testid="emit-import-curl" type="button" @click="$emit(\'import-curl\')" />'
              + '</div>',
          },
          CollectionOverviewTab: {
            name: 'CollectionOverviewTab',
            props: ['collectionId'],
            emits: ['run', 'history'],
            template: '<div data-testid="collection-overview">{{ collectionId }}</div>',
          },
          RequestTabBar: true,
          RequestWorkspace: true,
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
            emits: ['update:environmentId', 'create', 'edit'],
            template:
              '<button data-testid="header-env-select" @click="$emit(\'update:environmentId\', 5)">{{ environmentId }}</button>'
              + '<button data-testid="header-env-edit" type="button" @click="$emit(\'edit\', 5)" />',
          },
          EnvironmentEditDrawer: {
            name: 'EnvironmentEditDrawer',
            props: ['show', 'environmentId', 'projectId'],
            emits: ['update:show', 'saved'],
            template:
              '<div v-if="show" data-testid="env-edit-drawer">{{ environmentId }}</div>',
          },
          CurlImportDialog: {
            name: 'CurlImportDialog',
            props: ['show'],
            emits: ['update:show', 'imported'],
            template:
              '<div v-if="show" data-testid="curl-import-dialog">'
              + '<button data-testid="emit-curl-imported" type="button" '
              + '@click="$emit(\'imported\', ['
              + '{ url: \'/a\', method: \'GET\', headers: {}, body: \'\', contentType: \'application/json\', followRedirects: false, timeoutMs: 0, warnings: [] },'
              + '{ url: \'/b\', method: \'POST\', headers: {}, body: \'{}\', contentType: \'application/json\', followRedirects: false, timeoutMs: 0, warnings: [] }'
              + '])" />'
              + '</div>',
          },
        },
      },
    })
  }

  it('exposes compact density CSS variables on shell root', () => {
    const wrapper = mountShell()
    expect(wrapper.get('.api-test-shell').attributes('data-density')).toBe('compact')
  })

  it('does not render module rail', () => {
    const wrapper = mountShell()
    expect(wrapper.find('[data-testid="module-rail"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="collections-sidebar"]').exists()).toBe(true)
  })

  it('shows overview when active tab is collectionOverview', async () => {
    const wrapper = mountShell()
    useWorkspaceStore().openOrFocusCollectionOverview(1, 'Demo')
    await nextTick()
    expect(wrapper.find('[data-testid="collection-overview"]').exists()).toBe(true)
  })

  it('shows empty state when no active tab', async () => {
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('.n-empty').exists()).toBe(true)
    expect(wrapper.text()).toContain('选择集合或新建接口')
  })

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

  it('ignores ?module= query on mount', async () => {
    routeQuery.module = 'environments'
    mountShell()
    await flushPromises()
    expect(useWorkspaceStore().activeModule).toBe('apis')
  })

  it('opens the run drawer from CollectionsSidebar run/history without changing route', async () => {
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

  it('opens collection overview from ?collectionId= without runs', async () => {
    routeQuery.collectionId = '9'
    const wrapper = mountShell()
    await flushPromises()
    expect(collectionDetailMock).toHaveBeenCalledWith(9)
    const tab = useWorkspaceStore().activeTab
    expect(tab?.source).toBe('collectionOverview')
    expect(tab?.refId).toBe(9)
    expect(tab?.title).toBe('Payments')
    expect(wrapper.find('[data-testid="collection-overview"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="run-drawer"]').exists()).toBe(false)
  })

  it('opens overview from page list name without loading detail when already listed', async () => {
    routeQuery.collectionId = '9'
    const store = useCollectionStore()
    store.pageResult = {
      records: [{
        id: 9,
        projectId: 1,
        name: 'Listed Name',
        description: '',
        sortOrder: 0,
        folderCount: 0,
        itemCount: 0,
        createTime: '',
        updateTime: '',
      }],
      total: 1,
      size: 200,
      current: 1,
      pages: 1,
    }
    mountShell()
    await flushPromises()
    expect(collectionDetailMock).not.toHaveBeenCalled()
    expect(useWorkspaceStore().activeTab?.title).toBe('Listed Name')
  })

  it('opens history drawer from ?collectionId= and ?runs=1', async () => {
    routeQuery.collectionId = '9'
    routeQuery.runs = '1'
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.get('[data-testid="run-drawer"]').text()).toBe('9:history:0')
    expect(useWorkspaceStore().activeTab?.source).toBe('collectionOverview')
    expect(useWorkspaceStore().activeTab?.refId).toBe(9)
  })

  it('opens environment edit drawer from ?envEdit= after loadAll', async () => {
    routeQuery.envEdit = '5'
    const wrapper = mountShell()
    await flushPromises()
    expect(listAllMock).toHaveBeenCalledWith(1)
    expect(wrapper.get('[data-testid="env-edit-drawer"]').text()).toBe('5')
  })

  it('opens environment edit drawer when selector emits edit', async () => {
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('[data-testid="env-edit-drawer"]').exists()).toBe(false)
    await wrapper.get('[data-testid="header-env-edit"]').trigger('click')
    expect(wrapper.get('[data-testid="env-edit-drawer"]').text()).toBe('5')
  })

  it('opens curl import dialog from sidebar import-curl', async () => {
    const wrapper = mountShell()
    await flushPromises()
    expect(wrapper.find('[data-testid="curl-import-dialog"]').exists()).toBe(false)
    await wrapper.get('[data-testid="emit-import-curl"]').trigger('click')
    expect(wrapper.find('[data-testid="curl-import-dialog"]').exists()).toBe(true)
  })

  it('imports multiple curl results as separate scratch tabs', async () => {
    const wrapper = mountShell()
    await flushPromises()
    await wrapper.get('[data-testid="emit-import-curl"]').trigger('click')
    await wrapper.get('[data-testid="emit-curl-imported"]').trigger('click')
    await flushPromises()
    const ws = useWorkspaceStore()
    const scratches = ws.tabs.filter((t) => t.source === 'scratch')
    expect(scratches.length).toBeGreaterThanOrEqual(2)
    expect(scratches.some((t) => t.draft.url === '/a')).toBe(true)
    expect(scratches.some((t) => t.draft.url === '/b' && t.draft.method === 'POST')).toBe(true)
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
