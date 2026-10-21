import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { Pagination } from '@/components/console/Pagination'
import { setInputValue } from '@/test-utils/pipeline'

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

  it('derivesPageCountAndSizeOptions', () => {
    const onPageSizeChange = vi.fn()
    const view = mount(
      <Pagination
        page={1}
        pageCount={1}
        total={128}
        pageSize={10}
        showJump={false}
        onPageChange={() => {}}
        onPageSizeChange={onPageSizeChange}
      />,
    )
    root = view.root
    container = view.container

    expect(container.textContent).toContain('共 128 条 · 第 1 / 13 页')
    expect(container.querySelector('[aria-label="每页条数"]')?.textContent).toContain('每页 10 条')
    expect(container.textContent).not.toContain('跳至')
  })

  it('clampsJumpInput', () => {
    const onPageChange = vi.fn()
    const view = mount(
      <Pagination
        page={1}
        pageCount={20}
        total={200}
        pageSize={10}
        showJump
        onPageChange={onPageChange}
      />,
    )
    root = view.root
    container = view.container

    const input = container.querySelector('[aria-label="跳至页码"]') as HTMLInputElement
    expect(input).not.toBeNull()

    setInputValue(input, '999')
    act(() => {
      input.dispatchEvent(new FocusEvent('focusout', { bubbles: true }))
    })
    expect(onPageChange).toHaveBeenCalledWith(20)

    onPageChange.mockClear()
    setInputValue(input, 'abc')
    act(() => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    })
    expect(onPageChange).not.toHaveBeenCalled()
    expect(input.value).toBe('1')
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