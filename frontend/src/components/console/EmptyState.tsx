import type { ReactNode } from 'react'
import { Inbox, SearchX } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * 空数据 / 无结果占位。无结果变体使用搜索图标与更中性的文案。
 */
export function EmptyState({
  title,
  description,
  icon,
  action,
  variant = 'empty',
  className,
}: {
  title?: string
  description?: ReactNode
  icon?: ReactNode
  action?: ReactNode
  variant?: 'empty' | 'no-result'
  className?: string
}) {
  const Icon = variant === 'no-result' ? SearchX : Inbox
  return (
    <div
      data-slot="empty-state"
      className={cn('flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center', className)}
    >
      <div className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
        {icon ?? <Icon className="size-5" />}
      </div>
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium">{title ?? (variant === 'no-result' ? '没有匹配结果' : '暂无数据')}</p>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {action}
    </div>
  )
}
