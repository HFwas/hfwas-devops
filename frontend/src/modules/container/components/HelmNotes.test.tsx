import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { HelmNotes } from '@/modules/container/components/HelmNotes'

const NOTES = `
Get the URL:

  export NODE_PORT=$(kubectl get svc demo -o jsonpath="{.spec.ports[0].nodePort}")
  echo http://$NODE_IP:$NODE_PORT

**Please be patient**

** Please be patient while the chart is being deployed **
`.trim()

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('HelmNotes', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('renders commands as code and emphasis as text, not raw markup', () => {
    const view = mount(<HelmNotes notes={NOTES} />)
    root = view.root
    container = view.container
    const code = container.querySelector('pre code')
    expect(code?.textContent).toContain('export NODE_PORT=$(kubectl get svc demo')
    expect(code?.textContent).toContain('echo http://$NODE_IP:$NODE_PORT')
    expect(container.querySelector('strong')?.textContent).toBe('Please be patient')
    expect(container.textContent).toContain('** Please be patient while the chart is being deployed **')
    expect(container.textContent).toContain('Get the URL:')
  })
})
