export function interpolate(
  template: string,
  vars: Record<string, string>,
  secrets?: Record<string, boolean>,
): string {
  return template.replace(/\{\{\s*([^}]+?)\s*\}\}/g, (full, rawKey: string) => {
    const key = rawKey.trim()
    if (!(key in vars)) return full
    if (secrets?.[key]) return '******'
    return vars[key]
  })
}
