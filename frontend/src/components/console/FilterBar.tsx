import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface ActiveFilter {
  key: string
  label: ReactNode
  onRemove?: () => void
}

/**
 * 已应用筛选条件条：每个条件一个 chip，可单条移除或全部重置。
 * `showLabel=false` 用于「只展示已生效条件」的紧凑场景。
 */
export function FilterBar({
  activeFilters = [],
  onReset,
  resetLabel = '清除全部',
  showLabel = true,
  extra,
  children,
  className,
}: {
  activeFilters?: ActiveFilter[]
  onReset?: () => void
  resetLabel?: string
  showLabel?: boolean
  extra?: ReactNode
  children?: ReactNode
  className?: string
}) {
  const canReset = Boolean(onReset) || activeFilters.some((filter) => filter.onRemove)
  return (
    <div
      data-slot="filter-bar"
      className={cn('flex flex-wrap items-center gap-2 rounded-lg border bg-muted/30 px-3 py-2 text-sm', className)}
    >
      {children}
      {showLabel ? <span className="text-muted-foreground">筛选</span> : null}
      {activeFilters.length === 0 ? (
        showLabel ? <span className="text-muted-foreground">未设置条件</span> : null
      ) : (
        activeFilters.map((filter) => (
          <span
            key={filter.key}
            className="inline-flex items-center gap-1 rounded-md border bg-background px-2 py-0.5"
          >
            <span>{filter.label}</span>
            {filter.onRemove ? (
              <button
                type="button"
                aria-label={`移除条件 ${filter.key}`}
                className="text-muted-foreground hover:text-foreground"
                onClick={filter.onRemove}
              >
                <X className="size-3.5" />
              </button>
            ) : null}
          </span>
        ))
      )}
      {canReset ? (
        <Button type="button" variant="ghost" size="sm" className="h-7 px-2" onClick={onReset}>
          {resetLabel}
        </Button>
      ) : null}
      {extra ? <div className="ml-auto flex items-center gap-2">{extra}</div> : null}
    </div>
  )
}
