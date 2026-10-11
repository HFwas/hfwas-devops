import type { ReactNode } from 'react'
import { parseHelmNotes } from '@/modules/container/helm/notes'

export function HelmNotes({ notes }: { notes: string }) {
  const blocks = parseHelmNotes(notes)
  if (blocks.length === 0) return null
  return (
    <div className="flex flex-col gap-3">
      {blocks.map((block, index) => {
        if (block.type === 'heading') {
          return (
            <p key={index} className="text-sm font-semibold">
              <InlineText text={block.text} />
            </p>
          )
        }
        if (block.type === 'code') {
          return (
            <pre key={index} className="overflow-x-auto rounded-md border bg-muted/40 p-3 font-mono text-xs leading-5">
              <code>{block.text}</code>
            </pre>
          )
        }
        if (block.type === 'list') {
          const ListTag = block.ordered ? 'ol' : 'ul'
          return (
            <ListTag key={index} className={block.ordered ? 'list-decimal space-y-1 pl-5 text-sm' : 'list-disc space-y-1 pl-5 text-sm'}>
              {block.items.map((item, itemIndex) => (
                <li key={`${itemIndex}-${item}`}>
                  <InlineText text={item} />
                </li>
              ))}
            </ListTag>
          )
        }
        return (
          <p key={index} className="text-sm leading-6 whitespace-pre-wrap">
            <InlineText text={block.text} />
          </p>
        )
      })}
    </div>
  )
}

function InlineText({ text }: { text: string }) {
  const nodes: ReactNode[] = []
  const pattern = /\*\*([^*\s](?:[^*]*[^*\s])?)\*\*|`([^`]+)`|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g
  let last = 0
  let index = 0
  for (const match of text.matchAll(pattern)) {
    const start = match.index ?? 0
    if (start > last) nodes.push(text.slice(last, start))
    if (match[1] != null) nodes.push(<strong key={index}>{match[1]}</strong>)
    else if (match[2] != null) {
      nodes.push(
        <code key={index} className="rounded bg-muted px-1 font-mono text-xs">
          {match[2]}
        </code>,
      )
    } else if (match[3] && match[4]) {
      nodes.push(
        <a key={index} href={match[4]} target="_blank" rel="noreferrer" className="text-primary underline">
          {match[3]}
        </a>,
      )
    }
    last = start + match[0].length
    index += 1
  }
  if (last < text.length) nodes.push(text.slice(last))
  return <>{nodes}</>
}
