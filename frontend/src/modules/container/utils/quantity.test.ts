import { describe, expect, it } from 'vitest'
import { parseQuantity, quantityRatio } from '@/modules/container/utils/quantity'

describe('parseQuantity', () => {
  it('reads cpu and memory suffixes', () => {
    expect(parseQuantity('500m')).toBeCloseTo(0.5)
    expect(parseQuantity('1')).toBe(1)
    expect(parseQuantity('512Mi')).toBe(512 * 1024 ** 2)
    expect(parseQuantity('1Gi')).toBe(1024 ** 3)
  })

  it('rejects blank and unknown suffixes', () => {
    expect(parseQuantity('')).toBeNull()
    expect(parseQuantity('  ')).toBeNull()
    expect(parseQuantity('abc')).toBeNull()
    expect(parseQuantity('1Xi')).toBeNull()
  })
})

describe('quantityRatio', () => {
  it('divides request by limit when both parse', () => {
    expect(quantityRatio('500m', '1')).toBeCloseTo(0.5)
    expect(quantityRatio('512Mi', '1Gi')).toBeCloseTo(0.5)
  })

  it('returns null without a positive limit', () => {
    expect(quantityRatio('500m', '')).toBeNull()
    expect(quantityRatio('1', '0')).toBeNull()
    expect(quantityRatio('soon', '1')).toBeNull()
  })
})
