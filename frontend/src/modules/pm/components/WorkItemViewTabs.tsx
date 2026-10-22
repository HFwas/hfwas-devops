import type { PmSavedView } from '@/modules/pm/types'
import { BUILT_IN_VIEWS } from '@/modules/pm/utils/viewConfig'
import { cn } from '@/lib/utils'

/**
 * 列表视图页签：四个内置视图 + 已保存视图；配置变更时当前页签显示脏点。
 */
export function WorkItemViewTabs({
  activeView,
  savedViews,
  dirty,
  onSelect,
  className,
}: {
  activeView: string
  savedViews: PmSavedView[]
  dirty?: boolean
  onSelect: (view: string) => void
  className?: string
}) {
  const views = [
    ...BUILT_IN_VIEWS.map((view) => ({ id: view.id, label: view.label })),
    ...savedViews
      .filter((view) => view.id != null && !BUILT_IN_VIEWS.some((builtIn) => builtIn.id === String(view.id)))
      .map((view) => ({ id: String(view.id), label: view.name })),
  ]
  return (
    <div
      data-slot="work-item-view-tabs"
      role="tablist"
      aria-label="工作项视图"
      className={cn('flex flex-wrap items-center gap-1 border-b', className)}
    >
      {views.map((view) => {
        const active = activeView === view.id
        return (
          <button
            key={view.id}
            type="button"
            role="tab"
            aria-selected={active}
            className={cn(
              'relative -mb-px border-b-2 px-3 py-2 text-sm transition-colors',
              active
                ? 'border-primary font-medium text-foreground'
                : 'border-transparent text-muted-foreground hover:text-foreground',
            )}
            onClick={() => onSelect(view.id)}
          >
            {view.label}
            {active && dirty ? (
              <span
                className="ml-1 inline-block size-1.5 rounded-full bg-primary align-middle"
                title="视图已修改"
                aria-label="视图已修改"
              />
            ) : null}
          </button>
        )
      })}
    </div>
  )
}
