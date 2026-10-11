import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { YamlPanel } from '@/modules/container/components/YamlPanel'

const payload = JSON.stringify({
  apiVersion: 'v1',
  kind: 'Pod',
  metadata: {
    name: 'demo',
    annotations: {
      'kubectl.kubernetes.io/last-applied-configuration': JSON.stringify({ kind: 'Pod', metadata: { name: 'demo' } }),
    },
  },
})

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

describe('YamlPanel', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('shows pretty YAML and saves the original JSON when nothing changed', async () => {
    const save = vi.fn(async () => undefined)
    const view = mount(<YamlPanel queryKey={['yaml-panel-test']} load={async () => payload} save={save} />)
    root = view.root
    container = view.container
    let editor: HTMLTextAreaElement | null = null
    for (let attempt = 0; attempt < 15; attempt += 1) {
      const node = container.querySelector('textarea')
      if (node instanceof HTMLTextAreaElement && node.value.includes('apiVersion: v1')) {
        editor = node
        break
      }
      await flush()
    }
    expect(editor?.value).toContain('kind: Pod')
    expect(editor?.value).not.toContain('\\"')
    const button = [...container.querySelectorAll('button')].find((item) => item.textContent === '保存')
    await act(async () => {
      button?.click()
    })
    await flush()
    expect(save).toHaveBeenCalledWith(payload)
  })
})
