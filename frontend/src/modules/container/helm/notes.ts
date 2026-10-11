export type HelmNoteBlock =
  | { type: 'heading'; text: string }
  | { type: 'paragraph'; text: string }
  | { type: 'list'; ordered: boolean; items: string[] }
  | { type: 'code'; text: string }

const COMMAND_START =
  /^(?:\$\s+)?(?:export|echo|printf|kubectl|helm|curl|wget|openssl|cat|sudo|chmod|chown|cd|source|env|set|python3?|bash|sh|mkdir|cp|mv|rm|psql|mysql|redis-cli|mongo|pg_isready|nc|nslookup|dig)\b/

/**
 * 把 Helm NOTES.txt 拆成标题、段落、列表和命令块。不增删原文，只决定怎么排。
 */
export function parseHelmNotes(notes: string): HelmNoteBlock[] {
  const normalized = notes.replace(/\r\n/g, '\n').trim()
  if (!normalized) return []
  const blocks: HelmNoteBlock[] = []
  for (const part of splitFences(normalized)) {
    if (part.kind === 'code') {
      const text = part.text.replace(/^\n+|\n+$/g, '')
      if (text) blocks.push({ type: 'code', text })
      continue
    }
    for (const chunk of part.text.split(/\n{2,}/)) {
      const lines = chunk.split('\n')
      if (lines.every((line) => !line.trim())) continue
      if (isHeading(lines)) {
        blocks.push({ type: 'heading', text: lines[0].replace(/^#{1,6}\s+/, '').trim() })
        continue
      }
      if (isIndentedSnippet(lines)) {
        const text = dedent(lines)
        if (text) blocks.push({ type: 'code', text })
        continue
      }
      pushMixed(blocks, lines)
    }
  }
  return blocks
}

function splitFences(text: string): Array<{ kind: 'text' | 'code'; text: string }> {
  const parts: Array<{ kind: 'text' | 'code'; text: string }> = []
  const pattern = /```[^\n]*\n([\s\S]*?)```/g
  let last = 0
  for (const match of text.matchAll(pattern)) {
    const index = match.index ?? 0
    if (index > last) parts.push({ kind: 'text', text: text.slice(last, index) })
    parts.push({ kind: 'code', text: match[1] ?? '' })
    last = index + match[0].length
  }
  if (last < text.length) parts.push({ kind: 'text', text: text.slice(last) })
  if (parts.length === 0) parts.push({ kind: 'text', text })
  return parts
}

function pushMixed(blocks: HelmNoteBlock[], lines: string[]) {
  const groups: Array<{ kind: 'prose' | 'code'; lines: string[] }> = []
  let inCode = false
  let previous = ''
  for (const line of lines) {
    const command = isCommandLine(line)
    const continuation = isContinuation(line, inCode, previous)
    const kind = command || continuation ? 'code' : 'prose'
    if (kind === 'prose') inCode = false
    else inCode = true
    const current = groups[groups.length - 1]
    if (!current || current.kind !== kind) groups.push({ kind, lines: [line] })
    else current.lines.push(line)
    previous = line
  }
  for (const group of groups) {
    if (group.kind === 'code') {
      const text = dedent(group.lines)
      if (text) blocks.push({ type: 'code', text })
      continue
    }
    const list = asList(group.lines)
    if (list) {
      blocks.push({ type: 'list', ordered: list.ordered, items: list.items })
      continue
    }
    const text = group.lines.join('\n').trim()
    if (text) blocks.push({ type: 'paragraph', text })
  }
}

function isHeading(lines: string[]): boolean {
  return lines.length === 1 && /^#{1,6}\s+\S/.test(lines[0].trim())
}

function isIndentedSnippet(lines: string[]): boolean {
  const nonempty = lines.filter((line) => line.trim())
  return nonempty.length > 0 && nonempty.every((line) => /^[ \t]{2,}\S/.test(line))
}

function isCommandLine(line: string): boolean {
  const trimmed = line.trim()
  if (!trimmed || trimmed.startsWith('#')) return false
  if (COMMAND_START.test(trimmed)) return true
  return /^[A-Za-z_][A-Za-z0-9_]*=\S/.test(trimmed) && /\$\(/.test(trimmed)
}

function isContinuation(line: string, inCode: boolean, previous: string): boolean {
  if (!inCode) return false
  if (!line.trim()) return true
  if (/\\\s*$/.test(previous.trim())) return true
  if (/^[ \t]+\S/.test(line)) return true
  if (/^\s*#/.test(line)) return true
  return /^(?:&&|\|\|)/.test(line.trim())
}

function asList(lines: string[]): { ordered: boolean; items: string[] } | null {
  const nonempty = lines.map((line) => line.trim()).filter(Boolean)
  if (nonempty.length === 0) return null
  const ordered = nonempty.every((line) => /^\d+[.)]\s+\S/.test(line))
  const bullets = nonempty.every((line) => /^[-*]\s+\S/.test(line))
  if (!ordered && !bullets) return null
  return {
    ordered,
    items: nonempty.map((line) => line.replace(/^(?:\d+[.)]|[-*])\s+/, '')),
  }
}

function dedent(lines: string[]): string {
  const nonempty = lines.filter((line) => line.trim().length > 0)
  const min = nonempty.reduce((width, line) => {
    const indent = line.match(/^[ \t]*/)?.[0].length ?? 0
    return Math.min(width, indent)
  }, Number.POSITIVE_INFINITY)
  const trim = Number.isFinite(min) ? min : 0
  return lines
    .map((line) => (line.trim().length === 0 ? '' : line.slice(trim)))
    .join('\n')
    .replace(/^\n+|\n+$/g, '')
}
