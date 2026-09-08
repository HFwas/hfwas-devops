import { describe, expect, it, beforeEach, afterEach, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import type { PipelineRun, PipelineSummary } from '@/modules/pipeline/types/pipeline'

const push = vi.fn()
const replace = vi.fn()
const routeParams = { id: '8', runId: '50' }

const { getRunMock, pageRunsMock, getMock } = vi.hoisted(() => ({
  getRunMock: vi.fn(),
  pageRunsMock: vi.fn(),
  getMock: vi.fn(),
}))

vi.mock('vue-router', () => ({
  useRouter: () => ({ push, replace }),
  useRoute: () => ({ params: routeParams }),
}))

vi.mock('naive-ui', async () => {
  const actual = await vi.importActual('naive-ui')
  return {
    ...actual,
    useMessage: () => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn() }),
    useDialog: () => ({ warning: vi.fn() }),
  }
})

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineApi: {
    getRun: (...args: unknown[]) => getRunMock(...args),
    pageRuns: (...args: unknown[]) => pageRunsMock(...args),
    get: (...args: unknown[]) => getMock(...args),
    start: vi.fn(),
    cancel: vi.fn(),
    approve: vi.fn(),
    delete: vi.fn(),
  },
}))

import PipelineRunView from './PipelineRunView.vue'

const sampleRun: PipelineRun = {
  id: 50,
  pipelineId: 8,
  pipelineName: '百炼v3-workflow',
  status: 'SUCCEEDED',
  trigger: 'MANUAL',
  gitRef: 'refs/heads/master',
  commitSha: '6110fa30abcd',
  triggeredByName: '许董科',
  stack: 'JAVA_MAVEN',
  runtimeVersion: '21',
  toolVersion: '3.9',
  image: 'maven:3.9.9-eclipse-temurin-21',
  startedAt: '2026-09-08T06:47:21Z',
  finishedAt: '2026-09-08T06:50:39Z',
  jobs: [],
}

const samplePipeline: PipelineSummary = {
  id: 8,
  name: '百炼v3-workflow',
  repoUrl: 'https://git.example.com/enterprise-agentscope-platform.git',
  gitRef: 'master',
  stack: 'JAVA_MAVEN',
  runtimeVersion: '21',
  toolVersion: '3.9',
  lastRunId: 50,
}

describe('PipelineRunView layout', () => {
  beforeEach(() => {
    vi.useFakeTimers()
    push.mockReset()
    replace.mockReset()
    routeParams.id = '8'
    routeParams.runId = '50'
    getRunMock.mockReset()
    pageRunsMock.mockReset()
    getMock.mockReset()
    getRunMock.mockResolvedValue(sampleRun)
    getMock.mockResolvedValue(samplePipeline)
    pageRunsMock.mockResolvedValue({
      records: [sampleRun, { ...sampleRun, id: 49, status: 'FAILED' }],
      total: 50,
      size: 10,
      current: 1,
      pages: 5,
    })
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  function mountView() {
    return mount(PipelineRunView, {
      global: {
        stubs: {
          YunxiaoFlowCanvas: { template: '<div data-testid="flow-canvas" />' },
          'n-drawer': true,
          'n-drawer-content': true,
        },
      },
    })
  }

  it('puts 构建过程 and 运行历史 in the top bar, then shows the current run graph', async () => {
    const wrapper = mountView()
    await flushPromises()
    const header = wrapper.get('header')
    expect(header.text()).toContain('流水线')
    expect(header.text()).toContain('百炼v3-workflow')
    expect(header.get('[data-testid="run-tab-graph"]').text()).toBe('构建过程')
    expect(header.get('[data-testid="run-tab-history"]').text()).toBe('运行历史')
    expect(wrapper.get('[data-testid="run-tab-graph"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-testid="flow-canvas"]').exists()).toBe(true)
    expect(wrapper.text()).toContain('页面手动触发')
    expect(pageRunsMock).not.toHaveBeenCalled()
    wrapper.unmount()
  })

  it('loads paginated history when switching to 运行历史', async () => {
    const wrapper = mountView()
    await flushPromises()
    await wrapper.get('[data-testid="run-tab-history"]').trigger('click')
    await flushPromises()
    expect(pageRunsMock).toHaveBeenCalledWith('8', { pageNo: 1, pageSize: 10 })
    expect(wrapper.get('[data-testid="run-tab-history"]').classes()).toContain('is-active')
    expect(wrapper.find('[data-testid="flow-canvas"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('#50')
    expect(wrapper.text()).toContain('共 50 条')
    wrapper.unmount()
  })
})
