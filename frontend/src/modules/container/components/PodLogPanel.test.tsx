import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { PodLogPanel } from '@/modules/container/components/PodLogPanel'
import { TENANT_ID_KEY } from '@/modules/user/types'
import { getToken } from '@/shared/keycloak'

vi.mock('@/modules/container/api/pod', () => ({
  podApi: {
    logs: vi.fn(async () => 'boot ERROR disk\nWARN slow\n'),
  },
}))

vi.mock('@/shared/keycloak', () => ({
  getToken: vi.fn(async () => undefined),
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

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

describe('PodLogPanel', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
    vi.restoreAllMocks()
  })

  it('uses a dark body, highlights errors by default, and downloads the buffer', async () => {
    const view = mount(<PodLogPanel clusterId="c1" namespace="ns" name="api" containers={['app']} />)
    root = view.root
    container = view.container
    await flush()
    await flush()

    expect(container.innerHTML).toContain('bg-(--term-bg)')
    expect(container.textContent).toContain('实时跟踪')
    expect(container.textContent).toContain('刷新')
    const highlight = container.querySelector('[aria-label="高亮 ERROR 和 WARN"]') as HTMLInputElement
    expect(highlight.checked).toBe(true)
    const danger = [...container.querySelectorAll('span')].find((node) => node.textContent === 'ERROR')
    expect(danger?.className).toContain('term-danger')

    const create = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:log')
    const revoke = vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined)
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined)
    const download = [...container.querySelectorAll('button')].find((button) => button.textContent === '下载')
    await act(async () => {
      download?.click()
    })
    expect(create).toHaveBeenCalled()
    expect(click).toHaveBeenCalled()
    expect(revoke).toHaveBeenCalled()

    await act(async () => {
      highlight.click()
    })
    expect(highlight.checked).toBe(false)
    expect([...container.querySelectorAll('span')].some((node) => node.className.includes('term-danger'))).toBe(false)
    expect(container.textContent).toContain('ERROR')
  })

  it('sends tenantId in the log websocket url', async () => {
    vi.mocked(getToken).mockResolvedValue('token')
    const urls: string[] = []
    vi.stubGlobal(
      'WebSocket',
      class {
        static CONNECTING = 0
        static OPEN = 1
        static CLOSING = 2
        static CLOSED = 3
        readyState = 0
        binaryType = ''
        onopen: (() => void) | null = null
        onclose: (() => void) | null = null
        onmessage: (() => void) | null = null
        onerror: (() => void) | null = null
        constructor(url: string) {
          urls.push(url)
        }
        send() {}
        close() {}
      },
    )
    localStorage.setItem(TENANT_ID_KEY, '10')

    const view = mount(<PodLogPanel clusterId="c1" namespace="ns" name="api" containers={['app']} />)
    root = view.root
    container = view.container
    await flush()
    await flush()

    expect(urls[0]).toContain('tenantId=10')
    localStorage.removeItem(TENANT_ID_KEY)
    vi.unstubAllGlobals()
  })
})
