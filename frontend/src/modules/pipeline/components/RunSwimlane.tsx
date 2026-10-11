import { ChevronRight } from 'lucide-react'
import { resolveStatusTone, STATUS_DOT_CLASS } from '@/components/console/status'
import { StatusIcon } from '@/components/console/StatusIcon'
import { JobKindIcon } from '@/modules/pipeline/components/JobKindIcon'
import { jobKindLabel } from '@/modules/pipeline/graph/jobCatalog'
import type { RunStageGroup } from '@/modules/pipeline/runView'
import { jobRunDuration, runStatusLabel } from '@/modules/pipeline/status'
import type { PipelineRunJob } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

function StatusAccent({ status }: { status: string }) {
  return <span aria-hidden className={cn('absolute inset-y-0 left-0 w-1', STATUS_DOT_CLASS[resolveStatusTone(status)])} />
}

/**
 * 运行中的阶段泳道：从左到右是阶段，阶段内任务自上而下。
 * 视觉与编辑器泳道一致（列宽、分隔箭头、任务卡片），只读，点击打开日志。
 */
export function RunSwimlane({
  stages,
  selectedJobId,
  selectedStageName,
  now,
  onSelectJob,
  onSelectStage,
}: {
  stages: RunStageGroup<PipelineRunJob>[]
  selectedJobId: string | null
  selectedStageName: string | null
  now: number
  onSelectJob: (id: string) => void
  onSelectStage: (name: string) => void
}) {
  if (stages.length === 0) {
    return <p className="text-sm text-muted-foreground">这次运行没有任务</p>
  }

  return (
    <section data-slot="run-lanes" className="flex max-h-[50vh] min-h-[240px] flex-col overflow-hidden rounded-lg border bg-muted/20">
      <div className="flex shrink-0 items-center justify-between gap-2 border-b px-3 py-1.5">
        <h2 className="text-sm font-medium">阶段</h2>
        <span className="text-xs text-muted-foreground">点击阶段或任务查看日志</span>
      </div>
      <div className="min-h-0 flex-1 overflow-auto p-4">
        <div className="flex w-max min-w-full items-start gap-2">
          {stages.map((stage, index) => {
            const stageSelected = selectedStageName === stage.name
            const stageHasJob = stage.jobs.some((job) => String(job.id) === selectedJobId)
            return (
              <div key={stage.name} className="flex items-start gap-2">
                {index > 0 && <ChevronRight className="mt-16 size-4 shrink-0 text-muted-foreground" />}
                <div
                  data-slot="run-stage"
                  data-status={stage.status}
                  className={cn(
                    'relative flex w-60 shrink-0 flex-col overflow-hidden rounded-lg border bg-background',
                    (stageSelected || stageHasJob) && 'border-primary',
                    stageSelected && 'ring-2 ring-primary/20',
                  )}
                >
                  <StatusAccent status={stage.status} />
                  <div className="flex items-center gap-2 border-b py-2 pr-2 pl-3">
                    <button
                      type="button"
                      className="flex min-w-0 flex-1 items-center gap-1 text-left"
                      aria-pressed={stageSelected}
                      aria-label={`查看阶段${stage.name}日志`}
                      onClick={() => onSelectStage(stage.name)}
                    >
                      <span className="shrink-0 text-xs text-muted-foreground">阶段 {index + 1}</span>
                      <span className="truncate text-sm font-medium">{stage.name}</span>
                    </button>
                    <StatusIcon status={stage.status} label={runStatusLabel(stage.status)} className="shrink-0 text-xs" />
                  </div>
                  <div className="flex flex-col gap-2 p-2">
                    {stage.jobs.map((job) => {
                      const id = String(job.id)
                      const selected = selectedJobId === id
                      const duration = jobRunDuration(job, now)
                      return (
                        <div
                          key={id}
                          data-slot="run-job"
                          data-status={job.status}
                          className={cn(
                            'relative overflow-hidden rounded-lg border bg-card shadow-xs',
                            selected && 'border-primary ring-2 ring-primary/30',
                          )}
                        >
                          <StatusAccent status={job.status} />
                          <button
                            type="button"
                            className="flex w-full items-start gap-2 p-2 pl-3 text-left"
                            aria-pressed={selected}
                            aria-label={`查看${job.jobName}日志`}
                            onClick={() => onSelectJob(id)}
                          >
                            <JobKindIcon kind={job.kind} className="mt-0.5 size-4 shrink-0 text-primary" />
                            <span className="min-w-0">
                              <span className="block truncate text-sm font-medium">{job.jobName}</span>
                              <span className="block truncate text-xs text-muted-foreground">{jobKindLabel(job.kind)}</span>
                              <span className="mt-1 flex items-center gap-1 text-xs text-muted-foreground">
                                <StatusIcon status={job.status} showLabel={false} />
                                <span>{[duration, runStatusLabel(job.status)].filter(Boolean).join(' · ')}</span>
                              </span>
                            </span>
                          </button>
                        </div>
                      )
                    })}
                  </div>
                </div>
              </div>
            )
          })}
        </div>
      </div>
    </section>
  )
}
