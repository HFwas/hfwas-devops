import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { WorkItemViewTabs, mergedViewTabs } from '@/modules/pm/components/WorkItemViewTabs'
import type { PmSavedView } from '@/modules/pm/types'
import { mount, prepareActEnvironment, unmount } from '@/test-utils/pipeline'

const saved = (id: number, name: string): PmSavedView => ({
  id,
  projectId: 10,
  name,
  typeCode: 'requirement',
  querySpec: { projectId: 10, typeCode: 'requirement', pageNo: 1, pageSize: 10 },
})

describe('WorkItemViewTabs', () => {
  beforeEach(() => {
    prepareActEnvironment()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('同名已保存视图不重复渲染', () => {
    const views = mergedViewTabs([saved(1, '研发待办'), saved(2, '我的视图')])
    expect(views.filter((view) => view.label === '研发待办')).toHaveLength(1)
    expect(views.find((view) => view.label === '研发待办')?.id).toBe('dev')
    expect(views.find((view) => view.label === '我的视图')?.id).toBe('2')
  })

  it('保存视图按钮打开命名弹层', () => {
    const onSaveView = vi.fn()
    const view = mount(
      <WorkItemViewTabs activeView="dev" savedViews={[]} onSelect={() => {}} onSaveView={onSaveView} />,
    )
    const trigger = Array.from(view.container.querySelectorAll('button'))
      .find((button) => button.textContent?.includes('保存视图')) as HTMLButtonElement
    expect(trigger).toBeTruthy()
    trigger.click()
    expect(onSaveView).toHaveBeenCalled()
    unmount(view)
  })
})
