import type { ReactNode } from 'react'
import { X } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

export interface BulkAction {
  key: string
  label: string
  icon?: ReactNode
  variant?: 'default' | 'outline' | 'destructive' | 'ghost' | 'secondary'
  disabled?: boolean
  title?: string
  onSelect: () => void
}

/**
 * 列表底部批量操作栏；未选中任何行时不渲染。
 * 只表达当前页选择，跨页选择由调用方决定是否支持。
 */
export function BulkActionBar({
  selectedCount,
  actions,
  onClear,
  busy = false,
  className,
}: {
  selectedCount: number
  actions: BulkAction[]
  onClear: () => void
  busy?: boolean
  className?: string
}) {
  if (selectedCount <= 0) return null
  return (
    <div
      role="toolbar"
      aria-label="批量操作"
      data-slot="bulk-action-bar"
      className={cn(
        'fixed inset-x-0 bottom-4 z-40 mx-auto flex w-fit max-w-[calc(100vw-2rem)] flex-wrap items-center gap-2 rounded-lg border bg-background px-4 py-2 shadow-lg',
        className,
      )}
    >
      <span className="text-sm tabular-nums">
        已选 <span className="font-medium text-foreground">{selectedCount}</span> 项
      </span>
      <div className="flex flex-wrap items-center gap-2">
        {actions.map((action) => (
          <Button
            key={action.key}
            type="button"
            size="sm"
            variant={action.variant ?? 'outline'}
            disabled={busy || action.disabled}
            title={action.title}
            onClick={action.onSelect}
          >
            {action.icon}
            {action.label}
          </Button>
        ))}
      </div>
      <Button type="button" size="sm" variant="ghost" aria-label="取消选择" onClick={onClear}>
        <X className="size-4" />
        取消选择
      </Button>
    </div>
  )
}
