import { describe, expect, it, beforeEach, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useWorkspaceStore } from '@/modules/api-test/shell/stores/workspace'
import { emptyDraft } from '@/modules/api-test/shell/types/workspace'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useDebugStore } from '@/modules/api-test/debug/stores/debug'
import { useAuthStore } from '@/modules/user/stores/auth'

const { messageWarning, messageSuccess, messageError, executeMock, createMock, updateMock } = vi.hoisted(() => ({
  messageWarning: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
  executeMock: vi.fn(),
  createMock: vi.fn(),
  updateMock: vi.fn(),
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

import RequestWorkspace from './RequestWorkspace.vue'

describe('RequestWorkspace', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    messageWarning.mockReset()
    messageSuccess.mockReset()
    messageError.mockReset()
    executeMock.mockReset()
    createMock.mockReset()
    updateMock.mockReset()
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

  it('scratch save creates a definition then upgrades the tab', async () => {
    const workspace = useWorkspaceStore()
    workspace.openScratchTab()
    workspace.patchDraft(workspace.tabs[0].id, { url: '/new', method: 'PUT' })
    createMock.mockResolvedValue({ id: 55, name: 'Created' })

    const wrapper = mount(RequestWorkspace)
    await (wrapper.vm as any).handleSave()
    expect(createMock).not.toHaveBeenCalled()

    ;(wrapper.vm as any).scratchName = 'Created'
    ;(wrapper.vm as any).scratchGroupId = 2
    await (wrapper.vm as any).confirmScratchSave()

    expect(createMock).toHaveBeenCalledWith(expect.objectContaining({
      projectId: 1,
      name: 'Created',
      groupId: 2,
      path: '/new',
      method: 'PUT',
    }), 42)
    expect(workspace.tabs[0].source).toBe('definition')
    expect(workspace.tabs[0].definitionId).toBe(55)
    expect(workspace.tabs[0].refId).toBe(55)
    expect(workspace.tabs[0].title).toBe('Created')
    expect(workspace.tabs[0].dirty).toBe(false)
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
})
