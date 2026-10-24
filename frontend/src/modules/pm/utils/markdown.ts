/**
 * 极简 Markdown 渲染：标题、列表、引用、代码块、行内代码、加粗、斜体与 http(s) 链接。
 * 先转义 HTML 再做替换，链接仅允许 http/https，避免注入。
 */
function escapeHtml(value: string) {
  return value
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function inline(text: string) {
  const codes: string[] = []
  let html = text.replace(/`([^`]+)`/g, (_, code: string) => {
    codes.push(`<code class="rounded bg-muted px-1 py-0.5 text-[0.9em]">${code}</code>`)
    return `\u0000CODE${codes.length - 1}\u0000`
  })
  html = html
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    // 斜体要求星号两侧不是空白，避免把 "2 * 3 * 4" 误判为斜体。
    .replace(/(^|[^*\s])\*([^*\s][^*]*)\*/g, '$1<em>$2</em>')
    .replace(/\[([^\]]+)\]\(((?:https?:\/\/|mailto:)[^\s)]+)\)/g, '<a class="text-primary underline underline-offset-2" href="$2" target="_blank" rel="noreferrer noopener">$1</a>')
  return html.replace(/\u0000CODE(\d+)\u0000/g, (_, index: string) => codes[Number(index)])
}

export function renderMarkdown(source?: string): string {
  const text = escapeHtml(source ?? '').replace(/\r\n/g, '\n')
  if (!text.trim()) return ''

  const lines = text.split('\n')
  const out: string[] = []
  let list: 'ul' | 'ol' | null = null
  let code: string[] | null = null

  const closeList = () => {
    if (list) {
      out.push(`</${list}>`)
      list = null
    }
  }

  for (const line of lines) {
    if (line.trim().startsWith('```')) {
      if (code) {
        out.push(`<pre class="overflow-x-auto rounded-md bg-muted p-3 text-xs"><code>${code.join('\n')}</code></pre>`)
        code = null
      } else {
        closeList()
        code = []
      }
      continue
    }
    if (code) {
      code.push(line)
      continue
    }

    const heading = /^(#{1,4})\s+(.*)$/.exec(line)
    if (heading) {
      closeList()
      const level = Math.min(heading[1].length, 6)
      out.push(`<h${level} class="mt-3 mb-1 font-semibold">${inline(heading[2])}</h${level}>`)
      continue
    }

    const unordered = /^\s*[-*]\s+(.*)$/.exec(line)
    if (unordered) {
      if (list !== 'ul') {
        closeList()
        out.push('<ul class="my-1 list-disc pl-5">')
        list = 'ul'
      }
      out.push(`<li>${inline(unordered[1])}</li>`)
      continue
    }

    const ordered = /^\s*\d+\.\s+(.*)$/.exec(line)
    if (ordered) {
      if (list !== 'ol') {
        closeList()
        out.push('<ol class="my-1 list-decimal pl-5">')
        list = 'ol'
      }
      out.push(`<li>${inline(ordered[1])}</li>`)
      continue
    }

    const quote = /^&gt;\s?(.*)$/.exec(line)
    if (quote) {
      closeList()
      out.push(`<blockquote class="my-1 border-l-2 pl-3 text-muted-foreground">${inline(quote[1])}</blockquote>`)
      continue
    }

    if (!line.trim()) {
      closeList()
      continue
    }

    closeList()
    out.push(`<p class="my-1">${inline(line)}</p>`)
  }

  if (code) out.push(`<pre class="overflow-x-auto rounded-md bg-muted p-3 text-xs"><code>${code.join('\n')}</code></pre>`)
  closeList()
  return out.join('')
}
