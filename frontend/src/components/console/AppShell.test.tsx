import { act } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AppShell } from '@/components/console/AppShell'
import { useAuthStore } from '@/stores/auth'

vi.mock('@/modules/pm/api', () => ({
  pmProjectApi: {
    getById: vi.fn(async (id: string) => ({ id, code: 'DEVOPS', name: '云原生交付平台' })),
  },
  pmProjectIssueTypeApi: {
    list: vi.fn(async () => [
      { code: 'requirement', name: '需求', enabled: 1, workItemCount: 4, builtIn: 1 },
      { code: 'task', name: '任务', enabled: 1, workItemCount: 6, builtIn: 1 },
      { code: 'bug', name: '缺陷', enabled: 1, workItemCount: 2, builtIn: 1 },
      { code: 'epic', name: '史诗', enabled: 0 },
    ]),
  },
}))

function mount(path: string) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { path: 'workbench', element: <div>工作台内容</div> },
          { path: 'pm/projects', element: <div>项目内容</div> },
          { path: 'pm/projects/:projectId/items/:typeCode', element: <div>事项内容</div> },
          { path: 'pm/projects/:projectId/board/:typeCode', element: <div>看板内容</div> },
          { path: 'pipeline/pipelines', element: <div>流水线内容</div> },
          { path: 'container/clusters', element: <div>集群内容</div> },
          { path: 'api-test', element: <div>接口内容</div> },
          { path: 'user/settings', element: <div>账号设置内容</div> },
        ],
      },
    ],
    { initialEntries: [path] },
  )
  act(() => {
    root.render(
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>,
    )
  })
  return { container, root, router }
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

describe('AppShell', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: (query: string) => ({
        matches: false,
        media: query,
        onchange: null,
        addEventListener: () => {},
        removeEventListener: () => {},
        addListener: () => {},
        removeListener: () => {},
        dispatchEvent: () => false,
      }),
    })
    useAuthStore.setState({
      user: { username: 'ada', displayName: 'Ada', role: 'admin' },
      token: 'token',
      myTenants: [],
      fetchMe: async () => null,
      fetchMyTenants: async () => [],
      logout: async () => {},
      isLoggedIn: () => true,
      isAdmin: () => true,
    })
  })

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('keeps one shell and swaps the sidebar to the current product', async () => {
    const mounted = mount('/pm/projects')
    root = mounted.root
    container = mounted.container

    expect(container.querySelector('[data-slot="sidebar-inset"]')).toBeTruthy()
    const header = container.querySelector('header')
    expect(header?.className).toContain('bg-background/95')
    expect(header?.className).toContain('backdrop-blur')
    expect(header?.className).toContain('border-b')
    expect(container.querySelector('[aria-label="切换主题"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="搜索项目"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="用户菜单"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="切换产品"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="帮助"]')).toBeTruthy()
    expect(container.querySelector('[aria-label="通知（有未读）"]')).toBeTruthy()
    const sidebarContainer = container.querySelector('[data-slot="sidebar-container"]')
    expect(sidebarContainer?.className).toContain('top-(--header-height)')
    expect(sidebarContainer?.className).toContain('h-[calc(100svh-var(--header-height))]!')
    // header 必须在 SidebarInset 之外，且位于侧栏之前（全局 header）。
    expect(container.querySelector('header')?.closest('[data-slot="sidebar-inset"]')).toBeNull()
    const inset = container.querySelector('[data-slot="sidebar-inset"]')
    const sidebarEl = container.querySelector('[data-sidebar="sidebar"]')
    expect(
      header && sidebarEl && inset &&
      (header.compareDocumentPosition(sidebarEl) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0 &&
      (header.compareDocumentPosition(inset) & Node.DOCUMENT_POSITION_FOLLOWING) !== 0,
    ).toBe(true)
    expect(container.querySelector('[data-sidebar="header"]')).toBeNull()

    const shell = container.querySelector('[data-slot="sidebar-inset"]')?.parentElement
    expect(shell?.innerHTML).toContain('px-4')
    expect(shell?.innerHTML).toContain('lg:px-6')

    const sidebar = () => container?.querySelector('[data-sidebar="sidebar"]')?.textContent ?? ''
    // header 全局化：产品名在 header，侧栏只放菜单。
    expect(header?.textContent).toContain('项目管理')
    expect(header?.textContent).toContain('产品选择')
    expect(sidebar()).not.toContain('产品选择')
    expect(sidebar()).toContain('项目')
    expect(sidebar()).not.toContain('项目监控')
    expect(sidebar()).not.toContain('流水线')
    expect(sidebar()).not.toContain('接口测试')
    expect(sidebar()).not.toContain('容器管理')
    expect(container.textContent).toContain('项目内容')

    await act(async () => {
      await mounted.router.navigate('/pm/projects/12/items/task')
    })
    await flush()
    expect(sidebar()).toContain('云原生交付平台')
    expect(sidebar()).toContain('全部项目')
    expect(sidebar()).toContain('全部工作项')
    const sidebarLinks = Array.from(container?.querySelectorAll('a') ?? []).map((link) => link.getAttribute('href'))
    expect(sidebarLinks).toContain('/pm/projects/12/items/all')
    expect(sidebar()).toContain('12')
    expect(sidebar()).toContain('需求')
    expect(sidebar()).toContain('任务')
    expect(sidebar()).toContain('缺陷')
    expect(sidebar()).toContain('看板')
    expect(sidebar()).toContain('工作流')
    expect(sidebar()).not.toContain('史诗')
    expect(sidebar()).not.toContain('项目监控')
    expect(container.textContent).toContain('事项内容')
    expect(activeHref(container)).toBe('/pm/projects/12/items/task')
    expect(container.querySelector('a[href="/pm/projects/12/board/task"]')).toBeTruthy()
    expect(container.querySelector('a[href="/pm/projects/12/settings/workflow/task"]')).toBeTruthy()

    await act(async () => {
      await mounted.router.navigate('/pm/projects/12/board/bug')
    })
    await flush()
    expect(container.textContent).toContain('看板内容')
    expect(activeHref(container)).toBe('/pm/projects/12/board/bug')
    expect(container.querySelector('a[href="/pm/projects/12/settings/workflow/bug"]')).toBeTruthy()

    await act(async () => {
      await mounted.router.navigate('/pipeline/pipelines')
    })
    expect(sidebar()).toContain('流水线')
    expect(sidebar()).toContain('任务市场')
    expect(sidebar()).toContain('凭证')
    expect(sidebar()).not.toContain('项目监控')
    expect(sidebar()).not.toContain('容器管理')
    expect(container.textContent).toContain('流水线内容')

    await act(async () => {
      await mounted.router.navigate('/container/clusters')
    })
    expect(header?.textContent).toContain('容器管理')
    expect(sidebar()).not.toContain('容器管理')
    expect(sidebar()).toContain('集群')
    expect(sidebar()).toContain('工作负载')
    expect(sidebar()).toContain('Helm Release')
    expect(sidebar()).toContain('Chart 目录')
    expect(sidebar()).toContain('上传 Chart')
    expect(sidebar()).not.toContain('Helm Releases')
    expect(sidebar()).not.toContain('应用发布')
    expect(sidebar()).toContain('DaemonSet')
    expect(sidebar()).toContain('集群管理')
    expect(sidebar()).toContain('Pod')
    expect(sidebar()).toContain('镜像仓库')
    expect(container.querySelectorAll('[data-sidebar="group-label"]').length).toBe(9)
    expect(sidebar()).not.toContain('项目管理')
    expect(sidebar()).not.toContain('流水线')
    expect(container.textContent).toContain('集群内容')
    const podSearch = container.querySelector('[aria-label="搜索 Pod"]')
    expect(podSearch?.getAttribute('placeholder')).toBe('搜索 Pod 名称')
    // 产品名已上提到 header，面包屑只保留产品内路径（/container/clusters → 集群）。
    const crumbs = [...(container.querySelectorAll('[data-slot="breadcrumb-item"]') ?? [])]
    expect(crumbs).toHaveLength(1)
    expect(crumbs[0]?.textContent).toContain('集群')
    expect(crumbs[0]?.className).not.toContain('shrink-0')

    await act(async () => {
      await mounted.router.navigate('/api-test')
    })
    expect(header?.textContent).toContain('接口测试')
    expect(sidebar()).not.toContain('接口测试')
    expect(sidebar()).toContain('集合')
    expect(sidebar()).toContain('环境')
    expect(sidebar()).not.toContain('Pod')
    expect(container.textContent).toContain('接口内容')
  })

  it('pipeline search routes to pipeline list', async () => {
    const mounted = mount('/pipeline/pipelines')
    root = mounted.root
    container = mounted.container

    const search = container.querySelector('[aria-label="搜索流水线"]') as HTMLInputElement
    expect(search.getAttribute('placeholder')).toBe('搜索流水线名称')
    await submitSearch(search, '后端')

    expect(mounted.router.state.location.pathname).toBe('/pipeline/pipelines')
    expect(mounted.router.state.location.search).toBe('?q=%E5%90%8E%E7%AB%AF')
  })

  it('project search keeps existing behavior', async () => {
    const mounted = mount('/pm/projects')
    root = mounted.root
    container = mounted.container

    const search = container.querySelector('[aria-label="搜索项目"]') as HTMLInputElement
    await submitSearch(search, '项目')

    expect(mounted.router.state.location.pathname).toBe('/pm/projects')
    expect(mounted.router.state.location.search).toBe('?keyword=%E9%A1%B9%E7%9B%AE')
  })

  it('empty search does nothing', async () => {
    const mounted = mount('/pipeline/pipelines')
    root = mounted.root
    container = mounted.container

    const search = container.querySelector('[aria-label="搜索流水线"]') as HTMLInputElement
    await submitSearch(search, '   ')

    expect(mounted.router.state.location.pathname).toBe('/pipeline/pipelines')
    expect(mounted.router.state.location.search).toBe('')
  })
})

function activeHref(container: HTMLElement) {
  return container
    .querySelector('[data-sidebar="menu-button"][data-active="true"]')
    ?.getAttribute('href')
}

async function submitSearch(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
  act(() => {
    setter?.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
  await act(async () => {
    input.closest('form')?.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }))
    await Promise.resolve()
  })
}
