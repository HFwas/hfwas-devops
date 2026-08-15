import { beforeEach, describe, expect, it } from 'vitest'
import {
  RESPONSE_HEIGHT_KEY,
  SIDEBAR_WIDTH_KEY,
  clampResponseHeight,
  clampSidebarWidth,
  persistLayout,
  readStoredLayout,
  responseMaxHeight,
} from './layoutPersist'

describe('layoutPersist', () => {
  beforeEach(() => {
    sessionStorage.clear()
  })

  it('clamps sidebar width to 200–480', () => {
    expect(clampSidebarWidth(199)).toBe(200)
    expect(clampSidebarWidth(200)).toBe(200)
    expect(clampSidebarWidth(360)).toBe(360)
    expect(clampSidebarWidth(480)).toBe(480)
    expect(clampSidebarWidth(481)).toBe(480)
  })

  it('clamps response height to 120–60vh', () => {
    expect(responseMaxHeight(1000)).toBe(600)
    expect(clampResponseHeight(119, 1000)).toBe(120)
    expect(clampResponseHeight(120, 1000)).toBe(120)
    expect(clampResponseHeight(400, 1000)).toBe(400)
    expect(clampResponseHeight(600, 1000)).toBe(600)
    expect(clampResponseHeight(601, 1000)).toBe(600)
  })

  it('reads and clamps stored layout keys verbatim', () => {
    sessionStorage.setItem(SIDEBAR_WIDTH_KEY, '360')
    sessionStorage.setItem(RESPONSE_HEIGHT_KEY, '240')
    expect(readStoredLayout()).toEqual({ sidebarWidth: 360, responseHeight: 240 })
  })

  it('clamps out-of-range stored values and ignores invalid numbers', () => {
    sessionStorage.setItem(SIDEBAR_WIDTH_KEY, '80')
    sessionStorage.setItem(RESPONSE_HEIGHT_KEY, 'not-a-number')
    expect(readStoredLayout()).toEqual({ sidebarWidth: 200 })
  })

  it('persists layout sizes to the exact sessionStorage keys', () => {
    persistLayout({ sidebarWidth: 320, responseHeight: 280 })
    expect(sessionStorage.getItem(SIDEBAR_WIDTH_KEY)).toBe('320')
    expect(sessionStorage.getItem(RESPONSE_HEIGHT_KEY)).toBe('280')
  })

  it('persists clamped values when given out-of-range sizes', () => {
    persistLayout({ sidebarWidth: 10, responseHeight: 9999 })
    expect(sessionStorage.getItem(SIDEBAR_WIDTH_KEY)).toBe('200')
    expect(Number(sessionStorage.getItem(RESPONSE_HEIGHT_KEY))).toBe(responseMaxHeight())
  })
})
