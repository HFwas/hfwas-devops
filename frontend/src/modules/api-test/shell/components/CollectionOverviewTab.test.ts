import { describe, expect, it, beforeEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { CollectionDetailVO } from '@/modules/api-test/collection/types/collection'

const { detailMock, messageError } = vi.hoisted(() => ({
  detailMock: vi.fn(),
  messageError: vi.fn(),
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
    detail: (...args: unknown[]) => detailMock(...args),
    page: vi.fn(),
    run: vi.fn(),
    runHistory: vi.fn(),
    runDetail: vi.fn(),
  },
}))

import CollectionOverviewTab from './CollectionOverviewTab.vue'

const BAIDU_DETAIL: CollectionDetailVO = {
  id: 1,
  projectId: 1,
  name: '百度AI平台',
  description: '百度智能云 API 集合',
  sortOrder: 0,
  folders: [],
  items: [],
}

describe('CollectionOverviewTab', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    detailMock.mockReset()
    messageError.mockReset()
    detailMock.mockResolvedValue(BAIDU_DETAIL)
  })

  function mountTab(collectionId = 1) {
    return mount(CollectionOverviewTab, {
      props: { collectionId },
      global: {
        stubs: {
          ComingSoonPane: {
            name: 'ComingSoonPane',
            props: ['title', 'subtitle'],
            template: '<div data-testid="coming-soon">{{ title }}</div>',
          },
        },
      },
    })
  }

  it('shows collection name on overview', async () => {
    const wrapper = mountTab(1)
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(1)
    expect(wrapper.find('[data-testid="collection-overview"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('百度AI平台')
  })

  it('shows description on overview', async () => {
    const wrapper = mountTab(1)
    await flushPromises()

    expect(wrapper.text()).toContain('百度智能云 API 集合')
  })

  it('emits run and history with collectionId from Runs tab', async () => {
    const wrapper = mountTab(1)
    await flushPromises()

    const runsTab = wrapper.findAll('.n-tabs-tab').find((t) => t.text() === 'Runs')
    expect(runsTab).toBeTruthy()
    await runsTab!.trigger('click')
    await flushPromises()

    await wrapper.get('[data-testid="collection-overview-run"]').trigger('click')
    await wrapper.get('[data-testid="collection-overview-history"]').trigger('click')

    expect(wrapper.emitted('run')).toEqual([[1]])
    expect(wrapper.emitted('history')).toEqual([[1]])
  })

  it('reloads when collectionId prop changes', async () => {
    const other: CollectionDetailVO = {
      ...BAIDU_DETAIL,
      id: 2,
      name: 'Payments',
      description: '',
    }
    detailMock.mockResolvedValueOnce(BAIDU_DETAIL).mockResolvedValueOnce(other)

    const wrapper = mountTab(1)
    await flushPromises()
    expect(wrapper.text()).toContain('百度AI平台')

    await wrapper.setProps({ collectionId: 2 })
    await flushPromises()

    expect(detailMock).toHaveBeenCalledWith(2)
    expect(wrapper.text()).toContain('Payments')
  })
})
