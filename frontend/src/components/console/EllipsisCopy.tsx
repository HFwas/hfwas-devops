import { useState } from 'react'
import { Copy } from 'lucide-react'
import { toast } from 'sonner'
import { cn } from '@/lib/utils'

/** 单行省略，悬停看全文，并复制完整字符串。 */
export function EllipsisCopy({
  value,
  copyLabel,
  className,
}: {
  value: string
  copyLabel: string
  className?: string
}) {
  const [copied, setCopied] = useState(false)
  return (
    <span className={cn('flex min-w-0 items-center gap-1', className)}>
      <span className="min-w-0 flex-1 truncate" title={value}>
        {value}
      </span>
      <button
        type="button"
        className="inline-flex shrink-0 rounded-sm text-muted-foreground hover:text-foreground"
        aria-label={copyLabel}
        title={copied ? '已复制' : value}
        onClick={() => {
          void navigator.clipboard.writeText(value).then(
            () => {
              setCopied(true)
              toast.success('已复制')
            },
            () => toast.error('复制失败'),
          )
        }}
      >
        <Copy className="size-3.5" />
      </button>
    </span>
  )
}
