import { useEffect, useState, type ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select'
import { cn } from '@/lib/utils'

const SIBLING_COUNT = 1
const DEFAULT_PAGE_SIZES = [10, 20, 50, 100]

/** 生成要展示的页码序列，页数多时用 'gap' 表示省略号。 */
function pageItems(page: number, pageCount: number, siblingCount: number): Array<number | 'gap'> {
  // 首尾 + 当前 + 两侧相邻 + 两个省略号
  const windowSize = siblingCount * 2 + 5
  if (pageCount <= windowSize) {
    return Array.from({ length: pageCount }, (_, index) => index + 1)
  }
  const left = Math.max(page - siblingCount, 2)
  const right = Math.min(page + siblingCount, pageCount - 1)
  const items: Array<number | 'gap'> = [1]
  if (left > 2) items.push('gap')
  for (let current = left; current <= right; current += 1) items.push(current)
  if (right < pageCount - 1) items.push('gap')
  items.push(pageCount)
  return items
}

/**
 * 统一分页控件：左侧说明 + 右侧「上一页 / 页码 / 下一页」。
 * 页码过多时用省略号收拢；只有一页时只显示说明。
 * 受控组件：只负责展示与回调，页码状态由调用方（URL 或本地 state）持有。
 * 传入 total/pageSize 时按服务端总数计算页数，并显示页大小与跳页。
 */
export function Pagination({
  page,
  pageCount,
  onPageChange,
  summary,
  siblingCount = SIBLING_COUNT,
  className,
  total,
  pageSize,
  pageSizeOptions = DEFAULT_PAGE_SIZES,
  onPageSizeChange,
  showPageSize,
  showJump,
  disabled = false,
}: {
  page: number
  pageCount: number
  onPageChange: (page: number) => void
  /** 左侧说明；缺省显示「第 x / y 页」。 */
  summary?: ReactNode
  siblingCount?: number
  className?: string
  total?: number
  pageSize?: number
  pageSizeOptions?: number[]
  onPageSizeChange?: (size: number) => void
  showPageSize?: boolean
  showJump?: boolean
  disabled?: boolean
}) {
  const derivedPageCount =
    total !== undefined && pageSize && pageSize > 0 ? Math.ceil(total / pageSize) : pageCount
  const totalPages = Math.max(1, derivedPageCount)
  const safePage = Math.min(Math.max(1, page), totalPages)
  const items = pageItems(safePage, totalPages, Math.max(0, siblingCount))
  const sizeVisible = showPageSize ?? Boolean(onPageSizeChange)
  const jumpVisible = showJump ?? totalPages > 7
  const [jump, setJump] = useState(String(safePage))

  useEffect(() => {
    setJump(String(safePage))
  }, [safePage])

  function commitJump() {
    const parsed = Number(jump)
    if (!Number.isInteger(parsed) || parsed < 1) {
      setJump(String(safePage))
      return
    }
    const target = Math.min(parsed, totalPages)
    setJump(String(target))
    if (target !== safePage) onPageChange(target)
  }

  const defaultSummary =
    total !== undefined ? `共 ${total} 条 · 第 ${safePage} / ${totalPages} 页` : `第 ${safePage} / ${totalPages} 页`

  return (
    <nav
      data-slot="pagination"
      aria-label="分页"
      className={cn('flex flex-wrap items-center justify-between gap-3 text-sm text-muted-foreground', className)}
    >
      <div className="flex items-center gap-3 tabular-nums">
        <span>{summary ?? defaultSummary}</span>
        {sizeVisible ? (
          <Select
            value={String(pageSize ?? pageSizeOptions[0])}
            onValueChange={(value) => onPageSizeChange?.(Number(value))}
            disabled={disabled}
          >
            <SelectTrigger size="sm" aria-label="每页条数" className="h-8">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {pageSizeOptions.map((size) => (
                <SelectItem key={size} value={String(size)}>
                  每页 {size} 条
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : null}
      </div>
      {totalPages > 1 ? (
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="上一页"
            disabled={disabled || safePage <= 1}
            onClick={() => onPageChange(safePage - 1)}
          >
            <ChevronLeft />
          </Button>
          {items.map((item, index) =>
            item === 'gap' ? (
              <span key={`gap-${index}`} className="px-1 text-muted-foreground" aria-hidden>
                …
              </span>
            ) : (
              <button
                key={item}
                type="button"
                aria-label={`第 ${item} 页`}
                aria-current={item === safePage ? 'page' : undefined}
                disabled={disabled}
                className={cn(
                  'flex size-8 items-center justify-center rounded-md tabular-nums disabled:cursor-not-allowed disabled:opacity-50',
                  item === safePage ? 'bg-primary text-primary-foreground' : 'hover:bg-muted',
                )}
                onClick={() => onPageChange(item)}
              >
                {item}
              </button>
            ),
          )}
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="下一页"
            disabled={disabled || safePage >= totalPages}
            onClick={() => onPageChange(safePage + 1)}
          >
            <ChevronRight />
          </Button>
          {jumpVisible ? (
            <div className="ml-2 flex items-center gap-1">
              <span>跳至</span>
              <Input
                aria-label="跳至页码"
                value={jump}
                disabled={disabled}
                onChange={(event) => setJump(event.target.value)}
                onBlur={commitJump}
                onKeyDown={(event) => {
                  if (event.key === 'Enter') commitJump()
                }}
                className="h-8 w-14 text-center tabular-nums"
              />
              <span>页</span>
            </div>
          ) : null}
        </div>
      ) : null}
    </nav>
  )
}
