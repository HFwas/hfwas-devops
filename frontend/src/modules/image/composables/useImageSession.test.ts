import { describe, expect, it } from 'vitest'
import { formatFileSize, isPendingConvert, rawTagEntries, waitForConvert } from './imageHelpers'
import type { ImageConvertVO } from '@/modules/image/types/image'

describe('formatFileSize', () => {
  it('formats bytes and megabytes', () => {
    expect(formatFileSize(512)).toBe('512 B')
    expect(formatFileSize(2048)).toBe('2.0 KB')
    expect(formatFileSize(50 * 1024 * 1024)).toBe('50.0 MB')
  })
})

describe('isPendingConvert', () => {
  it('treats queued and running as pending', () => {
    expect(isPendingConvert('queued')).toBe(true)
    expect(isPendingConvert('running')).toBe(true)
    expect(isPendingConvert('completed')).toBe(false)
    expect(isPendingConvert('failed')).toBe(false)
  })
})

describe('rawTagEntries', () => {
  it('flattens maker notes for the inspector', () => {
    expect(rawTagEntries({ 'MakerNotes:LensModel': 'iPhone 15 back camera' })).toEqual([
      { key: 'MakerNotes:LensModel', value: 'iPhone 15 back camera' },
    ])
  })
})

describe('waitForConvert', () => {
  it('polls until the job completes', async () => {
    const polls: string[] = []
    const initial: ImageConvertVO = {
      sessionId: 's1',
      resultFileName: '',
      resultSize: 0,
      mimeType: '',
      width: 0,
      height: 0,
      downloadUrl: '',
      strippedGps: false,
      status: 'queued',
      jobId: 'j1',
    }
    const done = await waitForConvert(
      's1',
      initial,
      async (_sessionId, jobId) => {
        polls.push(jobId)
        return {
          ...initial,
          status: polls.length < 2 ? 'running' : 'completed',
          resultFileName: 'out.png',
          resultSize: 12,
          mimeType: 'image/png',
          width: 10,
          height: 8,
        }
      },
      async () => undefined,
    )
    expect(done.status).toBe('completed')
    expect(polls).toEqual(['j1', 'j1'])
  })

  it('throws when the job fails', async () => {
    const initial: ImageConvertVO = {
      sessionId: 's1',
      resultFileName: '',
      resultSize: 0,
      mimeType: '',
      width: 0,
      height: 0,
      downloadUrl: '',
      strippedGps: false,
      status: 'queued',
      jobId: 'j1',
    }
    await expect(
      waitForConvert(
        's1',
        initial,
        async () => ({ ...initial, status: 'failed', errorMessage: 'boom' }),
        async () => undefined,
      ),
    ).rejects.toThrow('boom')
  })
})
