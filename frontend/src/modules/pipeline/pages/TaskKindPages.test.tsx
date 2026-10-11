import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useAuthStore } from '@/stores/auth'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'

vi.mock('@/modules/pipeline/api/pipeline', () => ({
  pipelineTaskKindApi: {
    list: vi.fn(),
    get: vi.fn(),
    update: vi.fn(),
    toggle: vi.fn(),
    versions: vi.fn(),
    publish: vi.fn(),
    activate: vi.fn(),
  },
  pipelineJobParamApi: {
    previewApi: vi.fn(),
  },
}))

import { pipelineTaskKindApi } from '@/modules/pipeline/api/pipeline'
import { TaskKindDetailPage } from '@/modules/pipeline/pages/TaskKindDetailPage'
import { TaskKindListPage } from '@/modules/pipeline/pages/TaskKindListPage'

const kinds: TaskKindVO[] = [
  {
    kindValue: 'CLONE',
    label: '代码克隆',
    taskGroup: '代码',
    description: '拉取仓库',
    hint: '',
    defaultCommand: '',
    requiresCommand: false,
    enabled: true,
    sortOrder: 1,
    toolImage: '',
    defaultImage: '',
    commandTemplate: '',
    version: '',
    params: [
      {
        paramKey: 'GIT_TOKEN',
        paramLabel: 'Git Token',
        paramType: 'input',
        defaultValue: '',
        required: false,
        sortOrder: 0,
        visibility: 'hidden',
      },
    ],
  },
  {
    kindValue: 'BUILD',
    label: '构建',
    taskGroup: '构建',
    description: '编译源码',
    hint: '',
    defaultCommand: '',
    requiresCommand: true,
    enabled: false,
    sortOrder: 2,
    toolImage: 'maven:3',
    defaultImage: '',
    commandTemplate: 'mvn package',
    version: '1.2.0',
  },
]

function toggleButtons(root: ParentNode): HTMLButtonElement[] {
  return [...root.querySelectorAll('button')].filter((node) => node.textContent === '停用' || node.textContent === '启用')
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

describe('task marketplace pages', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.mocked(pipelineTaskKindApi.list).mockReset()
    vi.mocked(pipelineTaskKindApi.get).mockReset()
    vi.mocked(pipelineTaskKindApi.update).mockReset()
    vi.mocked(pipelineTaskKindApi.versions).mockReset()
    vi.mocked(pipelineTaskKindApi.publish).mockReset()
    vi.mocked(pipelineTaskKindApi.list).mockResolvedValue(kinds)
    vi.mocked(pipelineTaskKindApi.get).mockResolvedValue(kinds[0])
    vi.mocked(pipelineTaskKindApi.update).mockResolvedValue(undefined)
    vi.mocked(pipelineTaskKindApi.versions).mockResolvedValue([])
    vi.mocked(pipelineTaskKindApi.publish).mockResolvedValue({
      version: '1.0.0',
      note: '',
      createdAt: '2026-10-11T00:00:00',
      isCurrent: true,
    })
    useAuthStore.setState({
      user: { username: 'admin', displayName: '管理员', role: 'admin' },
    })
  })

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
    useAuthStore.setState({ user: null })
  })

  it('renders cards, filters by group, and shows toggle only for admins', async () => {
    const view = mount(withQuery(
      <MemoryRouter>
        <TaskKindListPage />
      </MemoryRouter>,
    ))
    root = view.root
    container = view.container
    await waitForText(container, '代码克隆')
    expect(container.querySelector('[data-kind="CLONE"]')).toBeTruthy()
    expect(container.querySelector('[data-kind="BUILD"]')?.textContent).toContain('1.2.0')
    expect(toggleButtons(container)).toHaveLength(2)

    const buildTab = [...container.querySelectorAll('[role="tab"]')].find((node) => node.textContent === '构建')
    act(() => {
      buildTab?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(container.querySelector('[data-kind="CLONE"]')).toBeNull()
    expect(container.querySelector('[data-kind="BUILD"]')).toBeTruthy()

    act(() => {
      root?.unmount()
    })
    container.remove()
    useAuthStore.setState({
      user: { username: 'user', displayName: '用户', role: 'user' },
    })
    const readonly = mount(withQuery(
      <MemoryRouter>
        <TaskKindListPage />
      </MemoryRouter>,
    ))
    root = readonly.root
    container = readonly.container
    await waitForText(container, '代码克隆')
    expect(toggleButtons(container)).toHaveLength(0)
  })

  it('lets an admin save overview without sending a version', async () => {
    const view = mount(withQuery(
      <MemoryRouter initialEntries={['/pipeline/task-kinds/CLONE']}>
        <Routes>
          <Route path="/pipeline/task-kinds/:kind" element={<TaskKindDetailPage />} />
        </Routes>
      </MemoryRouter>,
    ))
    root = view.root
    container = view.container
    await waitForText(container, '代码克隆')
    const edit = [...container.querySelectorAll('button')].find((node) => node.textContent === '编辑概览')
    act(() => {
      edit?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    const input = container.querySelector('#task-kind-label') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    act(() => {
      setter?.call(input, '克隆任务')
      input.dispatchEvent(new Event('input', { bubbles: true }))
    })
    const save = [...container.querySelectorAll('button')].find((node) => node.textContent === '保存')
    act(() => {
      save?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    for (let attempt = 0; attempt < 15 && vi.mocked(pipelineTaskKindApi.update).mock.calls.length === 0; attempt += 1) {
      await flush()
    }
    expect(pipelineTaskKindApi.update).toHaveBeenCalledWith('CLONE', expect.objectContaining({ label: '克隆任务' }))
    const body = vi.mocked(pipelineTaskKindApi.update).mock.calls[0]?.[1]
    expect(body).not.toHaveProperty('version')
    expect(body?.params).toBeUndefined()
  })

  it('shows param visibility and publishes a version explicitly', async () => {
    const view = mount(withQuery(
      <MemoryRouter initialEntries={['/pipeline/task-kinds/CLONE']}>
        <Routes>
          <Route path="/pipeline/task-kinds/:kind" element={<TaskKindDetailPage />} />
        </Routes>
      </MemoryRouter>,
    ))
    root = view.root
    container = view.container
    await waitForText(container, '代码克隆')
    const paramsTab = [...container.querySelectorAll('[role="tab"]')].find((node) => node.textContent === '参数')
    act(() => {
      paramsTab?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    await waitForText(container, '仅管理员')
    expect(container.textContent).toContain('添加参数')
    expect(container.textContent).toContain('删除')

    const versionsTab = [...container.querySelectorAll('[role="tab"]')].find((node) => node.textContent === '版本')
    act(() => {
      versionsTab?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    await waitForText(container, '还没有发布版本')
    const open = [...container.querySelectorAll('button')].find((node) => node.textContent === '基于当前配置发布版本')
    act(() => {
      open?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    const publish = [...document.body.querySelectorAll('button')].find((node) => node.textContent === '发布')
    act(() => {
      publish?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    for (let attempt = 0; attempt < 15 && vi.mocked(pipelineTaskKindApi.publish).mock.calls.length === 0; attempt += 1) {
      await flush()
    }
    expect(pipelineTaskKindApi.publish).toHaveBeenCalledWith('CLONE', { note: undefined, version: undefined })
  })

  it('treats ADMIN role as admin and keeps members read-only', async () => {
    useAuthStore.setState({
      user: { username: 'admin', displayName: '管理员', role: 'ADMIN' },
    })
    const adminView = mount(withQuery(
      <MemoryRouter initialEntries={['/pipeline/task-kinds/CLONE']}>
        <Routes>
          <Route path="/pipeline/task-kinds/:kind" element={<TaskKindDetailPage />} />
        </Routes>
      </MemoryRouter>,
    ))
    root = adminView.root
    container = adminView.container
    await waitForText(container, '编辑概览')

    act(() => {
      root?.unmount()
    })
    container.remove()
    useAuthStore.setState({
      user: { username: 'user', displayName: '用户', role: 'user' },
    })
    const readonly = mount(withQuery(
      <MemoryRouter initialEntries={['/pipeline/task-kinds/CLONE?tab=参数']}>
        <Routes>
          <Route path="/pipeline/task-kinds/:kind" element={<TaskKindDetailPage />} />
        </Routes>
      </MemoryRouter>,
    ))
    root = readonly.root
    container = readonly.container
    await waitForText(container, 'Git Token')
    expect(container.textContent).not.toContain('添加参数')
    expect(container.textContent).toContain('参数为只读')
  })
})
