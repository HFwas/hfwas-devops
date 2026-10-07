import type { ReactNode } from 'react'
import { OverviewMetricCards, type MetricItem } from '@/components/console/ResourceOverview'
import { cn } from '@/lib/utils'

export interface DetailTab {
  value: string
  label: ReactNode
}

/**
 * 详情壳：贴在顶栏下方的标题（名称在上、说明在下）、h-8 操作和同一套 Tab。
 * `metrics` 渲染在 Tab 正下方。概览左右栏用 ResourceOverview。
 */
export function DetailShell({
  title,
  description,
  meta,
  leading,
  actions,
  tabs,
  value,
  onValueChange,
  metrics,
  children,
  className,
}: {
  title: ReactNode
  description?: ReactNode
  meta?: ReactNode
  leading?: ReactNode
  actions?: ReactNode
  tabs?: DetailTab[]
  value?: string
  onValueChange?: (value: string) => void
  /** 概览指标卡，贴在 Tab 下方、正文上方。 */
  metrics?: MetricItem[]
  children: ReactNode
  className?: string
}) {
  return (
    <div data-slot="detail-shell" className={cn('flex flex-col gap-4', className)}>
      <div className="sticky top-(--header-height) z-30 -mx-4 border-b bg-background/95 px-4 backdrop-blur lg:-mx-6 lg:px-6">
        <div className="flex flex-wrap items-start justify-between gap-3 py-3">
          <div className="flex min-w-0 items-start gap-2">
            {leading}
            <div className="min-w-0">
              <h1 className="truncate text-2xl font-bold tracking-tight">{title}</h1>
              {description ? <p className="mt-0.5 text-sm text-muted-foreground">{description}</p> : null}
              {meta ? <div className="mt-1 text-sm text-muted-foreground">{meta}</div> : null}
            </div>
          </div>
          {actions ? <div className="flex flex-wrap items-center gap-2">{actions}</div> : null}
        </div>
        {tabs && tabs.length > 0 ? (
          <div role="tablist" className="flex gap-1 overflow-x-auto">
            {tabs.map((tab) => {
              const selected = tab.value === value
              return (
                <button
                  key={tab.value}
                  type="button"
                  role="tab"
                  aria-selected={selected}
                  className={cn(
                    '-mb-px border-b-2 px-3 py-2 text-sm whitespace-nowrap',
                    selected
                      ? 'border-primary font-medium text-primary'
                      : 'border-transparent text-muted-foreground hover:text-foreground',
                  )}
                  onClick={() => onValueChange?.(tab.value)}
                >
                  {tab.label}
                </button>
              )
            })}
          </div>
        ) : null}
      </div>
      {metrics && metrics.length > 0 ? <OverviewMetricCards items={metrics} /> : null}
      <div>{children}</div>
    </div>
  )
}
