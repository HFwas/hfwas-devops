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

const disabledKind: TaskKindVO = {
  ...buildKind,
  kindValue: 'NOTIFY',
  label: '隐藏通知',
  taskGroup: '通知',
  description: '不在添加列表里',
  enabled: false,
  sortOrder: 3,
  params: [],
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
    vi.mocked(pipelineTaskKindApi.list).mockResolvedValue([cloneKind, buildKind, disabledKind])
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

  function panel() {
    return container?.querySelector('[data-slot="pipeline-side-panel"]') ?? null
  }

  async function renderNew() {
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
    await waitFor(container, '新建流水线')
    return container
  }

  it('swaps the right panel between config and a click-to-add task list', async () => {
    const view = await renderNew()

    const lanes = view.querySelector('[data-slot="pipeline-lanes"]')
    expect(view.querySelector('[data-slot="pipeline-palette"]')).toBeNull()
    expect(view.querySelector('[aria-label="搜索任务"]')).toBeNull()
    expect(view.textContent).not.toContain('任务市场')
    expect(lanes).toBeTruthy()
    expect(panel()?.getAttribute('data-mode')).toBe('config')
    expect(panel()?.querySelector('[data-slot="pipeline-inspector"]')).toBeTruthy()
    expect(lanes!.compareDocumentPosition(panel()!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()
    expect(view.textContent).toContain('基本信息')
    expect(view.textContent).toContain('任务配置')
    expect(view.textContent).toContain('取消')
    expect(view.textContent).toContain('保存并运行')
    expect(view.querySelector('[aria-label="仓库"]')).toBeTruthy()
    expect(view.querySelector('[aria-label="分支"]')).toBeTruthy()
    expect(view.querySelector('[aria-label="凭证"]')).toBeTruthy()

    click(view, '添加阶段')
    await flush()
    expect(view.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(1)

    click(view, '在阶段 1 添加任务')
    await waitFor(view, '代码克隆')
    expect(view.textContent).toContain('点击任务即可添加')
    expect(panel()?.getAttribute('data-mode')).toBe('add')
    expect(panel()?.querySelector('[data-slot="pipeline-task-list"]')).toBeTruthy()
    expect(panel()?.querySelector('[data-slot="pipeline-inspector"]')).toBeNull()
    expect(panel()?.querySelector('input, textarea')).toBeNull()
    expect(view.textContent).toContain('添加任务到「阶段 1 · 未命名」')
    expect(view.textContent).toContain('代码 (1)')
    expect(view.textContent).toContain('构建 (1)')
    expect(view.textContent).not.toContain('隐藏通知')
    expect(view.querySelector('[aria-label="添加Maven 构建"]')).toBeNull()

    click(view, '关闭添加任务')
    await flush()
    expect(panel()?.getAttribute('data-mode')).toBe('config')
    expect(view.querySelector('[aria-label="选择代码克隆"]')).toBeNull()

    click(view, '在阶段 1 添加任务')
    await waitFor(view, '代码克隆')
    click(view, '添加代码克隆')
    await flush()
    const stage = view.querySelector('[data-slot="pipeline-stage"]')
    expect(stage?.className).toContain('flex-col')
    expect(stage?.textContent).toContain('CLONE')
    expect(panel()?.getAttribute('data-mode')).toBe('config')
    expect((view.querySelector('[aria-label="任务名称"]') as HTMLInputElement | null)?.value).toBe('代码克隆')
    expect(view.textContent).not.toContain('GIT_PASSWORD')
    expect(view.textContent).not.toContain('SECRET_TOKEN')
    expect(view.textContent).toContain('GIT_REF')

    click(view, '在阶段 1 添加任务')
    await waitFor(view, '添加任务到「阶段 1 · 拉取」')
    expect(panel()?.getAttribute('data-mode')).toBe('add')
    click(view, '构建 (1)')
    await flush()
    click(view, '添加Maven 构建')
    await flush()
    expect(view.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(1)
    expect(panel()?.getAttribute('data-mode')).toBe('config')
    expect((view.querySelector('[aria-label="任务名称"]') as HTMLInputElement | null)?.value).toBe('Maven 构建')
    const names = [...view.querySelectorAll('[aria-label^="选择"]')].map((node) => node.getAttribute('aria-label'))
    expect(names).toEqual(['选择代码克隆', '选择Maven 构建'])

    click(view, '在阶段 1 添加任务')
    await waitFor(view, '点击任务即可添加')
    click(view, '选择代码克隆')
    await flush()
    expect(panel()?.getAttribute('data-mode')).toBe('config')
    expect((view.querySelector('[aria-label="任务名称"]') as HTMLInputElement | null)?.value).toBe('代码克隆')

    click(view, '在阶段 1 添加任务')
    await waitFor(view, '添加任务到「阶段 1')
    click(view, '添加阶段')
    await flush()
    expect(view.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(2)
    expect(view.textContent).toContain('添加任务到「阶段 1')
    expect(view.textContent).not.toContain('添加任务到「阶段 2')
    click(view, '关闭添加任务')
    await flush()
    const extraStage = [...view.querySelectorAll('[aria-label="删除阶段"]')].at(-1) as HTMLElement
    act(() => {
      extraStage.click()
    })
    await flush()
    expect(view.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(1)

    click(view, '上移Maven 构建')
    await flush()
    const reordered = [...view.querySelectorAll('[aria-label^="选择"]')].map((node) => node.getAttribute('aria-label'))
    expect(reordered).toEqual(['选择Maven 构建', '选择代码克隆'])

    click(view, '添加阶段')
    await flush()
    expect(view.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(2)
    const deleteStage = [...view.querySelectorAll('[aria-label="删除阶段"]')].at(-1) as HTMLElement
    act(() => {
      deleteStage.click()
    })
    await flush()
    expect(view.querySelectorAll('[data-slot="pipeline-stage"]')).toHaveLength(1)

    setInput(view, '名称', '后端发布')
    setInput(view, '仓库', 'https://github.com/HFwas/hfwas-devops.git')
    setInput(view, '分支', 'dev')
    const save = [...view.querySelectorAll('button')].find((button) => button.textContent?.trim() === '保存')
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

  it('asks before deleting a job and keeps it when cancelled', async () => {
    const confirm = vi.fn(() => false)
    vi.stubGlobal('confirm', confirm)
    const view = await renderNew()
    click(view, '添加阶段')
    await flush()
    click(view, '在阶段 1 添加任务')
    await waitFor(view, '代码克隆')
    click(view, '添加代码克隆')
    await flush()

    click(view, '删除代码克隆')
    await flush()
    expect(confirm).toHaveBeenCalledWith('删除任务「代码克隆」？')
    expect(view.querySelector('[aria-label="选择代码克隆"]')).toBeTruthy()

    confirm.mockReturnValue(true)
    click(view, '删除代码克隆')
    await flush()
    expect(view.querySelector('[aria-label="选择代码克隆"]')).toBeNull()
    expect(panel()?.getAttribute('data-mode')).toBe('config')
    vi.unstubAllGlobals()
  })
})
