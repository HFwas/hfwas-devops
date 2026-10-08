import { describe, expect, it } from 'vitest'
import { valuesYamlError } from '@/modules/container/helm/yaml'

describe('valuesYamlError', () => {
  it('accepts empty values, comments, and YAML mappings', () => {
    expect(valuesYamlError('')).toBeNull()
    expect(valuesYamlError('   \n# only a comment\n')).toBeNull()
    expect(valuesYamlError('replicaCount: 1\n')).toBeNull()
    expect(valuesYamlError('{replicaCount: 1, service: {type: ClusterIP}}\n')).toBeNull()
    expect(valuesYamlError('items:\n  - nginx\n')).toBeNull()
    expect(valuesYamlError('defaults: &defaults\n  enabled: true\nprod:\n  <<: *defaults\n  replicas: 2\n')).toBeNull()
  })

  it('rejects invalid YAML syntax', () => {
    expect(valuesYamlError('replicaCount: [\n')).toBe('Values 不是合法的 YAML 对象')
    expect(valuesYamlError('foo: bar: baz\n')).toBe('Values 不是合法的 YAML 对象')
    expect(valuesYamlError('a: 1\n---\nb: 2\n')).toBe('Values 不是合法的 YAML 对象')
    expect(valuesYamlError('bad:\n\tindent: 1\n')).toBe('Values 不是合法的 YAML 对象')
    expect(valuesYamlError('a: *missing\n')).toBe('Values 不是合法的 YAML 对象')
  })

  it('rejects a root that is not a mapping', () => {
    expect(valuesYamlError('- a\n')).toBe('Values 必须是 YAML 对象')
    expect(valuesYamlError('[1, 2]\n')).toBe('Values 必须是 YAML 对象')
    expect(valuesYamlError('replicaCount\n')).toBe('Values 必须是 YAML 对象')
    expect(valuesYamlError('42\n')).toBe('Values 必须是 YAML 对象')
    expect(valuesYamlError('true\n')).toBe('Values 必须是 YAML 对象')
  })

  it('rejects values over the size limit', () => {
    expect(valuesYamlError(`${'a'.repeat(1024 * 1024 + 1)}`)).toBe('Values 超过大小限制')
  })
})
