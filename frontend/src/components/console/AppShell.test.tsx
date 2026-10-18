import { act } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { AppShell } from '@/components/console/AppShell'
import { useAuthStore } from '@/stores/auth'

function mount(path: string) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  const router = createMemoryRouter(
    [
      {
        path: '/',
        element: <AppShell />,
        children: [
          { path: 'workbench', element: <div>工作台内容</div> },
          { path: 'pm/projects', element: <div>项目内容</div> },
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
    root.render(<RouterProvider router={router} />)
  })
  return { container, root, router }
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

    const shell = container.querySelector('[data-slot="sidebar-inset"]')?.parentElement
    expect(shell?.innerHTML).toContain('px-4')
    expect(shell?.innerHTML).toContain('lg:px-6')

    const sidebar = () => container?.querySelector('[data-sidebar="sidebar"]')?.textContent ?? ''
    expect(sidebar()).toContain('项目管理')
    expect(sidebar()).toContain('项目')
    expect(sidebar()).toContain('项目监控')
    expect(sidebar()).not.toContain('流水线')
    expect(sidebar()).not.toContain('接口测试')
    expect(sidebar()).not.toContain('容器管理')
    expect(container.textContent).toContain('项目内容')

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
    expect(sidebar()).toContain('容器管理')
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
    const crumbs = [...(container.querySelectorAll('[data-slot="breadcrumb-item"]') ?? [])]
    expect(crumbs[0]?.className).toContain('shrink-0')
    expect(crumbs.at(-1)?.className).toContain('shrink')
    expect(crumbs.at(-1)?.className).not.toContain('shrink-0')

    await act(async () => {
      await mounted.router.navigate('/api-test')
    })
    expect(sidebar()).toContain('接口测试')
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
