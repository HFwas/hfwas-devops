import { type ReactNode } from 'react'
import { RANGE_STEP_MAP, type MonitorRange } from '@/modules/container/types/monitor'
import { cn } from '@/lib/utils'

const ranges = Object.keys(RANGE_STEP_MAP) as MonitorRange[]

export function MonitorTimeRange({
  value,
  onChange,
  extra,
}: {
  value: MonitorRange
  onChange: (value: MonitorRange) => void
  extra?: ReactNode
}) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div className="flex gap-1">
        {ranges.map((range) => (
          <button
            key={range}
            type="button"
            className={cn(
              'h-8 rounded-md px-3 text-sm',
              value === range ? 'bg-primary text-primary-foreground' : 'bg-muted text-muted-foreground',
            )}
            onClick={() => onChange(range)}
          >
            {RANGE_STEP_MAP[range].label}
          </button>
        ))}
      </div>
      {extra}
    </div>
  )
}
