import { describe, expect, it } from 'vitest'
import { asId } from '@/lib/utils'

describe('asId', () => {
  it('stringifies numeric and string ids', () => {
    expect(asId(12)).toBe('12')
    expect(asId('12')).toBe('12')
    expect(asId(0)).toBe('0')
  })
})