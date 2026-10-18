import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PipelineRun, PipelineSummary } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineApi: {
    get: vi.fn(),
    pageRuns: vi.fn(),
    getDefaultParams: vi.fn(),
    start: vi.fn(),
  },
  pipelineCredentialApi: { list: vi.fn() },
}))

import { pipelineApi, pipelineCredentialApi } from '@/modules/pipeline/api/pipeline'
import { PipelineDetailPage } from '@/modules/pipeline/pages/PipelineDetailPage'

const pipeline: PipelineSummary = {
  id: '9',
  name: '订单服务 CI',
  repoUrl: 'https://github.com/HFwas/order-service.git',
  gitRef: 'refs/heads/main',
  credentialId: '3',
  lastRunId: 128,
  lastRunStatus: 'RUNNING',
  lastRunTime: new Date(Date.now() - 120_000).toISOString(),
  stages: [
    { name: '构建', sortOrder: 0, jobs: [{ name: 'Maven 构建', kind: 'BUILD', sortOrder: 0 }] },
    { name: '测试', sortOrder: 1, jobs: [{ name: '单元测试', kind: 'TEST', sortOrder: 0 }] },
    { name: '部署', sortOrder: 2, jobs: [{ name: 'K8s 部署', kind: 'DEPLOY', sortOrder: 0 }] },
  ],
}

const now = Date.now()
const runs: PipelineRun[] = [
  {
    id: 128,
    pipelineId: '9',
    pipelineName: '订单服务 CI',
    status: 'RUNNING',
    trigger: 'MANUAL',
    gitRef: 'refs/heads/main',
    commitSha: '9f2c1ab000000000000000000000000000000000',
    triggeredByName: 'ada',
    stack: '',
    runtimeVersion: '',
    image: '',
    startedAt: new Date(now - 3 * 60_000).toISOString(),
    jobs: [],
  },
  {
    id: 126,
    pipelineId: '9',
    pipelineName: '订单服务 CI',
    status: 'FAILED',
    trigger: 'SCHEDULE',
    gitRef: 'refs/heads/main',
    stack: '',
    runtimeVersion: '',
    image: '',
    startedAt: new Date(now - 60 * 60_000).toISOString(),
    finishedAt: new Date(now - 60 * 60_000 + 65_000).toISOString(),
    jobs: [],
  },
]

function LocationProbe() {
  const location = useLocation()
  return <p>运行详情页 {location.pathname}</p>
}

function mount(initialEntry: string) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  act(() => {
    root.render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[initialEntry]}>
          <Routes>
            <Route path="/pipeline/pipelines/:pipelineId/history" element={<PipelineDetailPage />} />
            <Route path="/pipeline/pipelines/:pipelineId" element={<PipelineDetailPage />} />
            <Route path="/pipeline/pipelines/:pipelineId/runs/:runId" element={<LocationProbe />} />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
  })
  return { container, root }
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

async function waitForText(root: ParentNode, text: string) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (root.textContent?.includes(text)) return
    await flush()
  }
  expect(root.textContent).toContain(text)
}

function follows(before: Element, after: Element) {
  return (before.compareDocumentPosition(after) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0
}

describe('PipelineDetailPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.mocked(pipelineApi.get).mockResolvedValue(pipeline)
    vi.mocked(pipelineApi.pageRuns).mockResolvedValue({ records: runs, total: 48, current: 1, size: 100, pages: 1 })
    vi.mocked(pipelineApi.getDefaultParams).mockResolvedValue([])
    vi.mocked(pipelineCredentialApi.list).mockResolvedValue([{ id: '3', name: 'git-token', kind: 'TOKEN' }])
  })

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('shows a dash for commit when no run exists', async () => {
    vi.mocked(pipelineApi.pageRuns).mockResolvedValue({ records: [], total: 0, current: 1, size: 100, pages: 0 })
    const view = mount('/pipeline/pipelines/9')
    root = view.root
    container = view.container
    await waitForText(container, '订单服务 CI')
    const meta = container.querySelector('[data-slot="pipeline-detail-meta"]')
    expect(meta?.textContent).toContain('Commit')
    expect(meta?.textContent).toContain('—')
  })

  it('puts tabs above the repo meta strip and opens the definition canvas', async () => {
    const view = mount('/pipeline/pipelines/9')
    root = view.root
    container = view.container
    await waitForText(container, '订单服务 CI')
    await waitForText(container, 'Maven 构建')

    const shell = container.querySelector('[data-slot="detail-shell"]')
    const title = shell?.querySelector('h1')
    const tabs = shell?.querySelector('[role="tablist"]')
    const meta = shell?.querySelector('[data-slot="pipeline-detail-meta"]')
    const body = shell?.querySelector('[data-slot="pipeline-detail-body"]')
    expect(title?.textContent).toContain('订单服务 CI')
    expect(tabs).toBeTruthy()
    expect(meta).toBeTruthy()
    expect(body).toBeTruthy()
    expect(follows(title!, tabs!)).toBe(true)
    expect(follows(tabs!, meta!)).toBe(true)
    expect(follows(meta!, body!)).toBe(true)

    const back = container.querySelector('[data-slot="pipeline-detail-back"]')
    expect(back?.getAttribute('href')).toBe('/pipeline/pipelines')
    expect(follows(back!, tabs!)).toBe(true)
    const edit = container.querySelector('a[href="/pipeline/pipelines/9/edit"]')
    expect(edit?.textContent).toContain('编辑')
    expect(follows(edit!, tabs!)).toBe(true)

    const tabButtons = [...(tabs?.querySelectorAll('[role="tab"]') ?? [])]
    expect(tabButtons.map((tab) => tab.textContent)).toEqual(['详情', '运行历史'])
    expect(tabButtons[0]?.getAttribute('aria-selected')).toBe('true')
    expect(tabButtons[1]?.getAttribute('aria-selected')).toBe('false')

    expect(meta?.textContent).toContain('仓库')
    expect(meta?.textContent).toContain('HFwas/order-service')
    expect(meta?.textContent).toContain('分支')
    expect(meta?.textContent).toContain('main')
    expect(meta?.textContent).toContain('Commit')
    expect(meta?.textContent).toContain('9f2c1ab0')
    expect(meta?.textContent).not.toContain('9f2c1ab000000000000000000000000000000000')
    expect(meta?.textContent).toContain('构建 · 测试 · 部署')
    expect(meta?.textContent).toContain('凭证')
    expect(meta?.textContent).toContain('git-token')

    expect(body?.querySelector('[data-slot="definition-start"]')?.textContent).toContain('开始')
    expect(body?.querySelector('[data-slot="definition-end"]')?.textContent).toContain('结束')
    const stages = [...(body?.querySelectorAll('[data-slot="definition-stage"]') ?? [])]
    expect(stages.map((stage) => stage.textContent)).toEqual([
      expect.stringContaining('构建'),
      expect.stringContaining('测试'),
      expect.stringContaining('部署'),
    ])
    expect(stages[0]?.compareDocumentPosition(stages[2]!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(body?.textContent).not.toContain('运行编号')

    act(() => {
      ;(tabButtons[1] as HTMLButtonElement | undefined)?.click()
    })
    await waitForText(container, '运行编号')
    expect(container.querySelector('[data-slot="pipeline-definition-canvas"]')).toBeNull()
    const historyTabs = [...container.querySelectorAll('[role="tab"]')]
    expect(historyTabs[1]?.getAttribute('aria-selected')).toBe('true')
    expect(follows(historyTabs[0]!, container.querySelector('[data-slot="pipeline-detail-meta"]')!)).toBe(true)
  })

  it('filters run history via the URL and opens a run from the row', async () => {
    const view = mount('/pipeline/pipelines/9/history?status=FAILED')
    root = view.root
    container = view.container
    await waitForText(container, '#126')

    expect(container.textContent).toContain('触发方式')
    expect(container.textContent).toContain('定时')
    expect(container.textContent).toContain('1m 05s')
    expect(container.textContent).toContain('最近 2 次中 1 条')
    expect(container.textContent).toContain('近 7 天成功率 0%')
    expect(container.querySelector('[aria-label="状态"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="时间"]')).toBeTruthy()
    const historyTab = [...container.querySelectorAll('[role="tab"]')].find((tab) => tab.textContent === '运行历史')
    expect(historyTab?.getAttribute('aria-selected')).toBe('true')
    expect(container.textContent).toContain('#126')
    expect(container.textContent).not.toContain('#128')

    const detail = [...container.querySelectorAll('a')].find((node) => node.textContent === '查看详情')
    expect(detail?.getAttribute('href')).toBe('/pipeline/pipelines/9/runs/126')
    act(() => {
      detail?.click()
    })
    await waitForText(document.body, '运行详情页 /pipeline/pipelines/9/runs/126')

    act(() => {
      root?.unmount()
    })
    container?.remove()
    const again = mount('/pipeline/pipelines/9/history')
    root = again.root
    container = again.container
    await waitForText(container, '#128')
    const duration = [...container.querySelectorAll('td')].find((cell) => cell.textContent === '1m 05s')
    act(() => {
      duration?.click()
    })
    await waitForText(document.body, '运行详情页 /pipeline/pipelines/9/runs/126')
  })

  it('PIPE-149: shows pipeline load failure', async () => {
    vi.mocked(pipelineApi.get).mockRejectedValue(new Error('boom'))
    const view = mount('/pipeline/pipelines/9')
    root = view.root
    container = view.container
    await waitForText(container, '流水线加载失败')
  })
})
