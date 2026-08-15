import { describe, expect, it, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'
import type { CollectionDetailVO, CollectionItemVO, CollectionVO } from '@/modules/api-test/collection/types/collection'

const {
  pageMock,
  detailMock,
  createMock,
  loadDefMock,
  createReqMock,
  messageError,
  messageWarning,
  historyPageMock,
  historyDetailMock,
} = vi.hoisted(() => ({
  pageMock: vi.fn(),
  detailMock: vi.fn(),
  createMock: vi.fn(),
  loadDefMock: vi.fn(),
  createReqMock: vi.fn(),
  messageError: vi.fn(),
  messageWarning: vi.fn(),
  historyPageMock: vi.fn(),
  historyDetailMock: vi.fn(),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: vi.fn(),
      error: messageError,
      warning: messageWarning,
    }),
  }
})

vi.mock('@/modules/api-test/collection/api/collection', () => ({
  collectionApi: {
    page: (...args: unknown[]) => pageMock(...args),
    detail: (...args: unknown[]) => detailMock(...args),
    create: (...args: unknown[]) => createMock(...args),
    run: vi.fn(),
    runHistory: vi.fn(),
    runDetail: vi.fn(),
  },
}))

vi.mock('@/modules/api-test/shell/utils/loadDefinitionDraft', () => ({
  loadDefinitionIntoTab: (...args: unknown[]) => loadDefMock(...args),
}))

vi.mock('@/modules/api-test/shell/utils/createRequestInCollection', () => ({
  createRequestInCollection: (...args: unknown[]) => createReqMock(...args),
}))

vi.mock('@/modules/api-test/debug/api/debugHistory', () => ({
  debugHistoryApi: {
    page: (...args: unknown[]) => historyPageMock(...args),
    detail: (...args: unknown[]) => historyDetailMock(...args),
  },
}))

import CollectionsSidebar from './CollectionsSidebar.vue'

function collectionVO(partial: Partial<CollectionVO> & Pick<CollectionVO, 'id' | 'name'>): CollectionVO {
  return {
    projectId: 1,
    description: '',
    sortOrder: 0,
    folderCount: 0,
    itemCount: 1,
    createTime: '',
    updateTime: '',
    ...partial,
  }
}

const LOGIN_ITEM: CollectionItemVO = {
  id: 88,
  collectionId: 1,
  folderId: 12,
  definitionId: 7,
  name: 'Login',
  description: '',
  enabled: true,
  sortOrder: 0,
  method: 'POST',
  path: '/login',
}

const AUTH_COLLECTION: CollectionVO = collectionVO({ id: 1, name: 'Auth', itemCount: 1 })
const PAY_COLLECTION: CollectionVO = collectionVO({ id: 2, name: 'Payments', itemCount: 0 })

const AUTH_DETAIL: CollectionDetailVO = {
  id: 1,
  projectId: 1,
  name: 'Auth',
  description: '',
  sortOrder: 0,
  folders: [],
  items: [LOGIN_ITEM],
}

const PAY_DETAIL: CollectionDetailVO = {
  id: 2,
  projectId: 1,
  name: 'Payments',
  description: '',
  sortOrder: 0,
  folders: [],
  items: [],
}

describe('CollectionsSidebar', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pageMock.mockReset()
    detailMock.mockReset()
    createMock.mockReset()
    loadDefMock.mockReset()
    createReqMock.mockReset()
    messageError.mockReset()
    messageWarning.mockReset()
    historyPageMock.mockReset()
    historyDetailMock.mockReset()

    const auth = useAuthStore()
    auth.user = {
      id: 42,
      username: 'tester',
      displayName: 'Tester',
      role: 'user',
    }

    pageMock.mockResolvedValue({
      records: [AUTH_COLLECTION, PAY_COLLECTION],
      total: 2,
      size: 200,
      current: 1,
      pages: 1,
    })
    detailMock.mockImplementation(async (id: number) => (id === 2 ? PAY_DETAIL : AUTH_DETAIL))
    loadDefMock.mockResolvedValue({
      detail: { name: 'Create User', method: 'PUT' },
      draft: emptyDraft({ url: '/users', method: 'PUT' }),
    })
    createReqMock.mockResolvedValue({
      definitionId: 101,
      itemId: 55,
      name: 'Untitled Request',
      method: 'POST',
      path: '/',
    })
  })

  function mountSidebar() {
    return mount(CollectionsSidebar, {
      global: {
        stubs: {
          CollectionTree: {
            name: 'CollectionTree',
            props: ['folders', 'items', 'selectedId'],
            emits: ['selectItem', 'selectFolder'],
            template:
              '<div data-testid="collection-tree" :data-selected-id="selectedId ?? \'\'">'
              + '<button v-for="item in items" :key="item.id" type="button" :data-testid="`tree-item-${item.id}`" @click="$emit(\'selectItem\', item)">{{ item.name }}</button>'
              + '</div>',
          },
        },
      },
    })
  }

  it('renders COLLECTIONS header and create button', async () => {
    const wrapper = mountSidebar()
    await flushPromises()

    expect(wrapper.get('[data-testid="collections-sidebar"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="create-collection"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="sidebar-mode-collections"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="sidebar-mode-history"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="collection-row-1"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="collection-row-2"]').exists()).toBe(true)
  })

  it('clicking collection opens overview tab', async () => {
    const wrapper = mountSidebar()
    await flushPromises()

    await wrapper.get('[data-testid="collection-row-1"]').trigger('click')
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(1)
    const tab = useWorkspaceStore().activeTab
    expect(tab?.source).toBe('collectionOverview')
    expect(tab?.refId).toBe(1)
    expect(tab?.title).toBe('Auth')
  })

  it('keeps all collections visible when one is expanded (no drill+Back)', async () => {
    const wrapper = mountSidebar()
    await flushPromises()

    await wrapper.get('[data-testid="collection-row-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-testid="collection-back"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="collection-row-1"]').exists()).toBe(true)
    expect(wrapper.get('[data-testid="collection-row-2"]').exists()).toBe(true)
    expect(wrapper.find('[data-testid="collection-tree"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Login')
  })

  it('shows empty CTA when there are no collections', async () => {
    pageMock.mockResolvedValue({
      records: [],
      total: 0,
      size: 200,
      current: 1,
      pages: 0,
    })
    const wrapper = mountSidebar()
    await flushPromises()

    expect(wrapper.text()).toContain('创建第一个集合')
  })

  it('opens a collection item as source:collection', async () => {
    const wrapper = mountSidebar()
    await flushPromises()
    await wrapper.get('[data-testid="collection-row-1"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="tree-item-88"]').trigger('click')
    await flushPromises()

    expect(loadDefMock).toHaveBeenCalledWith(7)
    const tab = useWorkspaceStore().activeTab
    expect(tab?.source).toBe('collection')
    expect(tab?.refId).toBe(88)
    expect(tab?.definitionId).toBe(7)
    expect(tab?.folderId).toBe(12)
    expect(tab?.title).toBe('Login')
    expect(tab?.method).toBe('POST')
    expect(wrapper.get('[data-testid="collection-tree"]').attributes('data-selected-id')).toBe('88')
  })

  it('switches to history mode and loads page', async () => {
    historyPageMock.mockResolvedValue({ records: [], total: 0, size: 50, current: 1, pages: 0 })
    const wrapper = mountSidebar()
    await wrapper.get('[data-testid="sidebar-mode-history"]').trigger('click')
    await flushPromises()
    expect(historyPageMock).toHaveBeenCalledWith(expect.objectContaining({ projectId: 1 }))
    expect(wrapper.find('[data-testid="history-sidebar-list"]').exists()).toBe(true)
  })

  it('clicking history row opens a new scratch tab with mapped draft', async () => {
    historyPageMock.mockResolvedValue({
      records: [{
        id: 7,
        definitionId: null,
        environmentId: null,
        name: 'GET /x',
        requestUrl: '/x',
        requestMethod: 'GET',
        responseStatusCode: 200,
        responseSize: 1,
        durationMs: 5,
        status: 'SUCCESS',
        allAssertionsPassed: true,
        createTime: 't',
      }],
      total: 1,
      size: 50,
      current: 1,
      pages: 1,
    })
    historyDetailMock.mockResolvedValue({
      id: 7,
      projectId: 1,
      definitionId: null,
      environmentId: null,
      name: 'GET /x',
      requestUrl: '/x',
      requestMethod: 'GET',
      requestHeaders: { A: '1' },
      requestQuery: {},
      requestBody: '',
      requestContentType: 'application/json',
      responseStatusCode: 200,
      responseBody: '{}',
      durationMs: 5,
      status: 'SUCCESS',
      createBy: 1,
      createTime: 't',
    })
    const wrapper = mountSidebar()
    await wrapper.get('[data-testid="sidebar-mode-history"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="history-row-7"]').trigger('click')
    await flushPromises()
    const ws = useWorkspaceStore()
    expect(ws.tabs.some((t) => t.source === 'scratch' && t.draft.url === '/x')).toBe(true)
  })

  it('history-opened scratch tab is clean after hydrate', async () => {
    historyPageMock.mockResolvedValue({
      records: [{
        id: 7,
        definitionId: null,
        environmentId: null,
        name: 'GET /x',
        requestUrl: '/x',
        requestMethod: 'GET',
        responseStatusCode: 200,
        responseSize: 1,
        durationMs: 5,
        status: 'SUCCESS',
        allAssertionsPassed: true,
        createTime: 't',
      }],
      total: 1,
      size: 50,
      current: 1,
      pages: 1,
    })
    historyDetailMock.mockResolvedValue({
      id: 7,
      projectId: 1,
      definitionId: null,
      environmentId: null,
      name: 'GET /x',
      requestUrl: '/x',
      requestMethod: 'GET',
      requestHeaders: { A: '1' },
      requestQuery: {},
      requestBody: '',
      requestContentType: 'application/json',
      responseStatusCode: 200,
      responseBody: '{}',
      durationMs: 5,
      status: 'SUCCESS',
      createBy: 1,
      createTime: 't',
    })
    const wrapper = mountSidebar()
    await wrapper.get('[data-testid="sidebar-mode-history"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="history-row-7"]').trigger('click')
    await flushPromises()
    const tab = useWorkspaceStore().tabs.find((t) => t.source === 'scratch' && t.draft.url === '/x')
    expect(tab).toBeDefined()
    expect(tab?.dirty).toBe(false)
  })

  it('emits run and history from collection overflow', async () => {
    const wrapper = mountSidebar()
    await flushPromises()

    await wrapper.get('[data-testid="collection-menu-run-1"]').trigger('click')
    await wrapper.get('[data-testid="collection-menu-history-1"]').trigger('click')

    expect(wrapper.emitted('run')).toEqual([[1]])
    expect(wrapper.emitted('history')).toEqual([[1]])
  })

  it('syncs detailCache when collectionStore.currentDetail updates externally', async () => {
    const wrapper = mountSidebar()
    await flushPromises()

    await wrapper.get('[data-testid="collection-row-1"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('Login')
    expect(wrapper.text()).not.toContain('New From Scratch')

    const refreshed: CollectionDetailVO = {
      ...AUTH_DETAIL,
      items: [
        ...AUTH_DETAIL.items,
        {
          id: 200,
          collectionId: 1,
          folderId: null,
          definitionId: 201,
          name: 'New From Scratch',
          description: '',
          enabled: true,
          sortOrder: 1,
          method: 'GET',
          path: '/scratch',
        },
      ],
    }
    useCollectionStore().currentDetail = refreshed
    await flushPromises()

    expect(wrapper.text()).toContain('New From Scratch')
  })

  it('renders import curl button and emits import-curl', async () => {
    const wrapper = mountSidebar()
    await flushPromises()
    const btn = wrapper.get('[data-testid="import-curl"]')
    expect(btn.exists()).toBe(true)
    await btn.trigger('click')
    expect(wrapper.emitted('import-curl')).toBeTruthy()
  })
})
