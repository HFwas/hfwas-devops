import { Link } from 'react-router'
import {
  Bell,
  Boxes,
  Bug,
  CloudUpload,
  Container,
  FlaskConical,
  GitBranch,
  Hammer,
  Network,
  Package,
  Paintbrush,
  Rocket,
  SearchCode,
  Shield,
  ShieldAlert,
  Terminal,
  Upload,
  UserCheck,
  type LucideIcon,
} from 'lucide-react'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { displayVersion, groupTone } from '@/modules/pipeline/taskKindView'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

const ICONS: Record<string, LucideIcon> = {
  CLONE: GitBranch,
  BUILD: Hammer,
  IMAGE: Container,
  LINT_SEMGREP: Shield,
  LINT_SONAR: SearchCode,
  FORMAT: Paintbrush,
  DEPENDENCY_ANALYSIS: Network,
  SCAN: ShieldAlert,
  PACKAGE: Package,
  PUBLISH: Upload,
  UPLOAD: CloudUpload,
  DEPLOY: Rocket,
  TEST: FlaskConical,
  CUSTOM: Terminal,
  APPROVAL: UserCheck,
  NOTIFY: Bell,
  DEPENDENCY_TRACK: Bug,
  KUBECTL: Terminal,
}

export function TaskKindCard({
  item,
  showToggle,
  togglePending,
  onToggle,
}: {
  item: TaskKindVO
  showToggle: boolean
  togglePending: boolean
  onToggle: () => void
}) {
  const Icon = ICONS[item.kindValue] ?? Boxes
  const summary = item.description?.trim() || item.hint?.trim() || '没有说明'
  return (
    <Card
      data-kind={item.kindValue}
      className={cn(
        'gap-0 overflow-hidden py-0 shadow-sm transition hover:border-primary/40 hover:shadow-md',
        !item.enabled && 'opacity-60',
      )}
    >
      <Link
        to={`/pipeline/task-kinds/${encodeURIComponent(item.kindValue)}`}
        className="flex flex-1 flex-col gap-3 p-4"
      >
        <div className="flex items-start gap-3">
          <span className={cn('flex size-10 shrink-0 items-center justify-center rounded-lg', groupTone(item.taskGroup || item.kindValue))}>
            <Icon className="size-5" aria-hidden />
          </span>
          <div className="min-w-0 flex-1">
            <h2 className="truncate font-medium">{item.label}</h2>
            <div className="mt-1 flex flex-wrap gap-1">
              <Badge variant="outline">{item.taskGroup || '未分组'}</Badge>
              <Badge variant="secondary">{displayVersion(item.version)}</Badge>
            </div>
          </div>
        </div>
        <p className="line-clamp-2 min-h-10 text-sm text-muted-foreground">{summary}</p>
        <StatusIcon status={item.enabled ? 'enabled' : 'disabled'} label={item.enabled ? '启用' : '停用'} />
      </Link>
      {showToggle ? (
        <div className="flex justify-end border-t px-3 py-2">
          <Button variant="ghost" size="sm" disabled={togglePending} onClick={onToggle}>
            {item.enabled ? '停用' : '启用'}
          </Button>
        </div>
      ) : null}
    </Card>
  )
}
