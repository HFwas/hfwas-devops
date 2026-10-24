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
 * `showHeader=false` 时页头让位给视图页签行：标题只保留为无障碍标题，
 * 主操作通过 `rowActions` 落到页签行右侧；此时未单独给 `rowActions` 的 `actions` 也并入该行，避免按钮丢失。
 */
export function ListPageShell({
  title,
  description,
  actions,
  rowActions,
  showHeader = true,
  tabs,
  toolbar,
  batchBar,
  filters,
  summary,
  footer,
  loading = false,
  error,
  errorRequestId,
  onRetry,
  forbidden,
  empty = false,
  emptyTitle,
  emptyContent,
  children,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  actions?: ReactNode
  rowActions?: ReactNode
  showHeader?: boolean
  tabs?: ReactNode
  toolbar?: ReactNode
  /** 内联批量操作条：位于工具栏之下、筛选条之上。 */
  batchBar?: ReactNode
  filters?: ReactNode
  summary?: ReactNode
  footer?: ReactNode
  loading?: boolean
  error?: ReactNode
  errorRequestId?: string
  onRetry?: () => void
  forbidden?: ReactNode
  empty?: boolean
  emptyTitle?: string
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
    body = <EmptyState title={emptyTitle} description={emptyContent} variant={emptyTitle ? "no-result" : "empty"} />
  } else {
    body = children
  }

  return (
    <div data-slot="list-page-shell" className={cn('flex w-full flex-col gap-4 md:gap-6', className)}>
      {showHeader ? (
        <PageHeader title={title} description={description} actions={actions} />
      ) : (
        <h1 className="sr-only">{title}</h1>
      )}
      {tabs || rowActions || (!showHeader && actions) ? (
        <div data-slot="list-page-tab-row" className="flex items-end justify-between gap-3 border-b">
          <div className="min-w-0 flex-1">{tabs}</div>
          {rowActions || (!showHeader && actions) ? (
            <div className="flex shrink-0 flex-wrap items-center gap-2 pb-1.5">{rowActions ?? actions}</div>
          ) : null}
        </div>
      ) : null}
      {toolbar ? <div className="flex flex-wrap items-center gap-2">{toolbar}</div> : null}
      {batchBar}
      {filters}
      {summary ? <div className="flex items-center justify-between text-sm text-muted-foreground">{summary}</div> : null}
      {body}
      {footer}
    </div>
  )
}
