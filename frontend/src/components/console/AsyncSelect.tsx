import { useEffect, useId, useMemo, useState, type UIEvent } from 'react'
import { useInfiniteQuery } from '@tanstack/react-query'
import { ChevronDown, Loader2, Search } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover'
import { pageCountOf, DEFAULT_PAGE_SIZE } from '@/shared/types/pagination'
import { cn } from '@/lib/utils'

export interface AsyncSelectOption {
  value: string
  label: string
  /** 次要说明，显示在标签下一行的灰色小字。 */
  hint?: string
}

export interface AsyncSelectPage {
  records: AsyncSelectOption[]
  total?: number | string
  pages?: number | string
}

/**
 * 可检索、下拉到底自动加载下一页的实体选择器。
 * 受控组件：`value` / `onChange`；数据由 `fetchPage` 提供（服务端分页）。
 * 与 shadcn `Select` 视觉一致，但支持服务端检索与无限滚动，适合数据量大的实体下拉。
 */
export function AsyncSelect({
  value,
  onChange,
  fetchPage,
  pageSize = DEFAULT_PAGE_SIZE,
  placeholder = '请选择',
  searchPlaceholder = '搜索',
  emptyText = '没有匹配项',
  label,
  disabled,
  clearLabel,
  selectedLabel,
  className,
}: {
  value: string
  onChange: (value: string) => void
  fetchPage: (params: { page: number; pageSize: number; keyword: string }) => Promise<AsyncSelectPage>
  pageSize?: number
  placeholder?: string
  searchPlaceholder?: string
  emptyText?: string
  /** 无障碍标签（触发按钮的 aria-label）。 */
  label?: string
  disabled?: boolean
  /** 提供后在最上方显示一个用于清空的选项（如「全部项目」）。 */
  clearLabel?: string
  /** 选中值不在已加载页里时的回显兜底（如「当前凭证」）。 */
  selectedLabel?: string
  className?: string
}) {
  const id = useId()
  const [open, setOpen] = useState(false)
  const [keyword, setKeyword] = useState('')
  const [debounced, setDebounced] = useState('')

  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(keyword.trim()), 250)
    return () => window.clearTimeout(timer)
  }, [keyword])

  useEffect(() => {
    if (!open) {
      setKeyword('')
      setDebounced('')
    }
  }, [open])

  const query = useInfiniteQuery({
    queryKey: ['async-select', id, debounced],
    queryFn: ({ pageParam }) => fetchPage({ page: pageParam, pageSize, keyword: debounced }),
    initialPageParam: 1,
    getNextPageParam: (lastPage, allPages) => {
      const pageCount = pageCountOf(lastPage, pageSize)
      if (allPages.length < pageCount) return allPages.length + 1
      // 后端未返回 pages/total 时，用「本页是否满页」兜底判断。
      if (Number.isNaN(Number(lastPage.total)) && lastPage.records.length >= pageSize) {
        return allPages.length + 1
      }
      return undefined
    },
    enabled: open,
  })

  const options = useMemo(() => (query.data?.pages ?? []).flatMap((page) => page.records), [query.data])
  const selected = options.find((option) => option.value === value)
  const display = value ? (selected?.label ?? selectedLabel ?? value) : (clearLabel ?? placeholder)

  function onScroll(event: UIEvent<HTMLDivElement>) {
    if (!query.hasNextPage || query.isFetchingNextPage) return
    const el = event.currentTarget
    if (el.scrollTop + el.clientHeight >= el.scrollHeight - 24) void query.fetchNextPage()
  }

  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-label={label}
          aria-expanded={open}
          disabled={disabled}
          className={cn('h-8 w-full justify-between font-normal', !value && 'text-muted-foreground', className)}
        >
          <span className="min-w-0 flex-1 truncate text-left">{display}</span>
          <ChevronDown className="size-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="start" className="w-(--radix-popover-trigger-width) p-0">
        <div className="flex items-center gap-2 border-b px-2">
          <Search className="size-3.5 shrink-0 text-muted-foreground" aria-hidden />
          <Input
            value={keyword}
            onChange={(event) => setKeyword(event.target.value)}
            placeholder={searchPlaceholder}
            aria-label={searchPlaceholder}
            className="h-8 border-0 px-0 shadow-none focus-visible:ring-0"
          />
        </div>
        <div data-slot="async-select-list" className="max-h-64 overflow-auto p-1" onScroll={onScroll}>
          {clearLabel ? (
            <button
              type="button"
              className={cn(
                'flex w-full items-center rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent',
                !value && 'bg-accent',
              )}
              onClick={() => {
                onChange('')
                setOpen(false)
              }}
            >
              {clearLabel}
            </button>
          ) : null}
          {query.isLoading ? (
            <p className="px-2 py-3 text-sm text-muted-foreground">加载中…</p>
          ) : options.length === 0 ? (
            <p className="px-2 py-3 text-sm text-muted-foreground">{emptyText}</p>
          ) : (
            options.map((option) => (
              <button
                key={option.value}
                type="button"
                className={cn(
                  'flex w-full flex-col rounded-sm px-2 py-1.5 text-left text-sm hover:bg-accent',
                  option.value === value && 'bg-accent',
                )}
                onClick={() => {
                  onChange(option.value)
                  setOpen(false)
                }}
              >
                <span className="truncate">{option.label}</span>
                {option.hint ? <span className="truncate text-xs text-muted-foreground">{option.hint}</span> : null}
              </button>
            ))
          )}
          {query.isFetchingNextPage ? (
            <p className="flex items-center justify-center gap-1.5 px-2 py-2 text-xs text-muted-foreground">
              <Loader2 className="size-3.5 animate-spin" aria-hidden />
              加载更多…
            </p>
          ) : null}
        </div>
      </PopoverContent>
    </Popover>
  )
}