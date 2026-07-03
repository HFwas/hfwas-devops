const STORAGE_KEY = 'pm.authorName'

export function getCurrentAuthorName(): string {
  return localStorage.getItem(STORAGE_KEY) || '当前用户'
}

export function setCurrentAuthorName(name: string) {
  localStorage.setItem(STORAGE_KEY, name.trim() || '当前用户')
}

export function avatarColor(name: string): string {
  const colors = ['#f0a020', '#18a058', '#2080f0', '#d03050', '#8b5cf6']
  let hash = 0
  for (const ch of name) hash = (hash + ch.charCodeAt(0)) % colors.length
  return colors[hash]!
}

export function avatarInitial(name: string): string {
  return name.trim().charAt(0) || '?'
}

export function formatDateTime(value?: string): string {
  if (!value) return ''
  return value.replace('T', ' ').slice(0, 19)
}

export function formatCommentHtml(content: string): string {
  const escaped = content
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
  return escaped.replace(
    /(https?:\/\/[^\s<]+)/g,
    '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>',
  )
}
