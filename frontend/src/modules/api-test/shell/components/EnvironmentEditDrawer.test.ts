import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/modules/user/stores/auth'
import type { EnvironmentDetailVO } from '@/modules/api-test/environment/types/environment'

const { detailMock, updateMock, messageSuccess, messageError } = vi.hoisted(() => ({
  detailMock: vi.fn(),
  updateMock: vi.fn(),
  messageSuccess: vi.fn(),
  messageError: vi.fn(),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual('naive-ui')
  return {
    ...actual,
    useMessage: () => ({
      success: messageSuccess,
      error: messageError,
      warning: vi.fn(),
    }),
  }
})

vi.mock('@/modules/api-test/environment/api/environment', () => ({
  environmentApi: {
    listAll: vi.fn(),
    detail: (...args: unknown[]) => detailMock(...args),
    update: (...args: unknown[]) => updateMock(...args),
    page: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
  },
}))

import EnvironmentEditDrawer from './EnvironmentEditDrawer.vue'

const DETAIL: EnvironmentDetailVO = {
  id: 5,
  projectId: 1,
  name: 'Staging',
  description: 'stg',
  sortOrder: 0,
  createTime: '',
  updateTime: '',
  variables: [
    { id: 21, name: 'baseUrl', value: 'https://stg.example', description: '', isSecret: false, sortOrder: 0 },
    { id: 22, name: 'apiKey', value: 'sk-stg', description: '', isSecret: true, sortOrder: 1 },
  ],
}

describe('EnvironmentEditDrawer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    updateMock.mockReset()
    messageSuccess.mockReset()
    messageError.mockReset()
    detailMock.mockResolvedValue(DETAIL)
    updateMock.mockResolvedValue(DETAIL)
    const auth = useAuthStore()
    auth.user = { id: 42, username: 'tester', displayName: 'Tester', role: 'user' }
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  function mountDrawer(props: {
    show?: boolean
    environmentId?: number | null
    projectId?: number
  } = {}) {
    return mount(EnvironmentEditDrawer, {
      attachTo: document.body,
      props: {
        show: props.show ?? true,
        environmentId: props.environmentId ?? 5,
        projectId: props.projectId ?? 1,
      },
      global: {
        stubs: {
          VariableList: {
            name: 'VariableList',
            props: ['variables'],
            emits: ['update:variables'],
            template: '<div data-testid="variable-list">{{ variables.length }} vars</div>',
          },
        },
      },
    })
  }

  it('saves variables via environment store', async () => {
    const wrapper = mountDrawer({ show: true, environmentId: 5 })
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(5)
    const variableList = document.querySelector('[data-testid="variable-list"]')
    expect(variableList?.textContent).toContain('2 vars')

    const saveBtn = document.querySelector('[data-testid="env-drawer-save"]') as HTMLElement
    expect(saveBtn).not.toBeNull()
    saveBtn.click()
    await flushPromises()

    expect(updateMock).toHaveBeenCalledWith(
      5,
      expect.objectContaining({
        name: 'Staging',
        variables: [
          expect.objectContaining({ name: 'baseUrl', value: 'https://stg.example' }),
          expect.objectContaining({ name: 'apiKey', value: 'sk-stg', isSecret: true }),
        ],
      }),
      42,
    )
    expect(messageSuccess).toHaveBeenCalled()
    expect(wrapper.emitted('saved')).toBeTruthy()
    wrapper.unmount()
  })
})
