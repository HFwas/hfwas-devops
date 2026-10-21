import type { ReactNode } from 'react'
import { AlertTriangle, RotateCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { cn } from '@/lib/utils'

/**
 * 加载失败占位；requestId 便于按日志检索完整链路。
 */
export function ErrorState({
  title = '加载失败',
  message,
  requestId,
  onRetry,
  retryLabel = '重试',
  className,
}: {
  title?: string
  message?: ReactNode
  requestId?: string
  onRetry?: () => void
  retryLabel?: string
  className?: string
}) {
  return (
    <div
      role="alert"
      data-slot="error-state"
      className={cn('flex flex-col items-center justify-center gap-3 rounded-lg border border-destructive/30 bg-destructive/5 px-6 py-12 text-center', className)}
    >
      <AlertTriangle className="size-6 text-destructive" />
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium">{title}</p>
        {message ? <p className="text-sm text-muted-foreground">{message}</p> : null}
        {requestId ? <p className="font-mono text-xs text-muted-foreground">requestId: {requestId}</p> : null}
      </div>
      {onRetry ? (
        <Button type="button" variant="outline" size="sm" onClick={onRetry}>
          <RotateCw />
          {retryLabel}
        </Button>
      ) : null}
    </div>
  )
}
