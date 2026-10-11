import { describe, expect, it } from 'vitest'
import { presentResourceYaml, resourceYamlForSave } from '@/modules/container/yaml/present'

const applied = {
  apiVersion: 'v1',
  kind: 'Pod',
  metadata: { name: 'demo', annotations: { note: 'keep me' } },
}

const original = JSON.stringify({
  apiVersion: 'v1',
  kind: 'Pod',
  metadata: {
    name: 'demo',
    annotations: {
      'kubectl.kubernetes.io/last-applied-configuration': JSON.stringify(applied),
      note: 'plain',
    },
  },
  unused: null,
})

describe('presentResourceYaml', () => {
  it('renders JSON resources as YAML and expands JSON annotations', () => {
    const yaml = presentResourceYaml(original)
    expect(yaml).toContain('apiVersion: v1')
    expect(yaml).toContain('kind: Pod')
    expect(yaml).toContain('note: plain')
    expect(yaml).toContain('note: keep me')
    expect(yaml).not.toContain('\\"')
    expect(yaml).not.toContain('unused')
    expect(yaml).not.toMatch(/^\s*\{/m)
  })

  it('returns the original JSON when the presented YAML is unchanged', () => {
    const presented = presentResourceYaml(original)
    expect(resourceYamlForSave(presented, original)).toBe(original)
  })

  it('saves edited YAML as JSON and keeps annotation values as strings', () => {
    const presented = presentResourceYaml(original)
    const edited = presented.replace('name: demo', 'name: other')
    const saved = JSON.parse(resourceYamlForSave(edited, original)) as {
      metadata: { name: string; annotations: Record<string, unknown> }
    }
    expect(saved.metadata.name).toBe('other')
    expect(saved.metadata.annotations.note).toBe('plain')
    const raw = saved.metadata.annotations['kubectl.kubernetes.io/last-applied-configuration']
    expect(typeof raw).toBe('string')
    const inner = JSON.parse(String(raw)) as { metadata: { name: string; annotations: { note: string } } }
    expect(inner.metadata.name).toBe('demo')
    expect(inner.metadata.annotations.note).toBe('keep me')
  })

  it('leaves an existing YAML document readable', () => {
    const yaml = presentResourceYaml('apiVersion: v1\nkind: ConfigMap\nmetadata:\n  name: demo\n')
    expect(yaml).toContain('kind: ConfigMap')
    expect(yaml).toContain('name: demo')
  })
})
