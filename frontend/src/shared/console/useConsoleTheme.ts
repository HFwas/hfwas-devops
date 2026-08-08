/**
 * 控制台明暗主题
 * 仅切换 naive-ui 主题 + html.dark 类，卡片配色通过 --wb-* 变量跟随。
 */
const STORAGE_KEY = 'hfwas.console.theme'

function readStored(): boolean {
  const stored = localStorage.getItem(STORAGE_KEY)
  if (stored === 'dark') return true
  if (stored === 'light') return false
  return window.matchMedia?.('(prefers-color-scheme: dark)').matches ?? false
}

const isDark = ref(readStored())

function apply(dark: boolean) {
  document.documentElement.classList.toggle('dark', dark)
}

apply(isDark.value)

export function useConsoleTheme() {
  function toggle() {
    isDark.value = !isDark.value
    localStorage.setItem(STORAGE_KEY, isDark.value ? 'dark' : 'light')
    apply(isDark.value)
  }

  return { isDark, toggle }
}
