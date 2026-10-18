import { describe, expect, it } from 'vitest'
import { readErrorPayload } from '@/shared/api/request'

describe('readErrorPayload', () => {
  it('parses a JSON blob error body', async () => {
    const blob = new Blob(
      [JSON.stringify({ code: 20002, msg: '项目不存在或无权访问' })],
      { type: 'application/json' },
    )
    await expect(readErrorPayload(blob)).resolves.toMatchObject({ code: 20002 })
  })

  it('keeps a plain JSON object', async () => {
    await expect(readErrorPayload({ code: 11010, msg: '未登录或租户上下文缺失' }))
      .resolves.toMatchObject({ code: 11010 })
  })

  it('returns undefined for a non-JSON blob', async () => {
    await expect(readErrorPayload(new Blob(['not json']))).resolves.toBeUndefined()
  })
})
