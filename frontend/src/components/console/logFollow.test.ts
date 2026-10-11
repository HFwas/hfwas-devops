import { describe, expect, it } from 'vitest'
import {
  LOG_FOLLOW_THRESHOLD_PX,
  distanceFromBottom,
  followModeAfterScroll,
  scrollTopForFollow,
  shouldStickToBottom,
} from './logFollow'

const view = { scrollHeight: 1000, clientHeight: 200 }

describe('log follow helpers', () => {
  it('treats a short log and the bottom edge as stuck', () => {
    expect(shouldStickToBottom({ scrollTop: 0, scrollHeight: 80, clientHeight: 200 })).toBe(true)
    expect(distanceFromBottom({ ...view, scrollTop: 800 })).toBe(0)
    expect(shouldStickToBottom({ ...view, scrollTop: 800 - LOG_FOLLOW_THRESHOLD_PX })).toBe(true)
    expect(shouldStickToBottom({ ...view, scrollTop: 800 - LOG_FOLLOW_THRESHOLD_PX - 1 })).toBe(false)
  })

  it('pauses follow after the reader leaves the bottom, and resumes only when asked or they return', () => {
    expect(followModeAfterScroll({ ...view, scrollTop: 100 })).toBe('paused')
    expect(followModeAfterScroll({ ...view, scrollTop: 790 })).toBe('follow')
    expect(scrollTopForFollow('paused', { ...view, scrollTop: 100 })).toBeNull()
    expect(scrollTopForFollow('follow', { ...view, scrollTop: 100 })).toBe(800)
    expect(scrollTopForFollow('follow', { ...view, scrollTop: 800 })).toBeNull()
  })
})
