import type { ImageConvertVO, ImageCrop } from '@/modules/image/types/image'

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`
}

export function isPendingConvert(status?: string | null): boolean {
  return status === 'queued' || status === 'running'
}

export function rawTagEntries(rawTags?: Record<string, string> | null): { key: string; value: string }[] {
  if (!rawTags) return []
  return Object.entries(rawTags).map(([key, value]) => ({ key, value }))
}

export async function waitForConvert(
  sessionId: string,
  initial: ImageConvertVO,
  poll: (sessionId: string, jobId: string) => Promise<ImageConvertVO>,
  sleepFn: (ms: number) => Promise<void> = (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
  timeoutMs = 120000,
): Promise<ImageConvertVO> {
  let current = initial
  const start = Date.now()
  while (isPendingConvert(current.status)) {
    if (!current.jobId) {
      throw new Error('转换任务缺少 jobId')
    }
    if (Date.now() - start > timeoutMs) {
      throw new Error('转换超时')
    }
    await sleepFn(400)
    current = await poll(sessionId, current.jobId)
  }
  if (current.status === 'failed') {
    throw new Error(current.errorMessage || '转换失败')
  }
  return current
}

export function nextLocalId(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) {
    return crypto.randomUUID()
  }
  return `img-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`
}

export function isFullFrameCrop(
  crop: ImageCrop,
  displayedWidth: number,
  displayedHeight: number,
  epsilon = 2,
): boolean {
  if (displayedWidth <= 0 || displayedHeight <= 0) {
    return false
  }
  return (
    crop.x <= epsilon &&
    crop.y <= epsilon &&
    Math.abs(crop.width - displayedWidth) <= epsilon &&
    Math.abs(crop.height - displayedHeight) <= epsilon
  )
}

export function sizeAfterUserRotate(width: number, height: number, rotate: number): { width: number; height: number } {
  const r = ((rotate % 360) + 360) % 360
  if (r === 90 || r === 270) {
    return { width: height, height: width }
  }
  return { width, height }
}

export function cropForConvert(input: {
  crop: ImageCrop | null | undefined
  displayedWidth: number
  displayedHeight: number
  orientedWidth: number
  orientedHeight: number
  rotate?: number
}): ImageCrop | null {
  const crop = input.crop
  if (!crop || crop.width <= 0 || crop.height <= 0) {
    return null
  }
  if (input.displayedWidth <= 0 || input.displayedHeight <= 0) {
    return null
  }
  if (isFullFrameCrop(crop, input.displayedWidth, input.displayedHeight)) {
    return null
  }
  const target = sizeAfterUserRotate(input.orientedWidth, input.orientedHeight, input.rotate ?? 0)
  if (target.width <= 0 || target.height <= 0) {
    return null
  }
  if (input.displayedWidth === target.width && input.displayedHeight === target.height) {
    return { ...crop }
  }
  const sx = target.width / input.displayedWidth
  const sy = target.height / input.displayedHeight
  return {
    x: Math.max(0, Math.round(crop.x * sx)),
    y: Math.max(0, Math.round(crop.y * sy)),
    width: Math.max(1, Math.round(crop.width * sx)),
    height: Math.max(1, Math.round(crop.height * sy)),
  }
}

export async function mapPool<T, R>(
  items: T[],
  concurrency: number,
  fn: (item: T, index: number) => Promise<R>,
): Promise<R[]> {
  if (items.length === 0) {
    return []
  }
  const limit = Math.max(1, Math.min(concurrency, items.length))
  const results: R[] = new Array(items.length)
  let next = 0
  async function worker() {
    while (true) {
      const index = next
      next += 1
      if (index >= items.length) {
        return
      }
      results[index] = await fn(items[index], index)
    }
  }
  await Promise.all(Array.from({ length: limit }, () => worker()))
  return results
}
