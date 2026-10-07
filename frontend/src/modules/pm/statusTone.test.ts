import { describe, expect, it } from 'vitest'
import { priorityLabel, workflowStatusTone } from '@/modules/pm/statusTone'
import type { StatusDefinition } from '@/modules/pm/types'

function status(partial: Partial<StatusDefinition>): StatusDefinition {
  return { statusCode: 'custom', statusName: '自定义', ...partial }
}

describe('workflowStatusTone', () => {
  it('maps initial and final workflow states, and leaves middle states to the dictionary', () => {
    expect(workflowStatusTone(undefined)).toBeUndefined()
    expect(workflowStatusTone(status({ isInitial: 1 }))).toBe('progress')
    expect(workflowStatusTone(status({ isFinal: 1 }))).toBe('success')
    expect(workflowStatusTone(status({ isInitial: 0, isFinal: 0 }))).toBeUndefined()
  })

  it('labels built-in priorities', () => {
    expect(priorityLabel('high')).toBe('高')
    expect(priorityLabel(null)).toBe('—')
    expect(priorityLabel('p1')).toBe('p1')
  })
})
