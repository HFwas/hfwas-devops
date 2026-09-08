import { describe, expect, it } from 'vitest'
import { resolveActiveProductKey } from '@/shared/console/products'
import { resolveActiveTab } from '@/shared/console/tabs'
import { pipelineRoutes } from './pipelineRoutes'

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

  it('opens a pipeline on the latest run home; editor is an explicit /edit route', () => {
    const children = pipelineRoutes.find((item) => item.path === '/pipeline')?.children ?? []
    const byName = Object.fromEntries(
      children.filter((item) => item.name).map((item) => [String(item.name), item.path]),
    )
    expect(byName['pipeline-home']).toBe('pipelines/:id')
    expect(byName['pipeline-edit']).toBe('pipelines/:id/edit')
    expect(byName['pipeline-run']).toBe('pipelines/:id/runs/:runId')
    expect(byName['pipeline-new']).toBe('pipelines/new')
  })
})
