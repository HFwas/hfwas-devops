import { describe, expect, it } from 'vitest'
import { logDownloadName, tokenizeLog } from '@/modules/container/pod-log/session'

describe('pod log session', () => {
  it('highlights ERROR, FATAL and WARN without rewriting the line', () => {
    expect(tokenizeLog('boot ERROR disk\nWARNING slow\nWARN now\nfine')).toEqual([
      { text: 'boot ', tone: 'plain' },
      { text: 'ERROR', tone: 'danger' },
      { text: ' disk\n', tone: 'plain' },
      { text: 'WARNING', tone: 'warn' },
      { text: ' slow\n', tone: 'plain' },
      { text: 'WARN', tone: 'warn' },
      { text: ' now\nfine', tone: 'plain' },
    ])
    expect(tokenizeLog('error is lowercase').every((token) => token.tone === 'plain')).toBe(true)
  })

  it('names the download after the pod, container and time', () => {
    expect(logDownloadName('api pod', 'app', new Date('2026-10-11T05:03:00.000Z'))).toBe(
      'api_pod-app-2026-10-11T05-03-00-000Z.log',
    )
  })
})
