import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DeploymentDetailPage } from '@/modules/container/pages/WorkloadDetailPages'

vi.mock('@/modules/container/api/deployment', () => ({
  deploymentApi: {
    get: vi.fn(() => new Promise(() => {})),
    pods: vi.fn(() => new Promise(() => {})),
    volumes: vi.fn(() => new Promise(() => {})),
    scale: vi.fn(),
    restart: vi.fn(),
    delete: vi.fn(),
    yaml: vi.fn(),
    updateYaml: vi.fn(),
    env: vi.fn(),
    updateEnv: vi.fn(),
    updateVolumes: vi.fn(),
  },
}))

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  act(() => {
    root.render(<QueryClientProvider client={client}>{node}</QueryClientProvider>)
  })
  return { container, root }
}

describe('DeploymentDetailPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('opens on the kite-style detail shell before data arrives', () => {
    const router = createMemoryRouter(
      [{ path: '/container/clusters/:clusterId/deployments/:namespace/:name', element: <DeploymentDetailPage /> }],
      { initialEntries: ['/container/clusters/c1/deployments/juicefs/juicefs-csi-node'] },
    )
    const view = mount(<RouterProvider router={router} />)
    root = view.root
    container = view.container
    const shell = container.querySelector('[data-slot="detail-shell"]')
    expect(shell?.textContent).toContain('juicefs-csi-node')
    expect(shell?.textContent).toContain('命名空间：juicefs')
    for (const label of ['刷新', '描述', '克隆', '重启', '删除', '概览', 'Pods', '容器', 'YAML', '日志', '终端', '卷', '关联', '历史', '事件', '监控']) {
      expect(shell?.textContent).toContain(label)
    }
    const tabs = [...(shell?.querySelectorAll('[role="tab"]') ?? [])]
    expect(tabs[0]?.getAttribute('aria-selected')).toBe('true')
    expect(tabs[0]?.textContent).toContain('概览')
    expect(shell?.querySelector('button.bg-destructive')?.textContent).toContain('删除')
  })
})
