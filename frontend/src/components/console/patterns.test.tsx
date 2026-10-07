import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { LogPanel } from '@/components/console/LogPanel'
import { StatusIcon } from '@/components/console/StatusIcon'

function mount(node: ReactNode) {
  const container = document.createElement('div')
  document.body.appendChild(container)
  const root = createRoot(container)
  act(() => {
    root.render(node)
  })
  return { container, root }
}

describe('shared console patterns', () => {
  let root: Root | null = null
  let container: HTMLElement | null = null

  afterEach(() => {
    act(() => {
      root?.unmount()
    })
    container?.remove()
    root = null
    container = null
  })

  it('renders a dense table, then empty and loading states', () => {
    const view = mount(
      <DataTable
        columns={[{ id: 'name', header: '名称', cell: (row: { name: string }) => row.name }]}
        data={[{ name: 'demo' }]}
        getRowId={(row) => row.name}
      />,
    )
    root = view.root
    container = view.container
    const head = container.querySelector('th')
    const cell = container.querySelector('td')
    expect(head?.className).toContain('h-10')
    expect(head?.className).toContain('bg-muted')
    expect(head?.className).not.toMatch(/\bpx-3\b/)
    expect(cell?.className).toContain('p-2')
    expect(cell?.textContent).toBe('demo')
    expect(container.querySelector('table')?.parentElement?.className).toContain('rounded-lg')

    act(() => {
      root?.render(
        <DataTable
          columns={[{ id: 'name', header: '名称', cell: (row: { name: string }) => row.name }]}
          data={[]}
          getRowId={(row) => row.name}
          empty="还没有数据"
        />,
      )
    })
    expect(container.textContent).toContain('还没有数据')

    act(() => {
      root?.render(
        <DataTable
          columns={[{ id: 'name', header: '名称', cell: (row: { name: string }) => row.name }]}
          data={[]}
          getRowId={(row) => row.name}
          loading
        />,
      )
    })
    expect(container.querySelectorAll('.animate-pulse').length).toBeGreaterThan(0)
  })

  it('dims existing rows while a refetch is in flight', () => {
    const view = mount(
      <DataTable
        columns={[{ id: 'name', header: '名称', cell: (row: { name: string }) => row.name }]}
        data={[{ name: 'demo' }]}
        getRowId={(row) => row.name}
        loading
      />,
    )
    root = view.root
    container = view.container
    expect(container.querySelector('[data-slot="data-table"] .opacity-75')).toBeTruthy()
  })

  it('switches detail tabs from the sticky shell', () => {
    const onValueChange = vi.fn()
    const view = mount(
      <DetailShell
        title="workload"
        tabs={[
          { value: 'overview', label: '概览' },
          { value: 'logs', label: '日志' },
        ]}
        value="overview"
        onValueChange={onValueChange}
      >
        <p>正文</p>
      </DetailShell>,
    )
    root = view.root
    container = view.container
    const shell = container.querySelector('[data-slot="detail-shell"]')
    expect(shell?.textContent).toContain('workload')
    expect(shell?.querySelector('[role="tab"][aria-selected="true"]')?.textContent).toBe('概览')
    const logs = container.querySelectorAll('[role="tab"]')[1] as HTMLButtonElement
    act(() => {
      logs.click()
    })
    expect(onValueChange).toHaveBeenCalledWith('logs')
  })

  it('shows the log chrome connection light without owning a socket', () => {
    const view = mount(
      <LogPanel title="日志" connection="connected">
        line
      </LogPanel>,
    )
    root = view.root
    container = view.container
    const panel = container.querySelector('[data-slot="log-panel"]')
    expect(panel?.className).toContain('min-h-[120px]')
    expect(panel?.className).toContain('h-[40vh]')
    expect(panel?.textContent).toContain('已连接')
    expect(container.querySelector('header')?.className).toContain('h-10')
  })

  it('colors a status icon from the shared dictionary', () => {
    const view = mount(<StatusIcon status="Failed" />)
    root = view.root
    container = view.container
    expect(container.querySelector('svg')?.getAttribute('class')).toContain('text-red-500')
    expect(container.textContent).toContain('Failed')
  })
})
