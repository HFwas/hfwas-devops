import { Plus } from 'lucide-react'
import type { PmSavedView } from '@/modules/pm/types'
import { BUILT_IN_VIEWS } from '@/modules/pm/utils/viewConfig'
import { cn } from '@/lib/utils'

/** 已保存视图与内置视图同名时只保留内置项，避免出现两个「研发待办」。 */
export function mergedViewTabs(savedViews: PmSavedView[]) {
  const builtInIds = new Set(BUILT_IN_VIEWS.map((view) => view.id))
  const builtInLabels = new Set(BUILT_IN_VIEWS.map((view) => view.label.trim()))
  const seen = new Set<string>(builtInIds)
  const views: Array<{ id: string; label: string }> = BUILT_IN_VIEWS.map((view) => ({ id: view.id, label: view.label }))
  for (const view of savedViews) {
    if (view.id == null) continue
    const id = String(view.id)
    const label = (view.name ?? '').trim()
    if (seen.has(id) || !label || builtInLabels.has(label)) continue
    seen.add(id)
    views.push({ id, label })
  }
  return views
}

/**
 * 列表视图页签：四个内置视图 + 已保存视图 + 保存视图入口；配置变更时当前页签显示脏点。
 */
export function WorkItemViewTabs({
  activeView,
  savedViews,
  dirty,
  onSelect,
  onSaveView,
  className,
}: {
  activeView: string
  savedViews: PmSavedView[]
  dirty?: boolean
  onSelect: (view: string) => void
  onSaveView?: () => void
  className?: string
}) {
  const views = mergedViewTabs(savedViews)
  return (
    <div data-slot="work-item-view-tabs" className={cn('flex flex-wrap items-center gap-1', className)}>
      <div role="tablist" aria-label="工作项视图" className="flex flex-wrap items-center gap-1">
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
      {onSaveView ? (
        <button
          type="button"
          className="flex items-center gap-1 px-3 py-2 text-sm text-muted-foreground transition-colors hover:text-foreground"
          onClick={onSaveView}
        >
          <Plus className="size-3.5" />
          保存视图
        </button>
      ) : null}
    </div>
  )
}
