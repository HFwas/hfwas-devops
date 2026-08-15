import { describe, expect, it } from 'vitest'
import { resolveVariablesForUpdate } from './variablesForUpdate'
import type { EnvironmentVariableItemVO } from '@/modules/api-test/environment/types/environment'

const previous: EnvironmentVariableItemVO[] = [
  { id: 11, name: 'baseUrl', value: 'https://dev.example', description: '', isSecret: false, sortOrder: 0 },
  { id: 12, name: 'apiKey', value: 'sk-live', description: '', isSecret: true, sortOrder: 1 },
]

describe('resolveVariablesForUpdate', () => {
  it('restores blanked secret values from the previous detail', () => {
    const result = resolveVariablesForUpdate(
      [
        { id: 11, name: 'baseUrl', value: 'https://dev.example', isSecret: false, sortOrder: 0 },
        { id: 12, name: 'apiKey', value: '', isSecret: true, sortOrder: 1 },
      ],
      previous,
    )
    expect(result.find((v) => v.name === 'apiKey')?.value).toBe('sk-live')
    expect(result.find((v) => v.name === 'baseUrl')?.value).toBe('https://dev.example')
  })

  it('keeps a newly typed secret value', () => {
    const result = resolveVariablesForUpdate(
      [{ id: 12, name: 'apiKey', value: 'rotated-key', isSecret: true, sortOrder: 1 }],
      previous,
    )
    expect(result[0].value).toBe('rotated-key')
  })

  it('leaves a new blank secret empty when there is no previous value', () => {
    const result = resolveVariablesForUpdate(
      [{ name: 'newSecret', value: '', isSecret: true, sortOrder: 2 }],
      previous,
    )
    expect(result[0].value).toBe('')
  })
})
