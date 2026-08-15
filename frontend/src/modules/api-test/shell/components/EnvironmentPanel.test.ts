import { describe, expect, it, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import { useAuthStore } from '@/modules/user/stores/auth'
import type { EnvironmentDetailVO, EnvironmentVO } from '@/modules/api-test/environment/types/environment'

const { listAllMock, detailMock, updateMock, messageSuccess, messageError } = vi.hoisted(() => ({
  listAllMock: vi.fn(),
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
    listAll: (...args: unknown[]) => listAllMock(...args),
    detail: (...args: unknown[]) => detailMock(...args),
    update: (...args: unknown[]) => updateMock(...args),
    page: vi.fn(),
    create: vi.fn(),
    delete: vi.fn(),
  },
}))

import EnvironmentPanel from './EnvironmentPanel.vue'

function envVO(partial: Partial<EnvironmentVO> & Pick<EnvironmentVO, 'id' | 'name'>): EnvironmentVO {
  return {
    projectId: 1,
    description: '',
    variableCount: 0,
    sortOrder: 0,
    createTime: '',
    updateTime: '',
    ...partial,
  }
}

const DEV: EnvironmentVO = envVO({ id: 1, name: 'Dev', variableCount: 1 })
const STAGING: EnvironmentVO = envVO({ id: 2, name: 'Staging' })

const DEV_DETAIL: EnvironmentDetailVO = {
  id: 1,
  projectId: 1,
  name: 'Dev',
  description: 'local',
  sortOrder: 0,
  createTime: '',
  updateTime: '',
  variables: [
    { id: 11, name: 'baseUrl', value: 'https://dev.example', description: '', isSecret: false, sortOrder: 0 },
  ],
}

describe('EnvironmentPanel', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    listAllMock.mockReset()
    detailMock.mockReset()
    updateMock.mockReset()
    messageSuccess.mockReset()
    messageError.mockReset()
    listAllMock.mockResolvedValue([DEV, STAGING])
    detailMock.mockResolvedValue(DEV_DETAIL)
    updateMock.mockResolvedValue(DEV_DETAIL)
    const auth = useAuthStore()
    auth.user = { id: 42, username: 'tester', displayName: 'Tester', role: 'user' }
  })

  function mountPanel() {
    return mount(EnvironmentPanel, {
      global: {
        stubs: {
          VariableList: {
            name: 'VariableList',
            props: ['variables'],
            emits: ['update:variables'],
            template: '<div data-testid="variable-list">{{ variables.length }} vars</div>',
          },
          EnvironmentFormDialog: {
            name: 'EnvironmentFormDialog',
            props: ['show', 'environmentId', 'projectId'],
            emits: ['update:show', 'saved'],
            template: '<div v-if="show" data-testid="env-form-dialog" />',
          },
        },
      },
    })
  }

  it('loads all environments for project 1 on mount', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(listAllMock).toHaveBeenCalledWith(1)
    expect(wrapper.text()).toContain('Dev')
    expect(wrapper.text()).toContain('Staging')
  })

  it('clicking an environment selects it and loads detail', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="env-item-1"]').trigger('click')
    await flushPromises()

    const env = useEnvironmentStore()
    expect(env.selectedEnvironmentId).toBe(1)
    expect(detailMock).toHaveBeenCalledWith(1)
    expect(wrapper.get('[data-testid="variable-list"]').text()).toContain('1 vars')
    expect(wrapper.get('[data-testid="env-item-1"]').classes()).toContain('is-active')
  })

  it('syncs highlight and detail when selectedEnvironmentId changes from outside', async () => {
    const wrapper = mountPanel()
    await flushPromises()

    const env = useEnvironmentStore()
    env.selectEnvironment(2)
    detailMock.mockResolvedValue({
      ...DEV_DETAIL,
      id: 2,
      name: 'Staging',
      variables: [],
    })
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(2)
    expect(wrapper.get('[data-testid="env-item-2"]').classes()).toContain('is-active')
  })

  it('saves variables via environmentStore.update', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    await wrapper.get('[data-testid="env-item-1"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="env-save"]').trigger('click')
    await flushPromises()

    expect(updateMock).toHaveBeenCalledWith(
      1,
      expect.objectContaining({
        name: 'Dev',
        variables: [expect.objectContaining({ name: 'baseUrl', value: 'https://dev.example' })],
      }),
      42,
    )
    expect(messageSuccess).toHaveBeenCalled()
  })

  it('opens create dialog from the new-environment button', async () => {
    const wrapper = mountPanel()
    await flushPromises()
    expect(wrapper.find('[data-testid="env-form-dialog"]').exists()).toBe(false)
    await wrapper.get('[data-testid="env-create"]').trigger('click')
    expect(wrapper.find('[data-testid="env-form-dialog"]').exists()).toBe(true)
  })
})
