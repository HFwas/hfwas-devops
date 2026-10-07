import type { ReactNode } from 'react'
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from '@/components/ui/table'
import { cn } from '@/lib/utils'

export interface DataTableColumn<T> {
  id: string
  header: ReactNode
  cell: (row: T) => ReactNode
  className?: string
  headClassName?: string
}

/**
 * 紧凑资源表。表头 h-10 / bg-muted / px-2，单元格 p-2，外框 rounded-lg border。
 * 已有行时加载只降低透明度，避免整表闪白。
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
}: {
  columns: DataTableColumn<T>[]
  data: T[]
  getRowId: (row: T) => string
  toolbar?: ReactNode
  loading?: boolean
  error?: ReactNode
  empty?: ReactNode
  className?: string
}) {
  const showSkeleton = loading && data.length === 0
  const showError = !showSkeleton && Boolean(error) && data.length === 0
  const showEmpty = !showSkeleton && !showError && data.length === 0

  return (
    <div data-slot="data-table" className={cn('flex flex-col gap-3', className)}>
      {toolbar ? <div className="flex flex-wrap items-center gap-2">{toolbar}</div> : null}
      {error && data.length > 0 ? <p className="text-sm text-destructive">{error}</p> : null}
      <div className={cn(loading && data.length > 0 && 'opacity-75')}>
        <Table aria-busy={loading || undefined}>
          <TableHeader>
            <TableRow className="hover:bg-transparent">
              {columns.map((column) => (
                <TableHead key={column.id} className={cn('h-10 bg-muted px-2', column.headClassName)}>
                  {column.header}
                </TableHead>
              ))}
            </TableRow>
          </TableHeader>
          <TableBody>
            {showSkeleton &&
              Array.from({ length: 4 }, (_, index) => (
                <TableRow key={`loading-${index}`} className="hover:bg-transparent">
                  <TableCell colSpan={columns.length} className="p-2">
                    <div className="h-4 animate-pulse rounded bg-muted" />
                  </TableCell>
                </TableRow>
              ))}
            {showError && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={columns.length} className="h-40 p-2 text-center text-sm text-destructive">
                  {error}
                </TableCell>
              </TableRow>
            )}
            {showEmpty && (
              <TableRow className="hover:bg-transparent">
                <TableCell colSpan={columns.length} className="h-40 p-2 text-center text-sm text-muted-foreground">
                  {empty}
                </TableCell>
              </TableRow>
            )}
            {data.map((row) => (
              <TableRow key={getRowId(row)} className="hover:bg-muted/50">
                {columns.map((column) => (
                  <TableCell key={column.id} className={cn('p-2', column.className)}>
                    {column.cell(row)}
                  </TableCell>
                ))}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  )
}
