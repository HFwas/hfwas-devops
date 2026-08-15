import { describe, expect, it, beforeEach, vi } from 'vitest'
import { shallowMount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'

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

vi.mock('@/modules/api-test/define/stores/definition', () => ({
  useApiDefinitionStore: vi.fn(() => ({
    loadDetail: vi.fn().mockResolvedValue(null),
    currentDetail: { name: 'Test API' },
    update: vi.fn().mockResolvedValue(undefined),
  })),
}))

import ApiWorkspaceRequest from './ApiWorkspaceRequest.vue'

describe('ApiWorkspaceRequest', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
  })

  it('mounts successfully', () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: null,
        executing: false,
      },
    })
    expect(wrapper.exists()).toBe(true)
  })

  it('renders with definitionId and loads detail', async () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: 10,
        executing: false,
      },
    })
    await wrapper.vm.$nextTick()
    expect(wrapper.exists()).toBe(true)
  })

  it('getRequestData returns correct shape', () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: null,
        executing: false,
      },
    })
    const data = (wrapper.vm as any).getRequestData()
    expect(data).toHaveProperty('url')
    expect(data).toHaveProperty('method')
    expect(data).toHaveProperty('headers')
    expect(data).toHaveProperty('queryParams')
    expect(data).toHaveProperty('body')
    expect(data).toHaveProperty('contentType')
    expect(data).toHaveProperty('preRequestScript')
    expect(data).toHaveProperty('postResponseScript')
  })

  it('getRequestData returns current values', () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: null,
        executing: false,
      },
    })
    const vm = wrapper.vm as any
    vm.url = '/api/users'
    vm.method = 'POST'
    vm.body = '{"name":"test"}'
    const data = vm.getRequestData()
    expect(data.url).toBe('/api/users')
    expect(data.method).toBe('POST')
    expect(data.body).toBe('{"name":"test"}')
  })

  it('resetForm resets all fields to defaults', () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: null,
        executing: false,
      },
    })
    const vm = wrapper.vm as any
    vm.url = '/api/test'
    vm.method = 'POST'
    vm.body = '{"key":"value"}'
    vm.resetForm()
    expect(vm.url).toBe('')
    expect(vm.method).toBe('GET')
    expect(vm.contentType).toBe('application/json')
    expect(vm.body).toBe('')
  })

  it('emits send event', () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: null,
        executing: false,
      },
    })
    const data = (wrapper.vm as any).getRequestData()
    ;(wrapper.vm as any).$emit('send', data)
    expect(wrapper.emitted('send')).toBeTruthy()
    expect(wrapper.emitted('send')![0][0]).toHaveProperty('url')
  })

  it('activeTab defaults to params', () => {
    const wrapper = shallowMount(ApiWorkspaceRequest, {
      props: {
        definitionId: null,
        executing: false,
      },
    })
    expect((wrapper.vm as any).activeTab).toBe('params')
  })
})