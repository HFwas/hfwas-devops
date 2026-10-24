import { describe, expect, it } from 'vitest'
import { renderMarkdown } from '@/modules/pm/utils/markdown'

describe('renderMarkdown', () => {
  it('rendersHeadingsListsAndBold', () => {
    const html = renderMarkdown('## 问题\n\n- 第一步\n- **重点**')
    expect(html).toContain('<h2')
    expect(html).toContain('<ul')
    expect(html).toContain('<strong>重点</strong>')
  })

  it('rendersCodeBlocksAndInlineCode', () => {
    const html = renderMarkdown('```\nconst a = 1\n```\n\n使用 `npm test`')
    expect(html).toContain('<pre')
    expect(html).toContain('>npm test</code>')
  })

  it('escapesHtmlAndBlocksJavascriptLinks', () => {
    const html = renderMarkdown('<script>alert(1)</script>[bad](javascript:alert(1)) [ok](https://example.com)')
    expect(html).not.toContain('<script>')
    expect(html).toContain('&lt;script&gt;')
    expect(html).not.toContain('href="javascript')
    expect(html).toContain('href="https://example.com"')
    expect(html).toContain('rel="noreferrer noopener"')
  })

  it('doesNotItalicizePlainAsterisksOrTouchCode', () => {
    expect(renderMarkdown('2 * 3 * 4')).not.toContain('<em>')
    const html = renderMarkdown('`**not bold**`')
    expect(html).toContain('<code')
    expect(html).not.toContain('<strong>not bold</strong>')
  })

  it('supportsMailtoLinks', () => {
    expect(renderMarkdown('[mail](mailto:a@b.com)')).toContain('href="mailto:a@b.com"')
  })

  it('returnsEmptyForBlankSource', () => {
    expect(renderMarkdown('')).toBe('')
    expect(renderMarkdown(undefined)).toBe('')
  })
})
