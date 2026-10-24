import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { MemoryRouter, Route, Routes, useLocation } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  pmBatchActionConfigApi,
  pmFieldApi,
  pmModuleApi,
  pmProjectApi,
  pmProjectIssueTypeApi,
  pmStatusApi,
  pmViewApi,
  pmWorkItemApi,
} from '@/modules/pm/api'
import { userAuthApi } from '@/modules/user/api'
import { useAuthStore } from '@/stores/auth'
import { toApiError } from '@/shared/errors/apiError'
import type { BatchOperationResult, PmWorkItem } from '@/modules/pm/types'

vi.mock('@/modules/pm/api', () => ({
  pmProjectApi: { getById: vi.fn() },
  pmProjectIssueTypeApi: { list: vi.fn() },
  pmFieldApi: { list: vi.fn(), catalog: vi.fn(), resolveOptions: vi.fn() },
  pmStatusApi: { options: vi.fn(), allowed: vi.fn() },
  pmViewApi: { list: vi.fn(), save: vi.fn() },
  pmBatchActionConfigApi: { list: vi.fn(), save: vi.fn() },
  pmModuleApi: { flat: vi.fn() },
  pmWorkItemApi: {
    page: vi.fn(),
    save: vi.fn(),
    delete: vi.fn(),
    getById: vi.fn(),
    transition: vi.fn(),
    listComments: vi.fn(),
    countComments: vi.fn(),
    saveComment: vi.fn(),
    listActivities: vi.fn(),
    batchUpdate: vi.fn(),
    batchTransition: vi.fn(),
  },
}))

vi.mock('@/modules/user/api', () => ({
  userAuthApi: { userOptions: vi.fn() },
}))

import { WorkItemListPage } from '@/modules/pm/pages/WorkItemListPage'

const item: PmWorkItem = {
  id: 9,
  projectId: 10,
  itemKey: 'DEMO-9',
  typeCode: 'task',
  title: '登录页超时',
  status: 'open',
  priority: 'high',
  assigneeId: 2,
  endTime: '2026-11-01T00:00:00',
  updateTime: '2026-10-22T10:00:00',
  version: '1',
}

const batchResult: BatchOperationResult = {
  batchId: 'b1',
  dryRun: false,
  total: 1,
  success: 1,
  skipped: 0,
  failed: 0,
  items: [{ id: '9', outcome: 'SUCCESS', version: 2 }],
}

function LocationProbe() {
  const location = useLocation()
  return <span data-testid="location">{location.search}</span>
}

function mountPage(path: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(
      <QueryClientProvider client={client}>
        <MemoryRouter initialEntries={[path]}>
          <Routes>
            <Route
              path="/pm/projects/:projectId/items/:typeCode"
              element={<><WorkItemListPage /><LocationProbe /></>}
            />
          </Routes>
        </MemoryRouter>
      </QueryClientProvider>,
    )
  })
  return { container, root }
}

async function flush() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 20))
  })
}

async function waitFor(assertion: () => void) {
  let last: unknown
  for (let attempt = 0; attempt < 30; attempt += 1) {
    try {
      assertion()
      return
    } catch (error) {
      last = error
      await flush()
    }
  }
  throw last
}

function setInputValue(input: HTMLInputElement, value: string) {
  const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
  act(() => {
    setter?.call(input, value)
    input.dispatchEvent(new Event('input', { bubbles: true }))
  })
}

function click(element: Element | null | undefined) {
  act(() => {
    ;(element as HTMLElement | null)?.click()
  })
}

function buttonByText(root: ParentNode, text: string) {
  return Array.from(root.querySelectorAll('button')).find((button) => button.textContent?.trim() === text)
}

function setupDefaults() {
  vi.mocked(pmProjectApi.getById).mockResolvedValue({ id: 10, name: '云原生交付平台', code: 'DEVOPS' } as never)
  vi.mocked(pmProjectIssueTypeApi.list).mockResolvedValue([
    { code: 'task', name: '任务', enabled: 1, workItemCount: 5 },
    { code: 'bug', name: '缺陷', enabled: 1, workItemCount: 2 },
  ] as never)
  vi.mocked(pmFieldApi.list).mockResolvedValue([])
  vi.mocked(pmFieldApi.catalog).mockResolvedValue([])
  vi.mocked(pmWorkItemApi.getById).mockResolvedValue(item as never)
  vi.mocked(pmWorkItemApi.listComments).mockResolvedValue([])
  vi.mocked(pmWorkItemApi.countComments).mockResolvedValue(0)
  vi.mocked(pmWorkItemApi.listActivities).mockResolvedValue([])
  vi.mocked(pmWorkItemApi.saveComment).mockResolvedValue(1)
  vi.mocked(pmWorkItemApi.transition).mockResolvedValue(undefined)
  vi.mocked(pmStatusApi.allowed).mockResolvedValue({ transitions: [] } as never)
  vi.mocked(pmStatusApi.options).mockResolvedValue([
    { statusCode: 'open', statusName: '待处理' },
    { statusCode: 'done', statusName: '已完成' },
  ] as never)
  vi.mocked(pmViewApi.list).mockResolvedValue([])
  vi.mocked(pmViewApi.save).mockResolvedValue(77)
  vi.mocked(pmBatchActionConfigApi.list).mockResolvedValue([
    { actionCode: 'UPDATE_FIELDS', enabled: true, maxItems: 100, requireConfirm: true, allowedRoles: [], fieldWhitelist: ['end_time'] },
    { actionCode: 'TRANSITION', enabled: true, maxItems: 100, requireConfirm: true, allowedRoles: [], fieldWhitelist: null },
    { actionCode: 'DELETE', enabled: false, maxItems: 100, requireConfirm: true, allowedRoles: [], fieldWhitelist: null },
  ] as never)
  vi.mocked(pmModuleApi.flat).mockResolvedValue([])
  vi.mocked(userAuthApi.userOptions).mockResolvedValue([])
  vi.mocked(pmWorkItemApi.page).mockResolvedValue({
    records: [item],
    total: 1,
    size: 10,
    current: 1,
    pages: 1,
  } as never)
  vi.mocked(pmWorkItemApi.batchUpdate).mockResolvedValue(batchResult)
  vi.mocked(pmWorkItemApi.batchTransition).mockResolvedValue(batchResult)
  vi.mocked(pmWorkItemApi.delete).mockResolvedValue(undefined)
  useAuthStore.setState({
    user: { id: '2', username: 'zhangsan', displayName: '张三', role: 'user' },
  })
}

describe('WorkItemListPage', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    setupDefaults()
  })

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    root = null
    container = null
  })

  it('rendersDefaultList', async () => {
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    expect(view.container.textContent).toContain('任务')
    expect(view.container.textContent).toContain('共 1 条')
    const spec = vi.mocked(pmWorkItemApi.page).mock.calls[0][0]
    expect(spec.typeCode).toBe('task')
  })

  it('opensDetailInDialogAndClosesBackToList', async () => {
    const view = mountPage('/pm/projects/10/items/task?detail=9&page=1')
    root = view.root
    container = view.container
    await waitFor(() => expect(document.querySelector('[data-slot="dialog-content"]')?.textContent).toContain('登录页超时'))
    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    expect(vi.mocked(pmWorkItemApi.getById)).toHaveBeenCalledWith('9')

    const commentTab = Array.from(dialog.querySelectorAll('[role="tab"]')).find((button) => button.textContent?.startsWith('评论'))
    click(commentTab)
    await waitFor(() => {
      const search = view.container.querySelector('[data-testid="location"]')?.textContent ?? ''
      expect(search).toContain('dtab=comments')
      expect(search).toContain('page=1')
    })

    act(() => {
      const closeButton = Array.from(document.querySelectorAll('[data-slot="dialog-content"] button'))
        .find((button) => button.textContent?.trim() === '关闭')
      ;(closeButton as HTMLElement | undefined)?.click()
    })
    await waitFor(() => {
      const search = view.container.querySelector('[data-testid="location"]')?.textContent ?? ''
      expect(search).not.toContain('detail=9')
      expect(search).not.toContain('dtab=')
      expect(search).toContain('page=1')
    })
  })

  it('keepsDetailWhenListWrites', async () => {
    const view = mountPage('/pm/projects/10/items/task?detail=9&page=5')
    root = view.root
    container = view.container
    await waitFor(() => expect(document.querySelector('[data-slot="dialog-content"]')?.textContent).toContain('登录页超时'))
    await waitFor(() => {
      const search = view.container.querySelector('[data-testid="location"]')?.textContent ?? ''
      expect(search).toContain('detail=9')
      expect(search).not.toContain('page=5')
    })
    expect(document.querySelector('[data-slot="dialog-content"]')).not.toBeNull()
  })

  it('titleLinksToDetailParam', async () => {
    const view = mountPage('/pm/projects/10/items/task?page=1')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    const link = view.container.querySelector('a[href*="detail="]')
    expect(link?.getAttribute('href')).toContain('/pm/projects/10/items/task?')
    expect(link?.getAttribute('href')).toContain('detail=9')
    expect(link?.getAttribute('href')).toContain('page=1')
  })

  it('keepsCreateInTabRowAndDropsInlineFilterAdd', async () => {
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    const tabRow = view.container.querySelector('[data-slot="list-page-tab-row"]')
    expect(tabRow?.textContent).toContain('新建')
    expect(buttonByText(view.container, '添加筛选')).toBeUndefined()
    expect(view.container.textContent).not.toContain('更新于')
    expect(view.container.textContent).not.toContain('云原生交付平台')
    expect(view.container.querySelector('h1')?.className).toContain('sr-only')
  })

  it('allItemsOmitsTypeCode', async () => {
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    const spec = vi.mocked(pmWorkItemApi.page).mock.calls[0][0]
    expect(spec.typeCode).toBeUndefined()
    expect(spec.projectId).toBe('10')
  })

  it('searchWritesUrl', async () => {
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    const input = view.container.querySelector('input[aria-label^="搜索"]') as HTMLInputElement
    setInputValue(input, '登录')
    act(() => {
      input.dispatchEvent(new KeyboardEvent('keydown', { key: 'Enter', bubbles: true }))
    })
    await waitFor(() => expect(view.container.querySelector('[data-testid="location"]')?.textContent).toContain('q=%E7%99%BB%E5%BD%95'))
    await waitFor(() => {
      const matched = vi.mocked(pmWorkItemApi.page).mock.calls.some(([spec]) =>
        JSON.stringify(spec).includes('LIKE') && JSON.stringify(spec).includes('登录'))
      expect(matched).toBe(true)
    })
  })

  it('appliesFilter', async () => {
    const tree = {
      logic: 'AND',
      conditions: [{ id: 'c1', field: 'status', operator: 'IN', value: ['open'] }],
      groups: [],
    }
    const view = mountPage(`/pm/projects/10/items/all?overlay=filter&f=${encodeURIComponent(JSON.stringify(tree))}`)
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('应用筛选'))
    click(buttonByText(document.body, '应用筛选'))
    await waitFor(() => expect(view.container.querySelector('[data-testid="location"]')?.textContent).not.toContain('overlay=filter'))
    await waitFor(() => expect(view.container.textContent).toContain('筛后 1 条'))
    const filterBar = view.container.querySelector('[data-slot="filter-bar"]')
    expect(filterBar?.textContent).toContain('状态')
    expect(filterBar?.textContent).toContain('清空全部')
    expect(buttonByText(view.container, '添加筛选')).toBeUndefined()
    await waitFor(() => {
      const matched = vi.mocked(pmWorkItemApi.page).mock.calls.some(([spec]) =>
        JSON.stringify(spec).includes('"operator":"IN"') && JSON.stringify(spec).includes('"field":"status"'))
      expect(matched).toBe(true)
    })
  })

  it('columnConfigUpdates', async () => {
    const view = mountPage('/pm/projects/10/items/all?overlay=view-config&tab=columns')
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('视图配置'))
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    const headers = () => Array.from(view.container.querySelectorAll('thead th')).map((th) => th.textContent?.trim())
    const initialIndex = headers().indexOf('标题')
    click(document.body.querySelector('[aria-label="下移 标题"]'))
    await waitFor(() => expect(headers().indexOf('标题')).toBe(initialIndex + 1))
    click(buttonByText(document.body, '保存配置'))
    await waitFor(() => expect(pmViewApi.save).toHaveBeenCalled())
    const payload = vi.mocked(pmViewApi.save).mock.calls[0][0]
    const columns = payload.columns?.[0] as { listFields: string[] }
    expect(columns.listFields.indexOf('title')).toBe(3)
  })

  it('restoreViewConfigResetsWithoutPreviewEcho', async () => {
    const view = mountPage('/pm/projects/10/items/all?overlay=view-config&tab=columns')
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('视图配置'))
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    const headers = () => Array.from(view.container.querySelectorAll('thead th')).map((th) => th.textContent?.trim())
    const initial = headers()
    click(document.body.querySelector('[aria-label="下移 标题"]'))
    await waitFor(() => expect(headers().indexOf('标题')).toBe(initial.indexOf('标题') + 1))
    click(buttonByText(document.body, '恢复默认'))
    await waitFor(() => expect(headers()).toEqual(initial))
  })

  it('selectionShowsBulkBarAndBulkEdit', async () => {
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    click(view.container.querySelector('[aria-label="全选当前页"]'))
    await waitFor(() => expect(view.container.textContent).toContain('已选'))
    click(buttonByText(view.container, '批量编辑'))
    await waitFor(() => expect(document.body.textContent).toContain('批量编辑 1 个工作项'))
    const input = document.body.querySelector('[aria-label="截止时间新值"]') as HTMLInputElement
    setInputValue(input, '2026-11-01')
    click(buttonByText(document.body, '影响预览'))
    await waitFor(() => expect(pmWorkItemApi.batchUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ dryRun: true, patch: { end_time: '2026-11-01' }, versions: { '9': 1 } }),
    ))
    await waitFor(() => expect(document.body.textContent).toContain('共 1 项'))
    await flush()
    click(buttonByText(document.body, '应用修改'))
    await waitFor(() => expect(pmWorkItemApi.batchUpdate).toHaveBeenCalledWith(
      expect.objectContaining({ dryRun: false }),
    ))
  })

  it('bulkTransition', async () => {
    vi.mocked(pmWorkItemApi.batchTransition).mockResolvedValue({
      batchId: 'b1', dryRun: false, total: 1, success: 0, skipped: 1, failed: 0,
      items: [{ id: '9', outcome: 'SKIPPED', code: 21012, message: '当前状态不允许流转到 done' }],
    })
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    click(view.container.querySelector('[aria-label="全选当前页"]'))
    await waitFor(() => expect(view.container.textContent).toContain('已选'))
    click(buttonByText(view.container, '状态流转'))
    await waitFor(() => expect(document.body.textContent).toContain('状态流转 1 个工作项'))
    click(buttonByText(document.body, '执行流转'))
    await waitFor(() => expect(pmWorkItemApi.batchTransition).toHaveBeenCalledWith(
      expect.objectContaining({ toStatus: 'done', dryRun: false }),
    ))
    await waitFor(() => expect(document.body.textContent).toContain('当前状态不允许流转到 done'))
    expect(document.body.textContent).toContain('跳过 1')
  })

  it('bulkDeleteSequential', async () => {
    vi.mocked(pmBatchActionConfigApi.list).mockResolvedValue([
      { actionCode: 'UPDATE_FIELDS', enabled: true, maxItems: 100, requireConfirm: true, fieldWhitelist: ['end_time'] },
      { actionCode: 'DELETE', enabled: true, maxItems: 100, requireConfirm: true, fieldWhitelist: null },
    ] as never)
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    click(view.container.querySelector('[aria-label="全选当前页"]'))
    await waitFor(() => expect(view.container.textContent).toContain('已选'))
    click(buttonByText(view.container, '删除'))
    await waitFor(() => expect(document.body.textContent).toContain('删除 1 个工作项？'))
    click(buttonByText(document.body, '删除 1 个工作项'))
    await waitFor(() => expect(pmWorkItemApi.delete).toHaveBeenCalledWith(9))
  })

  it('bulkDeleteFailedKeepsSelection', async () => {
    vi.mocked(pmBatchActionConfigApi.list).mockResolvedValue([
      { actionCode: 'DELETE', enabled: true, maxItems: 100, requireConfirm: true, fieldWhitelist: null },
    ] as never)
    const second = { ...item, id: 10, itemKey: 'DEMO-10', title: '订单导出失败' }
    vi.mocked(pmWorkItemApi.page).mockResolvedValue({
      records: [item, second], total: 2, size: 10, current: 1, pages: 1,
    } as never)
    vi.mocked(pmWorkItemApi.delete)
      .mockRejectedValueOnce(new Error('删除失败'))
      .mockResolvedValueOnce(undefined)
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))
    click(view.container.querySelector('[aria-label="全选当前页"]'))
    await waitFor(() => expect(view.container.textContent).toContain('已选'))
    click(buttonByText(view.container, '删除'))
    await waitFor(() => expect(document.body.textContent).toContain('删除 2 个工作项？'))
    click(buttonByText(document.body, '删除 2 个工作项'))
    await waitFor(() => expect(view.container.querySelector('[data-testid="location"]')?.textContent).toContain('sel=9'))
  })

  it('forbiddenState', async () => {
    vi.mocked(pmWorkItemApi.page).mockRejectedValue(toApiError(20002))
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('无权访问'))
    expect(buttonByText(view.container, '新建')).toBeUndefined()
  })

  it('emptyAndErrorStates', async () => {
    vi.mocked(pmWorkItemApi.page).mockResolvedValue({ records: [], total: 0, size: 10, current: 1, pages: 0 } as never)
    const view = mountPage('/pm/projects/10/items/all')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('没有匹配的工作项'))

    act(() => root?.unmount())
    view.container.remove()
    vi.mocked(pmWorkItemApi.page).mockRejectedValue(new Error('服务不可用'))
    const second = mountPage('/pm/projects/10/items/all')
    root = second.root
    container = second.container
    await waitFor(() => expect(second.container.textContent).toContain('加载失败'))
  })

  it('应用筛选后写入 URL 并重新查询', async () => {
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))

    click(buttonByText(document.body, '筛选'))
    await waitFor(() => expect(document.body.textContent).toContain('筛选工作项'))
    click(buttonByText(document.body, '我的工作项'))
    click(buttonByText(document.body, '应用筛选'))

    await waitFor(() => {
      const search = decodeURIComponent(view.container.querySelector('[data-testid="location"]')?.textContent ?? '')
      expect(search).toContain('f=')
      expect(search).toContain('assignee_id')
    })
    await waitFor(() => {
      const calls = vi.mocked(pmWorkItemApi.page).mock.calls
      const last = calls.at(-1)?.[0]
      expect(JSON.stringify(last)).toContain('assignee_id')
    })
    expect(document.body.textContent).not.toContain('筛选工作项')
  })

  it('空态显示新建按钮', async () => {
    vi.mocked(pmWorkItemApi.page).mockResolvedValue({ records: [], total: 0, size: 10, current: 1, pages: 0 } as never)
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('没有匹配的工作项'))
    expect(buttonByText(view.container, '新建工作项')).toBeTruthy()
    expect(view.container.querySelector('thead')?.textContent).toContain('标题')
  })

  it('加载态保留表头与 8 行骨架', async () => {
    vi.mocked(pmWorkItemApi.page).mockReturnValue(new Promise(() => {}) as never)
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.querySelectorAll('tbody tr').length).toBe(8))
    expect(view.container.querySelector('thead')?.textContent).toContain('标题')
  })

  it('加载失败展示请求编号', async () => {
    vi.mocked(pmWorkItemApi.page).mockRejectedValue(toApiError(500, '服务不可用', 'req_demo_1') as never)
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('req_demo_1'))
  })

  it('保存视图后切换到新视图', async () => {
    vi.mocked(pmViewApi.list)
      .mockResolvedValueOnce([] as never)
      .mockResolvedValue([{ id: 77, projectId: 10, name: '研发待办 · 我的配置', typeCode: 'task', querySpec: {} }] as never)
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))

    click(buttonByText(view.container, '保存视图'))
    await waitFor(() => expect(document.body.textContent).toContain('视图名称'))
    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    click(buttonByText(dialog, '保存视图'))

    await waitFor(() => expect(vi.mocked(pmViewApi.save)).toHaveBeenCalled())
    await waitFor(() => {
      const search = view.container.querySelector('[data-testid="location"]')?.textContent ?? ''
      expect(search).toContain('view=77')
    })
  })

  it('选中后内联批量栏', async () => {
    const view = mountPage('/pm/projects/10/items/task')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.textContent).toContain('登录页超时'))

    click(view.container.querySelector('[aria-label="选择第 1 行"]'))
    await waitFor(() => expect(view.container.textContent).toContain('已选当前页'))
    const bar = view.container.querySelector('[data-slot="bulk-action-bar"]') as HTMLElement
    expect(bar.getAttribute('data-variant')).toBe('inline')
    expect(bar.className).not.toContain('fixed')
  })

  it('clampsPage', async () => {
    vi.mocked(pmWorkItemApi.page).mockResolvedValue({
      records: [item],
      total: 15,
      size: 10,
      current: 5,
      pages: 2,
    } as never)
    const view = mountPage('/pm/projects/10/items/all?page=5')
    root = view.root
    container = view.container
    await waitFor(() => expect(view.container.querySelector('[data-testid="location"]')?.textContent).toContain('page=2'))
  })
})
