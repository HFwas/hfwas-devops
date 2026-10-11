import { ChevronRight, Play, Power } from 'lucide-react'
import { JobKindIcon } from '@/modules/pipeline/components/JobKindIcon'
import { jobKindLabel } from '@/modules/pipeline/graph/jobCatalog'
import type { PipelineStage } from '@/modules/pipeline/types/pipeline'
import { cn } from '@/lib/utils'

function FlowArrow() {
  return (
    <div className="mt-8 flex w-8 shrink-0 items-center text-muted-foreground" aria-hidden>
      <span className="h-px flex-1 bg-border" />
      <ChevronRight className="-ml-1 size-4" />
    </div>
  )
}

function FlowCap({ label, slot, icon: Icon }: { label: string; slot: string; icon: typeof Play }) {
  return (
    <div data-slot={slot} className="mt-6 flex h-10 shrink-0 items-center gap-2 rounded-md border bg-background px-3 text-sm shadow-xs">
      <Icon className={cn('size-4', slot.endsWith('start') ? 'text-primary' : 'text-muted-foreground')} aria-hidden />
      {label}
    </div>
  )
}

/** 只读定义画布：开始 → 阶段从左到右 → 结束。 */
export function PipelineDefinitionCanvas({ stages }: { stages?: PipelineStage[] | null }) {
  const columns = [...(stages ?? [])].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))

  return (
    <section data-slot="pipeline-definition-canvas" className="min-h-[320px] overflow-auto rounded-lg border bg-muted/20 p-6">
      {columns.length === 0 ? (
        <p className="mb-4 text-sm text-muted-foreground">这条流水线还没有阶段。点右上角「编辑」后，阶段会显示在这里。</p>
      ) : null}
      <div className="flex w-max min-w-full items-start">
        <FlowCap slot="definition-start" label="开始" icon={Play} />
        {columns.map((stage, index) => {
          const jobs = [...stage.jobs].sort((a, b) => (a.sortOrder ?? 0) - (b.sortOrder ?? 0))
          return (
            <div key={String(stage.id ?? `${stage.name}-${index}`)} className="flex items-start">
              <FlowArrow />
              <div data-slot="definition-stage" className="flex w-56 shrink-0 flex-col rounded-lg border bg-background">
                <div className="border-b px-3 py-2 text-sm font-medium">
                  阶段 {index + 1} · {stage.name || '未命名'}
                </div>
                <div className="flex flex-col gap-2 p-2">
                  {jobs.length === 0 ? <p className="px-1 py-2 text-xs text-muted-foreground">这个阶段没有任务</p> : null}
                  {jobs.map((job, jobIndex) => (
                    <div
                      key={String(job.id ?? `${job.kind}-${jobIndex}`)}
                      data-slot="definition-job"
                      className="rounded-lg border bg-card px-2 py-2"
                    >
                      <div className="flex items-start gap-2">
                        <JobKindIcon kind={job.kind} className="mt-0.5 size-4 shrink-0 text-primary" />
                        <span className="min-w-0">
                          <span className="block truncate text-sm font-medium">{job.name}</span>
                          <span className="block truncate text-xs text-muted-foreground">{jobKindLabel(job.kind)}</span>
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            </div>
          )
        })}
        <FlowArrow />
        <FlowCap slot="definition-end" label="结束" icon={Power} />
      </div>
    </section>
  )
}
