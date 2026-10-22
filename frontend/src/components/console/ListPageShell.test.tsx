import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ListPageShell } from '@/components/console/ListPageShell'
import { mount, unmount } from '@/test-utils/pipeline'

describe('ListPageShell', () => {
  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('rendersStatePrecedence', () => {
    const onRetry = vi.fn()
    const view = mount(
      <ListPageShell
        title="工作项"
        forbidden="只读成员"
        error="加载失败"
        empty
        onRetry={onRetry}
      >
        <p>内容</p>
      </ListPageShell>,
    )
    expect(view.container.textContent).toContain('无权访问')
    expect(view.container.textContent).toContain('只读成员')
    expect(view.container.textContent).not.toContain('加载失败')

    act(() => {
      view.root.render(
        <ListPageShell title="工作项" error="接口 500" onRetry={onRetry}>
          <p>内容</p>
        </ListPageShell>,
      )
    })
    expect(view.container.textContent).toContain('加载失败')
    expect(view.container.textContent).toContain('接口 500')
    act(() => {
      ;(Array.from(view.container.querySelectorAll('button')).find(
        (button) => button.textContent?.includes('重试'),
      ) as HTMLElement).click()
    })
    expect(onRetry).toHaveBeenCalled()

    act(() => {
      view.root.render(
        <ListPageShell title="工作项" empty emptyContent="还没有事项">
          <p>内容</p>
        </ListPageShell>,
      )
    })
    expect(view.container.textContent).toContain('还没有事项')
    expect(view.container.textContent).not.toContain('内容')

    act(() => {
      view.root.render(<ListPageShell title="工作项" loading />)
    })
    expect(view.container.querySelector('[aria-busy]')).not.toBeNull()

    act(() => {
      view.root.render(
        <ListPageShell title="工作项">
          <p>内容</p>
        </ListPageShell>,
      )
    })
    expect(view.container.textContent).toContain('内容')
    unmount(view)
  })

  it('putsRowActionsInTabRowAndHidesHeader', () => {
    const view = mount(
      <ListPageShell
        title="工作项"
        showHeader={false}
        tabs={<span>研发待办</span>}
        rowActions={<button type="button">新建</button>}
      >
        <p>内容</p>
      </ListPageShell>,
    )
    const tabRow = view.container.querySelector('[data-slot="list-page-tab-row"]')
    expect(tabRow?.textContent).toContain('新建')
    expect(tabRow?.textContent).toContain('研发待办')
    const heading = view.container.querySelector('h1')
    expect(heading?.className).toContain('sr-only')
    expect(heading?.textContent).toBe('工作项')
    unmount(view)
  })

  it('fallsBackToActionsWhenHeaderHidden', () => {
    const view = mount(
      <ListPageShell
        title="工作项"
        showHeader={false}
        actions={<button type="button">导出</button>}
      >
        <p>内容</p>
      </ListPageShell>,
    )
    const tabRow = view.container.querySelector('[data-slot="list-page-tab-row"]')
    expect(tabRow?.textContent).toContain('导出')
    unmount(view)
  })
})
