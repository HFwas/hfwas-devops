import type { ReactNode } from 'react'
import { Lock } from 'lucide-react'
import { cn } from '@/lib/utils'

/**
 * 无权限 / 只读占位。按钮由调用方按权限决定是否渲染。
 */
export function NoPermissionState({
  title = '无权访问',
  description,
  action,
  className,
}: {
  title?: string
  description?: ReactNode
  action?: ReactNode
  className?: string
}) {
  return (
    <div
      role="status"
      data-slot="no-permission-state"
      className={cn('flex flex-col items-center justify-center gap-3 rounded-lg border border-dashed px-6 py-12 text-center', className)}
    >
      <div className="flex size-11 items-center justify-center rounded-full bg-muted text-muted-foreground">
        <Lock className="size-5" />
      </div>
      <div className="flex flex-col gap-1">
        <p className="text-sm font-medium">{title}</p>
        {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
      </div>
      {action}
    </div>
  )
}
