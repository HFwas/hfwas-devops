import type { BatchOperationResult } from '@/modules/pm/types'
import { cn } from '@/lib/utils'

const OUTCOME_LABEL = {
  SUCCESS: '成功',
  SKIPPED: '跳过',
  FAILED: '失败',
} as const

/**
 * 批量操作逐条结果；跳过的原因与失败码原样展示，便于用户重试。
 */
export function BatchResultList({
  result,
  titles,
  className,
}: {
  result: BatchOperationResult
  titles?: Map<string, string>
  className?: string
}) {
  return (
    <div data-slot="batch-result" className={cn('flex flex-col gap-2 rounded-md border p-3 text-sm', className)}>
      <div className="flex flex-wrap gap-3 tabular-nums">
        <span>共 {result.total} 项</span>
        <span className="text-emerald-600">成功 {result.success}</span>
        <span className="text-amber-600">跳过 {result.skipped}</span>
        <span className="text-destructive">失败 {result.failed}</span>
        {result.dryRun ? <span className="text-muted-foreground">（预演，未写入）</span> : null}
      </div>
      {result.items.length ? (
        <ul className="flex max-h-56 flex-col gap-1 overflow-y-auto text-xs">
          {result.items.map((item) => (
            <li key={item.id} className="flex flex-wrap items-center gap-2 rounded bg-muted/40 px-2 py-1">
              <span className="font-mono">{titles?.get(item.id) ?? `#${item.id}`}</span>
              <span className={cn(
                'rounded px-1.5 py-0.5',
                item.outcome === 'SUCCESS' && 'bg-emerald-100 text-emerald-700',
                item.outcome === 'SKIPPED' && 'bg-amber-100 text-amber-700',
                item.outcome === 'FAILED' && 'bg-destructive/10 text-destructive',
              )}>
                {OUTCOME_LABEL[item.outcome]}
              </span>
              {item.fromStatus || item.toStatus ? (
                <span className="text-muted-foreground">{item.fromStatus ?? '—'} → {item.toStatus ?? '—'}</span>
              ) : null}
              {item.changes?.map((change) => (
                <span key={change.fieldKey} className="text-muted-foreground">
                  {change.fieldKey}: {String(change.oldValue ?? '—')} → {String(change.newValue ?? '—')}
                </span>
              ))}
              {item.message ? <span className="text-muted-foreground">{item.message}</span> : null}
            </li>
          ))}
        </ul>
      ) : null}
    </div>
  )
}
