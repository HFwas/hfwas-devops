import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { FilterBar } from '@/components/console/FilterBar'
import { mount, unmount } from '@/test-utils/pipeline'

describe('FilterBar', () => {
  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('removesAndResetsChips', () => {
    const removeStatus = vi.fn()
    const onReset = vi.fn()
    const view = mount(
      <FilterBar
        activeFilters={[
          { key: 'status', label: '状态 是 进行中', onRemove: removeStatus },
          { key: 'priority', label: '优先级 是 高' },
        ]}
        onReset={onReset}
        extra={<span>预计匹配 12 条</span>}
      />,
    )

    expect(view.container.textContent).toContain('状态 是 进行中')
    expect(view.container.textContent).toContain('预计匹配 12 条')

    act(() => {
      ;(view.container.querySelector('[aria-label="移除条件 status"]') as HTMLElement).click()
    })
    expect(removeStatus).toHaveBeenCalled()

    act(() => {
      ;(Array.from(view.container.querySelectorAll('button')).find(
        (button) => button.textContent === '清除全部',
      ) as HTMLElement).click()
    })
    expect(onReset).toHaveBeenCalled()

    unmount(view)
  })

  it('showsPlaceholderWhenNoFilters', () => {
    const view = mount(<FilterBar />)
    expect(view.container.textContent).toContain('未设置条件')
    unmount(view)
  })

  it('rendersBareChipsWithoutLabel', () => {
    const onReset = vi.fn()
    const view = mount(
      <FilterBar
        activeFilters={[{ key: 'status', label: '状态 是 进行中', onRemove: vi.fn() }]}
        onReset={onReset}
        resetLabel="清空全部"
        showLabel={false}
        extra={<span>筛后 1 条</span>}
      />,
    )
    expect(view.container.textContent).toContain('状态 是 进行中')
    expect(view.container.textContent).toContain('筛后 1 条')
    expect(view.container.textContent).not.toContain('筛选')
    expect(view.container.textContent).toContain('清空全部')
    unmount(view)
  })
})
