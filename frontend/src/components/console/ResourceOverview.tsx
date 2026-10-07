import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export interface MetricItem {
  label: string
  value: ReactNode
  hint?: ReactNode
  /** 大号数字。状态句子用 false。 */
  emphasis?: boolean
}

/** 概览顶部一排状态/指标卡。 */
export function MetricStrip({ items }: { items: MetricItem[] }) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg border bg-card px-4 py-3 shadow-sm">
          <div className="text-sm text-muted-foreground">{item.label}</div>
          <div className={cn('mt-2', item.emphasis ? 'text-2xl font-semibold tracking-tight' : 'text-base font-medium')}>
            {item.value}
          </div>
          {item.hint ? <div className="mt-1 text-xs text-muted-foreground">{item.hint}</div> : null}
        </div>
      ))}
    </div>
  )
}

export function OverviewCard({ title, children }: { title: ReactNode; children: ReactNode }) {
  return (
    <section className="rounded-lg border bg-card shadow-sm">
      <header className="border-b px-4 py-3 text-sm font-medium">{title}</header>
      <div className="px-4 py-3">{children}</div>
    </section>
  )
}

export function InfoGrid({ rows }: { rows: { label: string; value: ReactNode }[] }) {
  return (
    <dl className="grid grid-cols-[7.5rem_minmax(0,1fr)] gap-x-3 gap-y-3 text-sm">
      {rows.map((row) => (
        <div key={row.label} className="contents">
          <dt className="pt-0.5 text-muted-foreground">{row.label}</dt>
          <dd className="min-w-0">{row.value == null || row.value === '' ? '—' : row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

export function PillList({ items, empty = '—' }: { items: string[]; empty?: ReactNode }) {
  if (items.length === 0) return <span className="text-sm text-muted-foreground">{empty}</span>
  return (
    <div className="flex flex-wrap gap-1.5">
      {items.map((item, index) => (
        <span key={`${item}-${index}`} className="rounded-md border bg-muted px-2 py-0.5 font-mono text-xs text-foreground">
          {item}
        </span>
      ))}
    </div>
  )
}

/**
 * 资源详情概览：指标卡 + 左主栏（表、信息）+ 右侧栏（事件、关联、标签）。
 */
export function ResourceOverview({
  metrics,
  main,
  side,
}: {
  metrics: MetricItem[]
  main: ReactNode
  side: ReactNode
}) {
  return (
    <div data-slot="resource-overview" className="flex flex-col gap-4">
      <MetricStrip items={metrics} />
      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(18rem,0.85fr)]">
        <div className="flex min-w-0 flex-col gap-4">{main}</div>
        <div className="flex min-w-0 flex-col gap-4">{side}</div>
      </div>
    </div>
  )
}
