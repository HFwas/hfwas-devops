import { describe, expect, it } from 'vitest'
import { parseHelmNotes } from '@/modules/container/helm/notes'

const NOTES = `
# Nginx

NGINX can be accessed through the following DNS name from within your cluster:

    nginx.default.svc.cluster.local (port 80)

1. Get the application URL by running these commands:
  export NODE_PORT=$(kubectl get --namespace default -o jsonpath="{.spec.ports[0].nodePort}" services demo)
  echo http://$NODE_IP:$NODE_PORT

** Please be patient while the chart is being deployed **
`.trim()

describe('parseHelmNotes', () => {
  it('keeps a plain sentence as one paragraph', () => {
    expect(parseHelmNotes('示例 Release。升级会新开一个 revision。')).toEqual([
      { type: 'paragraph', text: '示例 Release。升级会新开一个 revision。' },
    ])
  })

  it('splits headings, indented snippets, and shell commands without adding text', () => {
    const blocks = parseHelmNotes(NOTES)
    expect(blocks.map((block) => block.type)).toEqual(['heading', 'paragraph', 'code', 'list', 'code', 'paragraph'])
    expect(blocks[0]).toEqual({ type: 'heading', text: 'Nginx' })
    expect(blocks[2]).toMatchObject({
      type: 'code',
      text: 'nginx.default.svc.cluster.local (port 80)',
    })
    const commands = blocks.find((block) => block.type === 'code' && block.text.includes('export NODE_PORT'))
    expect(commands?.type === 'code' && commands.text.startsWith('export NODE_PORT=$(kubectl get')).toBe(true)
    expect(commands?.type === 'code' && commands.text).toContain('echo http://$NODE_IP:$NODE_PORT')
    const joined = blocks
      .map((block) => (block.type === 'list' ? block.items.join('\n') : block.text))
      .join('\n')
    expect(joined).toContain('Get the application URL by running these commands:')
    expect(joined).toContain('Please be patient while the chart is being deployed')
    expect(joined).not.toContain('例如')
  })

  it('keeps fenced commands as a code block', () => {
    const blocks = parseHelmNotes('Run:\n\n```bash\nkubectl get pods\n```')
    expect(blocks).toEqual([
      { type: 'paragraph', text: 'Run:' },
      { type: 'code', text: 'kubectl get pods' },
    ])
  })
})
