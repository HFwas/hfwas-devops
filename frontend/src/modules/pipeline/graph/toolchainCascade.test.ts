import { describe, expect, it } from 'vitest'
import { coerceToolchain, runtimesFor, toolsFor, uniqueStacks } from './toolchainCascade'
import type { ToolchainOption } from '@/modules/pipeline/types/pipeline'

const options: ToolchainOption[] = [
  {
    stack: 'JAVA_MAVEN',
    runtimeVersion: '17',
    toolVersion: '3.8',
    image: 'maven:3.8.8-eclipse-temurin-17',
    buildCommand: 'mvn -B -DskipTests package',
    testCommand: 'mvn -B test',
  },
  {
    stack: 'JAVA_MAVEN',
    runtimeVersion: '21',
    toolVersion: '3.9',
    image: 'maven:3.9.9-eclipse-temurin-21',
    buildCommand: 'mvn -B -DskipTests package',
    testCommand: 'mvn -B test',
  },
  {
    stack: 'GO',
    runtimeVersion: '1.23',
    toolVersion: null,
    image: 'golang:1.23',
    buildCommand: 'go build ./...',
    testCommand: 'go test ./...',
  },
]

describe('toolchain cascade', () => {
  it('lists stacks, runtimes and tools from the matrix', () => {
    expect(uniqueStacks(options)).toEqual(['JAVA_MAVEN', 'GO'])
    expect(runtimesFor(options, 'JAVA_MAVEN')).toEqual(['17', '21'])
    expect(toolsFor(options, 'JAVA_MAVEN', '21')).toEqual(['3.9'])
    expect(toolsFor(options, 'GO', '1.23')).toEqual([])
  })

  it('falls back to the first legal combo when switching stack', () => {
    const next = coerceToolchain(options, 'GO', '21', '3.9')
    expect(next?.stack).toBe('GO')
    expect(next?.runtimeVersion).toBe('1.23')
    expect(next?.toolVersion ?? null).toBeNull()
  })
})
