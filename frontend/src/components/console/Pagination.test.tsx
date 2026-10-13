import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Pagination } from '@/components/console/Pagination'

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('Pagination', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('shows the default summary and reports page changes', () => {
    const onPageChange = vi.fn()
    const view = mount(<Pagination page={2} pageCount={5} onPageChange={onPageChange} />)
    root = view.root
    container = view.container

    expect(container.textContent).toContain('第 2 / 5 页')
    expect(container.querySelector('[aria-current="page"]')?.textContent).toBe('2')

    act(() => {
      ;(container?.querySelector('[aria-label="下一页"]') as HTMLElement).click()
    })
    expect(onPageChange).toHaveBeenCalledWith(3)

    act(() => {
      ;(container?.querySelector('[aria-label="第 5 页"]') as HTMLElement).click()
    })
    expect(onPageChange).toHaveBeenCalledWith(5)
  })

  it('collapses long ranges with an ellipsis', () => {
    const view = mount(<Pagination page={10} pageCount={40} onPageChange={() => {}} />)
    root = view.root
    container = view.container

    expect(container.querySelectorAll('[aria-label^="第"]').length).toBeLessThan(10)
    expect(container.textContent).toContain('…')
  })

  it('hides the pager and keeps a custom summary when there is a single page', () => {
    const view = mount(
      <Pagination page={1} pageCount={1} onPageChange={() => {}} summary={<span>共 3 条</span>} />,
    )
    root = view.root
    container = view.container

    expect(container.textContent).toContain('共 3 条')
    expect(container.querySelector('[aria-label="下一页"]')).toBeNull()
    expect(container.querySelector('[aria-current="page"]')).toBeNull()
  })
})