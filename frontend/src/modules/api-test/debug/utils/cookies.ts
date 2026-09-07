export interface ParsedCookie {
  name: string
  value: string
  extra: string
}

export function parseSetCookieHeaders(headers: Record<string, string> | null | undefined): ParsedCookie[] {
  if (!headers) return []
  const raw = Object.entries(headers).find(([k]) => k.toLowerCase() === 'set-cookie')?.[1]
  if (!raw) return []
  return splitSetCookie(raw).flatMap((part) => {
    const [nv, ...attrs] = part.split(';').map((s) => s.trim()).filter(Boolean)
    if (!nv) return []
    const eq = nv.indexOf('=')
    const name = eq >= 0 ? nv.slice(0, eq) : nv
    const value = eq >= 0 ? nv.slice(eq + 1) : ''
    return [{ name, value, extra: attrs.join('; ') }]
  })
}

/** Split combined Set-Cookie values on commas that start a new cookie (name=). */
function splitSetCookie(header: string): string[] {
  const parts: string[] = []
  let buf = ''
  for (const token of header.split(',')) {
    if (/^[A-Za-z_][A-Za-z0-9_-]*=/.test(token.trim()) && buf) {
      parts.push(buf.trim())
      buf = token
    } else if (!buf) {
      buf = token
    } else {
      buf += `,${token}`
    }
  }
  if (buf.trim()) parts.push(buf.trim())
  return parts
}
