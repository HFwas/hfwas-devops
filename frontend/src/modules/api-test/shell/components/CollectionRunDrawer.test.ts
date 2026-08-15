import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useAuthStore } from '@/modules/user/stores/auth'
import { useEnvironmentStore } from '@/modules/api-test/environment/stores/environment'
import type { CollectionRunDetailVO, CollectionRunVO } from '@/modules/api-test/collection/types/collection'

const routeQuery: Record<string, string | undefined> = {}
const routerPush = vi.fn()

const { runMock, runHistoryMock, runDetailMock, messageError } = vi.hoisted(() => ({
  runMock: vi.fn(),
  runHistoryMock: vi.fn(),
  runDetailMock: vi.fn(),
  messageError: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRoute: () => ({ query: routeQuery }),
  useRouter: () => ({ push: routerPush }),
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
    page: vi.fn(),
    detail: vi.fn(),
    run: (...args: unknown[]) => runMock(...args),
    runHistory: (...args: unknown[]) => runHistoryMock(...args),
    runDetail: (...args: unknown[]) => runDetailMock(...args),
  },
}))

import CollectionRunDrawer from './CollectionRunDrawer.vue'

const RUN_VO: CollectionRunVO = {
  id: 301,
  collectionId: 9,
  projectId: 1,
  environmentId: 5,
  name: 'Auth run',
  status: 'COMPLETED',
  totalCount: 1,
  passedCount: 1,
  failedCount: 0,
  errorCount: 0,
  durationMs: 42,
  triggerMode: 'MANUAL',
  createTime: '2026-08-15 10:00:00',
}

const OLDER_RUN: CollectionRunVO = {
  ...RUN_VO,
  id: 300,
  name: 'Older run',
}

const RUN_DETAIL: CollectionRunDetailVO = {
  ...RUN_VO,
  items: [],
}

describe('CollectionRunDrawer', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    runMock.mockReset()
    runHistoryMock.mockReset()
    runDetailMock.mockReset()
    messageError.mockReset()
    routerPush.mockReset()
    routeQuery.collectionId = undefined
    routeQuery.runs = undefined
    runMock.mockResolvedValue(RUN_VO)
    runHistoryMock.mockResolvedValue({
      records: [RUN_VO, OLDER_RUN],
      total: 2,
      size: 20,
      current: 1,
      pages: 1,
    })
    runDetailMock.mockResolvedValue(RUN_DETAIL)
    const auth = useAuthStore()
    auth.user = { id: 42, username: 'tester', displayName: 'Tester', role: 'user' }
    useEnvironmentStore().selectEnvironment(5)
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  function mountDrawer(props: { show?: boolean; collectionId?: number | null; mode?: 'run' | 'history'; runNonce?: number } = {}) {
    return mount(CollectionRunDrawer, {
      attachTo: document.body,
      props: {
        show: props.show ?? true,
        collectionId: props.collectionId ?? 9,
        mode: props.mode ?? 'history',
        runNonce: props.runNonce ?? 0,
      },
      global: {
        stubs: {
          CollectionRunResult: {
            name: 'CollectionRunResult',
            props: ['runDetail'],
            template: '<div data-testid="run-result">{{ runDetail?.name }}</div>',
          },
        },
      },
    })
  }

  it('runs the collection and shows CollectionRunResult without navigating', async () => {
    const wrapper = mountDrawer({ mode: 'run' })
    await flushPromises()

    expect(runMock).toHaveBeenCalledWith(9, 5, 42)
    expect(runDetailMock).toHaveBeenCalledWith(301)
    expect(document.body.textContent).toContain('Auth run')
    expect(routerPush).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('re-executes when runNonce increments while already open in run mode', async () => {
    const wrapper = mountDrawer({ mode: 'run', runNonce: 1 })
    await flushPromises()
    expect(runMock).toHaveBeenCalledTimes(1)

    await wrapper.setProps({ runNonce: 2 })
    await flushPromises()
    expect(runMock).toHaveBeenCalledTimes(2)
    expect(runMock).toHaveBeenLastCalledWith(9, 5, 42)
    wrapper.unmount()
  })

  it('loads history and selecting a row shows that run detail', async () => {
    const wrapper = mountDrawer({ mode: 'history' })
    await flushPromises()

    expect(runHistoryMock).toHaveBeenCalledWith(9, expect.anything())
    expect(runMock).not.toHaveBeenCalled()
    expect(document.body.textContent).toContain('Older run')

    runDetailMock.mockResolvedValue({ ...RUN_DETAIL, id: 300, name: 'Older run' })
    const row = document.querySelector('[data-testid="run-history-item-300"]')
    expect(row).not.toBeNull()
    ;(row as HTMLElement).click()
    await flushPromises()

    expect(runDetailMock).toHaveBeenCalledWith(300)
    const result = document.querySelector('[data-testid="run-result"]')
    expect(result?.textContent).toContain('Older run')
    expect(routerPush).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('opens history from ?collectionId= and ?runs=1', async () => {
    routeQuery.collectionId = '9'
    routeQuery.runs = '1'
    const wrapper = mountDrawer({ show: false, collectionId: null, mode: 'run' })
    await flushPromises()

    expect(wrapper.emitted('update:show')).toEqual([[true]])
    expect(runHistoryMock).toHaveBeenCalledWith(9, expect.anything())
    expect(runMock).not.toHaveBeenCalled()
    expect(routerPush).not.toHaveBeenCalled()
    wrapper.unmount()
  })
})
