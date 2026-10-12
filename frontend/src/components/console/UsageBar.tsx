import { cn } from '@/lib/utils'

export type UsageKind = 'requests' | 'limits'

/** 用量相对容量（或限额）的填充色。Limits 的橙色只在未过阈值时出现。 */
export function usageFill(ratio: number, kind: UsageKind) {
  if (ratio > 0.9) return 'bg-red-500'
  if (ratio > 0.6) return 'bg-yellow-500'
  return kind === 'limits' ? 'bg-orange-500' : 'bg-primary'
}

/**
 * 细用量条。轨道高 `h-2`。调用方传入 0–1 的 ratio；超过 1 的宽度截到满条，颜色仍按真实比例。
 * 没有容量分母时不要传 ratio，改用纯文本。
 */
export function UsageBar({
  ratio,
  kind,
  caption,
}: {
  ratio: number
  kind: UsageKind
  caption: string
}) {
  const width = Math.max(0, Math.min(ratio, 1)) * 100
  return (
    <div data-slot="usage-bar" className="flex min-w-0 flex-col gap-1">
      <div
        className="h-2 overflow-hidden rounded-full bg-muted"
        role="meter"
        aria-valuemin={0}
        aria-valuemax={100}
        aria-valuenow={Math.round(width)}
        aria-label={caption}
      >
        <div className={cn('h-full', usageFill(ratio, kind))} style={{ width: `${width}%` }} />
      </div>
      <p className="text-xs text-muted-foreground tabular-nums">{caption}</p>
    </div>
  )
}
