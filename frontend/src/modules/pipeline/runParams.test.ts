import { describe, expect, it } from 'vitest'
import { initialRunParamValues, missingRunParam } from '@/modules/pipeline/runParams'
import type { RunParamValue } from '@/modules/pipeline/types/pipeline'

const defs: RunParamValue[] = [
  {
    paramKey: 'GIT_REF',
    paramLabel: '分支',
    paramType: 'input',
    defaultValue: 'main',
    required: true,
  },
  {
    paramKey: 'IMAGE_TAG',
    paramLabel: '标签',
    paramType: 'select',
    defaultValue: 'latest',
    required: false,
    options: ['latest'],
  },
]

describe('run params', () => {
  it('seeds defaults and flags a blank required field', () => {
    expect(initialRunParamValues(defs)).toEqual({ GIT_REF: 'main', IMAGE_TAG: 'latest' })
    expect(missingRunParam(defs, { GIT_REF: '  ', IMAGE_TAG: '' })).toBe('请填写分支')
    expect(missingRunParam(defs, { GIT_REF: 'dev', IMAGE_TAG: '' })).toBeNull()
  })
})
