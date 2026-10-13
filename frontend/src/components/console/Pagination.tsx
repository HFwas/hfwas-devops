import type { ReactNode } from 'react'
import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

const SIBLING_COUNT = 1

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
 */
export function Pagination({
  page,
  pageCount,
  onPageChange,
  summary,
  siblingCount = SIBLING_COUNT,
  className,
}: {
  page: number
  pageCount: number
  onPageChange: (page: number) => void
  /** 左侧说明；缺省显示「第 x / y 页」。 */
  summary?: ReactNode
  siblingCount?: number
  className?: string
}) {
  const total = Math.max(1, pageCount)
  const safePage = Math.min(Math.max(1, page), total)
  const items = pageItems(safePage, total, Math.max(0, siblingCount))
  return (
    <nav
      data-slot="pagination"
      aria-label="分页"
      className={cn('flex items-center justify-between gap-3 text-sm text-muted-foreground', className)}
    >
      <div className="tabular-nums">{summary ?? `第 ${safePage} / ${total} 页`}</div>
      {total > 1 ? (
        <div className="flex items-center gap-1">
          <Button
            type="button"
            variant="outline"
            size="icon"
            aria-label="上一页"
            disabled={safePage <= 1}
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
                className={cn(
                  'flex size-8 items-center justify-center rounded-md tabular-nums',
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
            disabled={safePage >= total}
            onClick={() => onPageChange(safePage + 1)}
          >
            <ChevronRight />
          </Button>
        </div>
      ) : null}
    </nav>
  )
}