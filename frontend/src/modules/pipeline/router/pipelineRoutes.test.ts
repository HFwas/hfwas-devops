import { describe, expect, it } from 'vitest'
import { resolveActiveProductKey } from '@/shared/console/products'
import { resolveActiveTab } from '@/shared/console/tabs'

describe('pipeline product registration', () => {
  it('resolves /pipeline/pipelines as the pipeline product', () => {
    expect(resolveActiveProductKey('/pipeline/pipelines')).toBe('pipeline')
    expect(resolveActiveProductKey('/pipeline/credentials')).toBe('pipeline')
  })

  it('highlights pipeline tabs on matching routes', () => {
    expect(resolveActiveTab('/pipeline/pipelines')).toBe('pipeline-list')
    expect(resolveActiveTab('/pipeline/pipelines/new')).toBe('pipeline-list')
    expect(resolveActiveTab('/pipeline/credentials')).toBe('pipeline-credentials')
    expect(resolveActiveTab('/pm/projects')).toBeNull()
  })
})
