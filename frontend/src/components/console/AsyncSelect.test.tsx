import { act, type ReactNode } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { AsyncSelect, type AsyncSelectPage } from '@/components/console/AsyncSelect'

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  act(() => {
    root.render(<QueryClientProvider client={client}>{node}</QueryClientProvider>)
  })
  return { container, root }
}

async function flush(ms = 30) {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, ms))
  })
}

function makeFetch() {
  const pages: Record<number, AsyncSelectPage> = {
    1: { records: [{ value: '1', label: '项目一' }, { value: '2', label: '项目二' }], total: 4, pages: 2 },
    2: { records: [{ value: '3', label: '项目三' }, { value: '4', label: '项目四' }], total: 4, pages: 2 },
  }
  return vi.fn(async ({ page }: { page: number; pageSize: number; keyword: string }) => pages[page] ?? { records: [], total: 0, pages: 0 })
}

describe('AsyncSelect', () => {
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

  function open() {
    act(() => {
      ;(container?.querySelector('[role="combobox"]') as HTMLElement).click()
    })
  }

  it('loads the first page on open and reports selection', async () => {
    const fetchPage = makeFetch()
    const onChange = vi.fn()
    const view = mount(<AsyncSelect label="项目" value="" onChange={onChange} fetchPage={fetchPage} clearLabel="全部项目" />)
    root = view.root
    container = view.container

    open()
    await flush()
    expect(fetchPage).toHaveBeenCalledWith(expect.objectContaining({ page: 1 }))
    expect(document.body.textContent).toContain('项目一')

    const option = [...document.body.querySelectorAll('button')].find((node) => node.textContent === '项目二')
    act(() => {
      option?.click()
    })
    expect(onChange).toHaveBeenCalledWith('2')
  })

  it('loads the next page when scrolled to the bottom', async () => {
    const fetchPage = makeFetch()
    const view = mount(<AsyncSelect label="项目" value="" onChange={() => {}} fetchPage={fetchPage} />)
    root = view.root
    container = view.container

    open()
    await flush()
    const list = document.body.querySelector('[data-slot="async-select-list"]') as HTMLElement
    act(() => {
      list.dispatchEvent(new Event('scroll', { bubbles: true }))
    })
    await flush()
    expect(fetchPage).toHaveBeenCalledWith(expect.objectContaining({ page: 2 }))
  })

  it('refetches with the keyword after typing', async () => {
    const fetchPage = makeFetch()
    const view = mount(<AsyncSelect label="项目" value="" onChange={() => {}} fetchPage={fetchPage} />)
    root = view.root
    container = view.container

    open()
    await flush()
    const input = document.body.querySelector('[aria-label="搜索"]') as HTMLInputElement
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value')?.set
    act(() => {
      setter?.call(input, '订')
      input.dispatchEvent(new Event('input', { bubbles: true }))
    })
    await flush(320)
    expect(fetchPage).toHaveBeenCalledWith(expect.objectContaining({ keyword: '订' }))
  })
})