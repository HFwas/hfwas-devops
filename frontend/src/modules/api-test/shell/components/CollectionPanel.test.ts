import { describe, expect, it, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'
import type { CollectionDetailVO, CollectionItemVO, CollectionVO } from '@/modules/api-test/collection/types/collection'

const routeQuery: Record<string, string | undefined> = {}

const { pageMock, detailMock, loadDefMock, messageError } = vi.hoisted(() => ({
  pageMock: vi.fn(),
  detailMock: vi.fn(),
  loadDefMock: vi.fn(),
  messageError: vi.fn(),
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
      error: messageError,
      warning: vi.fn(),
    }),
  }
})

vi.mock('@/modules/api-test/collection/api/collection', () => ({
  collectionApi: {
    page: (...args: unknown[]) => pageMock(...args),
    detail: (...args: unknown[]) => detailMock(...args),
    run: vi.fn(),
    runHistory: vi.fn(),
    runDetail: vi.fn(),
  },
}))

vi.mock('@/modules/api-test/shell/utils/loadDefinitionDraft', () => ({
  loadDefinitionIntoTab: (...args: unknown[]) => loadDefMock(...args),
}))

import CollectionPanel from './CollectionPanel.vue'

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
  collectionId: 9,
  folderId: null,
  definitionId: 7,
  name: 'Login',
  description: '',
  enabled: true,
  sortOrder: 0,
  method: 'POST',
  path: '/login',
}

const AUTH_COLLECTION: CollectionVO = collectionVO({ id: 9, name: 'Auth', itemCount: 1 })
const PAY_COLLECTION: CollectionVO = collectionVO({ id: 10, name: 'Payments', itemCount: 0 })

const AUTH_DETAIL: CollectionDetailVO = {
  id: 9,
  projectId: 1,
  name: 'Auth',
  description: '',
  sortOrder: 0,
  folders: [],
  items: [LOGIN_ITEM],
}

describe('CollectionPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    pageMock.mockReset()
    detailMock.mockReset()
    loadDefMock.mockReset()
    messageError.mockReset()
    routeQuery.collectionId = undefined
    routeQuery.runs = undefined
    pageMock.mockResolvedValue({
      records: [AUTH_COLLECTION, PAY_COLLECTION],
      total: 2,
      size: 200,
      current: 1,
      pages: 1,
    })
    detailMock.mockResolvedValue(AUTH_DETAIL)
    loadDefMock.mockResolvedValue({
      detail: { name: 'Create User', method: 'PUT' },
      draft: emptyDraft({ url: '/users', method: 'PUT' }),
    })
  })

  function mountPanel() {
    return mount(CollectionPanel, {
      global: {
        stubs: {
          CollectionTree: {
            name: 'CollectionTree',
            props: ['folders', 'items'],
            emits: ['selectItem', 'selectFolder'],
            template:
              '<div data-testid="collection-tree">'
              + '<button v-for="item in items" :key="item.id" type="button" :data-testid="`tree-item-${item.id}`" @click="$emit(\'selectItem\', item)">{{ item.name }}</button>'
              + '</div>',
          },
        },
      },
    })
  }

  it('loads collections for project 1 on mount', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(pageMock).toHaveBeenCalledWith({ projectId: 1, pageNo: 1, pageSize: 200 })
    expect(wrapper.text()).toContain('Auth')
    expect(wrapper.text()).toContain('Payments')
  })

  it('drills into a collection and shows the folder tree', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="collection-item-9"]').trigger('click')
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(9)
    expect(wrapper.find('[data-testid="collection-tree"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('Login')
    expect(wrapper.find('[data-testid="collection-item-10"]').exists()).toBe(false)
  })

  it('opens a collection item as source:collection with refId and definitionId', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="collection-item-9"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="tree-item-88"]').trigger('click')
    await flushPromises()

    expect(loadDefMock).toHaveBeenCalledWith(7)
    const tab = useWorkspaceStore().activeTab
    expect(tab?.source).toBe('collection')
    expect(tab?.refId).toBe(88)
    expect(tab?.definitionId).toBe(7)
    expect(tab?.title).toBe('Login')
    expect(tab?.method).toBe('POST')
  })

  it('keeps a definition tab when opening a collection item so both sources coexist', async () => {
    const workspace = useWorkspaceStore()
    workspace.openOrFocusTab({
      source: 'definition',
      refId: 7,
      definitionId: 7,
      title: 'Create User',
      method: 'PUT',
      draft: emptyDraft({ url: '/users', method: 'PUT' }),
    })

    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="collection-item-9"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="tree-item-88"]').trigger('click')
    await flushPromises()

    expect(workspace.tabs.map((t) => t.source)).toEqual(['definition', 'collection'])
    expect(workspace.tabs).toHaveLength(2)
    expect(workspace.tabs[0].refId).toBe(7)
    expect(workspace.tabs[1].refId).toBe(88)
    expect(workspace.activeTab?.source).toBe('collection')
  })

  it('falls back to definition name and method when the item omits them', async () => {
    detailMock.mockResolvedValue({
      ...AUTH_DETAIL,
      items: [{ ...LOGIN_ITEM, name: '', method: '' }],
    })
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="collection-item-9"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-testid="tree-item-88"]').trigger('click')
    await flushPromises()

    const tab = useWorkspaceStore().activeTab
    expect(tab?.title).toBe('Create User')
    expect(tab?.method).toBe('PUT')
  })

  it('emits run and history with the drilled collection id', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="collection-item-9"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="collection-run"]').trigger('click')
    await wrapper.get('[data-testid="collection-history"]').trigger('click')

    expect(wrapper.emitted('run')).toEqual([[9]])
    expect(wrapper.emitted('history')).toEqual([[9]])
  })

  it('drills into ?collectionId= on mount', async () => {
    routeQuery.collectionId = '9'
    const wrapper = mountPanel()
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(9)
    expect(wrapper.find('[data-testid="collection-tree"]').exists()).toBe(true)
  })
})
