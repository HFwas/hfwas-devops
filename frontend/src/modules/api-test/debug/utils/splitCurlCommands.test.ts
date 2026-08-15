import { describe, expect, it } from 'vitest'
import { splitCurlCommands } from './splitCurlCommands'

describe('splitCurlCommands', () => {
  it('returns empty for blank input', () => {
    expect(splitCurlCommands('')).toEqual([])
    expect(splitCurlCommands('   \n  ')).toEqual([])
  })

  it('keeps a single curl command as one entry', () => {
    const raw = `curl 'https://api.example.com/users' -H 'Accept: application/json' -X GET`
    expect(splitCurlCommands(raw)).toEqual([raw])
  })

  it('splits Chrome Copy-all-listed multi curl paste', () => {
    const raw = [
      `curl 'https://a.example.com/queryFirst' \\`,
      `  -H 'accept: application/json' \\`,
      `  --compressed`,
      `curl 'https://b.example.com/envInfo' \\`,
      `  -H 'accept: application/json'`,
      `curl -X POST 'https://c.example.com/list' -d '{}'`,
    ].join('\n')

    const parts = splitCurlCommands(raw)
    expect(parts).toHaveLength(3)
    expect(parts[0]).toContain('queryFirst')
    expect(parts[1]).toContain('envInfo')
    expect(parts[2]).toContain('/list')
  })

  it('treats non-curl blob as a single command', () => {
    expect(splitCurlCommands('not a curl')).toEqual(['not a curl'])
  })
})
