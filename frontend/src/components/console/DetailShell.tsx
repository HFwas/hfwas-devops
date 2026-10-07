import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export interface DetailTab {
  value: string
  label: ReactNode
}

/**
 * 详情壳：贴在顶栏下方的标题行（名称、元信息、h-8 操作）和同一套 Tab。
 * 各产品只换 Tab 内容。
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
  children: ReactNode
  className?: string
}) {
  return (
    <div data-slot="detail-shell" className={cn('flex flex-col gap-4', className)}>
      <div className="sticky top-(--header-height) z-30 -mx-4 border-b bg-background/95 px-4 backdrop-blur lg:-mx-6 lg:px-6">
        <div className="flex flex-wrap items-center justify-between gap-3 py-2">
          <div className="flex min-w-0 items-center gap-2">
            {leading}
            <div className="min-w-0">
              <div className="flex flex-wrap items-baseline gap-x-2 gap-y-1">
                <h1 className="truncate text-lg font-semibold">{title}</h1>
                {meta ? <div className="text-sm text-muted-foreground">{meta}</div> : null}
              </div>
              {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
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
      <div>{children}</div>
    </div>
  )
}
