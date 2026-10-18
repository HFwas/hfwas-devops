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

  it('PIPE-022: seeds empty string for missing defaults', () => {
    expect(
      initialRunParamValues([
        { paramKey: 'A', paramLabel: 'A', paramType: 'input', required: false, defaultValue: null as unknown as string },
      ]),
    ).toEqual({ A: '' })
  })

  it('PIPE-024: treats whitespace-only required values as missing', () => {
    expect(missingRunParam(defs, { GIT_REF: '   ', IMAGE_TAG: 'latest' })).toBe('请填写分支')
  })

  it('PIPE-024: ignores optional parameters', () => {
    expect(missingRunParam(defs, { GIT_REF: 'main', IMAGE_TAG: '' })).toBeNull()
  })

  it('PIPE-023: returns the first missing required parameter', () => {
    expect(
      missingRunParam(
        [
          { paramKey: 'A', paramLabel: '甲', paramType: 'input', required: true },
          { paramKey: 'B', paramLabel: '乙', paramType: 'input', required: true },
        ],
        {},
      ),
    ).toBe('请填写甲')
  })

  it('PIPE-021: no definitions means no validation error', () => {
    expect(initialRunParamValues([])).toEqual({})
    expect(missingRunParam([], {})).toBeNull()
  })
})
