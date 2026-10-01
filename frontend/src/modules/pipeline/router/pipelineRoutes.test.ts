import { describe, expect, it } from 'vitest'
import { resolveActiveProductKey } from '@/shared/console/products'
import { resolveActiveTab } from '@/shared/console/tabs'

describe('pipeline product registration', () => {
  it('resolves /pipeline/pipelines as the pipeline product', () => {
    expect(resolveActiveProductKey('/pipeline/pipelines')).toBe('pipeline')
    expect(resolveActiveProductKey('/pipeline/credentials')).toBe('pipeline')
  })

  it('does not use top-level console tabs; pipeline has its own left menu', () => {
    expect(resolveActiveTab('/pipeline/pipelines')).toBeNull()
    expect(resolveActiveTab('/pipeline/pipelines/new')).toBeNull()
    expect(resolveActiveTab('/pipeline/credentials')).toBeNull()
    expect(resolveActiveTab('/pm/projects')).toBeNull()
  })
})
