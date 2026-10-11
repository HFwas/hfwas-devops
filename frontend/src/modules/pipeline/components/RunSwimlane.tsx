import { ChevronRight, Play, Power } from 'lucide-react'
import { resolveStatusTone, STATUS_BORDER_CLASS, STATUS_TONE_CLASS } from '@/components/console/status'
import type { RunStageGroup } from '@/modules/pipeline/runView'
import { runStatusLabel, stageRunDuration } from '@/modules/pipeline/status'
import type { PipelineRunJob } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

function FlowArrow() {
  return (
    <div className="mt-4 flex w-8 shrink-0 items-center text-muted-foreground" aria-hidden>
      <span className="h-px flex-1 bg-border" />
      <ChevronRight className="-ml-1 size-4" />
    </div>
  )
}

function FlowCap({
  label,
  icon: Icon,
  iconClassName,
  slot,
}: {
  label: string
  icon: typeof Play
  iconClassName?: string
  slot: string
}) {
  return (
    <div data-slot={slot} className="mt-1 flex h-10 shrink-0 items-center gap-2 rounded-md border bg-background px-3 text-sm shadow-xs">
      <Icon className={cn('size-4', iconClassName)} aria-hidden />
      {label}
    </div>
  )
}

/**
 * 运行画布：开始 → 阶段卡片 → 结束。日志默认不展开，由卡片上的「日志」或右上角「查看全程日志」打开。
 */
export function RunSwimlane({
  stages,
  runStatus,
  selectedJobId,
  selectedStageName,
  overallOpen,
  now,
  onSelectJob,
  onSelectStage,
  onSelectOverall,
}: {
  stages: RunStageGroup<PipelineRunJob>[]
  runStatus?: string | null
  selectedJobId: string | null
  selectedStageName: string | null
  overallOpen: boolean
  now: number
  onSelectJob: (id: string) => void
  onSelectStage: (name: string) => void
  onSelectOverall: () => void
}) {
  const endTone = STATUS_TONE_CLASS[resolveStatusTone(runStatus)]

  return (
    <section data-slot="run-canvas" className="flex min-h-[280px] flex-col rounded-lg border bg-muted/20">
      <div className="flex shrink-0 items-center justify-end px-4 py-2">
        <button
          type="button"
          className={cn('text-sm text-primary hover:underline', overallOpen && 'font-medium')}
          aria-pressed={overallOpen}
          onClick={onSelectOverall}
        >
          查看全程日志
        </button>
      </div>
      <div className="min-h-0 flex-1 overflow-auto px-6 pt-4 pb-8">
        {stages.length === 0 ? (
          <p className="mb-4 text-sm text-muted-foreground">这次运行没有任务</p>
        ) : null}
        <div className="flex w-max min-w-full items-start">
          <FlowCap slot="run-start" label="开始" icon={Play} iconClassName="text-primary" />
          {stages.map((stage) => {
            const tone = resolveStatusTone(stage.status)
            const stageSelected = selectedStageName === stage.name
            const duration = stageRunDuration(stage.jobs, now)
            return (
              <div key={stage.name} className="flex items-start">
                <FlowArrow />
                <div className="flex w-44 shrink-0 flex-col gap-2">
                  <div
                    data-slot="run-stage"
                    data-status={stage.status}
                    className={cn(
                      'rounded-md border-2 bg-background px-3 py-2',
                      STATUS_BORDER_CLASS[tone],
                      stageSelected && 'ring-2 ring-primary/30',
                    )}
                  >
                    <div className="flex items-start justify-between gap-2">
                      <span className="truncate text-sm font-medium">{stage.name}</span>
                      {duration ? <span className="shrink-0 text-xs text-muted-foreground">{duration}</span> : null}
                    </div>
                    <span className="sr-only">{runStatusLabel(stage.status)}</span>
                    <button
                      type="button"
                      className="text-sm text-primary hover:underline"
                      aria-pressed={stageSelected}
                      aria-label={`查看阶段${stage.name}日志`}
                      onClick={() => onSelectStage(stage.name)}
                    >
                      日志
                    </button>
                  </div>
                  {stage.jobs.map((job) => {
                    const id = String(job.id)
                    const selected = selectedJobId === id
                    const several = stage.jobs.length > 1
                    return (
                      <div
                        key={id}
                        data-slot="run-job"
                        data-status={job.status}
                        className={cn(
                          'flex items-center justify-center gap-2 rounded-md border bg-background px-2 py-2 text-sm',
                          selected && 'border-primary ring-2 ring-primary/30',
                        )}
                      >
                        {several ? <span className="truncate">{job.jobName}</span> : null}
                        <button
                          type="button"
                          className={cn(several ? 'shrink-0 text-primary hover:underline' : 'w-full truncate text-center')}
                          aria-pressed={selected}
                          aria-label={`查看${job.jobName}日志`}
                          onClick={() => onSelectJob(id)}
                        >
                          {several ? '日志' : job.jobName}
                        </button>
                      </div>
                    )
                  })}
                </div>
              </div>
            )
          })}
          <FlowArrow />
          <FlowCap slot="run-end" label="结束" icon={Power} iconClassName={endTone} />
        </div>
      </div>
    </section>
  )
}
