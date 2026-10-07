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
    expect(sidebar()).toContain('Pod')
    expect(sidebar()).toContain('镜像仓库')
    expect(sidebar()).not.toContain('项目管理')
    expect(sidebar()).not.toContain('流水线')
    expect(container.textContent).toContain('集群内容')

    await act(async () => {
      await mounted.router.navigate('/api-test')
    })
    expect(sidebar()).toContain('接口测试')
    expect(sidebar()).toContain('集合')
    expect(sidebar()).toContain('环境')
    expect(sidebar()).not.toContain('Pod')
    expect(container.textContent).toContain('接口内容')
  })
})
