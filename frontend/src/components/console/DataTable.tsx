import { useRef, type ReactNode } from 'react'
import { ArrowDown, ArrowUp, ChevronsUpDown } from 'lucide-react'
import { Checkbox } from '@/components/ui/checkbox'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export interface DataTableColumn<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
  headClassName?: string
  /** 点击表头循环排序：升序 → 降序 → 取消。 */
  sortable?: boolean
}

export type DataTableDensity = 'compact' | 'standard'
export interface DataTableSort {
  field: string
  order: 'ASC' | 'DESC'
}

const DENSITY = {
  compact: { head: 'h-10 px-2', cell: 'p-2' },
  standard: { head: 'h-11 px-3', cell: 'px-3 py-2.5' },
} as const

/**
 * 紧凑资源表。表头 h-10 / bg-muted / px-2，单元格 p-2 tabular-nums，外框 rounded-lg border。
 * 数字列不必再各自加 tabular-nums。已有行时加载只降低透明度，避免整表闪白。
 * 新增能力（选择、排序、密度、吸顶）均为可选 props，默认行为与旧版一致。
 */
export function DataTable<T>({
  columns,
  data,
  getRowId,
  toolbar,
  loading = false,
  error,
  empty = '暂无数据',
  className,
  fixed = false,
  onRowClick,
  selectable = false,
  selectedIds,
  onSelectionChange,
  sorts,
  onSortChange,
  density = 'compact',
  stickyHeader = false,
  maxHeight,
  skeletonRows = 4,
}: {
  columns: DataTableColumn<T>[]
  data: T[]
  getRowId: (row: T) => string
  toolbar?: ReactNode
  loading?: boolean
  error?: ReactNode
  empty?: ReactNode
  className?: string
  /** 按列宽分配，避免长文本把后面的窄列撑开。 */
  fixed?: boolean
  /** 点击行内链接、按钮或输入框时不触发。 */
  onRowClick?: (row: T) => void
  selectable?: boolean
  selectedIds?: string[]
  onSelectionChange?: (ids: string[]) => void
  sorts?: DataTableSort[]
  onSortChange?: (sorts: DataTableSort[]) => void
  density?: DataTableDensity
  stickyHeader?: boolean
  maxHeight?: number | string
  /** 首屏骨架行数，默认 4；工作台按原型要求传 8。 */
  skeletonRows?: number
}) {
  const showSkeleton = loading && data.length === 0
  const showError = !showSkeleton && Boolean(error) && data.length === 0
  const showEmpty = !showSkeleton && !showError && data.length === 0
  const densityClass = DENSITY[density]
  const rowIds = data.map(getRowId)
  const selected = new Set(selectedIds ?? [])
  const selectedOnPage = rowIds.filter((id) => selected.has(id))
  const allSelected = rowIds.length > 0 && selectedOnPage.length === rowIds.length
  const someSelected = selectedOnPage.length > 0 && !allSelected
  const lastClicked = useRef<number | null>(null)
  const shiftPressed = useRef(false)
  const colSpan = columns.length + (selectable ? 1 : 0)

  function emitSelection(nextIds: string[]) {
    onSelectionChange?.(nextIds)
  }

  function toggleAll(checked: boolean) {
    const pageSet = new Set(rowIds)
    const next = new Set(selectedIds ?? [])
    for (const id of pageSet) {
      if (checked) next.add(id)
      else next.delete(id)
    }
    emitSelection([...next])
  }

  function toggleRow(index: number, checked: boolean, shiftKey: boolean) {
    const next = new Set(selectedIds ?? [])
    if (shiftKey && lastClicked.current !== null) {
      const start = Math.min(lastClicked.current, index)
      const end = Math.max(lastClicked.current, index)
      for (let current = start; current <= end; current += 1) {
        if (checked) next.add(rowIds[current])
        else next.delete(rowIds[current])
      }
    } else if (checked) {
      next.add(rowIds[index])
    } else {
      next.delete(rowIds[index])
    }
    lastClicked.current = index
    emitSelection([...next])
  }

  function toggleSort(columnId: string, shiftKey: boolean) {
    if (!onSortChange) return
    const current = sorts ?? []
    const existing = current.find((sort) => sort.field === columnId)
    if (shiftKey) {
      if (!existing) {
        onSortChange([...current, { field: columnId, order: 'ASC' }])
      } else if (existing.order === 'ASC') {
        onSortChange(current.map((sort) => (sort.field === columnId ? { ...sort, order: 'DESC' } : sort)))
      } else {
        onSortChange(current.filter((sort) => sort.field !== columnId))
      }
      return
    }
    if (!existing) {
      onSortChange([{ field: columnId, order: 'ASC' }])
    } else if (existing.order === 'ASC') {
      onSortChange([{ field: columnId, order: 'DESC' }])
    } else {
      onSortChange([])
    }
  }

  function sortOf(columnId: string): DataTableSort | undefined {
    return (sorts ?? []).find((sort) => sort.field === columnId)
  }

  return (
    <div data-slot="data-table" className={cn('flex flex-col gap-3', className)}>
      {toolbar ? <div className="flex flex-wrap items-center gap-2">{toolbar}</div> : null}
      {error && data.length > 0 ? <p className="text-sm text-destructive">{error}</p> : null}
      <div
        className={cn(loading && data.length > 0 && 'opacity-75', (stickyHeader || maxHeight) && 'overflow-auto')}
        style={maxHeight ? { maxHeight } : undefined}
      >
        <Table aria-busy={loading || undefined} className={fixed ? 'table-fixed' : undefined}>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              {selectable ? (
                <TableHead className={cn('bg-muted', densityClass.head, 'w-10')}>
                  <Checkbox
                    aria-label="全选当前页"
                    checked={allSelected ? true : someSelected ? 'indeterminate' : false}
                    onCheckedChange={(checked) => toggleAll(checked === true)}
                  />
                </TableHead>
              ) : null}
              {columns.map((column) => {
                const sort = sortOf(column.id)
                const ariaSort = sort ? (sort.order === 'ASC' ? 'ascending' : 'descending') : 'none'
                return (
                  <TableHead
                    key={column.id}
                    aria-sort={column.sortable ? ariaSort : undefined}
                    className={cn('bg-muted', densityClass.head, stickyHeader && 'sticky top-0 z-10',
                      column.headClassName)}
                  >
                    {column.sortable ? (
                      <button
                        type="button"
                        className="inline-flex items-center gap-1 text-left font-medium hover:text-foreground"
                        onClick={(event) => toggleSort(column.id, event.shiftKey)}
                      >
                        {column.header}
                        {sort ? (
                          sort.order === 'ASC' ? <ArrowUp className="size-3.5" /> : <ArrowDown className="size-3.5" />
                        ) : (
                          <ChevronsUpDown className="size-3.5 text-muted-foreground" />
                        )}
                      </button>
                    ) : (
                      column.header
                    )}
                  </TableHead>
                )
              })}
            </TableRow>
          </TableHeader>
          <TableBody>
            {showSkeleton &&
              Array.from({ length: skeletonRows }, (_, index) => (
                <TableRow key={`loading-${index}`} className="hover:bg-transparent">
                  <TableCell colSpan={colSpan} className="p-2">
                    <div className="h-4 animate-pulse rounded bg-muted" />
                  </TableCell>
                </TableRow>
              ))}
            {showError && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={colSpan} className="h-40 p-2 text-center text-sm text-destructive">
                  {error}
                </TableCell>
              </TableRow>
            )}
            {showEmpty && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={colSpan} className="h-40 p-2 text-center text-sm text-muted-foreground">
                  {empty}
                </TableCell>
              </TableRow>
            )}
            {data.map((row, index) => {
              const rowId = rowIds[index]
              const isSelected = selected.has(rowId)
              return (
                <TableRow
                  key={rowId}
                  aria-selected={selectable ? isSelected : undefined}
                  className={cn('hover:bg-muted/50', onRowClick && 'cursor-pointer',
                    isSelected && 'bg-primary/10 hover:bg-primary/15')}
                  onClick={
                    onRowClick
                      ? (event) => {
                          const target = event.target
                          if (target instanceof Element && target.closest('a, button, input, select, textarea')) return
                          onRowClick(row)
                        }
                      : undefined
                  }
                >
                  {selectable ? (
                    <TableCell className={cn(densityClass.cell, 'w-10')}>
                      <Checkbox
                        aria-label={`选择第 ${index + 1} 行`}
                        checked={isSelected}
                        onClick={(event) => {
                          event.stopPropagation()
                          shiftPressed.current = event.shiftKey
                        }}
                        onCheckedChange={(checked) =>
                          toggleRow(index, checked === true, shiftPressed.current)}
                      />
                    </TableCell>
                  ) : null}
                  {columns.map((column) => (
                    <TableCell key={column.id} className={cn(densityClass.cell, 'tabular-nums', column.className)}>
                      {column.cell(row)}
                    </TableCell>
                  ))}
                </TableRow>
              )
            })}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
