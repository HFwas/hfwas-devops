import { act } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { EmptyState } from '@/components/console/EmptyState'
import { ErrorState } from '@/components/console/ErrorState'
import { NoPermissionState } from '@/components/console/NoPermissionState'
import { mount, unmount } from '@/test-utils/pipeline'

describe('state views', () => {
  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('rendersEmptyVariants', () => {
    const view = mount(<EmptyState />)
    expect(view.container.textContent).toContain('暂无数据')
    unmount(view)

    const noResult = mount(<EmptyState variant="no-result" title="没有匹配结果" description="试试调整筛选" />)
    expect(noResult.container.textContent).toContain('没有匹配结果')
    expect(noResult.container.textContent).toContain('试试调整筛选')
    unmount(noResult)
  })

  it('rendersErrorWithRetry', () => {
    const onRetry = vi.fn()
    const view = mount(
      <ErrorState message="网络异常" requestId="req-1" onRetry={onRetry} />,
    )
    expect(view.container.querySelector('[role="alert"]')).not.toBeNull()
    expect(view.container.textContent).toContain('网络异常')
    expect(view.container.textContent).toContain('req-1')
    act(() => {
      ;(Array.from(view.container.querySelectorAll('button')).find(
        (button) => button.textContent?.includes('重试'),
      ) as HTMLElement).click()
    })
    expect(onRetry).toHaveBeenCalled()
    unmount(view)
  })

  it('rendersNoPermission', () => {
    const view = mount(<NoPermissionState description="请联系项目管理员" />)
    expect(view.container.querySelector('[role="status"]')).not.toBeNull()
    expect(view.container.textContent).toContain('无权访问')
    expect(view.container.textContent).toContain('请联系项目管理员')
    unmount(view)
  })
})
