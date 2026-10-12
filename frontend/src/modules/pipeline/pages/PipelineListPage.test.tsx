import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { pipelineApi } from '@/modules/pipeline/api/pipeline'
import type { PipelineSummary } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineApi: {
    page: vi.fn(),
    getDefaultParams: vi.fn(),
    start: vi.fn(),
    delete: vi.fn(),
  },
  pipelineCredentialApi: { list: vi.fn() },
  pipelineTaskKindApi: { list: vi.fn() },
}))

import { PipelineListPage } from '@/modules/pipeline/pages/PipelineListPage'

const rows: PipelineSummary[] = [
  {
    id: 1,
    name: '后端发布',
    repoUrl: 'https://github.com/acme-inc/backend.git',
    gitRef: 'refs/heads/main',
    lastRunId: 8,
    lastRunStatus: 'SUCCEEDED',
    lastRunTime: new Date(Date.now() - 120_000).toISOString(),
    stages: [
      { name: '拉取', sortOrder: 0, jobs: [{ name: '代码克隆', kind: 'CLONE', sortOrder: 0 }] },
      { name: '构建', sortOrder: 1, jobs: [{ name: 'Maven 构建', kind: 'BUILD', sortOrder: 0 }] },
    ],
  },
  {
    id: 2,
    name: '前端构建',
    repoUrl: 'https://github.com/acme-inc/frontend.git',
    gitRef: 'refs/heads/dev',
    lastRunStatus: 'FAILED',
    lastRunTime: new Date(Date.now() - 3_600_000).toISOString(),
    stages: [{ name: '构建', sortOrder: 0, jobs: [{ name: '构建', kind: 'BUILD', sortOrder: 0 }] }],
  },
]

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

describe('PipelineListPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.mocked(pipelineApi.page).mockResolvedValue({ records: rows, total: 2, current: 1, size: 100, pages: 1 })
    vi.mocked(pipelineApi.getDefaultParams).mockResolvedValue([])
    vi.mocked(pipelineApi.start).mockResolvedValue({
      id: 9,
      pipelineId: 1,
      pipelineName: '后端发布',
      status: 'QUEUED',
      trigger: 'MANUAL',
      stack: '',
      runtimeVersion: '',
      image: '',
      jobs: [],
    })
  })

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('shows a dense list with mono name, repo, branch, run and edit', async () => {
    const view = mount(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <PipelineListPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('后端发布'); attempt += 1) {
      await flush()
    }
    expect(container.textContent).toContain('共 2 条流水线')
    expect(container.textContent).toContain('acme-inc/backend')
    expect(container.textContent).toContain('main')
    expect(container.textContent).toContain('代码克隆、Maven 构建')
    expect(container.querySelectorAll('[data-slot="stage-dot"]')).toHaveLength(3)
    const create = container.querySelector('a[href="/pipeline/pipelines/new"]')
    expect(create?.textContent).toContain('新建流水线')
    const edit = container.querySelector('a[href="/pipeline/pipelines/1/edit"]')
    expect(edit?.textContent).toContain('编辑')
    expect(container.querySelectorAll('a[href="/pipeline/pipelines/1/edit"]')).toHaveLength(1)
    const history = container.querySelector('a[href="/pipeline/pipelines/1/history"]')
    expect(history?.textContent).toContain('历史')
    expect(container.textContent).toContain('删除')
    const nameLink = container.querySelector('a[href="/pipeline/pipelines/1"]')
    expect(nameLink?.className).toContain('font-console')
    const nameCell = [...container.querySelectorAll('td')].find((cell) => cell.textContent?.includes('后端发布'))
    expect(nameCell?.className).not.toContain('py-3')
    expect(container.querySelector('[data-slot="stage-dot"]')?.parentElement?.className).toContain('shadow-sm')

    const search = container.querySelector('[aria-label="搜索流水线、仓库或分支"]') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    setter?.call(search, 'dev')
    act(() => {
      search.dispatchEvent(new Event('input', { bubbles: true }))
    })
    expect(container.textContent).toContain('前端构建')
    expect(container.textContent).not.toContain('后端发布')
    expect(container.textContent).toContain('共 1 条流水线')
  })
})
