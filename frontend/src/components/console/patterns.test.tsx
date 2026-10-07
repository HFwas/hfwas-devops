import { act, type ReactNode } from 'react'
import { createRoot, type Root } from 'react-dom/client'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { DataTable } from '@/components/console/DataTable'
import { DetailShell } from '@/components/console/DetailShell'
import { LogPanel } from '@/components/console/LogPanel'
import { ResourceOverview } from '@/components/console/ResourceOverview'
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

  it('stacks the namespace under a bold title and places metric cards under the tabs', () => {
    const view = mount(
      <DetailShell
        title="juicefs-csi-node"
        description="命名空间：juicefs"
        tabs={[{ value: 'overview', label: '概览' }]}
        value="overview"
        metrics={[{ label: '状态', value: 'Available' }, { label: '期望', value: 2, emphasis: true }]}
      >
        <p>正文</p>
      </DetailShell>,
    )
    root = view.root
    container = view.container
    const shell = container.querySelector('[data-slot="detail-shell"]')
    const title = shell?.querySelector('h1')
    expect(title?.className).toContain('font-bold')
    expect(title?.textContent).toBe('juicefs-csi-node')
    expect(title?.nextElementSibling?.textContent).toBe('命名空间：juicefs')
    const cards = shell?.querySelector('[data-slot="overview-metric-cards"]')
    const tabs = shell?.querySelector('[role="tablist"]')
    expect(cards?.textContent).toContain('状态')
    expect(cards?.textContent).toContain('期望')
    expect(tabs?.compareDocumentPosition(cards as Node)).toBe(Node.DOCUMENT_POSITION_FOLLOWING)
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

  it('lays out overview metrics beside the two columns', () => {
    const view = mount(
      <ResourceOverview metrics={[{ label: '状态', value: 'Available' }]} main={<p>pods</p>} side={<p>events</p>} />,
    )
    root = view.root
    container = view.container
    const overview = container.querySelector('[data-slot="resource-overview"]')
    expect(overview?.textContent).toContain('状态')
    expect(overview?.textContent).toContain('Available')
    expect(overview?.textContent).toContain('pods')
    expect(overview?.textContent).toContain('events')
  })

  it('colors a status icon from the shared dictionary', () => {
    const view = mount(<StatusIcon status="Failed" />)
    root = view.root
    container = view.container
    expect(container.querySelector('svg')?.getAttribute('class')).toContain('text-red-500')
    expect(container.textContent).toContain('Failed')
  })

  it('draws a status dot with the same tone color', () => {
    const view = mount(<StatusIcon variant="dot" status="Available" />)
    root = view.root
    container = view.container
    expect(container.querySelector('svg')).toBeNull()
    expect(container.querySelector('span.rounded-full')?.className).toContain('bg-green-500')
    expect(container.textContent).toContain('Available')
  })

  it('paints a Running dot green and keeps the Running icon blue', () => {
    const view = mount(<StatusIcon variant="dot" status="Running" />)
    root = view.root
    container = view.container
    expect(container.querySelector('span.rounded-full')?.className).toContain('bg-green-500')

    act(() => {
      root?.render(<StatusIcon status="Running" />)
    })
    expect(container.querySelector('svg')?.getAttribute('class')).toContain('text-blue-500')
  })
})
