import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { PipelineSummary, TaskKindVO } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineApi: {
    page: vi.fn(),
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    getDefaultParams: vi.fn(),
    start: vi.fn(),
    pageRuns: vi.fn(),
  },
  pipelineCredentialApi: {
    list: vi.fn(),
  },
  pipelineTaskKindApi: {
    list: vi.fn(),
  },
}))

import { pipelineApi, pipelineCredentialApi, pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { PipelineEditorPage } from '@/modules/pipeline/pages/PipelineEditorPage'
import { PipelineDetailPage } from '@/modules/pipeline/pages/PipelineDetailPage'

const clone: TaskKindVO = {
  kindValue: 'CLONE',
  label: '代码克隆',
  taskGroup: '代码',
  description: '',
  hint: '',
  defaultCommand: '',
  requiresCommand: false,
  enabled: true,
  sortOrder: 1,
  toolImage: '',
  defaultImage: '',
  commandTemplate: '',
  params: [
    {
      paramKey: 'GIT_REF',
      paramLabel: '分支',
      paramType: 'input',
      defaultValue: 'main',
      required: false,
      sortOrder: 0,
      visibility: 'user',
    },
  ],
}

const pipeline: PipelineSummary = {
  id: '9',
  name: '发布',
  repoUrl: 'https://github.com/acme/app.git',
  gitRef: 'main',
  stages: [
    {
      name: '构建',
      sortOrder: 0,
      jobs: [{ name: '克隆', kind: 'CLONE', command: '', sortOrder: 0, paramBindings: { GIT_REF: { mode: 'runtime' } } }],
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

function setInput(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
  act(() => {
    setter?.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

describe('pipeline create and run', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.mocked(pipelineTaskKindApi.list).mockReset()
    vi.mocked(pipelineCredentialApi.list).mockReset()
    vi.mocked(pipelineCredentialApi.list).mockResolvedValue([])
    vi.mocked(pipelineApi.create).mockReset()
    vi.mocked(pipelineApi.get).mockReset()
    vi.mocked(pipelineApi.pageRuns).mockReset()
    vi.mocked(pipelineApi.getDefaultParams).mockReset()
    vi.mocked(pipelineApi.start).mockReset()
    vi.mocked(pipelineTaskKindApi.list).mockResolvedValue([clone])
    vi.mocked(pipelineCredentialApi.list).mockResolvedValue([])
    vi.mocked(pipelineApi.create).mockResolvedValue('12')
    vi.mocked(pipelineApi.get).mockResolvedValue(pipeline)
    vi.mocked(pipelineApi.pageRuns).mockResolvedValue({ records: [], total: 0, size: 50, current: 1, pages: 0 })
    vi.mocked(pipelineApi.getDefaultParams).mockResolvedValue([
      { paramKey: 'GIT_REF', paramLabel: '分支', paramType: 'input', defaultValue: 'main', required: true },
    ])
    vi.mocked(pipelineApi.start).mockResolvedValue({ ...pipeline, id: '44', pipelineId: '9', pipelineName: '发布', status: 'QUEUED', trigger: 'MANUAL', stack: '', runtimeVersion: '', image: '', jobs: [] })
  })

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('creates a pipeline from the editor', async () => {
    const view = mount(withQuery(
      <MemoryRouter initialEntries={['/pipeline/pipelines/new']}>
        <Routes>
          <Route path="/pipeline/pipelines/new" element={<PipelineEditorPage />} />
          <Route path="/pipeline/pipelines/:pipelineId" element={<p>运行记录</p>} />
        </Routes>
      </MemoryRouter>,
    ))
    root = view.root
    container = view.container
    await waitForText(container, '新建流水线')
    setInput(container.querySelector('[aria-label="名称"]') as HTMLInputElement, '夜间构建')
    setInput(container.querySelector('[aria-label="仓库"]') as HTMLInputElement, 'https://github.com/acme/app.git')
    const addStage = container.querySelector('[aria-label="添加阶段"]') as HTMLElement
    act(() => {
      addStage.click()
    })
    await flush()
    const addTask = container.querySelector('[aria-label="在阶段 1 添加任务"]') as HTMLElement
    act(() => {
      addTask.click()
    })
    await waitForText(container, '代码克隆')
    const addClone = container.querySelector('[aria-label="添加代码克隆"]') as HTMLElement
    act(() => {
      addClone.click()
    })
    await flush()
    const save = [...container.querySelectorAll('button')].find((node) => node.textContent === '保存')
    act(() => {
      save?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    for (let attempt = 0; attempt < 15 && vi.mocked(pipelineApi.create).mock.calls.length === 0; attempt += 1) {
      await flush()
    }
    expect(pipelineApi.create).toHaveBeenCalledWith(expect.objectContaining({
      name: '夜间构建',
      repoUrl: 'https://github.com/acme/app.git',
      stages: [
        expect.objectContaining({
          jobs: [expect.objectContaining({ kind: 'CLONE', paramBindings: { GIT_REF: { mode: 'fixed', value: 'main' } } })],
        }),
      ],
    }))
  })

  it('asks for runtime params and opens the run detail', async () => {
    const view = mount(withQuery(
      <MemoryRouter initialEntries={['/pipeline/pipelines/9']}>
        <Routes>
          <Route path="/pipeline/pipelines/:pipelineId/history" element={<PipelineDetailPage />} />
          <Route path="/pipeline/pipelines/:pipelineId" element={<PipelineDetailPage />} />
          <Route path="/pipeline/pipelines/:pipelineId/runs/:runId" element={<p>运行详情</p>} />
        </Routes>
      </MemoryRouter>,
    ))
    root = view.root
    container = view.container
    await waitForText(container, '发布')
    const run = [...container.querySelectorAll('button')].find((node) => node.textContent === '运行')
    act(() => {
      run?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    await waitForText(document.body, '运行参数')
    const confirm = [...document.body.querySelectorAll('[role="dialog"] button')].find((node) => node.textContent === '运行')
    act(() => {
      confirm?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    for (let attempt = 0; attempt < 15 && vi.mocked(pipelineApi.start).mock.calls.length === 0; attempt += 1) {
      await flush()
    }
    expect(pipelineApi.start).toHaveBeenCalledWith('9', { GIT_REF: 'main' })
    await waitForText(document.body, '运行详情')
  })

  // 期望：StartRunButton 对 paramType === 'api_select' 且 options 非空时渲染 Select。
  // 后端 getDefaultParams 已预取 options，当前前端仍渲染普通 Input（PIPE-910）。
  it.skip('PIPE-910: renders api_select options as a select', () => {
    expect(true).toBe(true)
  })
})
