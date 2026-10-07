import { describe, expect, it } from 'vitest'
import { apiStatusLabel } from '@/modules/api-test/define/statusLabel'

describe('apiStatusLabel', () => {
  it('uses the same words as the status dictionary', () => {
    expect(apiStatusLabel('DRAFT')).toBe('草稿')
    expect(apiStatusLabel('PUBLISHED')).toBe('已发布')
    expect(apiStatusLabel('DEPRECATED')).toBe('已废弃')
    expect(apiStatusLabel(null)).toBe('草稿')
    expect(apiStatusLabel('CUSTOM')).toBe('CUSTOM')
  })
})
