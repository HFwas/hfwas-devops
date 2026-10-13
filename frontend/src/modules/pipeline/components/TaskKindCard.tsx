import { Link } from 'react-router'
import { ConfirmDialog } from '@/components/console/ConfirmDialog'
import { StatusIcon } from '@/components/console/StatusIcon'
import { Badge } from '@/components/ui/badge'
import { Button } from '@/components/ui/button'
import { Card } from '@/components/ui/card'
import { jobKindIcon } from '@/modules/pipeline/components/JobKindIcon'
import { displayVersion, groupTone } from '@/modules/pipeline/taskKindView'
import type { TaskKindVO } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

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
  const Icon = jobKindIcon(item.kindValue)
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
        <div className="flex justify-end gap-1 border-t px-3 py-2">
          <Button variant="outline" size="sm" asChild>
            <Link to={`/pipeline/task-kinds/${encodeURIComponent(item.kindValue)}?edit=overview`}>编辑</Link>
          </Button>
          <Button variant="ghost" size="sm" asChild>
            <Link to={`/pipeline/task-kinds/${encodeURIComponent(item.kindValue)}?tab=参数`}>参数</Link>
          </Button>
          <ConfirmDialog
            title={`${item.enabled ? '停用' : '启用'}任务「${item.label}」？`}
            description={item.enabled ? '停用后该类型不会出现在流水线的添加列表里。' : '启用后该类型可被流水线选用。'}
            confirmText={item.enabled ? '停用' : '启用'}
            destructive={item.enabled}
            confirmDisabled={togglePending}
            onConfirm={onToggle}
          >
            <Button variant="ghost" size="sm" disabled={togglePending}>
              {item.enabled ? '停用' : '启用'}
            </Button>
          </ConfirmDialog>
        </div>
      ) : null}
    </Card>
  )
}