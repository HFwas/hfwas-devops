import { act } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  pmFieldApi,
  pmModuleApi,
  pmStatusApi,
  pmWorkItemApi,
} from '@/modules/pm/api'
import { userAuthApi } from '@/modules/user/api'
import type { PmWorkItem } from '@/modules/pm/types'

vi.mock('@/modules/pm/api', () => ({
  pmWorkItemApi: {
    getById: vi.fn(),
    save: vi.fn(),
    updateField: vi.fn(),
    delete: vi.fn(),
    transition: vi.fn(),
    listComments: vi.fn(),
    countComments: vi.fn(),
    saveComment: vi.fn(),
    listActivities: vi.fn(),
  },
  pmStatusApi: { options: vi.fn(), allowed: vi.fn() },
  pmModuleApi: { flat: vi.fn() },
  pmFieldApi: { list: vi.fn() },
}))

vi.mock('@/modules/user/api', () => ({
  userAuthApi: { userOptions: vi.fn() },
}))

import { WorkItemDetailDialog } from '@/modules/pm/components/WorkItemDetailDialog'

const item: PmWorkItem = {
  id: 9,
  projectId: 10,
  itemKey: 'DEMO-9',
  typeCode: 'task',
  title: '登录页超时',
  description: '## 现象\n\n- **重连**失败',
  status: 'open',
  priority: 'high',
  assigneeId: 2,
  moduleId: 3,
  endTime: '2026-11-01T00:00:00',
  createTime: '2026-10-01T09:00:00',
  updateTime: '2026-10-22T10:00:00',
  version: '1',
  customFields: { storyPoints: 5, risk: '高' },
}

const statusOptions = [
  { statusCode: 'open', statusName: '待处理', sortOrder: 1, isInitial: 1, isFinal: 0, transitions: [] },
  { statusCode: 'done', statusName: '已完成', sortOrder: 2, isInitial: 0, isFinal: 1, transitions: [] },
]

function mountDialog(props: Partial<Parameters<typeof WorkItemDetailDialog>[0]> = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(
      <QueryClientProvider client={client}>
        <WorkItemDetailDialog
          projectId="10"
          typeCode="task"
          itemId="9"
          tab="detail"
          open
          onOpenChange={() => {}}
          onTabChange={() => {}}
          {...props}
        />
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

function click(element: Element | null | undefined) {
  act(() => {
    ;(element as HTMLElement | null)?.click()
  })
}

describe('WorkItemDetailDialog', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  beforeEach(() => {
    ;(globalThis as { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true
    vi.clearAllMocks()
    vi.mocked(pmWorkItemApi.getById).mockResolvedValue(item as never)
    vi.mocked(pmWorkItemApi.save).mockResolvedValue(9)
    vi.mocked(pmWorkItemApi.updateField).mockResolvedValue({ item: { ...item, version: 2 }, fieldKey: 'title' } as never)
    vi.mocked(pmWorkItemApi.transition).mockResolvedValue(undefined)
    vi.mocked(pmWorkItemApi.listComments).mockResolvedValue([])
    vi.mocked(pmWorkItemApi.countComments).mockResolvedValue(2)
    vi.mocked(pmWorkItemApi.saveComment).mockResolvedValue(1)
    vi.mocked(pmWorkItemApi.listActivities).mockResolvedValue([])
    vi.mocked(pmWorkItemApi.delete).mockResolvedValue(undefined)
    vi.mocked(pmStatusApi.options).mockResolvedValue(statusOptions as never)
    vi.mocked(pmStatusApi.allowed).mockResolvedValue({ transitions: [{ id: 't1', name: '完成', toStatus: 'done', toStatusName: '已完成' }] } as never)
    vi.mocked(pmModuleApi.flat).mockResolvedValue([{ id: 3, projectId: 10, name: '登录模块' }] as never)
    vi.mocked(pmFieldApi.list).mockResolvedValue([
      { fieldKey: 'storyPoints', fieldName: '故事点', fieldType: 'NUMBER' },
      { fieldKey: 'risk', fieldName: '风险等级', fieldType: 'SELECT' },
    ] as never)
    vi.mocked(userAuthApi.userOptions).mockResolvedValue([
      { id: 2, username: 'zhangsan', displayName: '张三', role: 'user' },
    ] as never)
  })

  afterEach(() => {
    act(() => root?.unmount())
    container?.remove()
    document.body.innerHTML = ''
    root = null
    container = null
  })

  it('rendersTwoColumnPrototypeLayout', async () => {
    const view = mountDialog()
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('登录页超时'))

    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    const tabs = Array.from(dialog.querySelectorAll('[role="tab"]')).map((tab) => tab.textContent)
    expect(tabs[0]).toContain('详情')
    expect(tabs[1]).toContain('操作记录')
    expect(tabs[2]).toContain('评论')
    expect(dialog.textContent).toContain('基本信息')
    expect(dialog.textContent).toContain('计划')
    expect(dialog.textContent).toContain('归属与协作')
    expect(dialog.textContent).toContain('人员与时间')
    expect(dialog.textContent).toContain('故事点')
    expect(dialog.textContent).toContain('登录模块')
    expect(dialog.querySelector('strong')?.textContent).toBe('重连')
  })

  it('savesFullEditFields', async () => {
    const view = mountDialog()
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('登录页超时'))

    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === '完整编辑'))
    const titleInput = dialog.querySelector('input[aria-label="标题"]') as HTMLInputElement
    expect(titleInput).not.toBeNull()
    act(() => {
      const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
      setter?.call(titleInput, '登录页超时（已修复）')
      titleInput.dispatchEvent(new Event('input', { bubbles: true }))
    })
    click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === '保存修改'))
    await waitFor(() => expect(vi.mocked(pmWorkItemApi.updateField)).toHaveBeenCalled())
    const payload = vi.mocked(pmWorkItemApi.updateField).mock.calls[0][1]
    expect(payload.field).toBe('title')
    expect(payload.value).toBe('登录页超时（已修复）')
  })

  it('updatesPriorityInline', async () => {
    const view = mountDialog()
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('登录页超时'))

    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    click(dialog.querySelector('button[aria-label="编辑优先级"]'))
    const select = dialog.querySelector('select[aria-label="优先级"]') as HTMLSelectElement
    expect(select).not.toBeNull()
    act(() => {
      const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')?.set
      setter?.call(select, 'low')
      select.dispatchEvent(new Event('change', { bubbles: true }))
    })
    click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === '保存'))
    await waitFor(() => expect(vi.mocked(pmWorkItemApi.updateField)).toHaveBeenCalled())
    const payload = vi.mocked(pmWorkItemApi.updateField).mock.calls[0][1]
    expect(payload.field).toBe('priority')
    expect(payload.value).toBe('low')
  })

  it('clearsAssigneeInline', async () => {
    const view = mountDialog()
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('登录页超时'))

    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    click(dialog.querySelector('button[aria-label="编辑负责人"]'))
    const select = dialog.querySelector('select[aria-label="负责人"]') as HTMLSelectElement
    act(() => {
      const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')?.set
      setter?.call(select, '')
      select.dispatchEvent(new Event('change', { bubbles: true }))
    })
    click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === '保存'))
    await waitFor(() => expect(vi.mocked(pmWorkItemApi.updateField)).toHaveBeenCalled())
    const payload = vi.mocked(pmWorkItemApi.updateField).mock.calls[0][1]
    expect(payload.field).toBe('assignee_id')
    expect(payload.value).toBeNull()
  })

  it('sendsStatusWithTransitionId', async () => {
    const view = mountDialog()
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('登录页超时'))

    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === '完整编辑'))
    const statusSelect = dialog.querySelector('select[aria-label="状态"]') as HTMLSelectElement
    act(() => {
      const setter = Object.getOwnPropertyDescriptor(HTMLSelectElement.prototype, 'value')?.set
      setter?.call(statusSelect, 'done')
      statusSelect.dispatchEvent(new Event('change', { bubbles: true }))
    })
    click(Array.from(dialog.querySelectorAll('button')).find((button) => button.textContent === '保存修改'))
    await waitFor(() => expect(vi.mocked(pmWorkItemApi.updateField)).toHaveBeenCalled())
    const payload = vi.mocked(vi.mocked(pmWorkItemApi.updateField).mock.calls.at(-1)![1])
    expect(payload.field).toBe('status')
    expect(payload.value).toBe('done')
    expect(payload.transitionId).toBe('t1')
  })

  it('hidesWriteActionsInReadOnly', async () => {
    const view = mountDialog({ readOnly: true })
    root = view.root
    container = view.container
    await waitFor(() => expect(document.body.textContent).toContain('登录页超时'))

    const dialog = document.querySelector('[data-slot="dialog-content"]') as HTMLElement
    expect(Array.from(dialog.querySelectorAll('button')).some((button) => button.textContent === '完整编辑')).toBe(false)
    expect(dialog.textContent).not.toContain('编辑描述')
    expect(dialog.querySelector('button[aria-label="删除事项"]')).toBeNull()
    expect(dialog.textContent).toContain('只读')
    // 只读态不应残留任何行内「保存 / 取消」按钮。
    expect(Array.from(dialog.querySelectorAll('button')).some((button) => ['保存', '取消'].includes(button.textContent?.trim() ?? ''))).toBe(false)
  })
})
