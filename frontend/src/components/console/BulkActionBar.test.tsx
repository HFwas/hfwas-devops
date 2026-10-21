import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { BulkActionBar } from '@/components/console/BulkActionBar'
import { mount, unmount } from '@/test-utils/pipeline'

describe('BulkActionBar', () => {
  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('rendersOnlyWhenSelected', () => {
    const view = mount(
      <BulkActionBar selectedCount={0} actions={[]} onClear={() => {}} />,
    )
    expect(view.container.querySelector('[role="toolbar"]')).toBeNull()
    unmount(view)

    const onEdit = vi.fn()
    const onClear = vi.fn()
    const second = mount(
      <BulkActionBar
        selectedCount={2}
        actions={[{ key: 'edit', label: '批量编辑', onSelect: onEdit }]}
        onClear={onClear}
      />,
    )
    expect(second.container.textContent).toContain('已选')
    expect(second.container.textContent).toContain('2')

    act(() => {
      ;(Array.from(second.container.querySelectorAll('button')).find(
        (button) => button.textContent?.includes('批量编辑'),
      ) as HTMLElement).click()
    })
    expect(onEdit).toHaveBeenCalled()

    act(() => {
      ;(second.container.querySelector('[aria-label="取消选择"]') as HTMLElement).click()
    })
    expect(onClear).toHaveBeenCalled()
    unmount(second)
  })

  it('disablesActionsWhileBusy', () => {
    const view = mount(
      <BulkActionBar
        selectedCount={1}
        busy
        actions={[{ key: 'delete', label: '删除', onSelect: () => {} }]}
        onClear={() => {}}
      />,
    )
    const button = Array.from(view.container.querySelectorAll('button')).find(
      (item) => item.textContent?.includes('删除'),
    ) as HTMLButtonElement
    expect(button.disabled).toBe(true)
    unmount(view)
  })
})
