import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it } from 'vitest'
import { UsageBar, usageFill } from '@/components/console/UsageBar'

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('usageFill', () => {
  it('keeps requests blue and limits orange at or under 60%', () => {
    expect(usageFill(0.5, 'requests')).toBe('bg-primary')
    expect(usageFill(0.6, 'limits')).toBe('bg-orange-500')
  })

  it('turns yellow above 60% and red above 90%', () => {
    expect(usageFill(0.61, 'requests')).toBe('bg-yellow-500')
    expect(usageFill(0.61, 'limits')).toBe('bg-yellow-500')
    expect(usageFill(0.91, 'limits')).toBe('bg-red-500')
  })
})

describe('UsageBar', () => {
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

  it('draws a short track and tabular caption', () => {
    const view = mount(<UsageBar kind="requests" ratio={0.5} caption="500m / 1" />)
    root = view.root
    container = view.container
    const bar = container.querySelector('[data-slot="usage-bar"]')
    const track = bar?.querySelector('[role="meter"]')
    const fill = track?.firstElementChild
    expect(track?.className).toContain('h-2')
    expect(track?.className).toContain('bg-muted')
    expect(fill?.className).toContain('bg-primary')
    expect((fill as HTMLElement).style.width).toBe('50%')
    expect(bar?.querySelector('p')?.className).toContain('tabular-nums')
    expect(bar?.textContent).toContain('500m / 1')
  })

  it('caps the width at the track when the ratio is over 1', () => {
    const view = mount(<UsageBar kind="limits" ratio={1.4} caption="1.4 / 1" />)
    root = view.root
    container = view.container
    const fill = container.querySelector('[role="meter"] > div') as HTMLElement
    expect(fill.className).toContain('bg-red-500')
    expect(fill.style.width).toBe('100%')
  })
})
