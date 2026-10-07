export function apiStatusLabel(status?: string | null): string {
  if (status === 'PUBLISHED') return '已发布'
  if (status === 'DEPRECATED') return '已废弃'
  if (status === 'DRAFT' || !status?.trim()) return '草稿'
  return status
}
