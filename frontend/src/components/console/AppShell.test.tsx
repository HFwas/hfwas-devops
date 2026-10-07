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

  it('renders one sidebar shell for workbench, PM, pipeline, container, and API test', async () => {
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

    const shell = container.querySelector('[data-slot="sidebar-inset"]')?.parentElement
    expect(shell?.innerHTML).toContain('px-4')
    expect(shell?.innerHTML).toContain('lg:px-6')

    const text = container.textContent ?? ''
    for (const label of ['工作台', '用户中心', '用户管理', '项目管理', '流水线', '容器管理', '接口测试']) {
      expect(text).toContain(label)
    }
    expect(text).toContain('项目内容')
    expect(container.querySelector('[aria-current="page"]')?.textContent).toBe('项目管理')

    await act(async () => {
      await mounted.router.navigate('/pipeline/pipelines')
    })
    expect(container.textContent).toContain('流水线内容')
    expect(container.querySelector('[aria-current="page"]')?.textContent).toBe('流水线')

    await act(async () => {
      await mounted.router.navigate('/container/clusters')
    })
    expect(container.textContent).toContain('集群内容')
    expect(container.textContent).toContain('Pod')
    expect(container.textContent).toContain('镜像仓库')

    await act(async () => {
      await mounted.router.navigate('/api-test')
    })
    expect(container.textContent).toContain('接口内容')
    expect(container.querySelector('[aria-current="page"]')?.textContent).toBe('接口测试')
  })
})
