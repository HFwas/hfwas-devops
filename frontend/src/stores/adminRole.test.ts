import { describe, expect, it } from 'vitest'
import { isPlatformAdmin } from '@/stores/adminRole'

describe('isPlatformAdmin', () => {
  it('matches admin regardless of case and surrounding space', () => {
    expect(isPlatformAdmin('admin')).toBe(true)
    expect(isPlatformAdmin('ADMIN')).toBe(true)
    expect(isPlatformAdmin(' Admin ')).toBe(true)
  })

  it('rejects members and missing roles', () => {
    expect(isPlatformAdmin('user')).toBe(false)
    expect(isPlatformAdmin('tenant_admin')).toBe(false)
    expect(isPlatformAdmin('')).toBe(false)
    expect(isPlatformAdmin(null)).toBe(false)
    expect(isPlatformAdmin(undefined)).toBe(false)
  })
})
