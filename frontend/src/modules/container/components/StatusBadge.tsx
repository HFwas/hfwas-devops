import { Badge } from '@/components/ui/badge'
import { cn } from '@/lib/utils'

const tone: Record<string, string> = {
  Connected: 'border-transparent bg-primary/15 text-primary',
  Running: 'border-transparent bg-primary/15 text-primary',
  Ready: 'border-transparent bg-primary/15 text-primary',
  Bound: 'border-transparent bg-primary/15 text-primary',
  Degraded: 'border-transparent bg-accent text-accent-foreground',
  Pending: 'border-transparent bg-accent text-accent-foreground',
  Disconnected: 'border-transparent bg-destructive/15 text-destructive',
  Error: 'border-transparent bg-destructive/15 text-destructive',
  Failed: 'border-transparent bg-destructive/15 text-destructive',
}

export function StatusBadge({ status }: { status?: string | null }) {
  const label = status || 'Unknown'
  return <Badge className={cn('font-normal', tone[label])}>{label}</Badge>
}
