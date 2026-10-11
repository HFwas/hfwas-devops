import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { pipelineApi, pipelineCredentialApi, pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineApi: {
    get: vi.fn(),
    create: vi.fn(),
    update: vi.fn(),
    start: vi.fn(),
    getDefaultParams: vi.fn(),
    toolchains: vi.fn(),
    page: vi.fn(),
    delete: vi.fn(),
  },
  pipelineCredentialApi: { list: vi.fn() },
  pipelineTaskKindApi: { list: vi.fn() },
}))

import { PipelineEditorPage } from '@/modules/pipeline/pages/PipelineEditorPage'

const cloneKind: TaskKindVO = {
  kindValue: 'CLONE',
  label: '代码克隆',
  taskGroup: '代码',
  description: '从 Git 仓库拉取代码',
  hint: 'Clone 命令由平台生成',
  defaultCommand: '',
  requiresCommand: false,
  enabled: true,
  sortOrder: 1,
  toolImage: '',
  defaultImage: '',
  commandTemplate: 'git clone',
  params: [
    { paramKey: 'GIT_REF', paramLabel: '代码分支', paramType: 'input', defaultValue: 'main', required: true, sortOrder: 0 },
    { paramKey: 'GIT_PASSWORD', paramLabel: 'Git 密码', paramType: 'input', defaultValue: '', required: false, sortOrder: 1 },
    { paramKey: 'SECRET_TOKEN', paramLabel: '隐藏令牌', paramType: 'input', defaultValue: 'x', required: true, sortOrder: 2, hidden: true },
  ],
}

const buildKind: TaskKindVO = {
  ...cloneKind,
  kindValue: 'BUILD',
  label: 'Maven 构建',
  taskGroup: '构建',
  description: '编译打包',
  hint: '',
  defaultCommand: 'mvn package',
  commandTemplate: '',
  requiresCommand: true,
  sortOrder: 2,
  params: [
    { paramKey: 'DEST', paramLabel: '镜像推送目标', paramType: 'input', defaultValue: '/workspace', required: false, sortOrder: 0 },
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

async function waitFor(root: ParentNode, text: string) {
  for (let attempt = 0; attempt < 20; attempt += 1) {
    if (root.textContent?.includes(text)) return
    await flush()
  }
  expect(root.textContent).toContain(text)
}

function setInput(root: ParentNode, label: string, value: string) {
  const input = root.querySelector(`[aria-label="${label}"]`) as HTMLInputElement | null
  expect(input).toBeTruthy()
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
  setter?.call(input, value)
  input?.dispatchEvent(new Event('input', { bubbles: true }))
}

function click(root: ParentNode, label: string) {
  const target = root.querySelector(`[aria-label="${label}"]`) as HTMLElement | null
  expect(target, label).toBeTruthy()
  act(() => {
    target?.click()
  })
}

describe('PipelineEditorPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.mocked(pipelineTaskKindApi.list).mockResolvedValue([cloneKind, buildKind])
    vi.mocked(pipelineCredentialApi.list).mockResolvedValue([{ id: '9', name: 'git-token', kind: 'TOKEN' }])
    vi.mocked(pipelineApi.toolchains).mockResolvedValue([])
    vi.mocked(pipelineApi.getDefaultParams).mockResolvedValue([])
    vi.mocked(pipelineApi.create).mockResolvedValue('42')
    vi.mocked(pipelineApi.get).mockResolvedValue({
      id: '42',
      name: '后端发布',
      repoUrl: 'https://github.com/HFwas/hfwas-devops.git',
      gitRef: 'dev',
      credentialId: '9',
      stages: [
        {
          id: 1,
          name: '拉取',
          sortOrder: 0,
          jobs: [{ id: 2, name: '代码克隆', kind: 'CLONE', command: 'git clone', sortOrder: 0, paramBindings: { GIT_REF: { mode: 'fixed', value: 'dev' } } }],
        },
      ],
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

  it('lays out palette, left-to-right stages, and the inspector', async () => {
    const view = mount(
      withQuery(
        <MemoryRouter initialEntries={['/pipeline/pipelines/new']}>
          <Routes>
            <Route path="/pipeline/pipelines/new" element={<PipelineEditorPage />} />
            <Route path="/pipeline/pipelines/:pipelineId/edit" element={<PipelineEditorPage />} />
          </Routes>
        </MemoryRouter>,
      ),
    )
    root = view.root
    container = view.container
    await waitFor(container, '代码克隆')

    const palette = container.querySelector('[data-slot="pipeline-palette"]')
    const lanes = container.querySelector('[data-slot="pipeline-lanes"]')
    const inspector = container.querySelector('[data-slot="pipeline-inspector"]')
    expect(palette).toBeTruthy()
    expect(lanes).toBeTruthy()
    expect(inspector).toBeTruthy()
    expect(palette!.compareDocumentPosition(lanes!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(lanes!.compareDocumentPosition(inspector!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(container.textContent).toContain('基本信息')
    expect(container.querySelector('[aria-label="仓库"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="分支"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="凭证"]')).toBeTruthy()

    click(container, '添加代码克隆')
    await flush()
    const stage = container.querySelector('[data-slot="pipeline-stage"]')
    expect(stage?.className).toContain('flex-col')
    expect(stage?.textContent).toContain('CLONE')
    expect(container.textContent).not.toContain('GIT_PASSWORD')
    expect(container.textContent).not.toContain('SECRET_TOKEN')
    expect(container.textContent).toContain('GIT_REF')

    click(container, '添加Maven 构建')
    await flush()
    expect(container.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(1)
    const names = [...container.querySelectorAll('[aria-label^="选择"]')].map((node) => node.getAttribute('aria-label'))
    expect(names).toEqual(['选择代码克隆', '选择Maven 构建'])

    click(container, '上移Maven 构建')
    await flush()
    const reordered = [...container.querySelectorAll('[aria-label^="选择"]')].map((node) => node.getAttribute('aria-label'))
    expect(reordered).toEqual(['选择Maven 构建', '选择代码克隆'])

    click(container, '添加阶段')
    await flush()
    expect(container.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(2)
    const deleteStage = [...container.querySelectorAll('[aria-label="删除阶段"]')].at(-1) as HTMLElement
    act(() => {
      deleteStage.click()
    })
    await flush()
    expect(container.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(1)

    setInput(container, '名称', '后端发布')
    setInput(container, '仓库', 'https://github.com/HFwas/hfwas-devops.git')
    setInput(container, '分支', 'dev')
    const save = [...container.querySelectorAll('button')].find((button) => button.textContent?.trim() === '保存')
    await act(async () => {
      save?.click()
    })
    for (let attempt = 0; attempt < 20 && vi.mocked(pipelineApi.create).mock.calls.length === 0; attempt += 1) {
      await flush()
    }
    expect(pipelineApi.create).toHaveBeenCalledWith(
      expect.objectContaining({
        name: '后端发布',
        repoUrl: 'https://github.com/HFwas/hfwas-devops.git',
        gitRef: 'dev',
        credentialId: null,
      }),
    )
    const payload = vi.mocked(pipelineApi.create).mock.calls[0][0]
    expect(payload.stages).toHaveLength(1)
    expect(payload.stages[0].jobs.map((job) => job.kind)).toEqual(['BUILD', 'CLONE'])
    expect(JSON.stringify(payload)).not.toContain('GIT_PASSWORD')
    expect(JSON.stringify(payload)).not.toContain('SECRET_TOKEN')
  })
})
