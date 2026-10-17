/** 统一 PM 页面的时间展示：空值占位，非空截到秒。 */
export function formatDateTime(value?: string | null): string {
  if (!value) return '—'
  return value.replace('T', ' ').slice(0, 19)
}
