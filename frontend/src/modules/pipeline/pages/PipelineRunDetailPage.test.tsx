import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { PipelineRun } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineApi: {
    getRun: vi.fn(),
    cancel: vi.fn(),
    approve: vi.fn(),
  },
}))

import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import { PipelineRunDetailPage } from '@/modules/pipeline/pages/PipelineRunDetailPage'

const running: PipelineRun = {
  id: 3,
  pipelineId: 9,
  pipelineName: '发布',
  status: 'RUNNING',
  trigger: 'manual',
  gitRef: 'refs/heads/main',
  commitSha: '48b618f9408d74b9eb5f260be9db9d087350203a',
  triggeredByName: 'ada',
  stack: 'NODE',
  runtimeVersion: '20',
  image: 'node:20',
  startedAt: '2026-10-11T00:00:00Z',
  jobs: [
    {
      id: 1,
      stageName: '代码克隆',
      jobName: '克隆',
      kind: 'CLONE',
      status: 'SUCCEEDED',
      logText: 'clone ok',
      startedAt: '2026-10-11T00:00:00Z',
      finishedAt: '2026-10-11T00:00:05Z',
    },
    {
      id: 2,
      stageName: '构建',
      jobName: '编译',
      kind: 'BUILD',
      status: 'RUNNING',
      logText: 'compiling',
      startedAt: '2026-10-11T00:00:06Z',
    },
  ],
}

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

function withQuery(children: ReactNode) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

async function waitForText(root: ParentNode, text: string) {
  for (let attempt = 0; attempt < 15; attempt += 1) {
    if (root.textContent?.includes(text)) return
    await flush()
  }
  expect(root.textContent).toContain(text)
}

function renderRun() {
  return mount(
    withQuery(
      <MemoryRouter initialEntries={['/pipeline/pipelines/9/runs/3']}>
        <Routes>
          <Route path="/pipeline/pipelines/:pipelineId/runs/:runId" element={<PipelineRunDetailPage />} />
        </Routes>
      </MemoryRouter>,
    ),
  )
}

describe('PipelineRunDetailPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('shows a swimlane and keeps the selected job log beside the aggregated log', async () => {
    vi.mocked(pipelineApi.getRun).mockResolvedValue(running)
    const view = renderRun()
    root = view.root
    container = view.container
    await waitForText(container, '编译')

    const stages = container.querySelectorAll('[data-slot="run-stage"]')
    expect(Array.from(stages).map((stage) => stage.getAttribute('data-status'))).toEqual(['SUCCEEDED', 'RUNNING'])
    const overallPanel = Array.from(container.querySelectorAll('[data-slot="log-panel"]')).find((panel) =>
      panel.querySelector('header')?.textContent?.includes('总体日志'),
    )
    const overall = overallPanel?.textContent ?? ''
    expect(overall.indexOf('clone ok')).toBeGreaterThan(-1)
    expect(overall.indexOf('clone ok')).toBeLessThan(overall.indexOf('compiling'))

    const jobPanel = Array.from(container.querySelectorAll('[data-slot="log-panel"]')).find((panel) =>
      panel.textContent?.includes('构建 / 编译'),
    )
    expect(jobPanel?.textContent).toContain('compiling')
    expect(jobPanel?.textContent).not.toContain('clone ok')

    const stageButton = container.querySelector('[aria-label="查看阶段代码克隆日志"]') as HTMLButtonElement
    act(() => {
      stageButton.click()
    })
    const stagePanel = Array.from(container.querySelectorAll('[data-slot="log-panel"]')).find((panel) =>
      panel.textContent?.includes('阶段 · 代码克隆'),
    )
    expect(stagePanel?.textContent).toContain('clone ok')
    expect(stagePanel?.textContent).not.toContain('compiling')
    expect(container.textContent).toContain('取消')
    expect(container.textContent).not.toContain('通过')
  })

  it('keeps approval and cancel while a run is waiting', async () => {
    vi.mocked(pipelineApi.getRun).mockResolvedValue({
      ...running,
      status: 'WAITING_APPROVAL',
      jobs: [{ ...running.jobs[0], status: 'WAITING_APPROVAL', logText: '' }],
    })
    const view = renderRun()
    root = view.root
    container = view.container
    await waitForText(container, '待审批')
    expect(container.textContent).toContain('通过')
    expect(container.textContent).toContain('取消')
    expect(container.querySelector('[data-slot="run-stage"]')?.getAttribute('data-status')).toBe('WAITING_APPROVAL')
  })
})
