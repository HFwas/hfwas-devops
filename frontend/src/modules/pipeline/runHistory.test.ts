import { describe, expect, it } from 'vitest'
import { filterPipelineRuns, recentSuccessRate, triggerLabel } from '@/modules/pipeline/runHistory'
import type { PipelineRun } from '@/modules/pipeline/types/pipeline'

const now = Date.parse('2026-10-11T12:00:00Z')

function run(partial: Partial<PipelineRun> & Pick<PipelineRun, 'id' | 'status'>): PipelineRun {
  return {
    pipelineId: 1,
    pipelineName: '订单服务 CI',
    trigger: 'MANUAL',
    stack: '',
    runtimeVersion: '',
    image: '',
    jobs: [],
    startedAt: '2026-10-11T11:00:00Z',
    ...partial,
  }
}

describe('pipeline run history filters', () => {
  const runs = [
    run({ id: 128, status: 'RUNNING', trigger: 'MANUAL', gitRef: 'refs/heads/main' }),
    run({ id: 126, status: 'FAILED', trigger: 'SCHEDULE', gitRef: 'refs/heads/main', startedAt: '2026-10-11T10:00:00Z' }),
    run({ id: 120, status: 'SUCCEEDED', trigger: 'WEBHOOK', gitRef: 'refs/heads/release/1.2', startedAt: '2026-10-01T10:00:00Z' }),
  ]

  it('labels triggers and keeps the default 7 day window', () => {
    expect(triggerLabel('MANUAL')).toBe('手动')
    expect(triggerLabel('webhook')).toBe('Webhook')
    expect(triggerLabel('CRON')).toBe('定时')
    expect(triggerLabel('API')).toBe('API')
    expect(triggerLabel(undefined)).toBe('—')
    const visible = filterPipelineRuns(runs, { status: 'all', range: '7d', keyword: '', now })
    expect(visible.map((item) => item.id)).toEqual([128, 126])
  })

  it('filters by status and run number or branch', () => {
    expect(filterPipelineRuns(runs, { status: 'FAILED', range: 'all', keyword: '', now }).map((item) => item.id)).toEqual([126])
    expect(filterPipelineRuns(runs, { status: 'all', range: 'all', keyword: 'release/1.2', now }).map((item) => item.id)).toEqual([
      120,
    ])
    expect(filterPipelineRuns(runs, { status: 'all', range: 'all', keyword: '128', now }).map((item) => item.id)).toEqual([128])
  })

  it('computes the success rate for the chosen time window from finished runs', () => {
    expect(recentSuccessRate(runs, '7d', now)).toBe(0)
    expect(
      recentSuccessRate(
        [
          run({ id: 1, status: 'SUCCEEDED' }),
          run({ id: 2, status: 'FAILED' }),
          run({ id: 3, status: 'RUNNING' }),
          run({ id: 4, status: 'SUCCEEDED', startedAt: '2026-09-01T00:00:00Z' }),
        ],
        '7d',
        now,
      ),
    ).toBe(50)
    // 放宽到「全部时间」后，更早的那次成功计入，成功率上升。
    expect(
      recentSuccessRate(
        [
          run({ id: 1, status: 'SUCCEEDED' }),
          run({ id: 2, status: 'FAILED' }),
          run({ id: 4, status: 'SUCCEEDED', startedAt: '2026-09-01T00:00:00Z' }),
        ],
        'all',
        now,
      ),
    ).toBe(67)
  })
})
