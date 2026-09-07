import { describe, expect, it } from 'vitest'
import {
  cropForConvert,
  formatFileSize,
  isFullFrameCrop,
  isPendingConvert,
  mapPool,
  rawTagEntries,
  sizeAfterUserRotate,
  waitForConvert,
} from './imageHelpers'
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

  it('throws when polling exceeds timeout', async () => {
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
        async () => initial,
        async () => undefined,
        -1,
      ),
    ).rejects.toThrow('转换超时')
  })
})

describe('cropForConvert', () => {
  it('drops the default full-frame cropper box', () => {
    expect(
      isFullFrameCrop({ x: 0, y: 0, width: 2048, height: 1536 }, 2048, 1536),
    ).toBe(true)
    expect(
      cropForConvert({
        crop: { x: 0, y: 0, width: 2048, height: 1536 },
        displayedWidth: 2048,
        displayedHeight: 1536,
        orientedWidth: 4032,
        orientedHeight: 3024,
      }),
    ).toBeNull()
  })

  it('scales a real crop from server preview pixels to oriented original pixels', () => {
    expect(
      cropForConvert({
        crop: { x: 100, y: 50, width: 400, height: 300 },
        displayedWidth: 2048,
        displayedHeight: 1536,
        orientedWidth: 4032,
        orientedHeight: 3024,
      }),
    ).toEqual({ x: 197, y: 98, width: 788, height: 591 })
  })

  it('does not treat an iPhone preview full box as a crop of the unoriented file', () => {
    expect(
      cropForConvert({
        crop: { x: 0, y: 0, width: 1536, height: 2048 },
        displayedWidth: 1536,
        displayedHeight: 2048,
        orientedWidth: 3024,
        orientedHeight: 4032,
      }),
    ).toBeNull()
  })

  it('maps crop into post-rotate space when the canvas already baked a 90° turn', () => {
    expect(sizeAfterUserRotate(3024, 4032, 90)).toEqual({ width: 4032, height: 3024 })
    expect(
      cropForConvert({
        crop: { x: 10, y: 20, width: 100, height: 80 },
        displayedWidth: 2048,
        displayedHeight: 1536,
        orientedWidth: 3024,
        orientedHeight: 4032,
        rotate: 90,
      }),
    ).toEqual({ x: 20, y: 39, width: 197, height: 158 })
  })
})

describe('mapPool', () => {
  it('does not start more than the concurrency limit', async () => {
    const started: number[] = []
    const releases: Array<() => void> = []
    const running = mapPool([1, 2, 3], 2, (n) => {
      started.push(n)
      return new Promise<number>((resolve) => {
        releases.push(() => resolve(n))
      })
    })
    await Promise.resolve()
    expect(started).toEqual([1, 2])
    releases[0]()
    await Promise.resolve()
    await Promise.resolve()
    expect(started).toEqual([1, 2, 3])
    releases[1]()
    releases[2]()
    await expect(running).resolves.toEqual([1, 2, 3])
  })
})
