import type { ReactNode } from 'react'
import { cn } from '@/lib/utils'

export interface MetricItem {
  label: string
  value: ReactNode
  hint?: ReactNode
  /** 大号数字。状态句子用 false。 */
  emphasis?: boolean
}

/** 概览 Tab 下方的一排状态/指标卡：状态、期望、就绪、最新、可用、创建。 */
export function OverviewMetricCards({ items }: { items: MetricItem[] }) {
  return (
    <div data-slot="overview-metric-cards" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      {items.map((item) => (
        <div key={item.label} className="rounded-lg border bg-card px-4 py-3 shadow-sm">
          <div className="text-sm text-muted-foreground">{item.label}</div>
          <div className={cn('mt-2', item.emphasis ? 'text-2xl font-bold tracking-tight' : 'text-base font-semibold')}>
            {item.value}
          </div>
          {item.hint ? <div className="mt-1 text-xs text-muted-foreground">{item.hint}</div> : null}
        </div>
      ))}
    </div>
  )
}

/** 工作负载概览共用的六张卡，Deployment / StatefulSet 填同一组标签。 */
export function replicaMetricCards(input: {
  status: ReactNode
  desired: ReactNode
  ready: ReactNode
  updated: ReactNode
  available: ReactNode
  created: ReactNode
  createdHint?: ReactNode
}): MetricItem[] {
  return [
    { label: '状态', value: input.status },
    { label: '期望', value: input.desired, hint: '个 Pod', emphasis: true },
    { label: '就绪', value: input.ready, hint: '个 Pod', emphasis: true },
    { label: '最新', value: input.updated, hint: '个 Pod', emphasis: true },
    { label: '可用', value: input.available, hint: '个 Pod', emphasis: true },
    { label: '创建', value: input.created, hint: input.createdHint, emphasis: true },
  ]
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

/** 信息卡里的两列字段。`span` 的行（镜像列表）占满整行。 */
export function InfoPairs({ rows }: { rows: { label: string; value: ReactNode; span?: boolean }[] }) {
  return (
    <dl className="grid gap-x-8 gap-y-3 text-sm md:grid-cols-2">
      {rows.map((row) => (
        <div
          key={row.label}
          className={cn('grid grid-cols-[7.5rem_minmax(0,1fr)] items-start gap-x-3', row.span && 'md:col-span-2')}
        >
          <dt className="pt-0.5 text-muted-foreground">{row.label}</dt>
          <dd className="min-w-0">{row.value == null || row.value === '' ? '—' : row.value}</dd>
        </div>
      ))}
    </dl>
  )
}

export function ImageList({ items }: { items?: { name: string; image: string; pullPolicy?: string | null }[] }) {
  if (!items?.length) return <span className="text-sm text-muted-foreground">—</span>
  return (
    <ul className="flex flex-col gap-2">
      {items.map((item) => (
        <li key={item.name} className="flex flex-wrap items-center gap-x-3 gap-y-1">
          <span className="rounded-md bg-muted px-2 py-0.5 font-mono text-xs">{item.name}</span>
          <span className="min-w-0 text-muted-foreground">{item.image}</span>
          {item.pullPolicy ? <span className="ml-auto text-muted-foreground">{item.pullPolicy}</span> : null}
        </li>
      ))}
    </ul>
  )
}

export function PillList({
  items,
  empty = '—',
  stacked = false,
}: {
  items: string[]
  empty?: ReactNode
  /** 标签和注解按行铺开。选择器继续用默认的横排小药丸。 */
  stacked?: boolean
}) {
  if (items.length === 0) return <span className="text-sm text-muted-foreground">{empty}</span>
  if (stacked) {
    return (
      <ul className="flex flex-col gap-1.5">
        {items.map((item, index) => (
          <li key={`${item}-${index}`} className="rounded-md border bg-muted/50 px-2 py-1 font-mono text-xs break-all">
            {item}
          </li>
        ))}
      </ul>
    )
  }
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
  /** 也可以改挂到 DetailShell 的 metrics，避免和 Tab 下的卡重复。 */
  metrics?: MetricItem[]
  main: ReactNode
  side: ReactNode
}) {
  return (
    <div data-slot="resource-overview" className="flex flex-col gap-4">
      {metrics && metrics.length > 0 ? <OverviewMetricCards items={metrics} /> : null}
      <div className="grid items-start gap-4 xl:grid-cols-[minmax(0,1.5fr)_minmax(18rem,0.85fr)]">
        <div className="flex min-w-0 flex-col gap-4">{main}</div>
        <div className="flex min-w-0 flex-col gap-4">{side}</div>
      </div>
    </div>
  )
}
