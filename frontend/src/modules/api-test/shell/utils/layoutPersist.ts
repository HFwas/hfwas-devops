export const SIDEBAR_WIDTH_KEY = 'api-test.sidebarWidth'
export const RESPONSE_HEIGHT_KEY = 'api-test.responseHeight'

export const SIDEBAR_MIN = 200
export const SIDEBAR_MAX = 480
export const RESPONSE_MIN = 120
export const RESPONSE_MAX_VH = 0.6

export function responseMaxHeight(viewportHeight = defaultViewportHeight()): number {
  return viewportHeight * RESPONSE_MAX_VH
}

export function clampSidebarWidth(value: number): number {
  return Math.min(SIDEBAR_MAX, Math.max(SIDEBAR_MIN, value))
}

export function clampResponseHeight(value: number, viewportHeight = defaultViewportHeight()): number {
  const max = responseMaxHeight(viewportHeight)
  return Math.min(max, Math.max(RESPONSE_MIN, value))
}

export function readStoredLayout(): { sidebarWidth?: number; responseHeight?: number } {
  const sidebarWidth = readStoredNumber(SIDEBAR_WIDTH_KEY)
  const responseHeight = readStoredNumber(RESPONSE_HEIGHT_KEY)
  const partial: { sidebarWidth?: number; responseHeight?: number } = {}
  if (sidebarWidth != null) {
    partial.sidebarWidth = clampSidebarWidth(sidebarWidth)
  }
  if (responseHeight != null) {
    partial.responseHeight = clampResponseHeight(responseHeight)
  }
  return partial
}

export function persistLayout(partial: { sidebarWidth?: number; responseHeight?: number }) {
  if (partial.sidebarWidth != null) {
    writeStoredNumber(SIDEBAR_WIDTH_KEY, clampSidebarWidth(partial.sidebarWidth))
  }
  if (partial.responseHeight != null) {
    writeStoredNumber(RESPONSE_HEIGHT_KEY, clampResponseHeight(partial.responseHeight))
  }
}

function defaultViewportHeight(): number {
  return typeof window !== 'undefined' ? window.innerHeight : 800
}

function readStoredNumber(key: string): number | null {
  try {
    const raw = sessionStorage.getItem(key)
    if (raw == null || raw === '') return null
    const value = Number(raw)
    return Number.isFinite(value) ? value : null
  } catch {
    return null
  }
}

function writeStoredNumber(key: string, value: number) {
  try {
    sessionStorage.setItem(key, String(value))
  } catch {
    // ignore quota / private-mode failures
  }
}
