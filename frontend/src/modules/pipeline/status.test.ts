import { describe, expect, it } from 'vitest'
import {
  formatCommit,
  formatDateTime,
  formatCompactDuration,
  formatDuration,
  formatGitRef,
  formatRelativeTime,
  jobRunDuration,
  stageRunDuration,
  jobKindTone,
  parseInstant,
  runStatusLabel,
  runStatusTagType,
  stackTone,
} from './status'

describe('pipeline status helpers', () => {
  it('maps run status to console labels and tag types', () => {
    expect(runStatusLabel(null)).toBe('未运行')
    expect(runStatusLabel('SUCCEEDED')).toBe('成功')
    expect(runStatusLabel('WAITING_APPROVAL')).toBe('待审批')
    expect(runStatusTagType('FAILED')).toBe('error')
    expect(runStatusTagType('RUNNING')).toBe('info')
  })

  it('assigns stack and job tones used by console tiles', () => {
    expect(stackTone('JAVA_MAVEN')).toBe('blue')
    expect(stackTone('PYTHON')).toBe('amber')
    expect(jobKindTone('TEST')).toBe('green')
    expect(jobKindTone('APPROVAL')).toBe('violet')
  })

  it('formats a relative run time', () => {
    const now = Date.parse('2026-10-11T00:00:00Z')
    expect(formatRelativeTime(null, now)).toBe('—')
    expect(formatRelativeTime('2026-10-10T23:58:30Z', now)).toBe('1分钟前')
    expect(formatRelativeTime('2026-10-10T21:00:00Z', now)).toBe('3小时前')
  })

  it('formats timestamps without the T separator', () => {
    expect(formatDateTime(null)).toBe('—')
    const utc = '2026-09-08T10:11:12Z'
    const d = new Date(utc)
    const pad = (n: number) => String(n).padStart(2, '0')
    expect(formatDateTime(utc)).toBe(
      `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`,
    )
  })

  it('formats git ref, commit and duration', () => {
    expect(formatGitRef('refs/heads/master')).toBe('master')
    expect(formatCommit('48b618f9408d74b9eb5f260be9db9d087350203a')).toBe('48b618f9')
    expect(formatDuration('2026-09-08T09:37:00', '2026-09-08T09:40:59')).toBe('3分59秒')
    expect(formatDuration(null, null)).toBe('—')
    expect(formatCompactDuration('2026-09-08T09:37:00Z', '2026-09-08T09:40:59Z')).toBe('3m 59s')
    expect(formatCompactDuration('2026-09-08T09:37:00Z', '2026-09-08T09:38:05Z')).toBe('1m 05s')
    expect(formatCompactDuration('2026-09-08T09:37:00Z', '2026-09-08T09:37:58Z')).toBe('58s')
    expect(formatCompactDuration(null, null)).toBe('—')
  })

  it('treats naive backend timestamps as UTC so duration is not skewed by local offset', () => {
    expect(parseInstant('2026-09-08T07:31:12')).toBe(Date.parse('2026-09-08T07:31:12Z'))
    expect(formatDuration('2026-09-08T07:31:12', null, Date.parse('2026-09-08T07:31:23Z'))).toBe('11秒')
  })

  it('does not show duration for jobs that have not started', () => {
    const now = Date.parse('2026-09-08T07:40:00Z')
    expect(jobRunDuration({ status: 'QUEUED', startedAt: '2026-09-08T07:31:12' }, now)).toBe('')
    expect(jobRunDuration({ status: 'WAITING_APPROVAL' }, now)).toBe('')
    expect(jobRunDuration({ status: 'RUNNING' }, now)).toBe('')
    expect(jobRunDuration({ status: 'RUNNING', startedAt: '2026-09-08T07:39:50' }, now)).toBe('10秒')
    expect(
      jobRunDuration({ status: 'SUCCEEDED', startedAt: '2026-09-08T07:31:12', finishedAt: '2026-09-08T07:32:46' }, now),
    ).toBe('1分34秒')
  })

  it('spans a stage from the earliest start to the latest finish', () => {
    const now = Date.parse('2026-09-08T07:40:00Z')
    expect(stageRunDuration([{ status: 'QUEUED' }], now)).toBe('')
    expect(
      stageRunDuration(
        [
          { status: 'SUCCEEDED', startedAt: '2026-09-08T07:31:12', finishedAt: '2026-09-08T07:31:20' },
          { status: 'SUCCEEDED', startedAt: '2026-09-08T07:31:18', finishedAt: '2026-09-08T07:32:00' },
        ],
        now,
      ),
    ).toBe('48秒')
    expect(
      stageRunDuration([{ status: 'RUNNING', startedAt: '2026-09-08T07:39:50' }], now),
    ).toBe('10秒')
  })
})
