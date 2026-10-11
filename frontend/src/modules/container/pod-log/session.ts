const LOG_TOKEN = /(\bERROR\b|\bFATAL\b|\bWARNING\b|\bWARN\b)/g

export type LogTone = 'plain' | 'danger' | 'warn'

export interface LogToken {
  text: string
  tone: LogTone
}

/** 只给 ERROR / FATAL / WARN 上色，不改日志原文。 */
export function tokenizeLog(text: string): LogToken[] {
  return text
    .split(LOG_TOKEN)
    .filter((part) => part !== '')
    .map((part) => {
      if (part === 'ERROR' || part === 'FATAL') return { text: part, tone: 'danger' }
      if (part === 'WARN' || part === 'WARNING') return { text: part, tone: 'warn' }
      return { text: part, tone: 'plain' }
    })
}

export function logDownloadName(pod: string, container: string, when = new Date()): string {
  const stamp = when.toISOString().replace(/[:.]/g, '-')
  const safe = (value: string) => value.replace(/[^\w.-]+/g, '_') || 'log'
  return `${safe(pod)}-${container ? safe(container) : 'default'}-${stamp}.log`
}

export function downloadTextFile(filename: string, text: string) {
  const blob = new Blob([text], { type: 'text/plain;charset=utf-8' })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = filename
  link.click()
  URL.revokeObjectURL(url)
}
