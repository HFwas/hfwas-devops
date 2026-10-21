import { act, useState } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SearchInput } from '@/components/console/SearchInput'
import { mount, setInputValue, unmount } from '@/test-utils/pipeline'

describe('SearchInput', () => {
  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('debouncesAndSubmitsImmediately', () => {
    const onChange = vi.fn()
    const onSearch = vi.fn()
    const view = mount(<SearchInput value="" onChange={onChange} onSearch={onSearch} />)

    const input = view.container.querySelector('input') as HTMLInputElement
    setInputValue(input, 'abc')
    act(() => {
      vi.advanceTimersByTime(299)
    })
    expect(onChange).not.toHaveBeenCalled()
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(onChange).toHaveBeenCalledWith('abc')

    onChange.mockClear()
    setInputValue(input, 'helm')
    act(() => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    })
    expect(onChange).toHaveBeenCalledWith('helm')
    expect(onSearch).toHaveBeenCalledWith('helm')

    unmount(view)
  })

  it('clearsValue', () => {
    const onChange = vi.fn()
    const view = mount(<SearchInput value="abc" onChange={onChange} />)
    const button = view.container.querySelector('[aria-label="清空搜索"]') as HTMLElement
    act(() => {
      button.click()
    })
    expect(onChange).toHaveBeenCalledWith('')
    unmount(view)
  })

  it('keepsDraftWhenParentEchoesValue', () => {
    function Harness() {
      const [value, setValue] = useState('')
      return (
        <div>
          <SearchInput value={value} onChange={setValue} />
          <span data-testid="value">{value}</span>
        </div>
      )
    }
    const view = mount(<Harness />)
    const input = view.container.querySelector('input') as HTMLInputElement
    setInputValue(input, 'ok')
    act(() => {
      vi.advanceTimersByTime(300)
    })
    expect(input.value).toBe('ok')
    expect(view.container.querySelector('[data-testid="value"]')?.textContent).toBe('ok')
    unmount(view)
  })
})
