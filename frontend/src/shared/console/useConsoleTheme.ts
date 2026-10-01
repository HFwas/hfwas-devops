const STORAGE_KEY = 'hfwas.console.theme'

function readStored(): boolean {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark') return true
  if (stored === 'light') return false
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

let isDark = readStored()
const listeners = new Set<() => void>()

function apply(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}

apply(isDark)

export function getConsoleTheme() {
  return isDark
}

export function subscribeConsoleTheme(listener: () => void) {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

export function toggleConsoleTheme() {
  isDark = !isDark
  localStorage.setItem(STORAGE_KEY, isDark ? 'dark' : 'light')
  apply(isDark)
  listeners.forEach((listener) => listener())
}
