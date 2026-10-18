import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { PodShell } from '@/modules/container/components/PodShell'
import { TENANT_ID_KEY } from '@/modules/user/types'

vi.mock('@xterm/xterm', () => ({
  Terminal: class {
    cols = 80
    rows = 24
    loadAddon() {}
    open() {}
    onData() {
      return { dispose() {} }
    }
    onResize() {
      return { dispose() {} }
    }
    writeln() {}
    write() {}
    dispose() {}
  },
}))

vi.mock('@xterm/addon-fit', () => ({
  FitAddon: class {
    fit() {}
  },
}))

vi.mock('@/shared/keycloak', () => ({
  getToken: vi.fn(async () => 'token'),
}))

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('PodShell toolbar', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('keeps container, path, upload, and download on a wrapping toolbar', () => {
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
        send() {}
        close() {}
      },
    )
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
        unobserve() {}
      },
    )
    const view = mount(<PodShell clusterId="1" namespace="default" name="demo" containers={['app']} />)
    root = view.root
    container = view.container
    const toolbar = container.querySelector('[data-slot="log-toolbar"]')
    expect(toolbar?.className).toContain('flex-wrap')
    expect(toolbar?.textContent).toContain('上传到该路径')
    expect(toolbar?.textContent).toContain('下载该路径')
    expect(container.querySelector('[aria-label="容器"]')).toBeTruthy()
    const path = container.querySelector('[aria-label="容器内路径"]')
    expect(path).toBeInstanceOf(HTMLInputElement)
    expect(path?.className.split(/\s+/)).toContain('w-56')
    expect(path?.className.split(/\s+/)).not.toContain('w-full')
    const headerClass = container.querySelector('header')?.className.split(/\s+/) ?? []
    expect(headerClass).toContain('min-h-10')
    expect(headerClass).toContain('flex-wrap')
  })

  it('sends tenantId in the shell websocket url', async () => {
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
    vi.stubGlobal(
      'ResizeObserver',
      class {
        observe() {}
        disconnect() {}
        unobserve() {}
      },
    )
    localStorage.setItem(TENANT_ID_KEY, '10')

    const view = mount(<PodShell clusterId="1" namespace="default" name="demo" containers={['app']} />)
    root = view.root
    container = view.container
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20))
    })

    expect(urls[0]).toContain('tenantId=10')
    localStorage.removeItem(TENANT_ID_KEY)
    vi.unstubAllGlobals()
  })
})
