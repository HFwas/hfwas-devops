import { describe, expect, it } from 'vitest'
import { deploymentAvailability, replicaField, selectorPills } from '@/modules/container/utils/workloadStatus'

describe('workload status helpers', () => {
  it('reads replica counters from the deployment status string', () => {
    expect(replicaField('replicas=2 updated=2 ready=1 available=1', 'updated')).toBe(2)
    expect(replicaField('replicas=2 updated=2 ready=1', 'available')).toBeNull()
  })

  it('derives availability from replica counts', () => {
    expect(deploymentAvailability({ desiredReplicas: 2, readyReplicas: 2, availableReplicas: 2 })).toBe('Available')
    expect(deploymentAvailability({ desiredReplicas: 2, readyReplicas: 1, availableReplicas: 0 })).toBe('Progressing')
    expect(deploymentAvailability({ desiredReplicas: 0, readyReplicas: 0, availableReplicas: 0 })).toBe('Scaled to zero')
  })

  it('splits selector text and java map strings into pills', () => {
    expect(selectorPills('{app=juicefs, tier=node}')).toEqual(['app=juicefs', 'tier=node'])
    expect(selectorPills({ app: 'demo' })).toEqual(['app=demo'])
  })
})
