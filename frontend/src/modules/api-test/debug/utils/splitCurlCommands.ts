/**
 * Split Chrome / bash multi-cURL paste into individual commands.
 * Chrome "Copy all listed as cURL" concatenates commands with newlines.
 */
export function splitCurlCommands(input: string): string[] {
  const trimmed = input.trim()
  if (!trimmed) return []

  const text = trimmed.replace(/\r\n/g, '\n')
  const re = /(?:^|\n)([ \t]*curl\b[\s\S]*?)(?=\n[ \t]*curl\b|$)/gi
  const parts: string[] = []
  let match: RegExpExecArray | null
  while ((match = re.exec(text)) !== null) {
    const cmd = match[1]?.trim()
    if (cmd) parts.push(cmd)
  }

  if (parts.length === 0) {
    return [trimmed]
  }
  return parts
}
