import type { ReactNode } from 'react'
import { AlertTriangle, Ban, CheckCircle2, Circle, Loader2, PauseCircle, XCircle } from 'lucide-react'
import { cn } from '@/lib/utils'
import {
  normalizeStatusKey,
  resolveStatusTone,
  statusIconSpins,
  statusToneLabel,
  STATUS_DOT_CLASS,
  STATUS_TONE_CLASS,
  type StatusTone,
} from '@/components/console/status'

/** 圆点用在资源概览。Kite 把 Running 画成绿点；图标变体仍走进行中蓝。 */
const DOT_SUCCESS = new Set(['running', 'succeeded'])

function resolveDotTone(status: string | null | undefined, toneProp?: StatusTone): StatusTone {
  if (toneProp) return toneProp
  if (DOT_SUCCESS.has(normalizeStatusKey(status))) return 'success'
  return resolveStatusTone(status)
}

const ICONS = {
  success: CheckCircle2,
  failed: XCircle,
  warning: AlertTriangle,
  progress: Loader2,
  terminating: Ban,
  paused: PauseCircle,
  neutral: Circle,
} as const

export function StatusIcon({
  status,
  tone: toneProp,
  label,
  showLabel = true,
  variant = 'icon',
  className,
}: {
  status?: string | null
  /** 字典里没有的领域码（例如自定义工作流）直接指定语义。 */
  tone?: StatusTone
  label?: ReactNode
  showLabel?: boolean
  /** `dot` 用同一套语义色画圆点，给资源概览卡用。 */
  variant?: 'icon' | 'dot'
  className?: string
}) {
  const tone = variant === 'dot' ? resolveDotTone(status, toneProp) : (toneProp ?? resolveStatusTone(status))
  const Icon = ICONS[tone]
  const text = label ?? (status?.trim() ? status : statusToneLabel(tone))
  const spin = statusIconSpins(status, tone)

  if (variant === 'dot') {
    return (
      <span className={cn('inline-flex items-center gap-2 text-sm font-semibold text-foreground', className)}>
        <span className={cn('size-2 shrink-0 rounded-full', STATUS_DOT_CLASS[tone])} aria-hidden />
        {showLabel ? <span>{text}</span> : <span className="sr-only">{text}</span>}
      </span>
    )
  }

  return (
    <span className={cn('inline-flex items-center gap-1.5 text-sm', className)}>
      <Icon
        className={cn('size-3.5 shrink-0', STATUS_TONE_CLASS[tone], spin && 'animate-spin', tone === 'terminating' && 'animate-pulse')}
        aria-hidden
      />
      {showLabel ? <span>{text}</span> : <span className="sr-only">{text}</span>}
    </span>
  )
}
