import { describe, expect, it } from 'vitest'
import { formatDateTime } from '@/modules/pm/utils/format'

describe('formatDateTime', () => {
  it('空值返回占位符', () => {
    expect(formatDateTime(undefined)).toBe('—')
    expect(formatDateTime(null)).toBe('—')
    expect(formatDateTime('')).toBe('—')
  })

  it('把 ISO 时间转成空格分隔', () => {
    expect(formatDateTime('2026-10-17T12:34:56')).toBe('2026-10-17 12:34:56')
  })

  it('截断毫秒', () => {
    expect(formatDateTime('2026-10-17T12:34:56.789')).toBe('2026-10-17 12:34:56')
  })
})
