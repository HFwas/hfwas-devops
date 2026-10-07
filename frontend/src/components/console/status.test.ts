import { describe, expect, it } from 'vitest'
import { resolveStatusTone, statusIconSpins } from '@/components/console/status'

describe('status dictionary', () => {
  it('maps container, pipeline, and PM phrases onto one tone set', () => {
    expect(resolveStatusTone('Ready')).toBe('success')
    expect(resolveStatusTone('Connected')).toBe('success')
    expect(resolveStatusTone('SUCCEEDED')).toBe('success')
    expect(resolveStatusTone('已解决')).toBe('success')

    expect(resolveStatusTone('Failed')).toBe('failed')
    expect(resolveStatusTone('Disconnected')).toBe('failed')
    expect(resolveStatusTone('CrashLoopBackOff')).toBe('failed')

    expect(resolveStatusTone('Degraded')).toBe('warning')
    expect(resolveStatusTone('WAITING_APPROVAL')).toBe('warning')

    expect(resolveStatusTone('Pending')).toBe('progress')
    expect(resolveStatusTone('RUNNING')).toBe('progress')
    expect(resolveStatusTone('QUEUED')).toBe('progress')

    expect(resolveStatusTone('Terminating')).toBe('terminating')
    expect(resolveStatusTone('Paused')).toBe('paused')
    expect(resolveStatusTone('CANCELLED')).toBe('neutral')
    expect(resolveStatusTone('DRAFT')).toBe('neutral')
    expect(resolveStatusTone('PUBLISHED')).toBe('success')
    expect(resolveStatusTone('DEPRECATED')).toBe('warning')
    expect(resolveStatusTone('open')).toBe('progress')
    expect(resolveStatusTone('done')).toBe('success')
    expect(resolveStatusTone('closed')).toBe('success')
    expect(resolveStatusTone('Unknown')).toBe('neutral')
    expect(resolveStatusTone(null)).toBe('neutral')
    expect(resolveStatusTone('custom-workflow')).toBe('neutral')
  })

  it('spins only while a progress status is actively running', () => {
    expect(statusIconSpins('Running', 'progress')).toBe(true)
    expect(statusIconSpins('Pending', 'progress')).toBe(false)
    expect(statusIconSpins('Ready', 'success')).toBe(false)
  })
})
