import type { ImageConvertVO } from '@/modules/image/types/image'

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
