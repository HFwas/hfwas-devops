import { describe, expect, it } from 'vitest'
import { aggregateJobStatus, aggregateRunLog, groupJobsByStage, logBody } from './runView'

describe('run detail stage status and log aggregation', () => {
  it('maps a stage to the strongest job status', () => {
    expect(aggregateJobStatus([])).toBe('QUEUED')
    expect(aggregateJobStatus(['SUCCEEDED', 'SUCCEEDED'])).toBe('SUCCEEDED')
    expect(aggregateJobStatus(['QUEUED', 'QUEUED'])).toBe('QUEUED')
    expect(aggregateJobStatus(['RUNNING', 'QUEUED'])).toBe('RUNNING')
    expect(aggregateJobStatus(['SUCCEEDED', 'QUEUED'])).toBe('RUNNING')
    expect(aggregateJobStatus(['SUCCEEDED', 'WAITING_APPROVAL'])).toBe('WAITING_APPROVAL')
    expect(aggregateJobStatus(['RUNNING', 'FAILED'])).toBe('FAILED')
    expect(aggregateJobStatus(['SUCCEEDED', 'CANCELLED'])).toBe('CANCELLED')
    expect(aggregateJobStatus(['SUCCEEDED', 'ODD'])).toBe('ODD')
  })

  it('keeps stage order by first appearance and merges the same stage name', () => {
    const stages = groupJobsByStage([
      { stageName: '构建', jobName: '编译', status: 'QUEUED' },
      { stageName: '代码克隆', jobName: '克隆', status: 'SUCCEEDED' },
      { stageName: '构建', jobName: '打包', status: 'RUNNING' },
    ])
    expect(stages.map((stage) => stage.name)).toEqual(['构建', '代码克隆'])
    expect(stages[0].jobs.map((job) => job.jobName)).toEqual(['编译', '打包'])
    expect(stages[0].status).toBe('RUNNING')
    expect(stages[1].status).toBe('SUCCEEDED')
  })

  it('concatenates job logs in stage and job order', () => {
    const text = aggregateRunLog([
      { stageName: '构建', jobName: '编译', status: 'RUNNING', logText: '  still going  \n' },
      { stageName: '代码克隆', jobName: '克隆', status: 'SUCCEEDED', logText: 'clone ok' },
      { stageName: '构建', jobName: '打包', status: 'QUEUED', logText: '   ' },
    ])
    expect(text).toBe(
      [
        '──── 构建 / 编译 · 运行中 ────',
        '  still going',
        '',
        '──── 构建 / 打包 · 排队中 ────',
        '（暂无日志）',
        '',
        '──── 代码克隆 / 克隆 · 成功 ────',
        'clone ok',
      ].join('\n'),
    )
    expect(text.indexOf('clone ok')).toBeGreaterThan(text.indexOf('still going'))
    expect(logBody(null, '')).toBe('')
    expect(aggregateRunLog([])).toBe('')
  })
})
