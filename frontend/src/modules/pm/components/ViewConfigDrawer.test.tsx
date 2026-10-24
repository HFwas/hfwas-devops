import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ViewConfigDrawer } from '@/modules/pm/components/ViewConfigDrawer'
import { buildColumnCatalog } from '@/modules/pm/utils/columns'
import { defaultViewConfig } from '@/modules/pm/utils/viewConfig'
import { mount, prepareActEnvironment, unmount } from '@/test-utils/pipeline'

const catalog = buildColumnCatalog([])

function mountDrawer(tab: 'columns' | 'search' | 'filter' = 'columns') {
  return mount(
    <ViewConfigDrawer
      open
      onOpenChange={() => {}}
      tab={tab}
      onTabChange={() => {}}
      catalog={catalog}
      config={defaultViewConfig()}
      onSave={() => {}}
      onReset={() => {}}
    />,
  )
}

describe('ViewConfigDrawer', () => {
  beforeEach(() => {
    prepareActEnvironment()
  })

  afterEach(() => {
    document.body.innerHTML = ''
  })

  it('页脚与内容区分层', () => {
    const view = mountDrawer()
    const content = document.querySelector('[data-slot="sheet-content"]') as HTMLElement
    expect(content.className).toContain('overflow-hidden')
    const footer = document.querySelector('[data-slot="sheet-footer"]') as HTMLElement
    expect(footer.className).toContain('shrink-0')
    expect(footer.textContent).toContain('恢复默认')
    expect(footer.textContent).toContain('保存配置')
    unmount(view)
  })

  it('搜索字段展示实际数量', () => {
    const view = mountDrawer('search')
    expect(document.body.textContent).toContain(`搜索框会在这 ${defaultViewConfig().searchFields.length} 个字段中匹配`)
    expect(document.body.textContent).not.toContain('这 N 个字段')
    unmount(view)
  })

  it('显示列展示字段键与固定标记', () => {
    const view = mountDrawer('columns')
    expect(document.body.textContent).toContain('itemKey')
    expect(document.body.textContent).toContain('固定')
    unmount(view)
  })
})
