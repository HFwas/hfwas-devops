import type { ReactNode } from 'react'
import { PageHeader } from '@/components/console/PageHeader'
import { EmptyState } from '@/components/console/EmptyState'
import { ErrorState } from '@/components/console/ErrorState'
import { NoPermissionState } from '@/components/console/NoPermissionState'
import { Skeleton } from '@/components/ui/skeleton'
import { cn } from '@/lib/utils'

/**
 * 列表页外壳：统一页头、工具栏、筛选条、内容与状态区。
 * 状态优先级：无权限 > 失败 > 加载 > 空态 > 内容。
 */
export function ListPageShell({
  title,
  description,
  actions,
  toolbar,
  filters,
  summary,
  footer,
  loading = false,
  error,
  errorRequestId,
  onRetry,
  forbidden,
  empty = false,
  emptyContent,
  children,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  toolbar?: ReactNode
  filters?: ReactNode
  summary?: ReactNode
  footer?: ReactNode
  loading?: boolean
  error?: ReactNode
  errorRequestId?: string
  onRetry?: () => void
  forbidden?: ReactNode
  empty?: boolean
  emptyContent?: ReactNode
  children?: ReactNode
  className?: string
}) {
  let body: ReactNode
  if (forbidden) {
    body = <NoPermissionState title="无权访问" description={forbidden} />
  } else if (error) {
    body = <ErrorState message={error} requestId={errorRequestId} onRetry={onRetry} />
  } else if (loading && !children) {
    body = (
      <div className="flex flex-col gap-2" aria-busy>
        {Array.from({ length: 6 }, (_, index) => (
          <Skeleton key={index} className="h-10 w-full" />
        ))}
      </div>
    )
  } else if (empty) {
    body = <EmptyState description={emptyContent} />
  } else {
    body = children
  }

  return (
    <div data-slot="list-page-shell" className={cn('flex w-full flex-col gap-4 md:gap-6', className)}>
      <PageHeader title={title} description={description} actions={actions} />
      {toolbar ? <div className="flex flex-wrap items-center gap-2">{toolbar}</div> : null}
      {filters}
      {summary ? <div className="flex items-center justify-between text-sm text-muted-foreground">{summary}</div> : null}
      {body}
      {footer}
    </div>
  )
}
