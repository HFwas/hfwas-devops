import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, useLocation } from 'react-router'
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

function LocationProbe() {
  const location = useLocation()
  return <div data-testid="location">{`${location.pathname}${location.search}`}</div>
}

function renderList() {
  return mount(
    <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
      <MemoryRouter>
        <PipelineListPage />
        <LocationProbe />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function openRowMenu(name: string) {
  const trigger = document.querySelector(`[aria-label="更多操作：${name}"]`) as HTMLElement | null
  act(() => {
    trigger?.dispatchEvent(new MouseEvent('pointerdown', { bubbles: true, button: 0 }))
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

  it('shows a dense list with mono name, repo, branch, run and overflow actions', async () => {
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
    expect(container.querySelector('[aria-label="更多操作：后端发布"]')).toBeTruthy()
    expect(container.textContent).toContain('运行')
    expect(container.textContent).not.toContain('编辑')
    expect(container.textContent).not.toContain('删除')
    const nameLink = container.querySelector('a[href="/pipeline/pipelines/1"]')
    expect(nameLink?.className).toContain('font-console')
    const nameCell = [...container.querySelectorAll('td')].find((cell) => cell.textContent?.includes('后端发布'))
    expect(nameCell?.className).not.toContain('py-3')
    expect(container.querySelector('[data-slot="stage-dot"]')?.parentElement?.className).toContain('shadow-sm')

    // 关键词交给服务端：防抖后带 keyword 重新查询（不再前端过滤）。
    const search = container.querySelector('[aria-label="搜索流水线、仓库或分支"]') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    setter?.call(search, 'dev')
    act(() => {
      search.dispatchEvent(new Event('input', { bubbles: true }))
    })
    for (let attempt = 0; attempt < 40; attempt += 1) {
      if (vi.mocked(pipelineApi.page).mock.calls.some((call) => call[0]?.keyword === 'dev')) break
      await flush()
    }
    expect(pipelineApi.page).toHaveBeenCalledWith(expect.objectContaining({ pageNo: 1, pageSize: 10, keyword: 'dev' }))
  })

  it('PIPE-019: deletes a pipeline after confirmation', async () => {
    vi.mocked(pipelineApi.delete).mockResolvedValue(undefined)
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

    openRowMenu('后端发布')
    for (let attempt = 0; attempt < 15 && !document.body.textContent?.includes('删除'); attempt += 1) {
      await flush()
    }
    const deleteItem = [...document.querySelectorAll('[role="menuitem"]')].find(
      (node) => node.textContent?.trim() === '删除',
    )
    act(() => {
      ;(deleteItem as HTMLElement | undefined)?.click()
    })
    for (let attempt = 0; attempt < 15 && !document.body.textContent?.includes('删除流水线'); attempt += 1) {
      await flush()
    }
    const dialog = document.querySelector('[role="alertdialog"]')
    const confirm = [...(dialog ?? document.body).querySelectorAll('button')].find((node) => node.textContent === '删除')
    act(() => {
      confirm?.click()
    })
    for (let attempt = 0; attempt < 15 && vi.mocked(pipelineApi.delete).mock.calls.length === 0; attempt += 1) {
      await flush()
    }
    expect(pipelineApi.delete).toHaveBeenCalledWith(1)
  })

  it('PIPE-149: shows empty pipeline list', async () => {
    vi.mocked(pipelineApi.page).mockResolvedValue({ records: [], total: 0, current: 1, size: 10, pages: 0 })
    const view = mount(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <PipelineListPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('还没有流水线'); attempt += 1) {
      await flush()
    }
    expect(container.textContent).toContain('还没有流水线')
  })

  it('PIPE-149: shows pipeline list load failure', async () => {
    vi.mocked(pipelineApi.page).mockRejectedValue(new Error('boom'))
    const view = mount(
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <MemoryRouter>
          <PipelineListPage />
        </MemoryRouter>
      </QueryClientProvider>,
    )
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('流水线列表加载失败'); attempt += 1) {
      await flush()
    }
    expect(container.textContent).toContain('流水线列表加载失败')
  })

  it('TC-UX08-01: row click opens pipeline detail', async () => {
    const view = renderList()
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('后端发布'); attempt += 1) {
      await flush()
    }

    const row = [...container.querySelectorAll('tbody tr')].find((node) =>
      node.textContent?.includes('后端发布'),
    ) as HTMLElement
    act(() => {
      row.click()
    })
    await flush()

    expect(container.querySelector('[data-testid="location"]')?.textContent).toBe('/pipeline/pipelines/1')
  })

  it('TC-UX08-02: name link remains keyboard accessible', async () => {
    const view = renderList()
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('后端发布'); attempt += 1) {
      await flush()
    }

    const nameLink = container.querySelector('a[href="/pipeline/pipelines/1"]') as HTMLAnchorElement
    expect(nameLink.textContent?.trim()).toBe('后端发布')

    act(() => {
      nameLink.click()
    })
    await flush()
    expect(container.querySelector('[data-testid="location"]')?.textContent).toBe('/pipeline/pipelines/1')
  })

  it('TC-UX08-03: clicking run starts a run instead of opening the pipeline row', async () => {
    const view = renderList()
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('后端发布'); attempt += 1) {
      await flush()
    }

    const runButton = [...container.querySelectorAll('tbody tr button')].find(
      (node) => node.textContent?.trim() === '运行',
    ) as HTMLElement
    act(() => {
      runButton.click()
    })
    await flush()

    expect(container.querySelector('[data-testid="location"]')?.textContent).toBe('/pipeline/pipelines/1/runs/9')
  })

  it('TC-UX08-04: row menu exposes edit and delete', async () => {
    const view = renderList()
    root = view.root
    container = view.container
    for (let attempt = 0; attempt < 20 && !container.textContent?.includes('后端发布'); attempt += 1) {
      await flush()
    }

    openRowMenu('后端发布')
    for (let attempt = 0; attempt < 15 && !document.querySelector('[role="menuitem"]'); attempt += 1) {
      await flush()
    }

    expect(document.querySelector('a[href="/pipeline/pipelines/1/edit"]')?.textContent).toContain('编辑')
    expect(document.querySelector('a[href="/pipeline/pipelines/1/history"]')).toBeNull()
    const items = [...document.querySelectorAll('[role="menuitem"]')].map((node) => node.textContent?.trim())
    expect(items).toEqual(['编辑', '删除'])
  })
})
