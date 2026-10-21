import { act, useState } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { DataTable, type DataTableSort } from '@/components/console/DataTable'

interface Row {
  id: string
  title: string
  status: string
}

const rows: Row[] = [
  { id: '1', title: '登录超时', status: '进行中' },
  { id: '2', title: '订单导出失败', status: '待处理' },
  { id: '3', title: '首页白屏', status: '已完成' },
]

const columns = [
  { id: 'title', header: '标题', cell: (row: Row) => row.title, sortable: true },
  { id: 'status', header: '状态', cell: (row: Row) => row.status, sortable: true },
]

function mount(node: React.ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('DataTable', () => {
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

  it('keepsDefaultCompactMarkup', () => {
    const view = mount(<DataTable columns={columns} data={rows} getRowId={(row) => row.id} />)
    root = view.root
    container = view.container

    expect(container.querySelector('th')?.className).toContain('h-10')
    expect(container.querySelector('td')?.className).toContain('p-2')
    expect(container.querySelector('td')?.className).toContain('tabular-nums')
    expect(container.querySelector('[role="checkbox"]')).toBeNull()
  })

  function SelectionHarness({ onRowClick }: { onRowClick?: () => void }) {
    const [selected, setSelected] = useState<string[]>([])
    return (
      <div>
        <DataTable
          columns={columns}
          data={rows}
          getRowId={(row) => row.id}
          selectable
          selectedIds={selected}
          onSelectionChange={setSelected}
          onRowClick={onRowClick}
        />
        <span data-testid="selected">{selected.join(',')}</span>
      </div>
    )
  }

  it('selectionAndIndeterminate', () => {
    const onRowClick = vi.fn()
    const view = mount(<SelectionHarness onRowClick={onRowClick} />)
    root = view.root
    container = view.container

    const selectAll = container.querySelector('[aria-label="全选当前页"]') as HTMLElement
    act(() => {
      selectAll.click()
    })
    expect(container.querySelector('[data-testid="selected"]')?.textContent).toBe('1,2,3')
    expect(container.querySelectorAll('tbody tr[aria-selected="true"]')).toHaveLength(3)
    expect(onRowClick).not.toHaveBeenCalled()

    const firstRow = container.querySelector('[aria-label="选择第 1 行"]') as HTMLElement
    act(() => {
      firstRow.click()
    })
    expect(container.querySelector('[data-testid="selected"]')?.textContent).toBe('2,3')
    expect(container.querySelector('[aria-label="全选当前页"]')?.getAttribute('aria-checked')).toBe('mixed')
  })

  it('shiftSelectsRangeWithoutOpeningRow', () => {
    const onRowClick = vi.fn()
    const view = mount(<SelectionHarness onRowClick={onRowClick} />)
    root = view.root
    container = view.container

    act(() => {
      ;(container?.querySelector('[aria-label="选择第 1 行"]') as HTMLElement).click()
    })
    act(() => {
      ;(container?.querySelector('[aria-label="选择第 3 行"]') as HTMLElement).dispatchEvent(
        new MouseEvent('click', { bubbles: true, shiftKey: true }),
      )
    })
    expect(container.querySelector('[data-testid="selected"]')?.textContent).toBe('1,2,3')
    expect(onRowClick).not.toHaveBeenCalled()
  })

  function SortHarness() {
    const [sorts, setSorts] = useState<DataTableSort[]>([])
    return (
      <div>
        <DataTable
          columns={columns}
          data={rows}
          getRowId={(row) => row.id}
          sorts={sorts}
          onSortChange={setSorts}
        />
        <span data-testid="sorts">{sorts.map((sort) => `${sort.field}:${sort.order}`).join(',')}</span>
      </div>
    )
  }

  it('cyclesAndAppendsSorts', () => {
    const view = mount(<SortHarness />)
    root = view.root
    container = view.container

    const sortButton = () =>
      Array.from(container?.querySelectorAll('button') ?? []).find((button) => button.textContent === '标题') as HTMLElement

    act(() => sortButton().click())
    expect(container.querySelector('[data-testid="sorts"]')?.textContent).toBe('title:ASC')
    act(() => sortButton().click())
    expect(container.querySelector('[data-testid="sorts"]')?.textContent).toBe('title:DESC')
    act(() => sortButton().click())
    expect(container.querySelector('[data-testid="sorts"]')?.textContent).toBe('')

    act(() => sortButton().click())
    const statusButton = Array.from(container?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent === '状态',
    ) as HTMLElement
    act(() => {
      statusButton.dispatchEvent(new MouseEvent('click', { bubbles: true, shiftKey: true }))
    })
    expect(container.querySelector('[data-testid="sorts"]')?.textContent).toBe('title:ASC,status:ASC')
  })

  it('appliesStandardDensity', () => {
    const view = mount(
      <DataTable columns={columns} data={rows} getRowId={(row) => row.id} density="standard" />,
    )
    root = view.root
    container = view.container

    expect(container.querySelector('th')?.className).toContain('h-11')
    expect(container.querySelector('th')?.className).toContain('px-3')
    expect(container.querySelector('td')?.className).toContain('py-2.5')
  })
})
