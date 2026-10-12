import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { RequestLimitCell } from '@/modules/container/components/RequestLimitCell'

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('RequestLimitCell', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('draws a requests bar when request and limit both parse', () => {
    const view = mount(<RequestLimitCell request="500m" limit="1" />)
    root = view.root
    container = view.container
    const fill = container.querySelector('[role="meter"] > div')
    expect(fill?.className).toContain('bg-primary')
    expect(container.textContent).toContain('500m / 1')
  })

  it('keeps the original text when the quantity cannot be parsed', () => {
    const view = mount(<RequestLimitCell request="best-effort" limit="" />)
    root = view.root
    container = view.container
    expect(container.querySelector('[role="meter"]')).toBeNull()
    expect(container.textContent).toBe('best-effort / —')
  })
})