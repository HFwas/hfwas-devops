import { describe, expect, it, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import { useAuthStore } from '@/modules/user/stores/auth'

const {
  messageWarning,
  messageSuccess,
  messageError,
  executeMock,
  createMock,
  updateMock,
  createReqMock,
  collectionPageMock,
  collectionDetailMock,
} = vi.hoisted(() => ({
  messageWarning: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  executeMock: vi.fn(),
  createMock: vi.fn(),
  updateMock: vi.fn(),
  createReqMock: vi.fn(),
  collectionPageMock: vi.fn(),
  collectionDetailMock: vi.fn(),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: messageSuccess,
      error: messageError,
      warning: messageWarning,
    }),
    useDialog: () => ({
      warning: vi.fn(),
    }),
  }
})

vi.mock('@/modules/api-test/debug/api/debug', () => ({
  debugApi: {
    execute: (...args: unknown[]) => executeMock(...args),
  },
}))

vi.mock('@/modules/api-test/debug/api/debugHistory', () => ({
  debugHistoryApi: {
    listByDefinition: vi.fn(),
  },
}))

vi.mock('@/modules/api-test/define/api/definition', () => ({
  apiDefinitionApi: {
    create: (...args: unknown[]) => createMock(...args),
    update: (...args: unknown[]) => updateMock(...args),
  },
}))

vi.mock('@/modules/api-test/define/api/group', () => ({
  apiGroupApi: {
    tree: vi.fn().mockResolvedValue([]),
  },
}))

vi.mock('@/modules/api-test/collection/api/collection', () => ({
  collectionApi: {
    page: (...args: unknown[]) => collectionPageMock(...args),
    detail: (...args: unknown[]) => collectionDetailMock(...args),
  },
}))

vi.mock('@/modules/api-test/shell/utils/createRequestInCollection', () => ({
  createRequestInCollection: (...args: unknown[]) => createReqMock(...args),
}))

import RequestWorkspace from './RequestWorkspace.vue'
import { useCollectionStore } from '@/modules/api-test/collection/stores/collection'

describe('RequestWorkspace', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    messageWarning.mockReset()
    messageSuccess.mockReset()
    messageError.mockReset()
    executeMock.mockReset()
    createMock.mockReset()
    updateMock.mockReset()
    createReqMock.mockReset()
    collectionPageMock.mockReset()
    collectionDetailMock.mockReset()
    collectionDetailMock.mockResolvedValue({
      id: 7,
      projectId: 1,
      name: 'Auth',
      description: '',
      sortOrder: 0,
      folders: [],
      items: [],
    })
    const auth = useAuthStore()
    auth.user = { id: 42, username: 'tester', displayName: 'Tester', role: 'user' }
  })

  it('warns when sending without a URL', async () => {
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSend()
    expect(messageWarning).toHaveBeenCalledWith('请输入请求 URL')
    expect(executeMock).not.toHaveBeenCalled()
  })

  it('sends draft via debugStore.execute and stores result on the tab', async () => {
    const workspace = useWorkspaceStore()
    const env = useEnvironmentStore()
    env.selectEnvironment(7)
    const tab = workspace.openOrFocusTab({
      source: 'definition',
      refId: 9,
      definitionId: 9,
      title: 'Get User',
      method: 'GET',
      draft: emptyDraft({
        url: '{{baseUrl}}/users',
        method: 'GET',
        headers: { Accept: 'application/json' },
        queryParams: { page: '1' },
        body: '',
        contentType: 'application/json',
        preRequestScript: 'pm.environment.set("x", 1)',
        postResponseScript: 'console.log(1)',
        assertions: [{ source: 'RESPONSE_STATUS', compareType: 'EQUALS', expectedValue: '200' }],
        extracts: [{ variableName: 'id', expression: '$.id', source: 'RESPONSE_BODY' }],
      }),
    })
    const result = {
      requestUrl: 'https://api.example.com/users',
      requestMethod: 'GET',
      durationMs: 12,
      status: 'SUCCESS',
    }
    executeMock.mockResolvedValue(result)

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSend()

    expect(executeMock).toHaveBeenCalledWith({
      projectId: 1,
      definitionId: 9,
      environmentId: 7,
      url: '{{baseUrl}}/users',
      method: 'GET',
      headers: { Accept: 'application/json' },
      queryParams: { page: '1' },
      body: undefined,
      contentType: 'application/json',
      preRequestScript: 'pm.environment.set("x", 1)',
      postResponseScript: 'console.log(1)',
      assertions: tab.draft.assertions,
      extracts: tab.draft.extracts,
    })
    expect(workspace.tabs[0].result).toEqual(result)
    expect(messageSuccess).toHaveBeenCalledWith('调试完成')
  })

  it('blocks save with a warning when auth user is missing', async () => {
    const auth = useAuthStore()
    auth.user = null
    const workspace = useWorkspaceStore()
    workspace.openOrFocusTab({
      source: 'definition',
      refId: 3,
      definitionId: 3,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSave()

    expect(messageWarning).toHaveBeenCalled()
    expect(updateMock).not.toHaveBeenCalled()
  })

  it('blocks scratch confirm save when auth user is missing', async () => {
    const auth = useAuthStore()
    auth.user = null
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    workspace.patchDraft(workspace.tabs[0].id, { url: '/new', method: 'PUT' })

    const wrapper = mount(RequestWorkspace)
    ;(wrapper.vm as any).scratchName = 'Created'
    ;(wrapper.vm as any).scratchCollectionId = 7
    await (wrapper.vm as any).confirmScratchSave()

    expect(messageWarning).toHaveBeenCalled()
    expect(createReqMock).not.toHaveBeenCalled()
  })

  it('saves a definition tab via update then markClean', async () => {
    const workspace = useWorkspaceStore()
    const tab = workspace.openOrFocusTab({
      source: 'definition',
      refId: 3,
      definitionId: 3,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({
        url: '/login',
        method: 'POST',
        contentType: 'application/json',
        queryParams: { q: '1' },
        headers: { X: 'y' },
        body: '{"ok":true}',
      }),
    })
    workspace.patchDraft(tab.id, { url: '/login' })
    updateMock.mockResolvedValue({ id: 3, name: 'Login' })

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSave()

    expect(updateMock).toHaveBeenCalledWith(3, expect.objectContaining({
      name: 'Login',
      path: '/login',
      method: 'POST',
      contentType: 'application/json',
    }), 42)
    const payload = updateMock.mock.calls[0][1] as Record<string, unknown>
    expect(payload.params).toEqual(expect.arrayContaining([
      expect.objectContaining({ paramType: 'query', name: 'q', defaultValue: '1' }),
      expect.objectContaining({ paramType: 'header', name: 'X', defaultValue: 'y' }),
      expect.objectContaining({ paramType: 'body', name: 'body', defaultValue: '{"ok":true}' }),
    ]))
    expect(payload).not.toHaveProperty('preRequestScript')
    expect(payload).not.toHaveProperty('postResponseScript')
    expect(payload).not.toHaveProperty('assertions')
    expect(payload).not.toHaveProperty('extracts')
    expect(workspace.tabs[0].dirty).toBe(false)
  })

  it('saves a collection tab by updating the linked definitionId', async () => {
    const workspace = useWorkspaceStore()
    workspace.openOrFocusTab({
      source: 'collection',
      refId: 88,
      definitionId: 11,
      title: 'Item Login',
      method: 'POST',
      draft: emptyDraft({ url: '/login', method: 'POST' }),
    })
    updateMock.mockResolvedValue({ id: 11 })

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSave()

    expect(updateMock).toHaveBeenCalledWith(11, expect.objectContaining({
      name: 'Item Login',
      path: '/login',
      method: 'POST',
    }), 42)
    expect(workspace.tabs[0].dirty).toBe(false)
  })

  it('scratch save creates request in collection then upgrades the tab', async () => {
    const workspace = useWorkspaceStore()
    const collectionStore = useCollectionStore()
    collectionStore.pageResult = {
      records: [{
        id: 7,
        projectId: 1,
        name: 'Auth',
        description: '',
        sortOrder: 0,
        folderCount: 0,
        itemCount: 1,
        createTime: '',
        updateTime: '',
      }],
      total: 1,
      size: 200,
      current: 1,
      pages: 1,
    }
    workspace.openScratchTab()
    workspace.patchDraft(workspace.tabs[0].id, {
      url: '/new',
      method: 'PUT',
      description: 'scratch docs',
      headers: { X: '1' },
    })
    createReqMock.mockResolvedValue({
      definitionId: 55,
      itemId: 99,
      name: 'Created',
      method: 'PUT',
      path: '/new',
    })
    updateMock.mockResolvedValue({ id: 55, name: 'Created' })

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSave()
    expect(createReqMock).not.toHaveBeenCalled()

    ;(wrapper.vm as any).scratchName = 'Created'
    ;(wrapper.vm as any).scratchCollectionId = 7
    await (wrapper.vm as any).confirmScratchSave()

    expect(createReqMock).toHaveBeenCalledWith({
      projectId: 1,
      collectionId: 7,
      userId: 42,
      name: 'Created',
      method: 'PUT',
      path: '/new',
    })
    expect(updateMock).toHaveBeenCalledWith(55, expect.objectContaining({
      name: 'Created',
      path: '/new',
      method: 'PUT',
      description: 'scratch docs',
      contentType: 'application/json',
    }), 42)
    expect(workspace.tabs[0].source).toBe('collection')
    expect(workspace.tabs[0].definitionId).toBe(55)
    expect(workspace.tabs[0].refId).toBe(99)
    expect(workspace.tabs[0].title).toBe('Created')
    expect(workspace.tabs[0].dirty).toBe(false)
    expect(collectionDetailMock).toHaveBeenCalledWith(7)
    expect(collectionStore.currentDetail?.id).toBe(7)
  })

  it('renders docs textarea instead of ComingSoon for Docs', async () => {
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    workspace.patchDraft(workspace.tabs[0].id, { description: 'API notes' })

    const wrapper = mount(RequestWorkspace)
    ;(wrapper.vm as any).requestTab = 'docs'
    await wrapper.vm.$nextTick()

    const docsInput = wrapper.find('[data-testid="docs-description"]')
    expect(docsInput.exists()).toBe(true)
    const textarea = docsInput.find('textarea')
    expect(textarea.exists()).toBe(true)
    expect((textarea.element as HTMLTextAreaElement).value).toBe('API notes')

    const comingSoonTitles = wrapper.findAll('.coming-soon__title').map((n) => n.text())
    expect(comingSoonTitles).not.toContain('Docs')
    expect(wrapper.text()).toContain('Docs')
  })

  it('includes description when saving definition', async () => {
    const workspace = useWorkspaceStore()
    workspace.openOrFocusTab({
      source: 'definition',
      refId: 3,
      definitionId: 3,
      title: 'Login',
      method: 'POST',
      draft: emptyDraft({
        url: '/login',
        method: 'POST',
        description: 'Login API docs',
      }),
    })
    updateMock.mockResolvedValue({ id: 3, name: 'Login' })

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSave()

    expect(updateMock).toHaveBeenCalledWith(3, expect.objectContaining({
      description: 'Login API docs',
    }), 42)
  })

  it('shows execute errors without writing a tab result', async () => {
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    workspace.patchDraft(workspace.tabs[0].id, { url: 'https://example.com' })
    executeMock.mockRejectedValue(new Error('upstream timeout'))

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSend()

    expect(messageError).toHaveBeenCalledWith('upstream timeout')
    expect(workspace.tabs[0].result).toBeNull()
  })

  it('binds the response pane to tab.result not debugStore.currentResult', () => {
    const workspace = useWorkspaceStore()
    const debugStore = useDebugStore()
    const tab = workspace.openScratchTab()
    debugStore.currentResult = {
      requestUrl: '/global',
      requestMethod: 'GET',
      durationMs: 1,
      status: 'SUCCESS',
    } as any
    workspace.setTabResult(tab.id, {
      requestUrl: '/tab',
      requestMethod: 'GET',
      durationMs: 9,
      status: 'SUCCESS',
    } as any)

    const wrapper = mount(RequestWorkspace)
    expect(wrapper.text()).toContain('9ms')
    expect(wrapper.text()).not.toContain('1ms')
  })

  it('renders request tabs including ComingSoon panes', () => {
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    const wrapper = mount(RequestWorkspace)
    const text = wrapper.text()
    for (const label of ['Params', 'Auth', 'Headers', 'Body', 'Scripts', 'Tests', 'Docs', 'Settings', 'Visualize']) {
      expect(text).toContain(label)
    }
  })

  it('clamps response pane drag to 120–60vh', async () => {
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    const wrapper = mount(RequestWorkspace)
    const start = workspace.responseHeight
    await wrapper.get('[data-testid="response-resizer"]').trigger('pointerdown', { button: 0, clientY: 400 })
    window.dispatchEvent(new PointerEvent('pointermove', { clientY: 400 + start }))
    window.dispatchEvent(new PointerEvent('pointerup'))
    expect(workspace.responseHeight).toBe(120)
  })
})
