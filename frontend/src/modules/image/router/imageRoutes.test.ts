import { describe, expect, it } from 'vitest'
import { resolveActiveProductKey } from '@/shared/console/products'

describe('image product registration', () => {
  it('resolves /image as the image product', () => {
    expect(resolveActiveProductKey('/image')).toBe('image')
  })
})
